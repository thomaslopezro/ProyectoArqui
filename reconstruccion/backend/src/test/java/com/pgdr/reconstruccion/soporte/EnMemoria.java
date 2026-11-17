package com.pgdr.reconstruccion.soporte;

import com.pgdr.reconstruccion.aplicacion.casosdeuso.Dependencias;
import com.pgdr.reconstruccion.aplicacion.puertos.ConflictoDeVersionException;
import com.pgdr.reconstruccion.aplicacion.puertos.ConsultaEvaluaciones;
import com.pgdr.reconstruccion.aplicacion.puertos.GestionDesastres;
import com.pgdr.reconstruccion.aplicacion.puertos.PublicadorEventos;
import com.pgdr.reconstruccion.aplicacion.puertos.RegistroDeAuditoria;
import com.pgdr.reconstruccion.aplicacion.puertos.Reloj;
import com.pgdr.reconstruccion.aplicacion.puertos.RepositorioProyectos;
import com.pgdr.reconstruccion.aplicacion.puertos.UnidadDeTrabajo;
import com.pgdr.reconstruccion.dominio.Desastre;
import com.pgdr.reconstruccion.dominio.EstadoDesastre;
import com.pgdr.reconstruccion.dominio.EvaluacionDano;
import com.pgdr.reconstruccion.dominio.NivelDano;
import com.pgdr.reconstruccion.dominio.Proyecto;
import com.pgdr.reconstruccion.dominio.RegistroAuditoria;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Dobles en memoria de todos los puertos. Permiten probar las reglas de las capas 1 y 2
 * sin servidor ni base de datos. La unidad de trabajo deshace los cambios si el trabajo falla.
 */
public class EnMemoria implements RepositorioProyectos, ConsultaEvaluaciones,
        PublicadorEventos, Reloj, UnidadDeTrabajo {

    public record Evento(String tipo, UUID agregadoId, Map<String, String> datos) {}

    private Map<UUID, Proyecto> proyectos = new LinkedHashMap<>();
    private Map<UUID, Desastre> desastres = new LinkedHashMap<>();
    private final Map<UUID, EvaluacionDano> evaluaciones = new LinkedHashMap<>();
    private List<RegistroAuditoria> auditoria = new ArrayList<>();
    private List<Evento> eventos = new ArrayList<>();
    private Instant ahora = Instant.parse("2026-11-12T10:00:00Z");

    public Dependencias dependencias() {
        return new Dependencias(this, new Auditoria(), this, new Desastres(), this, this, this);
    }

    // --- preparación de datos de prueba ---
    public Desastre desastre(String nombre, EstadoDesastre estado) {
        Desastre d = new Desastre(UUID.randomUUID(), nombre, estado);
        desastres.put(d.id(), d);
        return d;
    }

    public EvaluacionDano evaluacion(UUID desastreId, String organizacion, String activo, NivelDano nivel,
                                     boolean validada) {
        EvaluacionDano e = new EvaluacionDano(UUID.randomUUID(), desastreId, organizacion, activo, activo, nivel, validada);
        evaluaciones.put(e.id(), e);
        return e;
    }

    public List<RegistroAuditoria> auditoriaCompleta() { return auditoria; }
    public List<Evento> eventos() { return eventos; }

    // --- UnidadDeTrabajo: copia el estado y lo restaura si el trabajo falla ---
    @Override
    public <T> T ejecutar(Supplier<T> trabajo) {
        Map<UUID, Proyecto> copiaProyectos = new LinkedHashMap<>();
        proyectos.forEach((id, p) -> copiaProyectos.put(id, copiar(p)));
        Map<UUID, Desastre> copiaDesastres = new LinkedHashMap<>(desastres);
        List<RegistroAuditoria> copiaAuditoria = new ArrayList<>(auditoria);
        List<Evento> copiaEventos = new ArrayList<>(eventos);
        try {
            return trabajo.get();
        } catch (RuntimeException e) {
            proyectos = copiaProyectos;
            desastres = copiaDesastres;
            auditoria = copiaAuditoria;
            eventos = copiaEventos;
            throw e;
        }
    }

    private static Proyecto copiar(Proyecto p) {
        return Proyecto.rehidratar(p.id(), p.desastreId(), p.organizacion(), p.nombre(), p.tipo(), p.evaluaciones(),
                p.estado(), p.presupuesto(), p.fuenteFinanciacion(), p.responsable(), p.contratista(), p.inicioPlan(),
                p.finPlan(), p.avance(), p.costoAcumulado(), p.seguimiento(), p.version());
    }

    // --- RepositorioProyectos ---
    @Override public void guardarNuevo(Proyecto p) { proyectos.put(p.id(), copiar(p)); }

    @Override
    public void actualizar(Proyecto p) {
        Proyecto guardado = proyectos.get(p.id());
        if (guardado == null || guardado.version() != p.version())
            throw new ConflictoDeVersionException("El proyecto cambió; consulte de nuevo y reintente.");
        p.marcarGuardado();
        proyectos.put(p.id(), copiar(p));
    }

    @Override public Optional<Proyecto> buscar(UUID id) { return Optional.ofNullable(proyectos.get(id)).map(EnMemoria::copiar); }

    @Override
    public List<Proyecto> porDesastre(UUID desastreId, String organizacion) {
        return proyectos.values().stream().filter(p -> p.desastreId().equals(desastreId))
                .filter(p -> organizacion == null || p.organizacion().equals(organizacion))
                .map(EnMemoria::copiar).toList();
    }

    @Override
    public boolean existeActivoParaEvaluacion(UUID evaluacionId) {
        return proyectos.values().stream().anyMatch(p -> p.estaActivo() && p.evaluaciones().contains(evaluacionId));
    }

    // --- RegistroDeAuditoria (clase interna: su porDesastre choca con el del repositorio) ---
    private class Auditoria implements RegistroDeAuditoria {
        @Override public void registrar(RegistroAuditoria r) { auditoria.add(r); }
        @Override public List<RegistroAuditoria> porEntidad(UUID id) { return auditoria.stream().filter(r -> id.equals(r.entidadId())).toList(); }

        @Override
        public List<RegistroAuditoria> porDesastre(UUID desastreId, String organizacion) {
            return auditoria.stream().filter(r -> desastreId.equals(r.desastreId()))
                    .filter(r -> r.organizacion().equals(organizacion)).toList();
        }
    }

    // --- ConsultaEvaluaciones ---
    @Override public List<EvaluacionDano> porIds(Collection<UUID> ids) { return ids.stream().map(evaluaciones::get).filter(e -> e != null).toList(); }
    @Override public List<EvaluacionDano> porDesastre(UUID desastreId) { return evaluaciones.values().stream().filter(e -> e.desastreId().equals(desastreId)).toList(); }

    // --- GestionDesastres (clase interna: su buscar(UUID) choca con el del repositorio) ---
    private class Desastres implements GestionDesastres {
        @Override public Optional<Desastre> buscar(UUID id) { return Optional.ofNullable(desastres.get(id)); }
        @Override public List<Desastre> listar() { return List.copyOf(desastres.values()); }

        @Override
        public void cambiarEstado(UUID id, EstadoDesastre nuevo) {
            Desastre d = desastres.get(id);
            desastres.put(id, new Desastre(d.id(), d.nombre(), nuevo));
        }
    }

    // --- PublicadorEventos y Reloj ---
    @Override public void publicar(String tipo, UUID agregadoId, Map<String, String> datos) { eventos.add(new Evento(tipo, agregadoId, datos)); }
    @Override public Instant ahora() { ahora = ahora.plusSeconds(1); return ahora; }
}
