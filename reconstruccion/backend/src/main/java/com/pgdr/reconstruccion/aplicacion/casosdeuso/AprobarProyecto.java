package com.pgdr.reconstruccion.aplicacion.casosdeuso;

import com.pgdr.reconstruccion.dominio.Actor;
import com.pgdr.reconstruccion.dominio.EstadoProyecto;
import com.pgdr.reconstruccion.dominio.Proyecto;
import com.pgdr.reconstruccion.dominio.Rol;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/** CU-03: asignar presupuesto, fuente de financiación y responsable, y aprobar (RN-10 a RN-12). */
public class AprobarProyecto extends CasoDeUso {
    public AprobarProyecto(Dependencias d) {
        super(d);
    }

    public Proyecto ejecutar(Actor actor, UUID proyectoId, BigDecimal presupuesto, String fuenteFinanciacion,
                             String responsable) {
        autorizar(actor, Rol.AUTORIDAD);
        return ejecutarAuditado(actor, "APROBAR_PROYECTO", PROYECTO, proyectoId, null, null, () -> {
            Proyecto p = cargarProyecto(actor, proyectoId);
            EstadoProyecto anterior = p.estado();
            p.aprobar(presupuesto, fuenteFinanciacion, responsable);
            d.proyectos().actualizar(p);
            auditar(actor, "CAMBIAR_ESTADO", PROYECTO, p.id(), p.desastreId(), anterior.name(), p.estado().name(), null);
            auditar(actor, "ASIGNAR_PRESUPUESTO", PROYECTO, p.id(), p.desastreId(), null,
                    p.presupuesto().toPlainString(), "Presupuesto inicial al aprobar");
            d.eventos().publicar("PROYECTO_APROBADO", p.id(),
                    Map.of("proyectoId", p.id().toString(), "presupuesto", p.presupuesto().toPlainString()));
            return p;
        });
    }
}
