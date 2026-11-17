package com.pgdr.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="sesiones")
public class Sesion {
    @Id @Column(name="token_hash",length=64) private String tokenHash;
    @Column(name="usuario_id",nullable=false) private UUID usuarioId;
    @Column(name="expira_en",nullable=false) private Instant expiraEn;
    protected Sesion() {}
    public Sesion(String tokenHash,UUID usuarioId,Instant expiraEn){
      this.tokenHash=tokenHash;this.usuarioId=usuarioId;this.expiraEn=expiraEn;
    }
    public UUID getUsuarioId(){return usuarioId;}
    public Instant getExpiraEn(){return expiraEn;}
}
