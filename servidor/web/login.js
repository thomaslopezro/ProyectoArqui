'use strict';
document.querySelector('#login').onsubmit=async e=>{
 e.preventDefault();const b=e.target.querySelector('button'),estado=document.querySelector('#estado');b.disabled=true;estado.textContent='Validando acceso…';
 try {
  const r=await fetch('/api/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({usuario:document.querySelector('#usuario').value,clave:document.querySelector('#clave').value})});
  if(!r.ok)throw new Error(r.status===401?'Usuario o contraseña incorrectos.':'El servicio de identidad no está disponible.');
  const data=await r.json();sessionStorage.setItem('arqui-token',data.accessToken);document.querySelector('#clave').value='';location.replace('/');
 }catch(error){estado.textContent=error.message;}finally{b.disabled=false;}
};
