'use strict';
const $ = id => document.getElementById(id);
const labels = {INUNDACION:'Inundación',INCENDIO:'Incendio',DESLIZAMIENTO:'Deslizamiento',TERREMOTO:'Terremoto',OTRO:'Otro',BAJA:'Baja',MEDIA:'Media',ALTA:'Alta',CRITICA:'Crítica'};
const colors = {BAJA:'#4e9975',MEDIA:'#b28d2d',ALTA:'#d16a35',CRITICA:'#b9414e'};
let desastresRegistrados = [];
const estadosVinculo = {VINCULADA:'Desastre registrado',PENDIENTE_DE_EVENTO:'Referencia pendiente de verificar',PRELIMINAR:'Evaluación preliminar'};
let mapa, puntos, borrador, registros = [], editando = null, requestSequence = 0, saving = false, operacionPendiente = null;
function mensaje(text, error = false) { $('mensaje').textContent = text; $('mensaje').classList.toggle('error', error); }
async function api(path, options = {}) {
  const response = await fetch('/api' + path, { ...options, headers:{'Content-Type':'application/json',...options.headers} });
  if (!response.ok) {
    const problem = await response.json().catch(() => ({}));
    const detail = problem.errors ? Object.entries(problem.errors).map(([key,values]) => `${key}: ${values.join(' ')}`).join(' · ') : problem.title;
    throw new Error(detail || `No se pudo completar la operación (${response.status}).`);
  }
  return response.status === 204 ? null : response.json();
}
function filtros() {
  const params = new URLSearchParams();
  if ($('filtro-clase').value) params.set('clase', $('filtro-clase').value);
  if ($('filtro-evento').value.trim()) params.set('eventoId', $('filtro-evento').value.trim());
  if ($('filtro-nivel').value) params.set('nivel', $('filtro-nivel').value);
  if ($('filtro-tipo').value) params.set('tipo', $('filtro-tipo').value);
  if ($('filtro-severidad').value) params.set('severidad', $('filtro-severidad').value);
  return params.toString() ? '?' + params : '';
}
function node(tag, text, className) {
  const el = document.createElement(tag); if (text !== undefined) el.textContent = text;
  if (className) el.className = className; return el;
}
function encuadrar() { if (puntos && puntos.getLayers().length) mapa.fitBounds(puntos.getBounds(), {padding:[35,35], maxZoom:13}); }
function ubicacion() {
  if (!mapa) return;
  const lat = $('latitud').valueAsNumber, lon = $('longitud').valueAsNumber;
  if (borrador) { mapa.removeLayer(borrador); borrador = null; }
  if (Number.isFinite(lat) && Number.isFinite(lon) && Math.abs(lat)<=90 && Math.abs(lon)<=180)
    borrador = L.circleMarker([lat,lon],{radius:12,color:'#183d36',weight:2,dashArray:'4 4',fillOpacity:.08}).addTo(mapa);
}
function pintar() {
  $('registros').replaceChildren(); puntos?.clearLayers();
  $('conteo').textContent = `${registros.length} ${registros.length === 1 ? 'punto' : 'puntos'}`;
  $('vacio').hidden = registros.length > 0;
  for (const item of registros) {
    let marker;
    if (mapa) {
      const popup = node('div'); popup.append(node('h3',item.titulo),node('div',item.clase === 'EVALUACION' ? `Evaluación · daño ${item.nivelAfectacion}/5 · ${item.eventoId || 'Preliminar'}` : `${labels[item.tipo]} · ${labels[item.severidad]}`),node('p',item.descripcion),node('small',`${item.latitud.toFixed(6)}, ${item.longitud.toFixed(6)}`));
      marker = L.circleMarker([item.latitud,item.longitud],{radius:9,color:'white',weight:2,fillColor:item.clase === 'EVALUACION' ? ['#3475de','#32cd9a','#dfb939','#db7334','#d92d2d'][item.nivelAfectacion-1] : colors[item.severidad],fillOpacity:.95}).bindPopup(popup).addTo(puntos);
    }
    const row = node('tr'), title = node('td',item.titulo);
    if (item.clase === 'EVALUACION') title.append(node('small', estadosVinculo[item.estadoVinculo] || 'Referencia pendiente'));
    if (item.descripcion) title.append(node('small',item.descripcion));
    const type = node('td',item.clase === 'EVALUACION' ? `Evaluación · ${item.eventoId || 'Preliminar'}` : labels[item.tipo]), severity = node('td'); severity.append(node('span',item.clase === 'EVALUACION' ? `Daño ${item.nivelAfectacion}/5` : labels[item.severidad],'pill'));
    const coords = node('td',`${item.latitud.toFixed(5)}, ${item.longitud.toFixed(5)}`);
    const actions = node('td'), group = node('div',undefined,'actions');
    const view = node('button','Ubicar','secondary'); view.type='button'; view.disabled=!mapa; view.onclick=()=>{mapa.setView([item.latitud,item.longitud],13);marker.openPopup();$('mapa').scrollIntoView({behavior:'smooth',block:'center'});};
    const edit = node('button','Editar','secondary'); edit.type='button';edit.onclick=()=>editar(item);
    const remove = node('button','Eliminar','secondary danger');remove.type='button';remove.onclick=()=>eliminar(item);
    if (item.clase === 'EVALUACION') { const detalle = node('button','Ver evaluación','secondary'); detalle.type='button'; detalle.onclick=()=>verEvaluacion(item.id); group.append(view,detalle); } else group.append(view,edit,remove);actions.append(group);row.append(title,type,severity,coords,actions);$('registros').append(row);
  }
}
async function cargar(notify = true) {
  const seq = ++requestSequence;
  try {
    const data = await api('/panorama'+filtros());
    if (seq !== requestSequence) return false;
    registros = data; pintar();
    const enlaceCalor=document.querySelector('a[href^="calor.html"]');
    if(enlaceCalor) enlaceCalor.href='calor.html'+($('filtro-evento').value.trim()?'?eventoId='+encodeURIComponent($('filtro-evento').value.trim()):'');
    if (notify) mensaje('Vista actualizada. Selecciona un punto para ver sus detalles.');
    return true;
  } catch(e) { if(seq === requestSequence) mensaje('No se pudo actualizar el registro. '+e.message,true); return false; }
}
function cancelar() {
  editando=null;operacionPendiente=null;$('formulario').reset();cambiarClase();$('form-title').textContent='Registrar afectación';$('guardar').textContent='+ Registrar afectación';$('cancelar').hidden=true;ubicacion();
}
function editar(item) {
  if (saving) return;
  editando=item;$('clase').value='AFECTACION';cambiarClase();
  for (const key of ['titulo','tipo','severidad','descripcion','latitud','longitud']) $(key).value=item[key];
  $('form-title').textContent='Editar afectación';$('guardar').textContent='Guardar cambios';$('cancelar').hidden=false;
  ubicacion();$('titulo').focus();
}
async function eliminar(item) {
  if (saving || !confirm(`¿Eliminar «${item.titulo}»? Esta acción no se puede deshacer.`)) return;
  saving=true;
  try { await api(`/afectaciones/${item.id}?version=${item.version}`,{method:'DELETE'});if(editando?.id===item.id)cancelar();if(await cargar(false)) mensaje('Afectación eliminada.'); }
  catch(e){mensaje(e.message,true);}finally{saving=false;}
}
$('formulario').addEventListener('submit',async event=>{
  event.preventDefault();if(saving)return;
  if ($('clase').value === 'EVALUACION') { await guardarEvaluacion(); return; }
  const input = {titulo:$('titulo').value,tipo:$('tipo').value,severidad:$('severidad').value,descripcion:$('descripcion').value,latitud:$('latitud').valueAsNumber,longitud:$('longitud').valueAsNumber};
  if(editando)input.version=editando.version;
  saving=true;$('guardar').disabled=true;$('cancelar').disabled=true;
  try {
    const item=await api('/afectaciones'+(editando?'/'+editando.id:''),{method:editando?'PUT':'POST',body:JSON.stringify(input)});
    cancelar();const loaded=await cargar(false);if(mapa)mapa.setView([item.latitud,item.longitud],12);
    if(loaded)mensaje('Afectación guardada. Si no aparece, revisa los filtros seleccionados.');
  }catch(e){mensaje(e.message,true);}finally{saving=false;$('guardar').disabled=false;$('cancelar').disabled=false;}
});
$('cancelar').onclick=cancelar;
$('latitud').addEventListener('input',ubicacion);$('longitud').addEventListener('input',ubicacion);
$('encuadrar').onclick=encuadrar;$('actualizar').onclick=()=>cargar();
for (const id of ['filtro-tipo','filtro-severidad','filtro-clase','filtro-nivel']) $(id).onchange=()=>cargar();
$('filtro-evento').onchange=()=>cargar();
$('exportar').onclick=async()=>{
  try {
    const geo=await api('/panorama/geojson'+filtros());const url=URL.createObjectURL(new Blob([JSON.stringify(geo,null,2)],{type:'application/geo+json'}));
    const a=node('a');a.href=url;a.download='territorio.geojson';document.body.append(a);a.click();a.remove();setTimeout(()=>URL.revokeObjectURL(url),1000);
    mensaje('GeoJSON exportado con los filtros actuales.');
  }catch(e){mensaje(e.message,true);}
};
$('archivo').onchange=async event=>{
  const file=event.target.files[0];if(!file)return;
  if(saving){mensaje('Espera a que termine la operación actual.',true);event.target.value='';return;}
  saving=true;
  try {
    if(file.size>2*1024*1024)throw new Error('El archivo supera el máximo de 2 MB.');
    const body=JSON.parse(await file.text());
    if(!confirm('La importación agrega puntos nuevos. Repetir el mismo archivo crea duplicados. ¿Continuar?'))return;
    const result=await api('/importaciones/geojson',{method:'POST',body:JSON.stringify(body)});
    if(await cargar(false)){encuadrar();mensaje(`${result.importadas} afectaciones importadas. El lote se guardó completo.`);}
  }catch(e){mensaje(e instanceof SyntaxError?'El archivo no contiene JSON válido.':e.message,true);}finally{saving=false;event.target.value='';}
};
async function iniciar() {
  if(window.L) {
    mapa=L.map('mapa').setView([4.65,-74.1],6);puntos=L.featureGroup().addTo(mapa);
    const tiles=L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:19,attribution:'&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'}).addTo(mapa);
    tiles.on('tileerror',()=>{$('mapa-estado').textContent='Mapa base no disponible. Revisa la conexión; los puntos siguen disponibles.';});
    mapa.on('click',e=>{if(saving)return;$('latitud').value=e.latlng.lat.toFixed(6);$('longitud').value=L.Util.wrapNum(e.latlng.lng,[-180,180],true).toFixed(6);ubicacion();});
  } else {$('mapa').append(node('p','No se pudo cargar el mapa. Revisa tu conexión. Puedes gestionar las coordenadas desde el formulario y la tabla.','empty'));}
  try {
    const catalogos=await api('/catalogos');
    for(const value of catalogos.tipos){$('tipo').add(new Option(labels[value],value));$('filtro-tipo').add(new Option(labels[value],value));}
    for(const value of catalogos.severidades){$('severidad').add(new Option(labels[value],value));$('filtro-severidad').add(new Option(labels[value],value));}
    await cargar();encuadrar();
    await cargarDesastres();
  }catch(e){mensaje('No se pudo conectar con la API. '+e.message,true);}
}
const referenciaInicial=new URLSearchParams(location.search).get('eventoId');
if(referenciaInicial){$('filtro-evento').value=referenciaInicial;$('filtro-clase').value='EVALUACION';}
iniciar();

function cambiarClase() {
  const evaluacion = $('clase').value === 'EVALUACION';
  $('campos-evaluacion').hidden = !evaluacion;
  $('campos-afectacion').hidden = evaluacion;
  for (const id of ['titulo','tipo','severidad']) { $(id).required = !evaluacion; $(id).disabled = evaluacion; }
  for (const id of ['activo']) { $(id).required = evaluacion; $(id).disabled = !evaluacion; }
  $('descripcion').maxLength = evaluacion ? 1000 : 2000;
  $('form-title').textContent = evaluacion ? 'Registrar evaluación' : 'Registrar afectación';
  $('guardar').textContent = evaluacion ? '+ Registrar evaluación' : '+ Registrar afectación';
}
cambiarClase();
$('clase').onchange=()=>{ editando=null; operacionPendiente=null; $('cancelar').hidden=true; cambiarClase(); };
async function guardarEvaluacion() {
  // Conservar la identidad al reintentar una respuesta perdida. Si cambian datos,
  // no se reenvía una operación de contenido diferente con la misma identidad.
  const datos = {
    eventoId: $('evento-id').value.trim() || null,
    nivelAfectacion: Number($('nivel').value), descripcion: $('descripcion').value.trim(),
    activo: $('activo').value.trim(), latitud: $('latitud').valueAsNumber,
    longitud: $('longitud').valueAsNumber, fuenteUbicacion: 'MANUAL'
  };
  const firma=JSON.stringify(datos);
  if (operacionPendiente && operacionPendiente.firma !== firma) {
    mensaje('Hay un envío sin confirmar. Reintenta con los mismos datos o recarga la lista para comprobar su recepción antes de registrar otro.', true); return;
  }
  operacionPendiente ??= { firma, operacion: {...datos, operacionId: crypto.randomUUID(), evaluacionId: crypto.randomUUID(), capturadaEnUtc: new Date().toISOString()} };
  saving=true; $('guardar').disabled=true;
  try {
    const respuesta=await api('/campo/lotes',{method:'POST',body:JSON.stringify({operaciones:[operacionPendiente.operacion]})});
    const resultado=respuesta.resultados?.[0];
    if (!resultado || !['recibida','incorporada'].includes(resultado.estado)) throw new Error(resultado?.motivo || 'No se confirmó el registro.');
    cancelar(); await cargar(false);
    mensaje(resultado.estado === 'recibida' ? 'Evaluación recibida. Aparecerá en ambos mapas cuando termine de incorporarse.' : 'Evaluación incorporada. Disponible en el mapa principal, el mapa de calor y GeoJSON.');
  } catch(error) { mensaje(error.message+' Reintentar conserva la identidad de la evaluación.',true); }
  finally { saving=false; $('guardar').disabled=false; }
}
async function verEvaluacion(id) {
  try {
    const item=await api('/campo/evaluaciones/'+id);
    const contenido=$('detalle-contenido');contenido.replaceChildren();
    for (const [etiqueta,valor] of [['Referencia',item.eventoId || 'Preliminar'],['Activo',item.activo || 'No registrado'],['Nivel de daño',`${item.nivelAfectacion}/5`],['Evaluador',item.evaluador || 'No registrado'],['Organización',item.organizacion || 'No registrada'],['Captura',new Date(item.capturadaEnUtc).toLocaleString()],['Descripción',item.descripcion || 'Sin descripción']]) contenido.append(node('p',`${etiqueta}: ${valor}`));
    contenido.append(node('p',`Vínculo: ${estadosVinculo[item.estadoVinculo] || 'Pendiente'}`));
    if(item.desastre) contenido.append(node('p',`Desastre: ${labels[item.desastre.tipo] || item.desastre.tipo} · ${item.desastre.ubicacion} · Severidad ${labels[item.desastre.severidad] || item.desastre.severidad}`));
    await cargarDesastres();
    const elegir=node('select');elegir.setAttribute('aria-label','Vincular a un desastre registrado');
    elegir.add(new Option('Seleccionar desastre registrado',''));
    for(const r of desastresRegistrados)elegir.add(new Option(`${r.desastre.id} · ${r.desastre.ubicacion}`,r.desastre.id));
    elegir.value=item.eventoId || '';
    const vincular=node('button','Vincular evaluación','secondary');vincular.type='button';
    const estadoVinculo=node('p');estadoVinculo.setAttribute('role','status');
    vincular.onclick=async()=>{ if(!elegir.value){estadoVinculo.textContent='Selecciona un desastre.';return;} vincular.disabled=true;
      try { await api(`/campo/evaluaciones/${id}/desastre`,{method:'PUT',body:JSON.stringify({eventoId:elegir.value})});estadoVinculo.textContent='Evaluación vinculada. Se conserva la captura original.';await cargar(false); }
      catch(e){estadoVinculo.textContent=e.message;} finally {vincular.disabled=false;} };
    contenido.append(elegir,vincular,estadoVinculo);
    if(item.estadoVinculo==='VINCULADA'){const enlace=node('a','Solicitar recursos','button secondary');enlace.href='solicitudes.html?eventoId='+encodeURIComponent(item.eventoId)+'&evaluacionId='+encodeURIComponent(item.evaluacionId);contenido.append(enlace);}
    contenido.append(node('p',`Versión actual: ${item.version??0}`));
    const historial=node('button','Historial de correcciones','secondary');historial.onclick=async()=>{try{const rows=await api(`/campo/evaluaciones/${id}/historial`);const lista=node('div');for(const r of rows)lista.append(node('p',`v${r.version} · ${r.actor} · ${r.motivo} · daño ${r.anterior.nivelAfectacion} → ${r.nuevo.nivelAfectacion}`));if(!rows.length)lista.append(node('p','Sin correcciones. La captura original se conserva.'));contenido.append(lista);}catch(e){mensaje(e.message,true);}};contenido.append(historial);
    if(document.body.dataset.rol!=='CONSULTA'){
      const corregir=node('button','Corregir evaluación','secondary');corregir.onclick=()=>{
        corregir.disabled=true;const f=node('form'),nivel=node('select');nivel.setAttribute('aria-label','Nuevo nivel de daño');for(let n=1;n<=5;n++)nivel.add(new Option(String(n),String(n)));nivel.value=String(item.nivelAfectacion);
        const texto=node('textarea');texto.setAttribute('aria-label','Descripción corregida');texto.value=item.descripcion;texto.maxLength=1000;
        const activo=node('input');activo.setAttribute('aria-label','Activo corregido');activo.value=item.activo??'';activo.maxLength=120;
        const motivo=node('input');motivo.placeholder='Motivo de la corrección';motivo.setAttribute('aria-label','Motivo de la corrección');motivo.required=true;motivo.maxLength=500;
        const guardar=node('button','Guardar corrección'),estado=node('p');estado.setAttribute('role','status');f.append(nivel,activo,texto,motivo,guardar,estado);contenido.append(f);const operacionId=crypto.randomUUID();let body;
        f.onsubmit=async e=>{e.preventDefault();guardar.disabled=true;body??={operacionId,versionBase:item.version??0,nivelAfectacion:Number(nivel.value),activo:activo.value.trim(),descripcion:texto.value,latitud:item.latitud,longitud:item.longitud,motivo:motivo.value.trim()};
          try{await api(`/campo/evaluaciones/${id}`,{method:'PUT',body:JSON.stringify(body)});$('detalle').close();await cargar(false);await verEvaluacion(id);}catch(error){estado.textContent=error.message+' Tu propuesta permanece aquí. Cierra y revisa la versión actual antes de decidir si la reenvías.';}finally{guardar.disabled=false;}
        };
      };contenido.append(corregir);
    }
    if (item.evidenciaId) { const img=node('img');const respuestaFoto=await fetch(`/api/campo/evaluaciones/${id}/foto`);if(!respuestaFoto.ok)throw new Error('No se pudo cargar la foto.');img.src=URL.createObjectURL(await respuestaFoto.blob());img.onload=()=>URL.revokeObjectURL(img.src);img.alt='Evidencia de la evaluación';contenido.append(img); }
    $('detalle').showModal();
  } catch(error) { mensaje(error.message,true); }
}
setInterval(()=>{if (!document.hidden && !saving) cargar(false);},10000);

async function cargarDesastres() {
  try {
    const catalogo=await api('/desastres');desastresRegistrados=catalogo.desastres;
    const anterior=$('evento-id').value;
    $('evento-id').replaceChildren(new Option('Evaluación preliminar · sin desastre',''));
    for(const r of catalogo.desastres) $('evento-id').add(new Option(`${r.desastre.id} · ${r.desastre.ubicacion}`,r.desastre.id));
    if(anterior && !catalogo.desastres.some(r=>r.desastre.id===anterior)) $('evento-id').add(new Option(`${anterior} · referencia pendiente`,anterior));
    $('evento-id').value=anterior;
    $('estado-desastres').textContent=catalogo.disponible ? 'Desastres del registro central. Puedes capturar una evaluación preliminar si aún no existe el evento.' : 'Registro temporalmente no disponible. Puedes usar la última copia o guardar una evaluación preliminar.';
  } catch(error) { $('estado-desastres').textContent='No se pudo actualizar el catálogo. Puedes guardar una evaluación preliminar.'; }
}
$('actualizar-desastres').onclick=cargarDesastres;
