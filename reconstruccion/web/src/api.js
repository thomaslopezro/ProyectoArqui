// Cliente de la API REST del módulo de Reconstrucción.
// La identidad viaja en cabeceras; en la plataforma integrada las fija la fachada tras validar la sesión.
const BASE = '/api/reconstruccion';

export class ErrorApi extends Error {
  constructor(estado, cuerpo) {
    super(cuerpo?.mensaje || `Error ${estado}`);
    this.estado = estado;
    this.regla = cuerpo?.regla || null;
    this.codigo = cuerpo?.codigo || null;
  }
}

// En la plataforma integrada la identidad sale de la sesión de la plataforma: se envía su token
// y el módulo lo valida contra el núcleo. Solo, el módulo usa las cabeceras de demostración.
export const INTEGRADO = import.meta.env.VITE_INTEGRADO === 'true';
const CLAVE_TOKEN = 'arqui-token';
const ROLES = { COORDINADOR: 'AUTORIDAD', EVALUADOR: 'RESPONSABLE' };
const NOMBRES_ROL = { AUTORIDAD: 'Autoridad', RESPONSABLE: 'Responsable de proyecto', AUDITOR: 'Consulta' };

export function irAlInicioDeSesion() {
  sessionStorage.removeItem(CLAVE_TOKEN);
  window.location.replace('/login.html');
}

/** Identidad de la sesión de la plataforma, con el rol que le corresponde en este módulo. */
export async function identidadDePlataforma() {
  const token = sessionStorage.getItem(CLAVE_TOKEN);
  if (!token) { irAlInicioDeSesion(); return null; }
  const respuesta = await fetch('/api/auth/me', { headers: { Authorization: 'Bearer ' + token } });
  if (respuesta.status === 401) { irAlInicioDeSesion(); return null; }
  if (!respuesta.ok) throw new Error('El servicio de identidad no está disponible.');
  const yo = await respuesta.json();
  const rol = ROLES[yo.rol] || 'AUDITOR';
  return { usuario: yo.usuario, rol, organizacion: yo.organizacionId, nombre: `${yo.usuario} · ${NOMBRES_ROL[rol]}` };
}

function cabeceras(actor) {
  if (INTEGRADO) {
    return { 'Content-Type': 'application/json', Authorization: 'Bearer ' + (sessionStorage.getItem(CLAVE_TOKEN) || '') };
  }
  return {
    'Content-Type': 'application/json',
    'X-Usuario': actor.usuario,
    'X-Rol': actor.rol,
    'X-Organizacion': actor.organizacion,
  };
}

async function pedir(actor, metodo, ruta, cuerpo) {
  const respuesta = await fetch(BASE + ruta, {
    method: metodo,
    headers: cabeceras(actor),
    body: cuerpo === undefined ? undefined : JSON.stringify(cuerpo),
  });
  if (INTEGRADO && respuesta.status === 401) { irAlInicioDeSesion(); throw new ErrorApi(401, { mensaje: 'La sesión venció.' }); }
  const texto = await respuesta.text();
  let datos = null;
  try { datos = texto ? JSON.parse(texto) : null; } catch { datos = { mensaje: texto }; }
  if (!respuesta.ok) throw new ErrorApi(respuesta.status, datos);
  return datos;
}

export const api = {
  desastres: (a) => pedir(a, 'GET', '/desastres'),
  seguimiento: (a, desastreId) => pedir(a, 'GET', `/desastres/${desastreId}/seguimiento`),
  evaluaciones: (a, desastreId) => pedir(a, 'GET', `/desastres/${desastreId}/evaluaciones`),
  iniciarReconstruccion: (a, desastreId) => pedir(a, 'POST', `/desastres/${desastreId}/iniciar-reconstruccion`),
  cerrarDesastre: (a, desastreId) => pedir(a, 'POST', `/desastres/${desastreId}/cerrar`),
  crearProyectos: (a, desastreId, proyectos) => pedir(a, 'POST', `/desastres/${desastreId}/proyectos`, { proyectos }),
  proyecto: (a, id) => pedir(a, 'GET', `/proyectos/${id}`),
  auditoria: (a, id) => pedir(a, 'GET', `/proyectos/${id}/auditoria`),
  aprobar: (a, id, datos) => pedir(a, 'POST', `/proyectos/${id}/aprobar`, datos),
  iniciar: (a, id, datos) => pedir(a, 'POST', `/proyectos/${id}/iniciar`, datos),
  presupuesto: (a, id, datos) => pedir(a, 'POST', `/proyectos/${id}/presupuesto`, datos),
  avance: (a, id, datos) => pedir(a, 'POST', `/proyectos/${id}/avance`, datos),
  costo: (a, id, datos) => pedir(a, 'POST', `/proyectos/${id}/costo`, datos),
  evidencia: (a, id, datos) => pedir(a, 'POST', `/proyectos/${id}/evidencia`, datos),
  estado: (a, id, datos) => pedir(a, 'POST', `/proyectos/${id}/estado`, datos),
};
