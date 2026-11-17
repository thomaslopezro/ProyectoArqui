package co.edu.javeriana.emergencias.aplicacion.puerto;

import java.util.List;

import co.edu.javeriana.emergencias.dominio.modelo.Prioridad;
import co.edu.javeriana.emergencias.dominio.modelo.Solicitud;

public interface SolicitudCasosUso {

    Solicitud registrarSolicitud(
            String descripcionDesastre,
            String tipoRecurso,
            int cantidad,
            Prioridad prioridad
    );

    List<Solicitud> listarSolicitudes();

    Solicitud buscarPorId(Long id);

    Solicitud atenderSolicitud(Long id);

    List<Solicitud> listarPendientes();
}
