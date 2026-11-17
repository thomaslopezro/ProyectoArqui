package com.pgdr.reconstruccion.dominio;

/** Ciclo del desastre dibujado por el profesor: Registro, Caracterización, Reconstrucción, Cierre. */
public enum EstadoDesastre {
    REGISTRO, CARACTERIZACION, RECONSTRUCCION, CIERRE;

    public boolean puedePasarA(EstadoDesastre destino) {
        return switch (this) {
            case REGISTRO -> destino == CARACTERIZACION || destino == CIERRE;       // RN-03
            case CARACTERIZACION -> destino == RECONSTRUCCION || destino == CIERRE; // RN-01, RN-03
            case RECONSTRUCCION -> destino == CIERRE;                               // RN-04
            case CIERRE -> false;                                                   // RN-05
        };
    }
}
