package com.pgdr.reconstruccion.adaptadores.entrada.rest;

import com.pgdr.reconstruccion.adaptadores.entrada.rest.Vistas.ErrorVista;
import com.pgdr.reconstruccion.aplicacion.casosdeuso.AccesoDenegadoException;
import com.pgdr.reconstruccion.aplicacion.casosdeuso.NoEncontradoException;
import com.pgdr.reconstruccion.aplicacion.puertos.ConflictoDeVersionException;
import com.pgdr.reconstruccion.dominio.ReglaVioladaException;
import java.time.format.DateTimeParseException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Traduce las excepciones del dominio y de la aplicación a respuestas HTTP. */
@RestControllerAdvice
public class ManejadorErrores {
    @ExceptionHandler(ReglaVioladaException.class)
    public ResponseEntity<ErrorVista> regla(ReglaVioladaException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ErrorVista("REGLA_DE_NEGOCIO", e.regla(), e.getMessage()));
    }

    @ExceptionHandler(AccesoDenegadoException.class)
    public ResponseEntity<ErrorVista> acceso(AccesoDenegadoException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ErrorVista("ACCESO_DENEGADO", "RN-10", e.getMessage()));
    }

    @ExceptionHandler(NoEncontradoException.class)
    public ResponseEntity<ErrorVista> noEncontrado(NoEncontradoException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorVista("NO_ENCONTRADO", null, e.getMessage()));
    }

    @ExceptionHandler(ConflictoDeVersionException.class)
    public ResponseEntity<ErrorVista> conflicto(ConflictoDeVersionException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorVista("CONFLICTO_DE_VERSION", null, e.getMessage()));
    }

    @ExceptionHandler({IllegalArgumentException.class, DateTimeParseException.class, MissingRequestHeaderException.class})
    public ResponseEntity<ErrorVista> solicitudInvalida(Exception e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorVista("SOLICITUD_INVALIDA", null, e.getMessage()));
    }
}
