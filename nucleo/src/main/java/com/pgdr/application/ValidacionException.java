package com.pgdr.application;

import jakarta.ejb.ApplicationException;

@ApplicationException(rollback=false)
public class ValidacionException extends RuntimeException {
    public ValidacionException(String mensaje){super(mensaje);}
}
