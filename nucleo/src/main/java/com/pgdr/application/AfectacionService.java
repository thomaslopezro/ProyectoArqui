package com.pgdr.application;

import com.pgdr.api.AfectacionRequest;
import com.pgdr.domain.Afectacion;
import com.pgdr.domain.EventoOutbox;
import com.pgdr.infrastructure.AfectacionRepository;
import com.pgdr.infrastructure.DesastreRepository;
import com.pgdr.infrastructure.OutboxRepository;
import jakarta.ejb.*;
import jakarta.json.Json;
import java.math.BigDecimal;
import java.util.*;

@Stateless
public class AfectacionService {
    @EJB private DesastreRepository desastres;
    @EJB private AfectacionRepository repository;
    @EJB private OutboxRepository outbox;
    public record Resultado(Afectacion afectacion,boolean creado,boolean conflicto,boolean desastreInexistente){}
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public Resultado registrar(Identidad actor,AfectacionRequest r){
      if(!actor.puedeRegistrarAfectacion())throw new SecurityException("SIN_PERMISO");
      if(r==null||r.solicitudId==null||r.desastreId==null||r.latitud==null||r.longitud==null||r.nivelAfectacion==null)
        throw new ValidacionException("Campos requeridos faltantes");
      BigDecimal lat=r.latitud.stripTrailingZeros(), lon=r.longitud.stripTrailingZeros();
      if(lat.compareTo(new BigDecimal("-90"))<0||lat.compareTo(new BigDecimal("90"))>0||lon.compareTo(new BigDecimal("-180"))<0||lon.compareTo(new BigDecimal("180"))>0||lat.scale()>6||lon.scale()>6||r.nivelAfectacion<1||r.nivelAfectacion>5||(r.observacion!=null&&r.observacion.length()>500))
        throw new ValidacionException("Afectacion invalida");
      String huella=Huellas.afectacion(r.desastreId.toString(),lat,lon,r.nivelAfectacion,r.observacion);
      Afectacion anterior=repository.porSolicitud(actor.organizacionId(),r.solicitudId);
      if(anterior!=null)return new Resultado(anterior,false,!huella.equals(anterior.getHuellaSolicitud()),false);
      if(desastres.buscar(r.desastreId,actor.organizacionId())==null)return new Resultado(null,false,false,true);
      UUID id=UUID.randomUUID();
      boolean creado=repository.insertarSiNoExiste(id,actor.organizacionId(),r.solicitudId,r.desastreId,lat,lon,r.nivelAfectacion,r.observacion,huella);
      Afectacion a=creado?repository.buscar(id):repository.porSolicitud(actor.organizacionId(),r.solicitudId);
      if(a==null)throw new IllegalStateException("No se pudo recuperar afectacion");
      if(!huella.equals(a.getHuellaSolicitud()))return new Resultado(a,false,true,false);
      if(creado){
        String json=Json.createObjectBuilder().add("afectacionId",a.getId().toString()).add("desastreId",a.getDesastreId().toString())
          .add("nivelAfectacion",a.getNivelAfectacion()).add("latitud",a.getLatitud()).add("longitud",a.getLongitud()).build().toString();
        outbox.guardar(new EventoOutbox(actor.organizacionId(),"AFECTACION_REGISTRADA",a.getId(),json));
      }
      return new Resultado(a,creado,false,false);
    }
    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public List<Afectacion> listar(Identidad actor,UUID desastre,int limit){return repository.listar(actor.organizacionId(),desastre,limit);}
}
