package com.pgdr.reconstruccion.adaptadores.plataforma;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Valida un token contra el núcleo, que es la autoridad de identidades. De su respuesta salen
 * el usuario, el rol y el organismo: lo que diga el cliente sobre sí mismo no se usa.
 */
@Component
@ConditionalOnProperty(name = "reconstruccion.integracion.habilitada", havingValue = "true")
public class ValidadorSesion {

    private final PlataformaHttp plataforma;

    public ValidadorSesion(PlataformaHttp plataforma) {
        this.plataforma = plataforma;
    }

    /** Devuelve la sesión, o null si el núcleo la declara inválida. Lanza NoDisponibleException si no responde. */
    public SesionActual.Sesion validar(String token) {
        if (token == null || token.isBlank()) return null;
        PlataformaHttp.Respuesta r = plataforma.nucleo("GET", "/auth/me", token, null);
        if (r.estado() == 401 || r.estado() == 403) return null;
        if (r.estado() != 200 || r.cuerpo() == null)
            throw new NoDisponibleException("El núcleo no pudo validar la sesión.");
        String usuario = r.cuerpo().path("usuario").asText("");
        String organizacion = r.cuerpo().path("organizacionId").asText("");
        if (usuario.isBlank() || organizacion.isBlank()) return null;
        return new SesionActual.Sesion(token, usuario, rolDelModulo(r.cuerpo().path("rol").asText("")), organizacion);
    }

    /**
     * Equivalencia entre los roles de la plataforma y los del módulo: el coordinador actúa como
     * autoridad, el evaluador (personal técnico del organismo) como responsable de proyecto, y
     * cualquier otro rol solo consulta.
     */
    static String rolDelModulo(String rolPlataforma) {
        return switch (rolPlataforma) {
            case "COORDINADOR" -> "AUTORIDAD";
            case "EVALUADOR" -> "RESPONSABLE";
            default -> "AUDITOR";
        };
    }
}
