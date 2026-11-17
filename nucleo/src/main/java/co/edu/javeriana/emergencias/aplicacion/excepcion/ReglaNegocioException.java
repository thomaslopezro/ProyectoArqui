
package co.edu.javeriana.emergencias.aplicacion.excepcion;

import jakarta.ejb.ApplicationException;

@ApplicationException(rollback = true)
public class ReglaNegocioException extends RuntimeException {

    public ReglaNegocioException(String mensaje) {
        super(mensaje);
    }
}
