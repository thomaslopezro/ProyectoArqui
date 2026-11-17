package co.edu.javeriana.emergencias.aplicacion.puerto;

import java.util.List;
import java.util.Optional;

import co.edu.javeriana.emergencias.dominio.modelo.EstadoSolicitud;
import co.edu.javeriana.emergencias.dominio.modelo.Solicitud;

public interface SolicitudRepositorio {

    Solicitud guardar(Solicitud solicitud);

    List<Solicitud> buscarTodas();

    Optional<Solicitud> buscarPorId(Long id);

    boolean actualizarEstadoSiCoincide(
            Long id,
            EstadoSolicitud estadoEsperado,
            EstadoSolicitud nuevoEstado
    );
}
