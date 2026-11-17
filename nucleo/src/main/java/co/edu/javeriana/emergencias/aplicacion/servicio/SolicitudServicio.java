
package co.edu.javeriana.emergencias.aplicacion.servicio;

import java.util.List;
import co.edu.javeriana.emergencias.aplicacion.excepcion.SolicitudNoEncontradaException;
import co.edu.javeriana.emergencias.aplicacion.excepcion.SolicitudYaAtendidaException;

import jakarta.ejb.Stateless;
import jakarta.inject.Inject;

import co.edu.javeriana.emergencias.aplicacion.excepcion.ReglaNegocioException;
import co.edu.javeriana.emergencias.aplicacion.puerto.SolicitudCasosUso;
import co.edu.javeriana.emergencias.aplicacion.puerto.SolicitudRepositorio;
import co.edu.javeriana.emergencias.dominio.modelo.EstadoSolicitud;
import co.edu.javeriana.emergencias.dominio.modelo.Prioridad;
import co.edu.javeriana.emergencias.dominio.modelo.Solicitud;

@Stateless
public class SolicitudServicio implements SolicitudCasosUso {

   
@Inject
private SolicitudRepositorio repositorio;

public SolicitudServicio() {
    // Constructor utilizado por el contenedor EJB.
}

public SolicitudServicio(SolicitudRepositorio repositorio) {
    this.repositorio = java.util.Objects.requireNonNull(
            repositorio,
            "El repositorio es obligatorio"
    );
}


    @Override
    public Solicitud registrarSolicitud(
            String descripcionDesastre,
            String tipoRecurso,
            int cantidad,
            Prioridad prioridad) {

        if (descripcionDesastre == null ||
                descripcionDesastre.isBlank()) {
            throw new ReglaNegocioException(
                    "La descripcion del desastre es obligatoria"
            );
        }

        if (tipoRecurso == null ||
                tipoRecurso.isBlank()) {
            throw new ReglaNegocioException(
                    "El tipo de recurso es obligatorio"
            );
        }

        if (cantidad <= 0) {
            throw new ReglaNegocioException(
                    "La cantidad debe ser mayor que cero"
            );
        }

        if (prioridad == null) {
            throw new ReglaNegocioException(
                    "La prioridad es obligatoria"
            );
        }

        Solicitud solicitud = new Solicitud(
                null,
                descripcionDesastre.trim(),
                tipoRecurso.trim(),
                cantidad,
                prioridad,
                EstadoSolicitud.PENDIENTE
        );

        return repositorio.guardar(solicitud);
    }

    @Override
    public List<Solicitud> listarSolicitudes() {
        return repositorio.buscarTodas();
    }

    @Override
    public Solicitud buscarPorId(Long id) {

        if (id == null || id <= 0) {
            throw new ReglaNegocioException(
                    "El identificador debe ser valido"
            );
        }

        return repositorio.buscarPorId(id)
                .orElseThrow(() ->
                        new SolicitudNoEncontradaException(
                                "No existe una solicitud con ID: " + id
                        )
                );
    }

    @Override
    public Solicitud atenderSolicitud(Long id) {
        Solicitud solicitud = buscarPorId(id);
        if (solicitud.getEstado() != EstadoSolicitud.PENDIENTE ||
                !repositorio.actualizarEstadoSiCoincide(
                        id, EstadoSolicitud.PENDIENTE, EstadoSolicitud.ATENDIDA)) {
            throw new SolicitudYaAtendidaException(
                    "La solicitud ya fue atendida: " + id);
        }
        return solicitud.conEstado(EstadoSolicitud.ATENDIDA);
    }

    @Override
    public List<Solicitud> listarPendientes() {
        return repositorio.buscarTodas().stream()
                .filter(solicitud -> solicitud.getEstado() == EstadoSolicitud.PENDIENTE)
                .toList();
    }
}
