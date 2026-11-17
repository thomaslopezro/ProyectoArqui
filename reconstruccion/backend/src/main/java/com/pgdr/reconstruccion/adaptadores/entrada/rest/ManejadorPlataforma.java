package com.pgdr.reconstruccion.adaptadores.entrada.rest;

import com.pgdr.reconstruccion.adaptadores.plataforma.NoDisponibleException;
import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Si otra unidad de la plataforma no responde, el módulo contesta 503 en lugar de un error interno. */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ManejadorPlataforma {

    @ExceptionHandler(NoDisponibleException.class)
    public ResponseEntity<Map<String, String>> noDisponible(NoDisponibleException e) {
        return ResponseEntity.status(503).body(Map.of("codigo", "NO_DISPONIBLE", "mensaje", e.getMessage()));
    }
}
