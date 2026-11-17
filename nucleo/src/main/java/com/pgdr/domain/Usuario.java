package com.pgdr.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity @Table(name="usuarios")
public class Usuario {
    @Id private UUID id;
    @Column(nullable=false,unique=true,length=80) private String usuario;
    @Column(name="password_hash",nullable=false,length=128) private String passwordHash;
    @Column(name="password_salt",nullable=false,length=64) private String passwordSalt;
    @Column(nullable=false,length=25) private String rol;
    @Column(name="organizacion_id",nullable=false) private UUID organizacionId;
    @Column(nullable=false) private boolean activo;
    protected Usuario() {}
    public Usuario(UUID id,String usuario,String hash,String salt,String rol,UUID org){
      this.id=id;this.usuario=usuario;this.passwordHash=hash;this.passwordSalt=salt;
      this.rol=rol;this.organizacionId=org;this.activo=true;
    }
    public UUID getId(){return id;}
    public String getUsuario(){return usuario;}
    public String getPasswordHash(){return passwordHash;}
    public String getPasswordSalt(){return passwordSalt;}
    public String getRol(){return rol;}
    public UUID getOrganizacionId(){return organizacionId;}
    public boolean isActivo(){return activo;}
}
