package com.pgdr.reconstruccion.aplicacion.casosdeuso;

import com.pgdr.reconstruccion.dominio.Actor;
import com.pgdr.reconstruccion.dominio.Desastre;
import com.pgdr.reconstruccion.dominio.EstadoProyecto;
import com.pgdr.reconstruccion.dominio.EvaluacionDano;
import com.pgdr.reconstruccion.dominio.Proyecto;
import com.pgdr.reconstruccion.dominio.RegistroAuditoria;
import com.pgdr.reconstruccion.dominio.Rol;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** CU-08: consultar seguimiento y auditoría por desastre (RN-10). Solo lectura. */
public class ConsultarSeguimiento extends CasoDeUso {
    /** Consolidado de los proyectos de una organización en un desastre. */
    public record Consolidado(Desastre desastre, List<Proyecto> proyectos, Map<EstadoProyecto, Integer> porEstado,
                              BigDecimal presupuestoTotal, BigDecimal costoTotal, int avancePromedio,
                              int conSobrecosto) {}

    /** Evaluación con la marca de si todavía puede originar un proyecto. */
    public record EvaluacionDisponible(EvaluacionDano evaluacion, boolean elegible, boolean conProyectoActivo) {}

    public ConsultarSeguimiento(Dependencias d) {
        super(d);
    }

    public List<Desastre> desastres(Actor actor) {
        autorizar(actor, Rol.AUTORIDAD, Rol.RESPONSABLE, Rol.AUDITOR);
        return d.desastres().listar();
    }

    public List<EvaluacionDisponible> evaluaciones(Actor actor, UUID desastreId) {
        autorizar(actor, Rol.AUTORIDAD);
        cargarDesastre(desastreId);
        return d.evaluaciones().porDesastre(desastreId).stream()
                .filter(e -> e.organizacion().equals(actor.organizacion()))
                .map(e -> new EvaluacionDisponible(e, e.esElegible(), d.proyectos().existeActivoParaEvaluacion(e.id())))
                .toList();
    }

    public Consolidado consolidado(Actor actor, UUID desastreId) {
        autorizar(actor, Rol.AUTORIDAD, Rol.RESPONSABLE, Rol.AUDITOR);
        Desastre desastre = cargarDesastre(desastreId);
        List<Proyecto> proyectos = d.proyectos().porDesastre(desastreId, actor.organizacion());
        Map<EstadoProyecto, Integer> porEstado = new EnumMap<>(EstadoProyecto.class);
        BigDecimal presupuesto = BigDecimal.ZERO;
        BigDecimal costo = BigDecimal.ZERO;
        int sumaAvance = 0;
        int vigentes = 0;
        int sobrecosto = 0;
        for (Proyecto p : proyectos) {
            porEstado.merge(p.estado(), 1, Integer::sum);
            if (p.estado() == EstadoProyecto.CANCELADO) continue;   // lo cancelado no suma al consolidado
            if (p.presupuesto() != null) presupuesto = presupuesto.add(p.presupuesto());
            costo = costo.add(p.costoAcumulado());
            sumaAvance += p.avance();
            vigentes++;
            if (p.tieneSobrecosto()) sobrecosto++;
        }
        int promedio = vigentes == 0 ? 0 : Math.round((float) sumaAvance / vigentes);
        return new Consolidado(desastre, proyectos, porEstado, presupuesto, costo, promedio, sobrecosto);
    }

    public Proyecto proyecto(Actor actor, UUID proyectoId) {
        autorizar(actor, Rol.AUTORIDAD, Rol.RESPONSABLE, Rol.AUDITOR);
        return cargarProyecto(actor, proyectoId);
    }

    public List<RegistroAuditoria> auditoriaDeProyecto(Actor actor, UUID proyectoId) {
        autorizar(actor, Rol.AUTORIDAD, Rol.AUDITOR);
        cargarProyecto(actor, proyectoId);
        return d.auditoria().porEntidad(proyectoId);
    }

    public List<RegistroAuditoria> auditoriaDeDesastre(Actor actor, UUID desastreId) {
        autorizar(actor, Rol.AUTORIDAD, Rol.AUDITOR);
        cargarDesastre(desastreId);
        return d.auditoria().porDesastre(desastreId, actor.organizacion());
    }
}
