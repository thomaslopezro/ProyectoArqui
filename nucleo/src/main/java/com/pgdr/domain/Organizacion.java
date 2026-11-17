package com.pgdr.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity @Table(name="organizaciones")
public class Organizacion {
    @Id private UUID id;
    @Column(nullable=false,length=120) private String nombre;
    protected Organizacion() {}
    public Organizacion(UUID id,String nombre){this.id=id;this.nombre=nombre;}
    public UUID getId(){return id;}
}
