package com.pgdr.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="outbox_eventos")
public class EventoOutbox {
    @Id private UUID id;
    @Column(name="organizacion_id",nullable=false) private UUID organizacionId;
    @Column(name="tipo",nullable=false,length=80) private String tipo;
    @Column(name="agregado_id",nullable=false) private UUID agregadoId;
    @Column(name="payload_json",nullable=false,columnDefinition="text") private String payloadJson;
    @Column(name="creado_en",nullable=false) private Instant creadoEn;
    @Column(name="publicado_en") private Instant publicadoEn;
    protected EventoOutbox() {}
    public EventoOutbox(UUID org,String tipo,UUID agregadoId,String json){
      this.id=UUID.randomUUID();this.organizacionId=org;this.tipo=tipo;
      this.agregadoId=agregadoId;this.payloadJson=json;this.creadoEn=Instant.now();
    }
}
