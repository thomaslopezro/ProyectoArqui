package com.pgdr.reconstruccion.adaptadores.entrada.rest;

import com.pgdr.reconstruccion.adaptadores.plataforma.NoDisponibleException;
import com.pgdr.reconstruccion.adaptadores.plataforma.SesionActual;
import com.pgdr.reconstruccion.adaptadores.plataforma.ValidadorSesion;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * En la plataforma integrada, valida el token de cada solicitud contra el núcleo y fija la
 * identidad con su respuesta. Las cabeceras X-Usuario, X-Rol y X-Organizacion que lee el
 * controlador se reemplazan por los valores validados: las que envíe el cliente se ignoran.
 */
@Component
@ConditionalOnProperty(name = "reconstruccion.integracion.habilitada", havingValue = "true")
public class FiltroSesion extends OncePerRequestFilter {

    private final ValidadorSesion validador;

    public FiltroSesion(ValidadorSesion validador) {
        this.validador = validador;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest solicitud) {
        return !solicitud.getRequestURI().startsWith("/api/reconstruccion");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest solicitud, HttpServletResponse respuesta, FilterChain cadena)
            throws ServletException, IOException {
        String cabecera = solicitud.getHeader("Authorization");
        if (cabecera == null || !cabecera.startsWith("Bearer ")) {
            responder(respuesta, 401, "SESION_REQUERIDA", "Inicia sesión en la plataforma.");
            return;
        }
        SesionActual.Sesion sesion;
        try {
            sesion = validador.validar(cabecera.substring(7).trim());
        } catch (NoDisponibleException e) {
            responder(respuesta, 503, "IDENTIDAD_NO_DISPONIBLE", "El servicio de identidad no está disponible.");
            return;
        }
        if (sesion == null) {
            responder(respuesta, 401, "SESION_INVALIDA", "La sesión no es válida o venció.");
            return;
        }
        SesionActual.fijar(sesion);
        try {
            cadena.doFilter(new SolicitudConIdentidad(solicitud, sesion), respuesta);
        } finally {
            SesionActual.limpiar();
        }
    }

    private static void responder(HttpServletResponse respuesta, int estado, String codigo, String mensaje)
            throws IOException {
        respuesta.setStatus(estado);
        respuesta.setContentType("application/json;charset=UTF-8");
        respuesta.getWriter().write("{\"codigo\":\"" + codigo + "\",\"mensaje\":\"" + mensaje + "\"}");
    }

    /** Solicitud cuyas cabeceras de identidad son las de la sesión validada. */
    private static final class SolicitudConIdentidad extends HttpServletRequestWrapper {

        private final SesionActual.Sesion sesion;

        SolicitudConIdentidad(HttpServletRequest solicitud, SesionActual.Sesion sesion) {
            super(solicitud);
            this.sesion = sesion;
        }

        private String validado(String nombre) {
            if (nombre == null) return null;
            return switch (nombre.toLowerCase(Locale.ROOT)) {
                case "x-usuario" -> sesion.usuario();
                case "x-rol" -> sesion.rol();
                case "x-organizacion" -> sesion.organizacion();
                default -> null;
            };
        }

        @Override
        public String getHeader(String nombre) {
            String valor = validado(nombre);
            return valor != null ? valor : super.getHeader(nombre);
        }

        @Override
        public Enumeration<String> getHeaders(String nombre) {
            String valor = validado(nombre);
            return valor != null ? Collections.enumeration(List.of(valor)) : super.getHeaders(nombre);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            Set<String> nombres = new LinkedHashSet<>(Collections.list(super.getHeaderNames()));
            nombres.add("X-Usuario");
            nombres.add("X-Rol");
            nombres.add("X-Organizacion");
            return Collections.enumeration(nombres);
        }
    }
}
