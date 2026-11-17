package com.pgdr.application;

import com.pgdr.domain.Desastre;
import com.pgdr.domain.AuditoriaDesastre;
import com.pgdr.domain.EventoOutbox;
import com.pgdr.infrastructure.DesastreRepository;
import com.pgdr.infrastructure.AuditoriaRepository;
import com.pgdr.infrastructure.OutboxRepository;
import jakarta.ejb.*;
import jakarta.json.Json;
import java.util.List;
import java.util.UUID;

@Stateless
public class DesastreService {
    @EJB private DesastreRepository repository;
    @EJB private AuditoriaRepository auditoria;
    @EJB private OutboxRepository outbox;
    public record Resultado(Desastre desastre,boolean creado,boolean conflicto) {}

    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public Resultado registrar(Identidad actor,UUID solicitudId,String nombre,String descripcion,Integer nivel){
      if(!actor.puedeRegistrarDesastre())throw new SecurityException("SIN_PERMISO");
      if(solicitudId==null)throw new ValidacionException("solicitudId es obligatorio");
      if(nombre==null||nombre.isBlank()||nombre.length()>150)throw new ValidacionException("Nombre invalido");
      if(nivel==null||nivel<1||nivel>4)throw new ValidacionException("Severidad fuera de rango");
      String limpio=nombre.trim();
      String huella=Huellas.desastre(limpio,descripcion,nivel);
      UUID id=UUID.randomUUID();
      boolean creado=repository.insertarSiNoExiste(id,actor.organizacionId(),solicitudId,limpio,descripcion,"Nivel_"+nivel,huella);
      Desastre d=creado?repository.buscar(id,actor.organizacionId()):repository.porSolicitud(actor.organizacionId(),solicitudId);
      if(d==null)throw new IllegalStateException("No se pudo recuperar el registro");
      if(!huella.equals(d.getHuellaSolicitud()))return new Resultado(d,false,true);
      if(creado){
        auditoria.guardar(new AuditoriaDesastre(d.getId(),"DESASTRE_REGISTRADO",actor.usuarioId().toString(),"Registro autorizado por API"));
        String payload=Json.createObjectBuilder().add("desastreId",d.getId().toString()).add("organizacionId",actor.organizacionId().toString()).add("severidad",d.getSeveridad()).build().toString();
        outbox.guardar(new EventoOutbox(actor.organizacionId(),"DESASTRE_REGISTRADO",d.getId(),payload));
      }
      return new Resultado(d,creado,false);
    }
    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public Desastre buscar(UUID id,Identidad actor){return repository.buscar(id,actor.organizacionId());}
    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public List<Desastre> listar(Identidad actor,int max){return repository.listar(actor.organizacionId(),max);}
}
