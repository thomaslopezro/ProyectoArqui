'use strict';
// Only same-origin API calls receive the session. Never add credentials to map tiles.
const fetchOriginal=window.fetch.bind(window);
window.fetch=async function(input,options={}) {
 const url=new URL(typeof input==='string'?input:input.url,location.href);
 if(url.origin===location.origin && url.pathname.startsWith('/api/') && url.pathname!=='/api/auth/login') {
  const token=sessionStorage.getItem('arqui-token');
  options={...options,headers:new Headers(options.headers)};
  if(token)options.headers.set('Authorization','Bearer '+token);
  const response=await fetchOriginal(input,options);
  if(response.status===401) {sessionStorage.removeItem('arqui-token');location.replace('login.html');}
  return response;
 }
 return fetchOriginal(input,options);
};
if(!sessionStorage.getItem('arqui-token')) location.replace('login.html');
else {
 const barra=document.createElement('div');barra.className='sesion';
 const nombre=document.createElement('span');nombre.textContent='Sesión iniciada';
 const usuarios=document.createElement('a');usuarios.href='usuarios.html';usuarios.textContent='Usuarios';
 const salir=document.createElement('button');salir.textContent='Cerrar sesión';salir.className='secondary';
 salir.onclick=async()=>{try{const r=await fetch('/api/auth/logout',{method:'POST',headers:{'Content-Type':'application/json'},body:'{}'});if(!r.ok && r.status!==401)throw new Error();sessionStorage.clear();location.replace('login.html');}catch(e){nombre.textContent='No se pudo cerrar la sesión en el servidor. Reintenta.';}};
 barra.append(nombre,usuarios,salir);document.body.prepend(barra);
 fetch('/api/auth/me').then(r=>{if(!r.ok)throw new Error();return r.json();}).then(u=>{nombre.textContent=`${u.usuario} · ${u.rol}`;usuarios.hidden=u.rol!=='COORDINADOR';document.body.dataset.rol=u.rol;}).catch(()=>{nombre.textContent='Identidad temporalmente no disponible';});
}
