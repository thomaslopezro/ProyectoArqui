package com.pgdr.application;
import co.edu.javeriana.emergencias.aplicacion.puerto.SolicitudCasosUso;
import co.edu.javeriana.emergencias.aplicacion.excepcion.*;
import co.edu.javeriana.emergencias.dominio.modelo.*;
import com.pgdr.infrastructure.*;
import com.pgdr.domain.SolicitudRecurso;
import jakarta.ejb.*;
import jakarta.inject.Inject;
import jakarta.persistence.*;
import java.util.*;

@Stateless
public class SolicitudesProyecto {
 @EJB SolicitudCasosUso reglas;
 @EJB DesastreRepository desastres;
 @EJB RegistroIntegrado catalogo;
 @Inject ContextoSolicitud contexto;
 @Inject SolicitudRepositorioJpa repository;
 public void iniciar(Identidad actor){contexto.actor(actor);}
 public record Nueva(UUID solicitudId,String eventoId,String tipoRecurso,Integer cantidad,Prioridad prioridad){}
 public record Creacion(SolicitudRecurso solicitud,boolean creada){}
 public Creacion desdeEvaluacion(Identidad actor,Nueva r,UUID id,long version,String resumen){
  if(id==null || version<0 || resumen==null || resumen.length()>5000)throw new ReglaNegocioException("Origen de evaluación inválido");
  contexto.evaluacionId(id);contexto.evaluacionVersion(version);contexto.evaluacionResumen(resumen);return crear(actor,r);
 }
 public Creacion crear(Identidad actor,Nueva r){
  iniciar(actor);
  if(!actor.puedeRegistrarAfectacion())throw new SecurityException("SIN_PERMISO");
  if(r==null || r.solicitudId()==null || r.eventoId()==null || r.eventoId().isBlank() || r.eventoId().length()>100 || r.cantidad()==null)
   throw new ReglaNegocioException("solicitudId, desastre y cantidad son obligatorios");
  if(r.tipoRecurso()!=null && r.tipoRecurso().trim().length()>120)throw new ReglaNegocioException("Recurso: máximo 120 caracteres");
  // Resolve the event through an organization-scoped lookup; do not scan other organizations.
  UUID desastre=desastresId(r.eventoId(),actor.organizacionId());
  var datos=catalogo.datos(desastre);
  contexto.desastreId(desastre);contexto.solicitudId(r.solicitudId());
  String recurso=r.tipoRecurso()==null?"":r.tipoRecurso().trim();
  String material=r.eventoId().length()+":"+r.eventoId()+recurso.length()+":"+recurso+":"+r.cantidad()+":"+r.prioridad();
  if(contexto.evaluacionId()!=null)material+=":"+contexto.evaluacionId()+":"+contexto.evaluacionVersion()+":"+contexto.evaluacionResumen();
  contexto.huella(Criptografia.sha256(material));
  String descripcion=datos.getString("tipo")+" · "+datos.getString("ubicacion");
  Solicitud s=reglas.registrarSolicitud(descripcion,r.tipoRecurso(),r.cantidad(),r.prioridad());
  return new Creacion(repository.entidad(s.getId()).orElseThrow(),contexto.creada());
 }
 @PersistenceContext(unitName="PGDR_PU") EntityManager em;
 private UUID desastresId(String ref,UUID org){
  var rows=em.createNativeQuery("SELECT d.id FROM desastres d LEFT JOIN desastres_integrados i ON i.desastre_id=d.id WHERE d.organizacion_id=?1 AND (i.referencia=?2 OR (i.referencia IS NULL AND d.id::text=?2))")
   .setParameter(1,org).setParameter(2,ref).getResultList();
  if(rows.isEmpty())throw new SolicitudNoEncontradaException("Desastre no encontrado");return UUID.fromString(rows.get(0).toString());
 }
 public List<SolicitudRecurso> listar(Identidad actor,boolean pendientes,String evento){
  iniciar(actor);
  // Reuse the workshop's pending-state rule; enrichment adds project identity and traceability.
  return (pendientes?reglas.listarPendientes():reglas.listarSolicitudes()).stream()
   .map(s->repository.entidad(s.getId()).orElseThrow()).filter(s->evento==null || catalogo.datos(s.desastreId).getString("id").equals(evento)).toList();
 }
 public java.util.Optional<SolicitudRecurso> porClave(Identidad actor,UUID clave){iniciar(actor);return em.createQuery("SELECT s FROM SolicitudRecurso s WHERE s.organizacionId=:org AND s.solicitudId=:clave",SolicitudRecurso.class).setParameter("org",actor.organizacionId()).setParameter("clave",clave).getResultList().stream().findFirst();}
 public SolicitudRecurso buscar(Identidad actor,Long id){iniciar(actor);reglas.buscarPorId(id);return repository.entidad(id).orElseThrow();}
 public SolicitudRecurso atender(Identidad actor,Long id){
  iniciar(actor);if(!actor.puedeRegistrarDesastre())throw new SecurityException("SIN_PERMISO");
  reglas.atenderSolicitud(id);return repository.entidad(id).orElseThrow();
 }
 public String referencia(UUID id){return catalogo.datos(id).getString("id");}
 public List<?> historial(Identidad actor,Long id){buscar(actor,id);
  return em.createNativeQuery("SELECT h.accion,u.usuario,h.ocurrida_en::text FROM solicitudes_historial h JOIN usuarios u ON u.id=h.actor_id WHERE h.solicitud_id=?1 ORDER BY h.id").setParameter(1,id).getResultList();
 }
}
