package com.pgdr.reconstruccion.dominio;

/** Quién ejecuta una operación: usuario, rol y organización a la que pertenece. */
public record Actor(String usuario, Rol rol, String organizacion) {
    public Actor {
        if (usuario == null || usuario.isBlank()) throw new IllegalArgumentException("usuario requerido");
        if (rol == null) throw new IllegalArgumentException("rol requerido");
        if (organizacion == null || organizacion.isBlank()) throw new IllegalArgumentException("organización requerida");
    }
}
