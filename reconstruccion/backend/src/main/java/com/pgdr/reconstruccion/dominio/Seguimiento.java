package com.pgdr.reconstruccion.dominio;

import java.math.BigDecimal;
import java.time.Instant;

/** Registro del historial de seguimiento: avance (porcentaje), costo (valor) o evidencia. */
public record Seguimiento(Tipo tipo, BigDecimal valor, String descripcion, Instant momento, String usuario) {
    public enum Tipo { AVANCE, COSTO, EVIDENCIA }
}
