'use strict';
const $=id=>document.getElementById(id);
const nodo=(tag,text)=>{const n=document.createElement(tag);if(text!==undefined)n.textContent=text;return n;};
let actor,solicitudId=crypto.randomUUID(),firmaPendiente=null,cargando=false;
const eventoInicial=new URLSearchParams(location.search).get('eventoId');
const evaluacionId=new URLSearchParams(location.search).get('evaluacionId');let evaluacionVersion;
async function api(path,options={}){
 const r=await fetch('/api'+path,{...options,headers:{'Content-Type':'application/json',...options.headers},cache:'no-store'});
 const body=await r.json().catch(()=>({}));
 if(!r.ok){const e=new Error(body.error || body.title || (r.status===403?'Tu rol no permite realizar esta acción.':`No se pudo completar (${r.status}).`));e.status=r.status;throw e;}
 return body;
}
function mensaje(text,error=false){$('estado').textContent=text;$('estado').classList.toggle('error',error);}
async function cargar(){
 if(cargando)return;cargando=true;
 try{
  const params=new URLSearchParams();if($('filtro-evento').value)params.set('eventoId',$('filtro-evento').value);
  const lista=await api('/solicitudes'+($('pendientes').checked?'/pendientes':'')+(params.size?'?'+params:''));
  $('solicitudes').replaceChildren();$('vacio').hidden=lista.length>0;
  for(const s of lista){
   const row=nodo('tr'),desastre=nodo('td',s.descripcionDesastre);desastre.append(nodo('small',s.eventoId));if(s.evaluacionId)desastre.append(nodo('small',`Origen: evaluación ${s.evaluacionId} · v${s.evaluacionVersion}`));
   const recurso=nodo('td',s.tipoRecurso);recurso.append(nodo('small',`Cantidad: ${s.cantidad}`));
   const estado=nodo('td',s.estado==='PENDIENTE'?'Pendiente':'Atendida');estado.append(nodo('small',new Date(s.creadaEn).toLocaleString('es-CO')));
   const acciones=nodo('td'),grupo=nodo('div');grupo.className='actions';
   if(s.estado==='PENDIENTE' && actor.rol==='COORDINADOR'){
    const b=nodo('button','Marcar atendida');b.type='button';b.onclick=async()=>{
     b.disabled=true;try{await api(`/solicitudes/${s.id}/atender`,{method:'PATCH'});await cargar();mensaje('Solicitud atendida. Se conservó su historial.');}
     catch(e){mensaje(e.message,true);await cargar();}finally{b.disabled=false;}
    };grupo.append(b);
   }
   const historial=nodo('button','Ver historial');historial.type='button';historial.className='secondary';historial.onclick=async()=>{
    try{const data=await api(`/solicitudes/${s.id}/historial`);$('detalle').querySelector('h2').textContent='Historial de la solicitud';$('historial').replaceChildren(...data.map(h=>nodo('p',`${h[0]==='SOLICITUD_REGISTRADA'?'Registrada':'Atendida'} · ${new Date(h[2]).toLocaleString('es-CO')} · responsable ${h[1]}`)));$('detalle').showModal();}
    catch(e){mensaje(e.message,true);}
   };grupo.append(historial);
   if(s.evaluacionResumen){const origen=nodo('button','Ver evaluación de origen');origen.type='button';origen.className='secondary';origen.onclick=()=>{try{const o=JSON.parse(s.evaluacionResumen);$('detalle').querySelector('h2').textContent='Evaluación que originó la solicitud';$('historial').replaceChildren(nodo('p',`Evaluación ${s.evaluacionId} · versión ${s.evaluacionVersion}`),nodo('p',`Activo: ${o.activo || 'No registrado'} · daño ${o.nivelAfectacion}/5`),nodo('p',o.descripcion || 'Sin descripción'),nodo('p',`Coordenadas: ${o.latitud}, ${o.longitud}`),nodo('p','Esta copia conserva la información utilizada al solicitar; las correcciones posteriores no la reemplazan.'));$('detalle').showModal();}catch(e){mensaje('No se pudo leer el origen de la solicitud.',true);}};grupo.append(origen);}
   acciones.append(grupo);
   row.append(desastre,recurso,nodo('td',({BAJA:'Baja',MEDIA:'Media',ALTA:'Alta'})[s.prioridad] || s.prioridad),estado,acciones);$('solicitudes').append(row);
  }
  mensaje(`${lista.length} solicitudes en tu organismo.`);
 }catch(e){mensaje(e.message,true);}finally{cargando=false;}
}
$('nueva-solicitud').onsubmit=async e=>{
 e.preventDefault();$('crear').disabled=true;
 const body={versionBase:evaluacionVersion,eventoId:$('evento').value,tipoRecurso:$('recurso').value.trim(),cantidad:Number($('cantidad').value),prioridad:$('prioridad').value};
 const firma=JSON.stringify(body);
 if(firmaPendiente && firmaPendiente!==firma){mensaje('Hay un intento sin confirmar. Reintenta con los mismos datos antes de modificarlos.',true);$('crear').disabled=false;return;}
 firmaPendiente=firma;sessionStorage.setItem(`arqui-solicitud:${actor.organizacionId}:${actor.usuario}`,JSON.stringify({solicitudId,body}));
 try{await api(evaluacionId?'/campo/evaluaciones/'+encodeURIComponent(evaluacionId)+'/solicitudes':'/solicitudes',{method:'POST',body:JSON.stringify({...body,solicitudId})});solicitudId=crypto.randomUUID();firmaPendiente=null;sessionStorage.removeItem(`arqui-solicitud:${actor.organizacionId}:${actor.usuario}`);$('recurso').value=$('cantidad').value='';await cargar();mensaje('Solicitud registrada y guardada como pendiente.');}
 catch(e){if(e.status>=400 && e.status<500){firmaPendiente=null;solicitudId=crypto.randomUUID();sessionStorage.removeItem(`arqui-solicitud:${actor.organizacionId}:${actor.usuario}`);}mensaje(e.message,true);}finally{$('crear').disabled=false;}
};
$('filtros').onsubmit=e=>{e.preventDefault();cargar();};$('pendientes').onchange=()=>cargar();$('filtro-evento').onchange=()=>cargar();$('cerrar').onclick=()=>$('detalle').close();
(async()=>{
 try{
  const [a,catalogo]=await Promise.all([api('/auth/me'),api('/desastres')]);actor=a;
  $('registro-solicitud').hidden=actor.rol==='CONSULTA';
  for(const r of catalogo.desastres){const d=r.desastre;for(const id of ['evento','filtro-evento']){const o=nodo('option',`${d.id} · ${d.ubicacion}`);o.value=d.id;$(id).append(o);}}
  if(evaluacionId){const origen=await api('/campo/evaluaciones/'+encodeURIComponent(evaluacionId));if(origen.estadoVinculo!=='VINCULADA')throw new Error('Vincula primero la evaluación a un desastre.');evaluacionVersion=origen.version;const nota=nodo('p',`Solicitud originada en la evaluación ${evaluacionId}, versión ${evaluacionVersion}. Se conservará una copia de sus datos.`);$('nueva-solicitud').prepend(nota);$('evento').value=origen.eventoId;$('evento').disabled=true;}
  if(eventoInicial && catalogo.desastres.some(r=>r.desastre.id===eventoInicial))$('evento').value=$('filtro-evento').value=eventoInicial;
  const borrador=sessionStorage.getItem(`arqui-solicitud:${actor.organizacionId}:${actor.usuario}`);if(borrador){try{const d=JSON.parse(borrador);solicitudId=d.solicitudId;firmaPendiente=JSON.stringify(d.body);$('evento').value=d.body.eventoId;$('recurso').value=d.body.tipoRecurso;$('cantidad').value=d.body.cantidad;$('prioridad').value=d.body.prioridad;}catch(e){sessionStorage.removeItem(`arqui-solicitud:${actor.organizacionId}:${actor.usuario}`);}}
  if(!catalogo.desastres.length && actor.rol!=='CONSULTA')mensaje('Registra un desastre antes de solicitar recursos.');
  await cargar();
 }catch(e){mensaje(e.message,true);}
})();
