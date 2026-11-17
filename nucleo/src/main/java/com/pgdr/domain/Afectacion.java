package com.pgdr.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="afectaciones")
public class Afectacion {
    @Id private UUID id;
    @Column(name="desastre_id",nullable=false) private UUID desastreId;
    @Column(name="organizacion_id",nullable=false) private UUID organizacionId;
    @Column(name="solicitud_id",nullable=false) private UUID solicitudId;
    @Column(name="huella_solicitud",nullable=false,length=64) private String huellaSolicitud;
    @Column(nullable=false,precision=9,scale=6) private BigDecimal latitud;
    @Column(nullable=false,precision=9,scale=6) private BigDecimal longitud;
    @Column(name="nivel_afectacion",nullable=false) private Integer nivelAfectacion;
    @Column(name="observacion",length=500) private String observacion;
    @Column(name="creado_en",nullable=false) private Instant creadoEn;
    protected Afectacion() {}
    public UUID getId(){return id;}
    public UUID getDesastreId(){return desastreId;}
    public UUID getOrganizacionId(){return organizacionId;}
    public UUID getSolicitudId(){return solicitudId;}
    public String getHuellaSolicitud(){return huellaSolicitud;}
    public BigDecimal getLatitud(){return latitud;}
    public BigDecimal getLongitud(){return longitud;}
    public Integer getNivelAfectacion(){return nivelAfectacion;}
    public String getObservacion(){return observacion;}
    public Instant getCreadoEn(){return creadoEn;}
}
