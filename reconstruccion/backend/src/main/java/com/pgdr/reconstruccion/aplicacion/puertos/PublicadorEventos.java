package com.pgdr.reconstruccion.aplicacion.puertos;

import java.util.Map;
import java.util.UUID;

/** Publica un evento de dominio dentro de la transacción en curso (bandeja de salida). */
public interface PublicadorEventos {
    void publicar(String tipo, UUID agregadoId, Map<String, String> datos);
}
