package com.pgdr.reconstruccion.dominio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Reglas que dependen solo del proyecto (capa 1). Sin base de datos ni framework. */
class ProyectoTest {
    private static final UUID DESASTRE = UUID.randomUUID();
    private static final Instant AHORA = Instant.parse("2026-11-12T10:00:00Z");

    private static EvaluacionDano evaluacion(NivelDano nivel, boolean validada) {
        return new EvaluacionDano(UUID.randomUUID(), DESASTRE, "UNGRD", "Puente", "Puente La Vega", nivel, validada);
    }

    private static Proyecto propuesto() {
        return Proyecto.proponer(UUID.randomUUID(), DESASTRE, "UNGRD", "Puente La Vega", null,
                List.of(evaluacion(NivelDano.GRAVE, true)));
    }

    private static Proyecto enEjecucion() {
        Proyecto p = propuesto();
        p.aprobar(new BigDecimal("1000"), "Fondo Nacional", "Ana Ruiz");
        p.iniciarEjecucion("Constructora Andes", LocalDate.of(2027, 1, 10), LocalDate.of(2027, 6, 30));
        return p;
    }

    private static String regla(Runnable accion) {
        return assertThrows(ReglaVioladaException.class, accion::run).regla();
    }

    @Test
    void naceEnPropuestoConSusEvaluaciones() {
        Proyecto p = propuesto();
        assertEquals(EstadoProyecto.PROPUESTO, p.estado());
        assertEquals(1, p.evaluaciones().size());
        assertEquals(TipoProyecto.REPARACION, p.tipo());
    }

    @Test
    void rn07_exigeAlMenosUnaEvaluacionDelMismoDesastre() {
        assertEquals("RN-07", regla(() -> Proyecto.proponer(UUID.randomUUID(), DESASTRE, "UNGRD", "X", null, List.of())));
        EvaluacionDano deOtro = new EvaluacionDano(UUID.randomUUID(), UUID.randomUUID(), "UNGRD", "Escuela", "E",
                NivelDano.GRAVE, true);
        assertEquals("RN-07", regla(() -> Proyecto.proponer(UUID.randomUUID(), DESASTRE, "UNGRD", "X", null, List.of(deOtro))));
    }

    @Test
    void rn02_rechazaEvaluacionLeveOSinValidar() {
        assertEquals("RN-02", regla(() -> Proyecto.proponer(UUID.randomUUID(), DESASTRE, "UNGRD", "X", null,
                List.of(evaluacion(NivelDano.LEVE, true)))));
        assertEquals("RN-02", regla(() -> Proyecto.proponer(UUID.randomUUID(), DESASTRE, "UNGRD", "X", null,
                List.of(evaluacion(NivelDano.GRAVE, false)))));
    }

    @Test
    void rn08_destruccionTotalObligaReconstruccion() {
        List<EvaluacionDano> total = List.of(evaluacion(NivelDano.DESTRUCCION_TOTAL, true));
        assertEquals(TipoProyecto.RECONSTRUCCION,
                Proyecto.proponer(UUID.randomUUID(), DESASTRE, "UNGRD", "X", null, total).tipo());
        assertEquals("RN-08", regla(() -> Proyecto.proponer(UUID.randomUUID(), DESASTRE, "UNGRD", "X",
                TipoProyecto.REPARACION, total)));
    }

    @Test
    void rn11_aprobarExigePresupuestoFuenteYResponsable() {
        assertEquals("RN-12", regla(() -> propuesto().aprobar(BigDecimal.ZERO, "Fondo", "Ana")));
        assertEquals("RN-11", regla(() -> propuesto().aprobar(BigDecimal.TEN, " ", "Ana")));
        assertEquals("RN-11", regla(() -> propuesto().aprobar(BigDecimal.TEN, "Fondo", null)));
        Proyecto p = propuesto();
        p.aprobar(BigDecimal.TEN, "Fondo", "Ana");
        assertEquals(EstadoProyecto.APROBADO, p.estado());
    }

    @Test
    void rn11_iniciarExigeContratistaYCronogramaCoherente() {
        Proyecto p = propuesto();
        p.aprobar(BigDecimal.TEN, "Fondo", "Ana");
        assertEquals("RN-11", regla(() -> p.iniciarEjecucion("", LocalDate.of(2027, 1, 1), LocalDate.of(2027, 2, 1))));
        assertEquals("RN-11", regla(() -> p.iniciarEjecucion("Andes", LocalDate.of(2027, 2, 1), LocalDate.of(2027, 1, 1))));
        assertEquals("RN-16", regla(() -> propuesto().iniciarEjecucion("Andes", LocalDate.of(2027, 1, 1), LocalDate.of(2027, 2, 1))));
    }

    @Test
    void rn12_presupuestoMayorQueCeroConMotivoYNoEnTerminales() {
        Proyecto p = enEjecucion();
        assertEquals("RN-12", regla(() -> p.modificarPresupuesto(new BigDecimal("-1"), "ajuste")));
        assertEquals("RN-12", regla(() -> p.modificarPresupuesto(new BigDecimal("1500"), " ")));
        assertEquals(new BigDecimal("1000"), p.modificarPresupuesto(new BigDecimal("1500"), "Mayor cantidad de obra"));
        assertEquals(new BigDecimal("1500"), p.presupuesto());
        p.cambiarEstado(EstadoProyecto.CANCELADO, "Sin recursos");
        assertEquals("RN-12", regla(() -> p.modificarPresupuesto(new BigDecimal("2000"), "ajuste")));
        assertEquals("RN-12", regla(() -> propuesto().modificarPresupuesto(BigDecimal.TEN, "ajuste")));
    }

    @Test
    void rn13_avanceEntreCeroYCienSoloEnEjecucionYSinRetroceder() {
        Proyecto p = enEjecucion();
        assertEquals("RN-13", regla(() -> p.registrarAvance(101, null, null, AHORA, "ana")));
        p.registrarAvance(40, "Cimentación", null, AHORA, "ana");
        assertEquals("RN-13", regla(() -> p.registrarAvance(30, null, null, AHORA, "ana")));
        p.registrarAvance(30, null, "Error de digitación", AHORA, "ana");
        assertEquals(30, p.avance());
        assertEquals("RN-13", regla(() -> propuesto().registrarAvance(10, null, null, AHORA, "ana")));
    }

    @Test
    void rn14_sobrecostoSeMarcaPeroNoBloquea() {
        Proyecto p = enEjecucion();
        assertFalse(p.registrarCosto(new BigDecimal("900"), "Acta 1", AHORA, "ana"));
        assertFalse(p.tieneSobrecosto());
        assertTrue(p.registrarCosto(new BigDecimal("200"), "Acta 2", AHORA, "ana"));
        assertTrue(p.tieneSobrecosto());
        assertFalse(p.registrarCosto(new BigDecimal("50"), "Acta 3", AHORA, "ana"));   // ya estaba marcado
        assertEquals(new BigDecimal("1150"), p.costoAcumulado());
        assertEquals("RN-14", regla(() -> propuesto().registrarCosto(BigDecimal.TEN, "x", AHORA, "ana")));
    }

    @Test
    void rn15_finalizarExigeCienPorCientoYEvidencia() {
        Proyecto p = enEjecucion();
        assertEquals("RN-15", regla(() -> p.cambiarEstado(EstadoProyecto.FINALIZADO, null)));
        p.registrarAvance(100, "Obra terminada", null, AHORA, "ana");
        assertEquals("RN-15", regla(() -> p.cambiarEstado(EstadoProyecto.FINALIZADO, null)));
        p.adjuntarEvidencia("Acta de entrega n.º 12", AHORA, "ana");
        p.cambiarEstado(EstadoProyecto.FINALIZADO, null);
        assertEquals(EstadoProyecto.FINALIZADO, p.estado());
    }

    @Test
    void rn16_suspenderYCancelarExigenMotivoYLosTerminalesNoCambian() {
        Proyecto p = enEjecucion();
        assertEquals("RN-16", regla(() -> p.cambiarEstado(EstadoProyecto.SUSPENDIDO, "")));
        p.cambiarEstado(EstadoProyecto.SUSPENDIDO, "Invierno");
        assertEquals("RN-13", regla(() -> p.registrarAvance(50, null, null, AHORA, "ana")));
        p.cambiarEstado(EstadoProyecto.EN_EJECUCION, null);   // reanudar no exige motivo
        assertEquals("RN-16", regla(() -> p.cambiarEstado(EstadoProyecto.CANCELADO, null)));
        p.cambiarEstado(EstadoProyecto.CANCELADO, "Sin recursos");
        assertEquals("RN-16", regla(() -> p.cambiarEstado(EstadoProyecto.EN_EJECUCION, null)));
        assertEquals("RN-16", regla(() -> p.adjuntarEvidencia("foto", AHORA, "ana")));
    }

    @Test
    void lasTransicionesSonLasDelDiagrama() {
        assertTrue(EstadoProyecto.PROPUESTO.puedePasarA(EstadoProyecto.CANCELADO));
        assertFalse(EstadoProyecto.PROPUESTO.puedePasarA(EstadoProyecto.EN_EJECUCION));
        assertFalse(EstadoProyecto.APROBADO.puedePasarA(EstadoProyecto.FINALIZADO));
        assertTrue(EstadoProyecto.FINALIZADO.esTerminal());
        assertTrue(EstadoDesastre.CARACTERIZACION.puedePasarA(EstadoDesastre.RECONSTRUCCION));
        assertFalse(EstadoDesastre.REGISTRO.puedePasarA(EstadoDesastre.RECONSTRUCCION));
        assertFalse(EstadoDesastre.CIERRE.puedePasarA(EstadoDesastre.RECONSTRUCCION));
    }
}
