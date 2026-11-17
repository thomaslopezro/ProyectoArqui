package com.pgdr.reconstruccion.dominio;

import java.util.EnumSet;
import java.util.Set;

/** Estados del proyecto y sus transiciones válidas (tabla, no condicionales dispersos). */
public enum EstadoProyecto {
    PROPUESTO, APROBADO, EN_EJECUCION, SUSPENDIDO, FINALIZADO, CANCELADO;

    public Set<EstadoProyecto> destinos() {
        return switch (this) {
            case PROPUESTO -> EnumSet.of(APROBADO, CANCELADO);
            case APROBADO -> EnumSet.of(EN_EJECUCION, CANCELADO);
            case EN_EJECUCION -> EnumSet.of(SUSPENDIDO, FINALIZADO, CANCELADO);
            case SUSPENDIDO -> EnumSet.of(EN_EJECUCION, CANCELADO);
            case FINALIZADO, CANCELADO -> EnumSet.noneOf(EstadoProyecto.class);
        };
    }

    public boolean puedePasarA(EstadoProyecto destino) {
        return destinos().contains(destino);
    }

    /** RN-16: Finalizado y Cancelado son terminales. */
    public boolean esTerminal() {
        return destinos().isEmpty();
    }
}
