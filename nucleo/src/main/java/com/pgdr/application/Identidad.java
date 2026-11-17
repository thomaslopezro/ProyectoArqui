package com.pgdr.application;

import java.util.UUID;

public record Identidad(UUID usuarioId,UUID organizacionId,String usuario,String rol) {
    public boolean puedeRegistrarDesastre(){return "COORDINADOR".equals(rol);}
    public boolean puedeRegistrarAfectacion(){return "COORDINADOR".equals(rol)||"EVALUADOR".equals(rol);}
}
