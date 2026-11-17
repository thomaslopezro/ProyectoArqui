
package co.edu.javeriana.emergencias.infraestructura.persistencia;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import jakarta.enterprise.context.ApplicationScoped;

import co.edu.javeriana.emergencias.aplicacion.puerto.SolicitudRepositorio;
import co.edu.javeriana.emergencias.dominio.modelo.EstadoSolicitud;
import co.edu.javeriana.emergencias.dominio.modelo.Solicitud;

@ApplicationScoped
public class RepositorioSolicitudMemoria implements SolicitudRepositorio {

    private final ConcurrentHashMap<Long, Solicitud> solicitudes =
            new ConcurrentHashMap<>();

    private final AtomicLong secuencia = new AtomicLong(0);

    
@Override
public Solicitud guardar(Solicitud solicitud) {

    if (solicitud == null) {
        throw new IllegalArgumentException(
                "La solicitud no puede ser nula"
        );
    }

    if (solicitud.getId() != null) {
        throw new IllegalArgumentException(
                "Solo se permite guardar solicitudes nuevas"
        );
    }

    Long nuevoId = secuencia.incrementAndGet();

    Solicitud nuevaSolicitud = solicitud.conId(nuevoId);

    solicitudes.putIfAbsent(nuevoId, nuevaSolicitud);

    return nuevaSolicitud;
}


    @Override
    public List<Solicitud> buscarTodas() {

        return new ArrayList<>(solicitudes.values());
    }

    @Override
    public Optional<Solicitud> buscarPorId(Long id) {

        if (id == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(solicitudes.get(id));
    }

    @Override
    public boolean actualizarEstadoSiCoincide(
            Long id,
            EstadoSolicitud estadoEsperado,
            EstadoSolicitud nuevoEstado) {

        if (id == null || estadoEsperado == null || nuevoEstado == null) {
            return false;
        }

        final boolean[] actualizado = {false};

        solicitudes.computeIfPresent(id, (clave, solicitudActual) -> {

            if (solicitudActual.getEstado() == estadoEsperado) {

                actualizado[0] = true;

                return solicitudActual.conEstado(nuevoEstado);
            }

            return solicitudActual;
        });

        return actualizado[0];
    }
}
