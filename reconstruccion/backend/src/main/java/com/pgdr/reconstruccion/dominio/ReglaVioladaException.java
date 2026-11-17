package com.pgdr.reconstruccion.dominio;

/** Una regla de negocio (RN-xx) impidió la operación. */
public class ReglaVioladaException extends RuntimeException {
    private final String regla;

    public ReglaVioladaException(String regla, String mensaje) {
        super(mensaje);
        this.regla = regla;
    }

    public String regla() {
        return regla;
    }
}
