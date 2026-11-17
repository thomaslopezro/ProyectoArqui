package com.pgdr.reconstruccion.aplicacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.pgdr.reconstruccion.aplicacion.casosdeuso.AccesoDenegadoException;
import com.pgdr.reconstruccion.aplicacion.casosdeuso.AprobarProyecto;
import com.pgdr.reconstruccion.aplicacion.casosdeuso.CambiarEstado;
import com.pgdr.reconstruccion.aplicacion.casosdeuso.CerrarDesastre;
import com.pgdr.reconstruccion.aplicacion.casosdeuso.ConsultarSeguimiento;
import com.pgdr.reconstruccion.aplicacion.casosdeuso.CrearProyectos;
import com.pgdr.reconstruccion.aplicacion.casosdeuso.Dependencias;
import com.pgdr.reconstruccion.aplicacion.casosdeuso.IniciarEjecucion;
import com.pgdr.reconstruccion.aplicacion.casosdeuso.IniciarReconstruccion;
import com.pgdr.reconstruccion.aplicacion.casosdeuso.ModificarPresupuesto;
import com.pgdr.reconstruccion.aplicacion.casosdeuso.RegistrarSeguimiento;
import com.pgdr.reconstruccion.aplicacion.puertos.ConflictoDeVersionException;
import com.pgdr.reconstruccion.dominio.Actor;
import com.pgdr.reconstruccion.dominio.Desastre;
import com.pgdr.reconstruccion.dominio.EstadoDesastre;
import com.pgdr.reconstruccion.dominio.EstadoProyecto;
import com.pgdr.reconstruccion.dominio.EvaluacionDano;
import com.pgdr.reconstruccion.dominio.NivelDano;
import com.pgdr.reconstruccion.dominio.Proyecto;
import com.pgdr.reconstruccion.dominio.RegistroAuditoria;
import com.pgdr.reconstruccion.dominio.ReglaVioladaException;
import com.pgdr.reconstruccion.dominio.Rol;
import com.pgdr.reconstruccion.soporte.EnMemoria;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Casos de uso CU-01 a CU-09 (capa 2) con los puertos sustituidos por dobles en memoria. */
class CasosDeUsoTest {
    private static final Actor AUTORIDAD = new Actor("carlos", Rol.AUTORIDAD, "UNGRD");
    private static final Actor RESPONSABLE = new Actor("ana", Rol.RESPONSABLE, "UNGRD");
    private static final Actor AUDITOR = new Actor("luisa", Rol.AUDITOR, "UNGRD");
    private static final Actor OTRA_ORG = new Actor("pedro", Rol.AUTORIDAD, "Alcaldia");

    private EnMemoria mem;
    private Dependencias d;
    private Desastre desastre;
    private EvaluacionDano puente;
    private EvaluacionDano escuela;

    @BeforeEach
    void preparar() {
        mem = new EnMemoria();
        d = mem.dependencias();
        desastre = mem.desastre("Sismo del Eje Cafetero", EstadoDesastre.CARACTERIZACION);
        puente = mem.evaluacion(desastre.id(), "UNGRD", "Puente", NivelDano.DESTRUCCION_TOTAL, true);
        escuela = mem.evaluacion(desastre.id(), "UNGRD", "Escuela", NivelDano.GRAVE, true);
    }

    private static String regla(Runnable accion) {
        return assertThrows(ReglaVioladaException.class, accion::run).regla();
    }

    private Proyecto crear(EvaluacionDano e) {
        return new CrearProyectos(d).crearUno(AUTORIDAD, desastre.id(),
                new CrearProyectos.Solicitud("Proyecto " + e.tipoActivo(), null, List.of(e.id())));
    }

    private Proyecto enEjecucion(EvaluacionDano e) {
        Proyecto p = crear(e);
        new AprobarProyecto(d).ejecutar(AUTORIDAD, p.id(), new BigDecimal("1000"), "Fondo Nacional", "Ana Ruiz");
        return new IniciarEjecucion(d).ejecutar(RESPONSABLE, p.id(), "Constructora Andes",
                LocalDate.of(2027, 1, 10), LocalDate.of(2027, 6, 30));
    }

    private long rechazados() {
        return mem.auditoriaCompleta().stream().filter(r -> r.resultado() == RegistroAuditoria.Resultado.RECHAZADO).count();
    }

    @Test
    void cu01_soloSeEntraAReconstruccionDesdeCaracterizacionConDanos() {
        Desastre registro = mem.desastre("Inundación", EstadoDesastre.REGISTRO);
        assertEquals("RN-01", regla(() -> new IniciarReconstruccion(d).ejecutar(AUTORIDAD, registro.id())));
        Desastre sinDanos = mem.desastre("Deslizamiento", EstadoDesastre.CARACTERIZACION);
        mem.evaluacion(sinDanos.id(), "UNGRD", "Vía", NivelDano.LEVE, true);
        assertEquals("RN-02", regla(() -> new IniciarReconstruccion(d).ejecutar(AUTORIDAD, sinDanos.id())));
        assertEquals(EstadoDesastre.RECONSTRUCCION, new IniciarReconstruccion(d).ejecutar(AUTORIDAD, desastre.id()).estado());
        assertEquals(EstadoDesastre.RECONSTRUCCION, d.desastres().buscar(desastre.id()).orElseThrow().estado());
    }

    @Test
    void cu02_rn06_noSeCreanProyectosFueraDeReconstruccion() {
        assertEquals("RN-06", regla(() -> crear(puente)));
        assertEquals(1, rechazados());   // el intento rechazado quedó auditado
    }

    @Test
    void cu02_rn09_unaEvaluacionNoOriginaDosProyectosActivos() {
        new IniciarReconstruccion(d).ejecutar(AUTORIDAD, desastre.id());
        Proyecto primero = crear(puente);
        assertEquals("RN-09", regla(() -> crear(puente)));
        new CambiarEstado(d).ejecutar(AUTORIDAD, primero.id(), EstadoProyecto.CANCELADO, "Duplicado");
        assertEquals(EstadoProyecto.PROPUESTO, crear(puente).estado());   // al cancelar, la evaluación queda libre
    }

    @Test
    void cu02_rn18_elLoteCreaLosValidosYReportaLosInvalidos() {
        new IniciarReconstruccion(d).ejecutar(AUTORIDAD, desastre.id());
        EvaluacionDano leve = mem.evaluacion(desastre.id(), "UNGRD", "Parque", NivelDano.LEVE, true);
        List<CrearProyectos.Resultado> r = new CrearProyectos(d).crearLote(AUTORIDAD, desastre.id(), List.of(
                new CrearProyectos.Solicitud("Puente", null, List.of(puente.id())),
                new CrearProyectos.Solicitud("Parque", null, List.of(leve.id())),
                new CrearProyectos.Solicitud("Escuela", null, List.of(escuela.id()))));
        assertTrue(r.get(0).creado());
        assertFalse(r.get(1).creado());
        assertEquals("RN-02", r.get(1).regla());
        assertTrue(r.get(2).creado());
        assertEquals(2, d.proyectos().porDesastre(desastre.id(), "UNGRD").size());
    }

    @Test
    void cu02_rn18_unaEvaluacionDeOtraOrganizacionNoTumbaElLote() {
        new IniciarReconstruccion(d).ejecutar(AUTORIDAD, desastre.id());
        EvaluacionDano ajena = mem.evaluacion(desastre.id(), "Alcaldia", "Carretera", NivelDano.GRAVE, true);
        List<CrearProyectos.Resultado> r = new CrearProyectos(d).crearLote(AUTORIDAD, desastre.id(), List.of(
                new CrearProyectos.Solicitud("Puente", null, List.of(puente.id())),
                new CrearProyectos.Solicitud("Carretera", null, List.of(ajena.id())),
                new CrearProyectos.Solicitud("Escuela", null, List.of(escuela.id()))));
        assertTrue(r.get(0).creado());
        assertFalse(r.get(1).creado());
        assertEquals("RN-10", r.get(1).regla());
        assertTrue(r.get(2).creado());
    }

    @Test
    void rn10_rolesYOrganizacion() {
        new IniciarReconstruccion(d).ejecutar(AUTORIDAD, desastre.id());
        Proyecto p = crear(puente);
        assertThrows(AccesoDenegadoException.class, () -> new CrearProyectos(d).crearUno(RESPONSABLE, desastre.id(),
                new CrearProyectos.Solicitud("X", null, List.of(escuela.id()))));
        assertThrows(AccesoDenegadoException.class, () -> new AprobarProyecto(d).ejecutar(RESPONSABLE, p.id(),
                BigDecimal.TEN, "Fondo", "Ana"));
        assertThrows(AccesoDenegadoException.class, () -> new AprobarProyecto(d).ejecutar(OTRA_ORG, p.id(),
                BigDecimal.TEN, "Fondo", "Ana"));
        assertThrows(AccesoDenegadoException.class, () -> new ConsultarSeguimiento(d).auditoriaDeProyecto(RESPONSABLE, p.id()));
        assertEquals(0, new ConsultarSeguimiento(d).consolidado(OTRA_ORG, desastre.id()).proyectos().size());
    }

    @Test
    void cu06_rn17_cambioDePresupuestoYAuditoriaVanJuntos() {
        new IniciarReconstruccion(d).ejecutar(AUTORIDAD, desastre.id());
        Proyecto p = enEjecucion(puente);
        new ModificarPresupuesto(d).ejecutar(AUTORIDAD, p.id(), new BigDecimal("1500"), "Mayor cantidad de obra");
        RegistroAuditoria r = new ConsultarSeguimiento(d).auditoriaDeProyecto(AUDITOR, p.id()).stream()
                .filter(x -> x.operacion().equals("MODIFICAR_PRESUPUESTO")).findFirst().orElseThrow();
        assertEquals("carlos", r.usuario());
        assertEquals("1000", r.valorAnterior());
        assertEquals("1500", r.valorNuevo());
        assertEquals("Mayor cantidad de obra", r.motivo());
        assertEquals(RegistroAuditoria.Resultado.EXITOSO, r.resultado());
        assertTrue(mem.eventos().stream().anyMatch(e -> e.tipo().equals("PRESUPUESTO_MODIFICADO")));
    }

    @Test
    void cu06_rechazoNoCambiaNadaPeroQuedaAuditado() {
        new IniciarReconstruccion(d).ejecutar(AUTORIDAD, desastre.id());
        Proyecto p = enEjecucion(puente);
        int eventosAntes = mem.eventos().size();
        assertEquals("RN-12", regla(() -> new ModificarPresupuesto(d).ejecutar(AUTORIDAD, p.id(), new BigDecimal("1500"), "")));
        assertEquals(new BigDecimal("1000"), d.proyectos().buscar(p.id()).orElseThrow().presupuesto());
        assertEquals(eventosAntes, mem.eventos().size());
        RegistroAuditoria rechazo = mem.auditoriaCompleta().get(mem.auditoriaCompleta().size() - 1);
        assertEquals(RegistroAuditoria.Resultado.RECHAZADO, rechazo.resultado());
        assertEquals(desastre.id(), rechazo.desastreId());
        assertTrue(rechazo.detalle().startsWith("RN-12"));
    }

    @Test
    void cambiosSimultaneosElSegundoSeRechaza() {
        new IniciarReconstruccion(d).ejecutar(AUTORIDAD, desastre.id());
        Proyecto p = enEjecucion(puente);
        Proyecto copiaA = d.proyectos().buscar(p.id()).orElseThrow();
        Proyecto copiaB = d.proyectos().buscar(p.id()).orElseThrow();
        copiaA.modificarPresupuesto(new BigDecimal("1200"), "A");
        d.proyectos().actualizar(copiaA);
        copiaB.modificarPresupuesto(new BigDecimal("1300"), "B");
        assertThrows(ConflictoDeVersionException.class, () -> d.proyectos().actualizar(copiaB));
        assertEquals(new BigDecimal("1200"), d.proyectos().buscar(p.id()).orElseThrow().presupuesto());
    }

    @Test
    void cu05_rn14_elSobrecostoNotificaUnaSolaVez() {
        new IniciarReconstruccion(d).ejecutar(AUTORIDAD, desastre.id());
        Proyecto p = enEjecucion(puente);
        RegistrarSeguimiento seg = new RegistrarSeguimiento(d);
        seg.costo(RESPONSABLE, p.id(), new BigDecimal("800"), "Acta 1");
        seg.costo(RESPONSABLE, p.id(), new BigDecimal("300"), "Acta 2");
        seg.costo(RESPONSABLE, p.id(), new BigDecimal("100"), "Acta 3");
        assertEquals(1, mem.eventos().stream().filter(e -> e.tipo().equals("PROYECTO_CON_SOBRECOSTO")).count());
        assertTrue(d.proyectos().buscar(p.id()).orElseThrow().tieneSobrecosto());
    }

    @Test
    void cu08_consolidadoPorDesastre() {
        new IniciarReconstruccion(d).ejecutar(AUTORIDAD, desastre.id());
        Proyecto a = enEjecucion(puente);
        Proyecto b = crear(escuela);
        new RegistrarSeguimiento(d).avance(RESPONSABLE, a.id(), 60, "Estructura", null);
        new RegistrarSeguimiento(d).costo(RESPONSABLE, a.id(), new BigDecimal("400"), "Acta 1");
        ConsultarSeguimiento.Consolidado c = new ConsultarSeguimiento(d).consolidado(AUDITOR, desastre.id());
        assertEquals(2, c.proyectos().size());
        assertEquals(1, c.porEstado().get(EstadoProyecto.EN_EJECUCION));
        assertEquals(1, c.porEstado().get(EstadoProyecto.PROPUESTO));
        assertEquals(new BigDecimal("1000"), c.presupuestoTotal());
        assertEquals(new BigDecimal("400"), c.costoTotal());
        assertEquals(30, c.avancePromedio());
        assertEquals(b.id(), c.proyectos().get(1).id());
    }

    @Test
    void cu09_rn04_rn05_cierreSoloConTodosLosProyectosTerminados() {
        new IniciarReconstruccion(d).ejecutar(AUTORIDAD, desastre.id());
        Proyecto a = enEjecucion(puente);
        Proyecto b = crear(escuela);
        assertEquals("RN-04", regla(() -> new CerrarDesastre(d).ejecutar(AUTORIDAD, desastre.id())));
        new CambiarEstado(d).ejecutar(AUTORIDAD, b.id(), EstadoProyecto.CANCELADO, "Se atiende con otro programa");
        RegistrarSeguimiento seg = new RegistrarSeguimiento(d);
        seg.avance(RESPONSABLE, a.id(), 100, "Obra terminada", null);
        seg.evidencia(RESPONSABLE, a.id(), "Acta de entrega n.º 12");
        new CambiarEstado(d).ejecutar(RESPONSABLE, a.id(), EstadoProyecto.FINALIZADO, null);
        assertEquals(EstadoDesastre.CIERRE, new CerrarDesastre(d).ejecutar(AUTORIDAD, desastre.id()).estado());
        assertEquals("RN-05", regla(() -> new CerrarDesastre(d).ejecutar(AUTORIDAD, desastre.id())));
        assertEquals("RN-06", regla(() -> crear(escuela)));
    }

    @Test
    void escenarioE_30EscuelasY12Puentes() {
        new IniciarReconstruccion(d).ejecutar(AUTORIDAD, desastre.id());
        List<CrearProyectos.Solicitud> lote = new ArrayList<>();
        for (int i = 1; i <= 30; i++)
            lote.add(new CrearProyectos.Solicitud("Escuela " + i, null,
                    List.of(mem.evaluacion(desastre.id(), "UNGRD", "Escuela", NivelDano.GRAVE, true).id())));
        for (int i = 1; i <= 12; i++)
            lote.add(new CrearProyectos.Solicitud("Puente " + i, null,
                    List.of(mem.evaluacion(desastre.id(), "UNGRD", "Puente", NivelDano.DESTRUCCION_TOTAL, true).id())));
        List<CrearProyectos.Resultado> r = new CrearProyectos(d).crearLote(AUTORIDAD, desastre.id(), lote);
        assertEquals(42, r.stream().filter(CrearProyectos.Resultado::creado).count());
        assertEquals(42, new ConsultarSeguimiento(d).consolidado(AUTORIDAD, desastre.id()).proyectos().size());
    }
}
