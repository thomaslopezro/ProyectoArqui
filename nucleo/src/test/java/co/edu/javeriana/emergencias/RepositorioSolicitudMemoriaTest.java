package co.edu.javeriana.emergencias;

import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import co.edu.javeriana.emergencias.dominio.modelo.*;
import co.edu.javeriana.emergencias.infraestructura.persistencia.RepositorioSolicitudMemoria;

class RepositorioSolicitudMemoriaTest {
    @Test
    void transicionAtomicaSoloPermiteUnGanador() throws Exception {
        RepositorioSolicitudMemoria repositorio = new RepositorioSolicitudMemoria();
        Long id = repositorio.guardar(new Solicitud(null, "Incendio", "Agua", 5,
                Prioridad.ALTA, EstadoSolicitud.PENDIENTE)).getId();
        ExecutorService ejecutor = Executors.newFixedThreadPool(2);
        CyclicBarrier barrera = new CyclicBarrier(2);
        Callable<Boolean> cambiar = () -> {
            barrera.await(5, TimeUnit.SECONDS);
            return repositorio.actualizarEstadoSiCoincide(id, EstadoSolicitud.PENDIENTE, EstadoSolicitud.ATENDIDA);
        };
        try {
            Future<Boolean> a = ejecutor.submit(cambiar);
            Future<Boolean> b = ejecutor.submit(cambiar);
            assertNotEquals(a.get(5, TimeUnit.SECONDS), b.get(5, TimeUnit.SECONDS));
            assertEquals(EstadoSolicitud.ATENDIDA, repositorio.buscarPorId(id).orElseThrow().getEstado());
        } finally {
            ejecutor.shutdownNow();
        }
    }
}
