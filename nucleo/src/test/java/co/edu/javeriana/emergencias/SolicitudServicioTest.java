
package co.edu.javeriana.emergencias;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import co.edu.javeriana.emergencias.aplicacion.excepcion.ReglaNegocioException;
import co.edu.javeriana.emergencias.aplicacion.puerto.SolicitudRepositorio;
import co.edu.javeriana.emergencias.aplicacion.servicio.SolicitudServicio;
import co.edu.javeriana.emergencias.dominio.modelo.EstadoSolicitud;
import co.edu.javeriana.emergencias.dominio.modelo.Prioridad;
import co.edu.javeriana.emergencias.dominio.modelo.Solicitud;

class SolicitudServicioTest {

    private SolicitudRepositorio repositorio;
    private SolicitudServicio servicio;

    @BeforeEach
    void configurar() {
        repositorio = mock(SolicitudRepositorio.class);
        servicio = new SolicitudServicio(repositorio);
    }

    @Test
    void registrarSolicitudValida() {

        when(repositorio.guardar(any(Solicitud.class)))
                .thenAnswer(invocacion -> {
                    Solicitud solicitud = invocacion.getArgument(0);
                    return solicitud.conId(1L);
                });

        Solicitud resultado = servicio.registrarSolicitud(
                "Inundacion",
                "Agua potable",
                50,
                Prioridad.ALTA
        );

        assertNotNull(resultado);
        assertEquals(1L, resultado.getId());
        assertEquals("Inundacion", resultado.getDescripcionDesastre());
        assertEquals("Agua potable", resultado.getTipoRecurso());
        assertEquals(50, resultado.getCantidad());
        assertEquals(Prioridad.ALTA, resultado.getPrioridad());
        assertEquals(EstadoSolicitud.PENDIENTE, resultado.getEstado());

        verify(repositorio).guardar(any(Solicitud.class));
    }

    @Test
    void rechazarCantidadNegativa() {

        assertThrows(
                ReglaNegocioException.class,
                () -> servicio.registrarSolicitud(
                        "Incendio",
                        "Botiquines",
                        -5,
                        Prioridad.MEDIA
                )
        );

        verifyNoInteractions(repositorio);
    }

    @Test
    void rechazarCantidadCero() {

        assertThrows(
                ReglaNegocioException.class,
                () -> servicio.registrarSolicitud(
                        "Incendio",
                        "Botiquines",
                        0,
                        Prioridad.MEDIA
                )
        );

        verifyNoInteractions(repositorio);
    }

    @Test
    void rechazarDescripcionVacia() {

        assertThrows(
                ReglaNegocioException.class,
                () -> servicio.registrarSolicitud(
                        "   ",
                        "Alimentos",
                        20,
                        Prioridad.ALTA
                )
        );

        verifyNoInteractions(repositorio);
    }

    @Test
    void rechazarTipoRecursoVacio() {

        assertThrows(
                ReglaNegocioException.class,
                () -> servicio.registrarSolicitud(
                        "Terremoto",
                        "",
                        30,
                        Prioridad.ALTA
                )
        );

        verifyNoInteractions(repositorio);
    }

    @Test
    void rechazarPrioridadNula() {

        assertThrows(
                ReglaNegocioException.class,
                () -> servicio.registrarSolicitud(
                        "Deslizamiento",
                        "Cobijas",
                        10,
                        null
                )
        );

        verifyNoInteractions(repositorio);
    }

    @Test
    void buscarSolicitudExistente() {

        Solicitud solicitud = new Solicitud(
                1L,
                "Inundacion",
                "Agua",
                50,
                Prioridad.ALTA,
                EstadoSolicitud.PENDIENTE
        );

        when(repositorio.buscarPorId(1L))
                .thenReturn(Optional.of(solicitud));

        Solicitud resultado = servicio.buscarPorId(1L);

        assertEquals(1L, resultado.getId());
        assertEquals("Inundacion", resultado.getDescripcionDesastre());
        verify(repositorio).buscarPorId(1L);
    }

    @Test
    void rechazarBusquedaInexistente() {

        when(repositorio.buscarPorId(99L))
                .thenReturn(Optional.empty());

        assertThrows(
                ReglaNegocioException.class,
                () -> servicio.buscarPorId(99L)
        );
    }

    @Test
    void listarTodasLasSolicitudes() {

        Solicitud solicitud1 = new Solicitud(
                1L, "Inundacion", "Agua", 50,
                Prioridad.ALTA, EstadoSolicitud.PENDIENTE
        );

        Solicitud solicitud2 = new Solicitud(
                2L, "Incendio", "Botiquines", 20,
                Prioridad.MEDIA, EstadoSolicitud.ATENDIDA
        );

        when(repositorio.buscarTodas())
                .thenReturn(List.of(solicitud1, solicitud2));

        List<Solicitud> resultado = servicio.listarSolicitudes();

        assertEquals(2, resultado.size());
        verify(repositorio).buscarTodas();
    }
}
