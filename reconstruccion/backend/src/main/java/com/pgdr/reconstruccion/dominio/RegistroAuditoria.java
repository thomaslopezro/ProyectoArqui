package com.pgdr.reconstruccion.dominio;

import java.time.Instant;
import java.util.UUID;

/** RN-17: quién, qué hizo, cuándo, sobre qué y con qué resultado. Inmutable. */
public record RegistroAuditoria(UUID id, Instant momento, String usuario, Rol rol, String organizacion,
                                String operacion, String entidad, UUID entidadId, UUID desastreId,
                                String valorAnterior, String valorNuevo, String motivo,
                                Resultado resultado, String detalle) {
    public enum Resultado { EXITOSO, RECHAZADO }
}
