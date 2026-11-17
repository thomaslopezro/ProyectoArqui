package com.pgdr.reconstruccion.adaptadores.entrada.rest;

import com.pgdr.reconstruccion.aplicacion.casosdeuso.ConsultarSeguimiento;
import com.pgdr.reconstruccion.aplicacion.casosdeuso.CrearProyectos;
import com.pgdr.reconstruccion.dominio.Desastre;
import com.pgdr.reconstruccion.dominio.EstadoProyecto;
import com.pgdr.reconstruccion.dominio.Proyecto;
import com.pgdr.reconstruccion.dominio.RegistroAuditoria;
import com.pgdr.reconstruccion.dominio.Seguimiento;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Formas JSON que expone la API REST. El dominio no se serializa directamente. */
public final class Vistas {
    private Vistas() {}

    public record DesastreVista(String id, String nombre, String estado) {
        public static DesastreVista de(Desastre d) {
            return new DesastreVista(d.id().toString(), d.nombre(), d.estado().name());
        }
    }

    public record SeguimientoVista(String tipo, BigDecimal valor, String descripcion, String momento, String usuario) {
        public static SeguimientoVista de(Seguimiento s) {
            return new SeguimientoVista(s.tipo().name(), s.valor(), s.descripcion(), s.momento().toString(), s.usuario());
        }
    }

    public record ProyectoVista(String id, String desastreId, String organizacion, String nombre, String tipo, String estado,
                         BigDecimal presupuesto, String fuenteFinanciacion, String responsable, String contratista,
                         String inicioPlan, String finPlan, int avance, BigDecimal costoAcumulado, boolean sobrecosto,
                         List<String> evaluaciones, List<SeguimientoVista> seguimiento, List<String> estadosSiguientes,
                         long version) {
        public static ProyectoVista de(Proyecto p) {
            return new ProyectoVista(p.id().toString(), p.desastreId().toString(), p.organizacion(), p.nombre(),
                    p.tipo().name(), p.estado().name(), p.presupuesto(), p.fuenteFinanciacion(), p.responsable(),
                    p.contratista(), p.inicioPlan() == null ? null : p.inicioPlan().toString(),
                    p.finPlan() == null ? null : p.finPlan().toString(), p.avance(), p.costoAcumulado(),
                    p.tieneSobrecosto(), p.evaluaciones().stream().map(Object::toString).toList(),
                    p.seguimiento().stream().map(SeguimientoVista::de).toList(),
                    p.estado().destinos().stream().map(Enum::name).toList(), p.version());
        }
    }

    public record EvaluacionVista(String id, String tipoActivo, String descripcion, String nivel, boolean validada,
                           boolean elegible, boolean conProyectoActivo) {
        public static EvaluacionVista de(ConsultarSeguimiento.EvaluacionDisponible e) {
            return new EvaluacionVista(e.evaluacion().id().toString(), e.evaluacion().tipoActivo(),
                    e.evaluacion().descripcion(), e.evaluacion().nivel().name(), e.evaluacion().validada(),
                    e.elegible(), e.conProyectoActivo());
        }
    }

    public record ConsolidadoVista(DesastreVista desastre, List<ProyectoVista> proyectos, Map<String, Integer> porEstado,
                            BigDecimal presupuestoTotal, BigDecimal costoTotal, int avancePromedio, int conSobrecosto) {
        public static ConsolidadoVista de(ConsultarSeguimiento.Consolidado c) {
            Map<String, Integer> porEstado = new LinkedHashMap<>();
            for (EstadoProyecto e : EstadoProyecto.values()) porEstado.put(e.name(), c.porEstado().getOrDefault(e, 0));
            return new ConsolidadoVista(DesastreVista.de(c.desastre()),
                    c.proyectos().stream().map(ProyectoVista::de).toList(), porEstado, c.presupuestoTotal(),
                    c.costoTotal(), c.avancePromedio(), c.conSobrecosto());
        }
    }

    public record ResultadoLoteVista(int indice, String nombre, boolean creado, String proyectoId, String regla, String error) {
        public static ResultadoLoteVista de(CrearProyectos.Resultado r) {
            return new ResultadoLoteVista(r.indice(), r.nombre(), r.creado(),
                    r.creado() ? r.proyecto().id().toString() : null, r.regla(), r.error());
        }
    }

    public record AuditoriaVista(String momento, String usuario, String rol, String organizacion, String operacion,
                          String entidad, String entidadId, String valorAnterior, String valorNuevo, String motivo,
                          String resultado, String detalle) {
        public static AuditoriaVista de(RegistroAuditoria r) {
            return new AuditoriaVista(r.momento().toString(), r.usuario(), r.rol().name(), r.organizacion(),
                    r.operacion(), r.entidad(), r.entidadId() == null ? null : r.entidadId().toString(),
                    r.valorAnterior(), r.valorNuevo(), r.motivo(), r.resultado().name(), r.detalle());
        }
    }

    public record ErrorVista(String codigo, String regla, String mensaje) {}
}
