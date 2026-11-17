
package com.pgdr.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "auditoria_desastres")
public class AuditoriaDesastre {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "desastre_id", nullable = false)
    private UUID desastreId;

    @Column(name = "accion", nullable = false, length = 40)
    private String accion;

    @Column(name = "actor_id", length = 100)
    private String actorId;

    @Column(name = "detalle")
    private String detalle;

    @Column(name = "ocurrido_en", nullable = false)
    private Instant ocurridoEn;

    protected AuditoriaDesastre() {
    }

    public AuditoriaDesastre(
            UUID desastreId,
            String accion,
            String actorId,
            String detalle) {

        this.id = UUID.randomUUID();
        this.desastreId = desastreId;
        this.accion = accion;
        this.actorId = actorId;
        this.detalle = detalle;
        this.ocurridoEn = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getDesastreId() {
        return desastreId;
    }

    public String getAccion() {
        return accion;
    }

    public String getActorId() {
        return actorId;
    }

    public Instant getOcurridoEn() {
        return ocurridoEn;
    }
}
