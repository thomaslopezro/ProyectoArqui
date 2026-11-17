'use strict';
document.querySelector('#nuevo-usuario').onsubmit=async e=>{
 e.preventDefault();const b=e.target.querySelector('button'),s=document.querySelector('#estado');b.disabled=true;
 try{const r=await fetch('/api/auth/usuarios',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({usuario:document.querySelector('#usuario').value,clave:document.querySelector('#clave').value,rol:document.querySelector('#rol').value})});
 if(!r.ok)throw new Error(r.status===409?'El usuario ya existe.':r.status===403?'Solo un coordinador puede crear usuarios.':'Revisa usuario, clave y rol.');s.textContent='Usuario creado en tu organización.';e.target.reset();}catch(error){s.textContent=error.message;}finally{b.disabled=false;}
};
