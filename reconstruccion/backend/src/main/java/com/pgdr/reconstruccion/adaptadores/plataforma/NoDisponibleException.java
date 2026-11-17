package com.pgdr.reconstruccion.adaptadores.plataforma;

/** Otra unidad de la plataforma no respondió o respondió con un error propio. */
public class NoDisponibleException extends RuntimeException {
    public NoDisponibleException(String mensaje) {
        super(mensaje);
    }
}
