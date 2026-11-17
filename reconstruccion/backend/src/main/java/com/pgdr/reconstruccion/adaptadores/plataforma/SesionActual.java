package com.pgdr.reconstruccion.adaptadores.plataforma;

/**
 * Sesión validada de la solicitud en curso. La fija el adaptador de entrada (filtro REST o
 * interceptor gRPC) después de validar el token contra el núcleo, y la leen los adaptadores
 * de salida que llaman a otras unidades en nombre del usuario.
 */
public final class SesionActual {

    public record Sesion(String token, String usuario, String rol, String organizacion) {}

    private static final ThreadLocal<Sesion> ACTUAL = new ThreadLocal<>();

    private SesionActual() {}

    public static void fijar(Sesion sesion) {
        ACTUAL.set(sesion);
    }

    public static void limpiar() {
        ACTUAL.remove();
    }

    public static Sesion actual() {
        return ACTUAL.get();
    }

    public static Sesion requerida() {
        Sesion sesion = ACTUAL.get();
        if (sesion == null) throw new IllegalStateException("No hay una sesión validada en esta solicitud.");
        return sesion;
    }
}
