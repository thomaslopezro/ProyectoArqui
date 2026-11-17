'use strict';
const $ = id => document.getElementById(id);
const etiquetas = {INUNDACION:'Inundación',TERREMOTO:'Terremoto',INCENDIO:'Incendio',DESLIZAMIENTO:'Deslizamiento',OTRO:'Otro',BAJA:'Baja',MEDIA:'Media',ALTA:'Alta',CRITICA:'Crítica'};
let pendientes = new Set(), cargando=false;
function nodo(tag,texto) { const n=document.createElement(tag); if(texto!==undefined)n.textContent=texto; return n; }
async function api(ruta,body,metodo) {
 const r=await fetch('/api'+ruta,{method:metodo || (body?'POST':'GET'),headers:{'Content-Type':'application/json'},body:body?JSON.stringify(body):undefined,cache:'no-store'});
 const data=await r.json().catch(()=>({}));
 if(!r.ok) throw new Error(data.mensaje || data.error || data.title || `No se pudo completar (${r.status}).`);
 return data;
}
function identificador() { $('desastre-id').value=crypto.randomUUID(); }
identificador();
const ahora=new Date();$('desastre-fecha').value=new Date(ahora.getTime()-ahora.getTimezoneOffset()*60000).toISOString().slice(0,16);
async function cargar() {
 if(cargando)return;cargando=true;
 try {
  const catalogo=await api('/desastres');
  $('desastres').replaceChildren();$('sin-desastres').hidden=catalogo.desastres.length>0;
  for(const r of catalogo.desastres) {
   const d=r.desastre;pendientes.delete(d.id);const fila=nodo('tr');
   const nombre=nodo('td',`${etiquetas[d.tipo] || d.tipo} · ${d.id}`);nombre.append(nodo('small',d.ubicacion),nodo('small',d.descripcion));
   const severidad=nodo('td',etiquetas[d.severidad] || d.severidad);severidad.append(nodo('small','Fase: '+(nombresFase[d.fase] || nombresFase.REGISTRO)));
   const avisos=nodo('td',r.notificacionPublicada?'Publicadas':'Pendientes');avisos.append(nodo('small',`Correos confirmados: ${r.correosEnviados?.length || 0}/${r.destinatarios?.length || 0}`));
   const accion=nodo('td');const enlace=nodo('a','Ver evaluaciones');enlace.className='button secondary';enlace.href='/?eventoId='+encodeURIComponent(d.id);accion.append(enlace);
   const historia=nodo('button','Ver historial');historia.className='secondary';historia.onclick=()=>historialDesastre(d.id);accion.append(historia);
   if(document.body.dataset.rol==='COORDINADOR'){const cambiar=nodo('button','Cambiar gravedad');cambiar.className='secondary';cambiar.onclick=()=>cambiarGravedad(d);accion.append(cambiar);if((siguientesFases[d.fase || 'REGISTRO'] || []).length){const fase=nodo('button','Cambiar fase');fase.className='secondary';fase.onclick=()=>cambiarFase(d);accion.append(fase);}}
   fila.append(nombre,severidad,avisos,accion);$('desastres').append(fila);
  }
  $('estado').textContent=!catalogo.disponible ? `El registro no responde. Se muestra la última copia${catalogo.actualizadoEn?' del '+new Date(catalogo.actualizadoEn).toLocaleString():' disponible'}. Las evaluaciones siguen funcionando.` : pendientes.size ? 'Desastre guardado. Actualizando catálogo…' : `${catalogo.desastres.length} desastres registrados. Catálogo actualizado.`;
  $('estado').classList.toggle('error',!catalogo.disponible);
 } catch(e) { $('estado').textContent=e.message;$('estado').classList.add('error'); }
 finally {cargando=false;}
}
async function sistemas() {
 try { const lista=await api('/sistemas');$('sistemas').replaceChildren(...lista.map(s=>nodo('li',`${s.nombre} · ${s.id} · ${s.correo}`))); }
 catch(e) {$('estado-sistemas').textContent=e.message;}
}
$('nuevo-desastre').onsubmit=async e=>{
 e.preventDefault();$('crear-desastre').disabled=true;
 const body={id:$('desastre-id').value.trim(),tipo:$('desastre-tipo').value,severidad:$('desastre-severidad').value,ubicacion:$('desastre-ubicacion').value.trim(),descripcion:$('desastre-descripcion').value.trim(),fecha:new Date($('desastre-fecha').value).toISOString()};
 try { await api('/desastres',body);pendientes.add(body.id);identificador();$('estado').textContent='Desastre guardado. Actualizando catálogo…';await cargar(); }
 catch(error) {$('estado').textContent=error.message;$('estado').classList.add('error');}
 finally {$('crear-desastre').disabled=false;}
};
$('nuevo-sistema').onsubmit=async e=>{
 e.preventDefault();$('crear-sistema').disabled=true;
 try { await api('/sistemas',{id:$('sistema-id').value.trim(),nombre:$('sistema-nombre').value.trim(),correo:$('sistema-correo').value.trim()});$('estado-sistemas').textContent='Sistema registrado. Recibirá notificaciones de los próximos desastres.';await sistemas(); }
 catch(error) {$('estado-sistemas').textContent=error.message;}
 finally {$('crear-sistema').disabled=false;}
};
$('actualizar').onclick=()=>{cargar();sistemas();};cargar();sistemas();setInterval(()=>{if(!document.hidden)cargar();},3000);

function dialogo(titulo){const d=nodo('dialog');d.append(nodo('h2',titulo));const cerrar=nodo('button','Cerrar');cerrar.className='secondary';cerrar.onclick=()=>{d.close();d.remove();};d.append(cerrar);document.body.append(d);return d;}
async function historialDesastre(id){try{const rows=await api('/desastres/'+encodeURIComponent(id)+'/historial');const d=dialogo('Historial de cambios · '+id);for(const r of rows)d.insertBefore(nodo('p',`v${r.version} · ${(r.anterior.fase||'REGISTRO')!==(r.nuevo.fase||'REGISTRO')?'Fase: '+(r.anterior.fase||'REGISTRO')+' → '+r.nuevo.fase:r.anterior.severidad+' → '+r.nuevo.severidad} · ${r.actor} · ${r.motivo}`),d.lastChild);if(!rows.length)d.insertBefore(nodo('p','Aún no hay cambios.'),d.lastChild);d.showModal();}catch(e){$('estado').textContent=e.message;}}
function cambiarGravedad(evento){
 const d=dialogo('Cambiar gravedad · '+evento.id),form=nodo('form'),seleccion=nodo('select');seleccion.setAttribute('aria-label','Nueva gravedad');
 for(const value of ['BAJA','MEDIA','ALTA','CRITICA'])seleccion.add(new Option(etiquetas[value],value));seleccion.value=evento.severidad;
 const motivo=nodo('input');motivo.placeholder='Motivo del cambio';motivo.setAttribute('aria-label','Motivo del cambio');motivo.required=true;motivo.maxLength=500;
 const enviar=nodo('button','Guardar cambio y notificar'),estado=nodo('p');estado.setAttribute('role','status');form.append(seleccion,motivo,enviar,estado);d.insertBefore(form,d.lastChild);const cambioId=crypto.randomUUID();let body;
 form.onsubmit=async e=>{e.preventDefault();enviar.disabled=true;body??={cambioId,versionBase:evento.version??0,severidad:seleccion.value,motivo:motivo.value.trim()};
 try{await api('/desastres/'+encodeURIComponent(evento.id)+'/gravedad',body,'PATCH');d.close();d.remove();await cargar();$('estado').textContent='Gravedad actualizada. Historial guardado y aviso pendiente de entrega.';}catch(error){estado.textContent=error.message+' Si el evento cambió, cierra y actualiza la lista antes de volver a editar.';}finally{enviar.disabled=false;}};
 d.showModal();
}

// Fase del desastre: Registro, Caracterización, Reconstrucción y Cierre. El núcleo valida la transición.
const nombresFase={REGISTRO:'Registro',CARACTERIZACION:'Caracterización',RECONSTRUCCION:'Reconstrucción',CIERRE:'Cierre'};
const siguientesFases={REGISTRO:['CARACTERIZACION','CIERRE'],CARACTERIZACION:['CIERRE'],RECONSTRUCCION:[],CIERRE:[]};
function cambiarFase(evento){
 const actual=evento.fase || 'REGISTRO';
 const d=dialogo('Cambiar fase · '+evento.id),form=nodo('form'),seleccion=nodo('select');seleccion.setAttribute('aria-label','Nueva fase');
 for(const value of siguientesFases[actual])seleccion.add(new Option(nombresFase[value],value));
 const nota=nodo('p','Fase actual: '+nombresFase[actual]+'. El paso a Reconstrucción y el cierre de un desastre en reconstrucción se hacen desde el módulo de Reconstrucción.');
 const motivo=nodo('input');motivo.placeholder='Motivo del cambio';motivo.setAttribute('aria-label','Motivo del cambio');motivo.required=true;motivo.maxLength=500;
 const enviar=nodo('button','Guardar cambio de fase'),estado=nodo('p');estado.setAttribute('role','status');form.append(nota,seleccion,motivo,enviar,estado);d.insertBefore(form,d.lastChild);const cambioId=crypto.randomUUID();let body;
 form.onsubmit=async e=>{e.preventDefault();enviar.disabled=true;body??={cambioId,versionBase:evento.version??0,fase:seleccion.value,motivo:motivo.value.trim()};
 try{await api('/desastres/'+encodeURIComponent(evento.id)+'/fase',body,'PATCH');d.close();d.remove();await cargar();$('estado').textContent='Fase actualizada. El cambio quedó en el historial.';}catch(error){estado.textContent=error.message+' Si el evento cambió, cierra y actualiza la lista antes de volver a intentar.';}finally{enviar.disabled=false;}};
 d.showModal();
}
