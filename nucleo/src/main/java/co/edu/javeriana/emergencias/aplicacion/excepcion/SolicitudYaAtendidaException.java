package co.edu.javeriana.emergencias.aplicacion.excepcion;

public class SolicitudYaAtendidaException extends ReglaNegocioException {
    public SolicitudYaAtendidaException(String mensaje) {
        super(mensaje);
    }
}
