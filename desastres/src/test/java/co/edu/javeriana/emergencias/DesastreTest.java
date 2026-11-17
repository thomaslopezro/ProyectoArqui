package co.edu.javeriana.emergencias;

import co.edu.javeriana.emergencias.aplicacion.servicio.DesastreServicio;
import co.edu.javeriana.emergencias.aplicacion.puerto.*;
import co.edu.javeriana.emergencias.dominio.desastres.*;
import co.edu.javeriana.emergencias.infraestructura.persistencia.RepositorioArchivo;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DesastreTest {
    @TempDir Path carpeta;
    RepositorioArchivo repo;
    NotificadorMensajes mensajes;
    NotificadorCorreo correo;
    DesastreServicio servicio;
    @BeforeEach void preparar() {
        repo = new RepositorioArchivo(carpeta.resolve("estado.json"));
        mensajes = mock(NotificadorMensajes.class);
        correo = mock(NotificadorCorreo.class);
        servicio = new DesastreServicio(repo, mensajes, correo);
    }
    Desastre desastre() {
        Desastre d = new Desastre();
        d.id = "evento-1"; d.tipo = "INUNDACION"; d.ubicacion = "Bogota";
        d.descripcion = "Desbordamiento del rio"; d.severidad = "ALTA"; d.fecha = "2026-10-21T12:00:00Z";
        return d;
    }
    Sistema sistema() {
        Sistema s = new Sistema(); s.id = "bomberos"; s.nombre = "Bomberos"; s.correo = "bomberos@example.test";
        return s;
    }
    @Test void registraInformacionYSalidaPendienteJuntas() {
        servicio.registrarSistema(sistema());
        assertTrue(servicio.registrar(desastre()));
        Registro r = repo.buscar("evento-1").orElseThrow();
        assertEquals("Bogota", r.desastre.ubicacion);
        assertEquals(1, r.destinatarios.size());
        assertFalse(r.notificacionPublicada);
        verify(mensajes).suscribir(any());
        verify(mensajes, never()).publicar(any());
    }
    @Test void duplicadoNoCreaOtroRegistroNiOtraSalida() {
        assertTrue(servicio.registrar(desastre()));
        assertFalse(servicio.registrar(desastre()));
        assertEquals(1, repo.listar().size());
        assertEquals(1, repo.pendientes().size());
    }
    @Test void rechazaDesastreInvalidoAntesDeGuardar() {
        Desastre d = desastre(); d.severidad = "URGENTE";
        assertThrows(IllegalArgumentException.class, () -> servicio.registrar(d));
        assertTrue(repo.listar().isEmpty());
    }
    @Test void rechazaCamposObligatorios() {
        Desastre d = desastre(); d.descripcion = " ";
        assertThrows(IllegalArgumentException.class, d::validar);
        d = desastre(); d.fecha = "ayer";
        assertThrows(IllegalArgumentException.class, d::validar);
        d = desastre(); d.id = "../mal";
        assertThrows(IllegalArgumentException.class, d::validar);
    }
    @Test void rechazaCorreoInvalido() {
        Sistema s = sistema(); s.correo = "invalido";
        assertThrows(IllegalArgumentException.class, () -> servicio.registrarSistema(s));
        verifyNoInteractions(mensajes);
    }
    @Test void reinicioRecuperaRegistroSuscriptoresYPendientes() {
        servicio.registrarSistema(sistema()); servicio.registrar(desastre());
        RepositorioArchivo reiniciado = new RepositorioArchivo(carpeta.resolve("estado.json"));
        assertEquals(1, reiniciado.listar().size());
        assertEquals(1, reiniciado.sistemas().size());
        assertEquals(1, reiniciado.pendientes().size());
    }
    @Test void soloConfirmaSalidaDespuesDePublicar() {
        servicio.registrar(desastre());
        doThrow(new IllegalStateException("broker caido")).when(mensajes).publicar(any());
        assertThrows(IllegalStateException.class, servicio::despacharPendientes);
        assertEquals(1, repo.pendientes().size());
        reset(mensajes);
        servicio.despacharPendientes(); servicio.despacharPendientes();
        assertTrue(repo.pendientes().isEmpty());
        verify(mensajes, times(1)).publicar(any());
    }
    @Test void correoConfirmadoNoSeReenviaAlReentregarMensaje() {
        servicio.registrarSistema(sistema()); servicio.registrar(desastre());
        servicio.enviarCorreos("evento-1"); servicio.enviarCorreos("evento-1");
        verify(correo, times(1)).enviar(any(), any());
        assertEquals(1, new RepositorioArchivo(carpeta.resolve("estado.json")).buscar("evento-1").orElseThrow().correosEnviados.size());
    }
    @Test void correoFallidoQuedaPendienteParaReintento() {
        servicio.registrarSistema(sistema()); servicio.registrar(desastre());
        doThrow(new IllegalStateException("smtp caido")).when(correo).enviar(any(),any());
        assertThrows(IllegalStateException.class, () -> servicio.enviarCorreos("evento-1"));
        assertTrue(repo.buscar("evento-1").orElseThrow().correosEnviados.isEmpty());
    }
    @Test void nuevoSuscriptorNoRecibeEventosHistoricos() {
        servicio.registrar(desastre()); servicio.registrarSistema(sistema());
        assertTrue(repo.buscar("evento-1").orElseThrow().destinatarios.isEmpty());
    }
    @Test void sistemaDuplicadoEsIdempotenteYNoPermiteCambiarDatos() {
        servicio.registrarSistema(sistema()); servicio.registrarSistema(sistema());
        assertEquals(1, repo.sistemas().size());
        Sistema modificado = sistema(); modificado.correo = "otro@example.test";
        assertThrows(IllegalArgumentException.class, () -> servicio.registrarSistema(modificado));
        assertEquals("bomberos@example.test", new RepositorioArchivo(carpeta.resolve("estado.json")).sistemas().get(0).correo);
    }
    @Test void lecturasNoPermitenMutarEstadoPersistido() {
        servicio.registrar(desastre());
        repo.listar().get(0).desastre.descripcion = "modificada";
        assertEquals("Desbordamiento del rio", repo.listar().get(0).desastre.descripcion);
    }
}
