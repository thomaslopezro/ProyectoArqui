package com.pgdr.reconstruccion.aplicacion.puertos;

/** Otro usuario cambió el proyecto primero; hay que reintentar sobre el dato vigente. */
public class ConflictoDeVersionException extends RuntimeException {
    public ConflictoDeVersionException(String mensaje) {
        super(mensaje);
    }
}
