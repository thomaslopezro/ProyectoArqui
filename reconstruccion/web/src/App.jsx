import React, { useCallback, useEffect, useState } from 'react';
import { api, INTEGRADO, identidadDePlataforma } from './api.js';

// Identidades de demostración. En la plataforma integrada vienen de la sesión del usuario.
const ACTORES = [
  { usuario: 'carlos', nombre: 'Carlos · Autoridad (UNGRD)', rol: 'AUTORIDAD', organizacion: 'UNGRD' },
  { usuario: 'ana', nombre: 'Ana · Responsable de proyecto (UNGRD)', rol: 'RESPONSABLE', organizacion: 'UNGRD' },
  { usuario: 'luisa', nombre: 'Luisa · Auditora (UNGRD)', rol: 'AUDITOR', organizacion: 'UNGRD' },
  { usuario: 'pedro', nombre: 'Pedro · Autoridad (Alcaldía)', rol: 'AUTORIDAD', organizacion: 'Alcaldia' },
];

const ETAPAS = ['REGISTRO', 'CARACTERIZACION', 'RECONSTRUCCION', 'CIERRE'];
const NOMBRES = {
  REGISTRO: 'Registro', CARACTERIZACION: 'Caracterización', RECONSTRUCCION: 'Reconstrucción', CIERRE: 'Cierre',
  PROPUESTO: 'Propuesto', APROBADO: 'Aprobado', EN_EJECUCION: 'En ejecución', SUSPENDIDO: 'Suspendido',
  FINALIZADO: 'Finalizado', CANCELADO: 'Cancelado', REPARACION: 'Reparación',
  SIN_DANO: 'Sin daño', LEVE: 'Leve', MODERADO: 'Moderado', GRAVE: 'Grave', DESTRUCCION_TOTAL: 'Destrucción total',
  AVANCE: 'Avance', COSTO: 'Costo', EVIDENCIA: 'Evidencia',
};
const nombre = (clave) => NOMBRES[clave] || clave;
const pesos = (valor) => (valor === null || valor === undefined ? '—'
  : new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 0 }).format(valor));
const fechaHora = (iso) => new Date(iso).toLocaleString('es-CO', { dateStyle: 'short', timeStyle: 'short' });

function Insignia({ valor }) {
  return <span className={`insignia insignia-${valor}`}>{nombre(valor)}</span>;
}

/** Formulario pequeño: campos, botón y envío. Limpia los campos cuando la acción termina bien. */
function Formulario({ titulo, campos, boton, onEnviar }) {
  const [valores, setValores] = useState({});
  const enviar = async (e) => {
    e.preventDefault();
    if (await onEnviar(valores)) setValores({});
  };
  return (
    <form className="formulario" onSubmit={enviar}>
      <h4>{titulo}</h4>
      {campos.map((c) => (
        <label key={c.clave}>
          <span>{c.etiqueta}</span>
          <input type={c.tipo || 'text'} value={valores[c.clave] || ''} placeholder={c.ayuda || ''}
            min={c.min} max={c.max}
            onChange={(e) => setValores({ ...valores, [c.clave]: e.target.value })} />
        </label>
      ))}
      <button type="submit">{boton}</button>
    </form>
  );
}

// En la plataforma integrada primero se resuelve la identidad de la sesión; solo, se usan las de demostración.
export default function App() {
  const [actor, setActor] = useState(INTEGRADO ? null : ACTORES[0]);
  const [errorSesion, setErrorSesion] = useState(null);
  useEffect(() => {
    if (!INTEGRADO) return;
    identidadDePlataforma().then((a) => { if (a) setActor(a); }).catch((e) => setErrorSesion(e.message));
  }, []);
  if (errorSesion) return <div className="pagina"><p>{errorSesion}</p></div>;
  if (!actor) return <div className="pagina"><p>Comprobando la sesión…</p></div>;
  return <Modulo actorInicial={actor} />;
}

function Modulo({ actorInicial }) {
  const [actor, setActor] = useState(actorInicial);
  const [desastres, setDesastres] = useState([]);
  const [desastreId, setDesastreId] = useState(null);
  const [consolidado, setConsolidado] = useState(null);
  const [evaluaciones, setEvaluaciones] = useState([]);
  const [seleccion, setSeleccion] = useState([]);
  const [resultadoLote, setResultadoLote] = useState(null);
  const [proyecto, setProyecto] = useState(null);
  const [auditoria, setAuditoria] = useState(null);
  const [aviso, setAviso] = useState(null);

  const esAutoridad = actor.rol === 'AUTORIDAD';
  const esResponsable = actor.rol === 'RESPONSABLE';
  const veAuditoria = actor.rol === 'AUTORIDAD' || actor.rol === 'AUDITOR';

  const mostrarError = (e) => setAviso({ tipo: 'error', regla: e.regla, texto: e.message });

  const cargarDesastre = useCallback(async (id, quien) => {
    try {
      const c = await api.seguimiento(quien, id);
      setConsolidado(c);
      if (quien.rol === 'AUTORIDAD' && c.desastre.estado === 'RECONSTRUCCION') {
        setEvaluaciones(await api.evaluaciones(quien, id));
      } else {
        setEvaluaciones([]);
      }
    } catch (e) { mostrarError(e); }
  }, []);

  const cargarProyecto = useCallback(async (id, quien) => {
    try {
      setProyecto(await api.proyecto(quien, id));
      setAuditoria(quien.rol === 'RESPONSABLE' ? null : await api.auditoria(quien, id));
    } catch (e) { mostrarError(e); }
  }, []);

  // Al cambiar de usuario se recarga todo con sus permisos.
  useEffect(() => {
    (async () => {
      setProyecto(null); setAuditoria(null); setSeleccion([]); setResultadoLote(null); setAviso(null);
      try {
        const lista = await api.desastres(actor);
        setDesastres(lista);
        const id = desastreId || (lista[0] && lista[0].id);
        if (id) { setDesastreId(id); await cargarDesastre(id, actor); }
      } catch (e) { mostrarError(e); }
    })();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [actor]);

  const elegirDesastre = async (id) => {
    setDesastreId(id); setProyecto(null); setAuditoria(null); setSeleccion([]); setResultadoLote(null); setAviso(null);
    await cargarDesastre(id, actor);
  };

  /** Ejecuta una acción, muestra el resultado o la regla que la rechazó, y refresca la pantalla. */
  const ejecutar = async (accion, mensaje) => {
    try {
      const respuesta = await accion();
      setAviso({ tipo: 'ok', texto: mensaje });
      setDesastres(await api.desastres(actor));
      await cargarDesastre(desastreId, actor);
      if (proyecto) await cargarProyecto(proyecto.id, actor);
      return respuesta || true;
    } catch (e) {
      mostrarError(e);
      if (proyecto && veAuditoria) setAuditoria(await api.auditoria(actor, proyecto.id).catch(() => auditoria));
      return false;
    }
  };

  const crearLote = async () => {
    const elegidas = evaluaciones.filter((e) => seleccion.includes(e.id));
    const solicitudes = elegidas.map((e) => ({ nombre: `${e.tipoActivo}: ${e.descripcion}`, tipo: null, evaluaciones: [e.id] }));
    const r = await ejecutar(() => api.crearProyectos(actor, desastreId, solicitudes), 'Lote procesado.');
    if (r) { setResultadoLote(r); setSeleccion([]); }
  };

  const desastre = consolidado && consolidado.desastre;

  return (
    <div className="pagina">
      <header>
        <div>
          <h1>Módulo de Reconstrucción</h1>
          <p>Plataforma para la Gestión de Desastres y Reconstrucción</p>
        </div>
        <label className="usuario">
          <span>Usuario</span>
          {INTEGRADO ? <strong>{actor.nombre} · <a href="/">Volver a la plataforma</a></strong> : (
            <select value={actor.usuario} onChange={(e) => setActor(ACTORES.find((a) => a.usuario === e.target.value))}>
              {ACTORES.map((a) => <option key={a.usuario} value={a.usuario}>{a.nombre}</option>)}
            </select>
          )}
        </label>
      </header>

      {aviso && (
        <div className={`aviso aviso-${aviso.tipo}`} role="status">
          {aviso.regla && <strong>{aviso.regla}</strong>} {aviso.texto}
          <button className="cerrar" onClick={() => setAviso(null)} aria-label="Cerrar aviso">×</button>
        </div>
      )}

      <div className="columnas">
        <aside>
          <h2>Desastres</h2>
          {desastres.map((d) => (
            <button key={d.id} className={`tarjeta ${d.id === desastreId ? 'activa' : ''}`} onClick={() => elegirDesastre(d.id)}>
              <span>{d.nombre}</span>
              <Insignia valor={d.estado} />
            </button>
          ))}
        </aside>

        {desastre && (
          <main>
            <section>
              <div className="encabezado">
                <h2>{desastre.nombre}</h2>
                {esAutoridad && desastre.estado === 'CARACTERIZACION' && (
                  <button onClick={() => ejecutar(() => api.iniciarReconstruccion(actor, desastre.id), 'El desastre pasó a Reconstrucción.')}>
                    Iniciar reconstrucción
                  </button>
                )}
                {esAutoridad && desastre.estado === 'RECONSTRUCCION' && (
                  <button onClick={() => ejecutar(() => api.cerrarDesastre(actor, desastre.id), 'El desastre quedó cerrado.')}>
                    Cerrar desastre
                  </button>
                )}
              </div>
              <ol className="etapas">
                {ETAPAS.map((e) => (
                  <li key={e} className={e === desastre.estado ? 'actual' : ETAPAS.indexOf(e) < ETAPAS.indexOf(desastre.estado) ? 'pasada' : ''}>
                    {nombre(e)}
                  </li>
                ))}
              </ol>
              <div className="indicadores">
                <div><strong>{consolidado.proyectos.length}</strong><span>Proyectos</span></div>
                <div><strong>{pesos(consolidado.presupuestoTotal)}</strong><span>Presupuesto total</span></div>
                <div><strong>{pesos(consolidado.costoTotal)}</strong><span>Costo acumulado</span></div>
                <div><strong>{consolidado.avancePromedio} %</strong><span>Avance promedio</span></div>
                <div><strong>{consolidado.conSobrecosto}</strong><span>Con sobrecosto</span></div>
              </div>
            </section>

            {esAutoridad && desastre.estado === 'RECONSTRUCCION' && (
              <section>
                <div className="encabezado">
                  <h3>Evaluaciones de daño</h3>
                  <button disabled={seleccion.length === 0} onClick={crearLote}>
                    Crear {seleccion.length || ''} proyecto{seleccion.length === 1 ? '' : 's'}
                  </button>
                </div>
                <table>
                  <thead><tr><th></th><th>Activo</th><th>Descripción</th><th>Daño</th><th>Situación</th></tr></thead>
                  <tbody>
                    {evaluaciones.map((e) => (
                      <tr key={e.id}>
                        <td>
                          <input type="checkbox" aria-label={`Seleccionar ${e.descripcion}`} disabled={e.conProyectoActivo}
                            checked={seleccion.includes(e.id)}
                            onChange={() => setSeleccion(seleccion.includes(e.id) ? seleccion.filter((x) => x !== e.id) : [...seleccion, e.id])} />
                        </td>
                        <td>{e.tipoActivo}</td>
                        <td>{e.descripcion}</td>
                        <td>{nombre(e.nivel)}</td>
                        <td>{e.conProyectoActivo ? 'Ya tiene proyecto' : !e.validada ? 'Sin validar' : e.elegible ? 'Disponible' : 'No requiere intervención'}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
                {resultadoLote && (
                  <ul className="lote">
                    {resultadoLote.map((r) => (
                      <li key={r.indice} className={r.creado ? 'ok' : 'error'}>
                        {r.nombre}: {r.creado ? 'creado' : `rechazado por ${r.regla}. ${r.error}`}
                      </li>
                    ))}
                  </ul>
                )}
              </section>
            )}

            <section>
              <h3>Proyectos</h3>
              {consolidado.proyectos.length === 0 ? <p className="vacio">Este desastre no tiene proyectos de su organización.</p> : (
                <table>
                  <thead><tr><th>Proyecto</th><th>Tipo</th><th>Estado</th><th className="num">Presupuesto</th><th className="num">Costo</th><th>Avance</th></tr></thead>
                  <tbody>
                    {consolidado.proyectos.map((p) => (
                      <tr key={p.id} className={`fila ${proyecto && proyecto.id === p.id ? 'activa' : ''}`} onClick={() => cargarProyecto(p.id, actor)}>
                        <td>{p.nombre}</td>
                        <td>{nombre(p.tipo)}</td>
                        <td><Insignia valor={p.estado} /></td>
                        <td className="num">{pesos(p.presupuesto)}</td>
                        <td className={`num ${p.sobrecosto ? 'sobrecosto' : ''}`}>{pesos(p.costoAcumulado)}{p.sobrecosto ? ' ▲' : ''}</td>
                        <td><div className="barra"><div style={{ width: `${p.avance}%` }} /></div><small>{p.avance} %</small></td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </section>

            {proyecto && (
              <section>
                <div className="encabezado">
                  <h3>{proyecto.nombre}</h3>
                  <Insignia valor={proyecto.estado} />
                </div>
                <dl className="datos">
                  <div><dt>Tipo</dt><dd>{nombre(proyecto.tipo)}</dd></div>
                  <div><dt>Presupuesto</dt><dd>{pesos(proyecto.presupuesto)}</dd></div>
                  <div><dt>Fuente de financiación</dt><dd>{proyecto.fuenteFinanciacion || '—'}</dd></div>
                  <div><dt>Responsable</dt><dd>{proyecto.responsable || '—'}</dd></div>
                  <div><dt>Contratista</dt><dd>{proyecto.contratista || '—'}</dd></div>
                  <div><dt>Cronograma</dt><dd>{proyecto.inicioPlan ? `${proyecto.inicioPlan} a ${proyecto.finPlan}` : '—'}</dd></div>
                  <div><dt>Avance</dt><dd>{proyecto.avance} %</dd></div>
                  <div><dt>Costo acumulado</dt><dd className={proyecto.sobrecosto ? 'sobrecosto' : ''}>{pesos(proyecto.costoAcumulado)}{proyecto.sobrecosto ? ' (sobrecosto)' : ''}</dd></div>
                </dl>

                <div className="acciones">
                  {esAutoridad && proyecto.estado === 'PROPUESTO' && (
                    <Formulario titulo="Aprobar" boton="Aprobar proyecto"
                      campos={[{ clave: 'presupuesto', etiqueta: 'Presupuesto (COP)', tipo: 'number' }, { clave: 'fuenteFinanciacion', etiqueta: 'Fuente de financiación' }, { clave: 'responsable', etiqueta: 'Responsable' }]}
                      onEnviar={(v) => ejecutar(() => api.aprobar(actor, proyecto.id, { ...v, presupuesto: v.presupuesto ? Number(v.presupuesto) : null }), 'Proyecto aprobado.')} />
                  )}
                  {esResponsable && proyecto.estado === 'APROBADO' && (
                    <Formulario titulo="Iniciar ejecución" boton="Iniciar"
                      campos={[{ clave: 'contratista', etiqueta: 'Contratista' }, { clave: 'inicio', etiqueta: 'Inicio', tipo: 'date' }, { clave: 'fin', etiqueta: 'Fin', tipo: 'date' }]}
                      onEnviar={(v) => ejecutar(() => api.iniciar(actor, proyecto.id, v), 'Ejecución iniciada.')} />
                  )}
                  {esAutoridad && ['APROBADO', 'EN_EJECUCION', 'SUSPENDIDO'].includes(proyecto.estado) && (
                    <Formulario titulo="Modificar presupuesto" boton="Guardar cambio"
                      campos={[{ clave: 'presupuesto', etiqueta: 'Nuevo presupuesto (COP)', tipo: 'number' }, { clave: 'motivo', etiqueta: 'Motivo' }]}
                      onEnviar={(v) => ejecutar(() => api.presupuesto(actor, proyecto.id, { presupuesto: v.presupuesto ? Number(v.presupuesto) : null, motivo: v.motivo }), 'Presupuesto modificado.')} />
                  )}
                  {esResponsable && proyecto.estado === 'EN_EJECUCION' && (
                    <>
                      <Formulario titulo="Registrar avance" boton="Registrar"
                        campos={[{ clave: 'porcentaje', etiqueta: 'Avance (%)', tipo: 'number', min: 0, max: 100 }, { clave: 'descripcion', etiqueta: 'Descripción' }, { clave: 'motivoCorreccion', etiqueta: 'Motivo si corrige hacia abajo' }]}
                        onEnviar={(v) => ejecutar(() => api.avance(actor, proyecto.id, { ...v, porcentaje: v.porcentaje === undefined || v.porcentaje === '' ? null : Number(v.porcentaje) }), 'Avance registrado.')} />
                      <Formulario titulo="Registrar costo" boton="Registrar"
                        campos={[{ clave: 'valor', etiqueta: 'Valor (COP)', tipo: 'number' }, { clave: 'descripcion', etiqueta: 'Descripción' }]}
                        onEnviar={(v) => ejecutar(() => api.costo(actor, proyecto.id, { valor: v.valor ? Number(v.valor) : null, descripcion: v.descripcion }), 'Costo registrado.')} />
                    </>
                  )}
                  {esResponsable && !['FINALIZADO', 'CANCELADO'].includes(proyecto.estado) && (
                    <Formulario titulo="Adjuntar evidencia" boton="Adjuntar"
                      campos={[{ clave: 'descripcion', etiqueta: 'Descripción o referencia del documento' }]}
                      onEnviar={(v) => ejecutar(() => api.evidencia(actor, proyecto.id, v), 'Evidencia adjuntada.')} />
                  )}
                  {(esAutoridad || esResponsable) && proyecto.estadosSiguientes.length > 0 && (
                    <CambioDeEstado proyecto={proyecto}
                      onCambiar={(estado, motivo) => ejecutar(() => api.estado(actor, proyecto.id, { estado, motivo }), `Proyecto ${nombre(estado).toLowerCase()}.`)} />
                  )}
                </div>

                <h4>Seguimiento</h4>
                {proyecto.seguimiento.length === 0 ? <p className="vacio">Sin registros de avance, costo ni evidencias.</p> : (
                  <table>
                    <thead><tr><th>Fecha</th><th>Tipo</th><th className="num">Valor</th><th>Descripción</th><th>Usuario</th></tr></thead>
                    <tbody>
                      {proyecto.seguimiento.map((s, i) => (
                        <tr key={i}>
                          <td>{fechaHora(s.momento)}</td><td>{nombre(s.tipo)}</td>
                          <td className="num">{s.tipo === 'AVANCE' ? `${s.valor} %` : s.tipo === 'COSTO' ? pesos(s.valor) : '—'}</td>
                          <td>{s.descripcion || '—'}</td><td>{s.usuario}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                )}

                {veAuditoria && auditoria && (
                  <>
                    <h4>Auditoría</h4>
                    <table>
                      <thead><tr><th>Fecha</th><th>Usuario</th><th>Operación</th><th>Anterior</th><th>Nuevo</th><th>Motivo</th><th>Resultado</th></tr></thead>
                      <tbody>
                        {auditoria.map((a, i) => (
                          <tr key={i} className={a.resultado === 'RECHAZADO' ? 'rechazado' : ''}>
                            <td>{fechaHora(a.momento)}</td><td>{a.usuario} ({nombre(a.rol).toLowerCase()})</td>
                            <td>{a.operacion}</td><td>{a.valorAnterior || '—'}</td><td>{a.valorNuevo || '—'}</td>
                            <td>{a.motivo || '—'}</td><td>{a.resultado === 'RECHAZADO' ? `Rechazado. ${a.detalle}` : 'Exitoso'}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </>
                )}
              </section>
            )}
          </main>
        )}
      </div>
    </div>
  );
}

/** Suspender, reanudar, finalizar o cancelar. Solo ofrece los destinos válidos desde el estado actual. */
function CambioDeEstado({ proyecto, onCambiar }) {
  const [motivo, setMotivo] = useState('');
  const destinos = proyecto.estadosSiguientes.filter((e) => e !== 'APROBADO' && (e !== 'EN_EJECUCION' || proyecto.estado === 'SUSPENDIDO'));
  const etiqueta = { SUSPENDIDO: 'Suspender', EN_EJECUCION: 'Reanudar', FINALIZADO: 'Finalizar', CANCELADO: 'Cancelar proyecto' };
  if (destinos.length === 0) return null;
  const cambiar = async (estado) => { if (await onCambiar(estado, motivo)) setMotivo(''); };
  return (
    <div className="formulario">
      <h4>Cambiar estado</h4>
      <label>
        <span>Motivo (obligatorio para suspender y cancelar)</span>
        <input value={motivo} onChange={(e) => setMotivo(e.target.value)} />
      </label>
      <div className="botones">
        {destinos.map((e) => (
          <button key={e} type="button" className={e === 'CANCELADO' ? 'peligro' : ''} onClick={() => cambiar(e)}>{etiqueta[e]}</button>
        ))}
      </div>
    </div>
  );
}
