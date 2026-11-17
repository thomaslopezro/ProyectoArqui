package com.pgdr.infrastructure;
import co.edu.javeriana.emergencias.aplicacion.puerto.SolicitudRepositorio;
import co.edu.javeriana.emergencias.aplicacion.excepcion.ReglaNegocioException;
import co.edu.javeriana.emergencias.dominio.modelo.*;
import com.pgdr.application.ContextoSolicitud;
import com.pgdr.domain.SolicitudRecurso;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.*;
import java.util.*;

@ApplicationScoped
public class SolicitudRepositorioJpa implements SolicitudRepositorio {
 @PersistenceContext(unitName="PGDR_PU") EntityManager em;
 @Inject ContextoSolicitud contexto;
 public Solicitud guardar(Solicitud s){
  var a=contexto.identidad();
  int n=em.createNativeQuery("INSERT INTO solicitudes_recursos(solicitud_id,organizacion_id,desastre_id,descripcion_desastre,tipo_recurso,cantidad,prioridad,estado,huella,creada_por,evaluacion_id,evaluacion_version,evaluacion_resumen) VALUES (?1,?2,?3,?4,?5,?6,?7,'PENDIENTE',?8,?9,?10,?11,?12) ON CONFLICT(organizacion_id,solicitud_id) DO NOTHING")
    .setParameter(1,contexto.solicitudId()).setParameter(2,a.organizacionId()).setParameter(3,contexto.desastreId())
    .setParameter(4,s.getDescripcionDesastre()).setParameter(5,s.getTipoRecurso()).setParameter(6,s.getCantidad())
    .setParameter(7,s.getPrioridad().name()).setParameter(8,contexto.huella()).setParameter(9,a.usuarioId()).setParameter(10,contexto.evaluacionId()).setParameter(11,contexto.evaluacionVersion()).setParameter(12,contexto.evaluacionResumen()).executeUpdate();
  var item=em.createQuery("SELECT s FROM SolicitudRecurso s WHERE s.organizacionId=:org AND s.solicitudId=:id",SolicitudRecurso.class)
    .setParameter("org",a.organizacionId()).setParameter("id",contexto.solicitudId()).getSingleResult();
  if(!item.huella.equals(contexto.huella()))throw new ConflictoSolicitud();
  contexto.creada(n==1);
  if(n==1)auditar(item.id,"SOLICITUD_REGISTRADA");
  return dominio(item);
 }
 private void auditar(Long id,String accion){
  em.createNativeQuery("INSERT INTO solicitudes_historial(solicitud_id,actor_id,accion) VALUES (?1,?2,?3)")
   .setParameter(1,id).setParameter(2,contexto.identidad().usuarioId()).setParameter(3,accion).executeUpdate();
 }
 public List<Solicitud> buscarTodas(){return em.createQuery("SELECT s FROM SolicitudRecurso s WHERE s.organizacionId=:org ORDER BY s.id DESC",SolicitudRecurso.class)
  .setParameter("org",contexto.identidad().organizacionId()).getResultList().stream().map(this::dominio).toList();}
 public Optional<Solicitud> buscarPorId(Long id){return entidad(id).map(this::dominio);}
 public Optional<SolicitudRecurso> entidad(Long id){return em.createQuery("SELECT s FROM SolicitudRecurso s WHERE s.id=:id AND s.organizacionId=:org",SolicitudRecurso.class)
  .setParameter("id",id).setParameter("org",contexto.identidad().organizacionId()).getResultList().stream().findFirst();}
 public boolean actualizarEstadoSiCoincide(Long id,EstadoSolicitud esperado,EstadoSolicitud nuevo){
  if(esperado!=EstadoSolicitud.PENDIENTE || nuevo!=EstadoSolicitud.ATENDIDA)return false;
  if(!contexto.identidad().puedeRegistrarDesastre())throw new SecurityException("SIN_PERMISO");
  int n=em.createNativeQuery("UPDATE solicitudes_recursos SET estado='ATENDIDA',atendida_por=?1,atendida_en=now() WHERE id=?2 AND organizacion_id=?3 AND estado='PENDIENTE'")
   .setParameter(1,contexto.identidad().usuarioId()).setParameter(2,id).setParameter(3,contexto.identidad().organizacionId()).executeUpdate();
  if(n==1){auditar(id,"SOLICITUD_ATENDIDA");em.clear();}
  return n==1;
 }
 private Solicitud dominio(SolicitudRecurso s){return new Solicitud(s.id,s.descripcionDesastre,s.tipoRecurso,s.cantidad,Prioridad.valueOf(s.prioridad),EstadoSolicitud.valueOf(s.estado));}
 public static class ConflictoSolicitud extends ReglaNegocioException {public ConflictoSolicitud(){super("La solicitudId ya corresponde a otros datos");}}
}
