package co.edu.javeriana.emergencias.aplicacion.excepcion;

public class SolicitudNoEncontradaException extends ReglaNegocioException {
    public SolicitudNoEncontradaException(String mensaje) {
        super(mensaje);
    }
}
