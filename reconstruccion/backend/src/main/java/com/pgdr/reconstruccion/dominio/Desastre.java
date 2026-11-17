package com.pgdr.reconstruccion.dominio;

import java.util.UUID;

/** Vista del desastre que necesita Reconstrucción: identidad y estado. */
public record Desastre(UUID id, String nombre, EstadoDesastre estado) {}
