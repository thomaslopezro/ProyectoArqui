package com.pgdr.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="desastres")
public class Desastre {
    @Id @Column(name="id",nullable=false) private UUID id;
    @Column(name="nombre",nullable=false,length=150) private String nombre;
    @Column(name="descripcion") private String descripcion;
    @Column(name="severidad",nullable=false,length=10) private String severidad;
    @Column(name="creado_en",nullable=false) private Instant creadoEn;
    @Version @Column(name="version",nullable=false) private Long version;
    @Column(name="organizacion_id") private UUID organizacionId;
    @Column(name="solicitud_id") private UUID solicitudId;
    @Column(name="huella_solicitud",length=64) private String huellaSolicitud;
    protected Desastre() {}
    public UUID getId(){return id;}
    public String getNombre(){return nombre;}
    public String getDescripcion(){return descripcion;}
    public String getSeveridad(){return severidad;}
    public Instant getCreadoEn(){return creadoEn;}
    public Long getVersion(){return version;}
    public UUID getOrganizacionId(){return organizacionId;}
    public UUID getSolicitudId(){return solicitudId;}
    public String getHuellaSolicitud(){return huellaSolicitud;}
}
