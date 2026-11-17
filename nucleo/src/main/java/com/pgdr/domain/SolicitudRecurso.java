package com.pgdr.domain;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="solicitudes_recursos")
public class SolicitudRecurso {
 @Id public Long id;
 @Column(name="solicitud_id") public UUID solicitudId;
 @Column(name="organizacion_id") public UUID organizacionId;
 @Column(name="desastre_id") public UUID desastreId;
 @Column(name="descripcion_desastre") public String descripcionDesastre;
 @Column(name="tipo_recurso") public String tipoRecurso;
 public int cantidad;
 public String prioridad,estado,huella;
 @Column(name="creada_por") public UUID creadaPor;
 @Column(name="creada_en") public Instant creadaEn;
 @Column(name="atendida_por") public UUID atendidaPor;
 @Column(name="atendida_en") public Instant atendidaEn;
 @Column(name="evaluacion_id") public UUID evaluacionId;
 @Column(name="evaluacion_version") public Long evaluacionVersion;
 @Column(name="evaluacion_resumen") public String evaluacionResumen;
 public SolicitudRecurso(){}
}
