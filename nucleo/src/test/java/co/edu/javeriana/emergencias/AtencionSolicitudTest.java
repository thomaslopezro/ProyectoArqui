package co.edu.javeriana.emergencias;

import java.util.List;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import co.edu.javeriana.emergencias.aplicacion.excepcion.*;
import co.edu.javeriana.emergencias.aplicacion.puerto.SolicitudRepositorio;
import co.edu.javeriana.emergencias.aplicacion.servicio.SolicitudServicio;
import co.edu.javeriana.emergencias.dominio.modelo.*;
import co.edu.javeriana.emergencias.infraestructura.persistencia.RepositorioSolicitudMemoria;

class AtencionSolicitudTest {
    private SolicitudServicio servicio;
    private RepositorioSolicitudMemoria repositorio;

    @BeforeEach
    void configurar() {
        repositorio = new RepositorioSolicitudMemoria();
        servicio = new SolicitudServicio(repositorio);
    }

    private Solicitud registrar() {
        return servicio.registrarSolicitud("Inundacion", "Agua", 50, Prioridad.ALTA);
    }

    @Test
    void atenderPendienteActualizaAlmacenamientoYConservaDatos() {
        Solicitud original = registrar();
        Solicitud resultado = servicio.atenderSolicitud(original.getId());
        assertEquals(EstadoSolicitud.ATENDIDA, resultado.getEstado());
        assertEquals(EstadoSolicitud.ATENDIDA, servicio.buscarPorId(original.getId()).getEstado());
        assertEquals(original.getId(), resultado.getId());
        assertEquals(original.getDescripcionDesastre(), resultado.getDescripcionDesastre());
        assertEquals(original.getTipoRecurso(), resultado.getTipoRecurso());
        assertEquals(original.getCantidad(), resultado.getCantidad());
        assertEquals(original.getPrioridad(), resultado.getPrioridad());
    }

    @Test
    void rechazarSegundaAtencion() {
        Long id = registrar().getId();
        servicio.atenderSolicitud(id);
        assertThrows(SolicitudYaAtendidaException.class, () -> servicio.atenderSolicitud(id));
    }

    @Test
    void rechazarInexistente() {
        assertThrows(SolicitudNoEncontradaException.class, () -> servicio.atenderSolicitud(99L));
    }

    @Test
    void rechazarIdentificadoresInvalidosSinAccederAlRepositorio() {
        SolicitudRepositorio simulado = mock(SolicitudRepositorio.class);
        SolicitudServicio aislado = new SolicitudServicio(simulado);
        for (Long id : new Long[]{null, 0L, -1L}) {
            assertThrows(ReglaNegocioException.class, () -> aislado.atenderSolicitud(id));
        }
        verifyNoInteractions(simulado);
    }

    @Test
    void pendientesExcluyeAtendidas() {
        Solicitud primera = registrar();
        Solicitud segunda = registrar();
        servicio.atenderSolicitud(primera.getId());
        assertEquals(List.of(segunda.getId()), servicio.listarPendientes().stream().map(Solicitud::getId).toList());
    }

    @Test
    void sinSolicitudesDevuelveListaVacia() {
        assertTrue(servicio.listarPendientes().isEmpty());
    }

    @Test
    void todasAtendidasDevuelveListaVacia() {
        servicio.atenderSolicitud(registrar().getId());
        assertTrue(servicio.listarPendientes().isEmpty());
    }

    @Test
    void cambioConcurrenteEntreLecturaYActualizacionSeRechaza() {
        SolicitudRepositorio simulado = mock(SolicitudRepositorio.class);
        Solicitud pendiente = registrar();
        when(simulado.buscarPorId(pendiente.getId())).thenReturn(java.util.Optional.of(pendiente));
        when(simulado.actualizarEstadoSiCoincide(pendiente.getId(), EstadoSolicitud.PENDIENTE, EstadoSolicitud.ATENDIDA)).thenReturn(false);
        assertThrows(SolicitudYaAtendidaException.class,
                () -> new SolicitudServicio(simulado).atenderSolicitud(pendiente.getId()));
    }

    @Test
    void dosAtencionesConcurrentesSoloUnaTieneExito() throws Exception {
        Long id = registrar().getId();
        ExecutorService ejecutor = Executors.newFixedThreadPool(2);
        CountDownLatch inicio = new CountDownLatch(1);
        Callable<Boolean> atender = () -> {
            if (!inicio.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Tiempo agotado");
            try {
                servicio.atenderSolicitud(id);
                return true;
            } catch (SolicitudYaAtendidaException esperada) {
                return false;
            }
        };
        try {
            Future<Boolean> primera = ejecutor.submit(atender);
            Future<Boolean> segunda = ejecutor.submit(atender);
            inicio.countDown();
            assertNotEquals(primera.get(5, TimeUnit.SECONDS), segunda.get(5, TimeUnit.SECONDS));
            assertEquals(EstadoSolicitud.ATENDIDA, servicio.buscarPorId(id).getEstado());
        } finally {
            ejecutor.shutdownNow();
        }
    }
}
