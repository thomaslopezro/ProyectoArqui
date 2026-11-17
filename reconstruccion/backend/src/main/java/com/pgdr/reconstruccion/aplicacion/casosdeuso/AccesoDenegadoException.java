package com.pgdr.reconstruccion.aplicacion.casosdeuso;

/** RN-10: el actor no tiene el rol requerido o el recurso es de otra organización. */
public class AccesoDenegadoException extends RuntimeException {
    public AccesoDenegadoException(String mensaje) {
        super(mensaje);
    }
}
