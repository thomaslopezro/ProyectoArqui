package com.pgdr.reconstruccion.aplicacion.casosdeuso;

import com.pgdr.reconstruccion.dominio.Actor;
import com.pgdr.reconstruccion.dominio.Proyecto;
import com.pgdr.reconstruccion.dominio.Rol;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/** CU-05: registrar avance, costos, documentación y evidencias (RN-13, RN-14). */
public class RegistrarSeguimiento extends CasoDeUso {
    public RegistrarSeguimiento(Dependencias d) {
        super(d);
    }

    public Proyecto avance(Actor actor, UUID proyectoId, int porcentaje, String descripcion, String motivoCorreccion) {
        autorizar(actor, Rol.RESPONSABLE);
        return ejecutarAuditado(actor, "REGISTRAR_AVANCE", PROYECTO, proyectoId, null, motivoCorreccion, () -> {
            Proyecto p = cargarProyecto(actor, proyectoId);
            int anterior = p.registrarAvance(porcentaje, descripcion, motivoCorreccion, d.reloj().ahora(), actor.usuario());
            d.proyectos().actualizar(p);
            auditar(actor, "REGISTRAR_AVANCE", PROYECTO, p.id(), p.desastreId(), anterior + " %", p.avance() + " %",
                    porcentaje < anterior ? motivoCorreccion : null);
            return p;
        });
    }

    public Proyecto costo(Actor actor, UUID proyectoId, BigDecimal valor, String descripcion) {
        autorizar(actor, Rol.RESPONSABLE);
        return ejecutarAuditado(actor, "REGISTRAR_COSTO", PROYECTO, proyectoId, null, null, () -> {
            Proyecto p = cargarProyecto(actor, proyectoId);
            BigDecimal anterior = p.costoAcumulado();
            boolean nuevoSobrecosto = p.registrarCosto(valor, descripcion, d.reloj().ahora(), actor.usuario());
            d.proyectos().actualizar(p);
            auditar(actor, "REGISTRAR_COSTO", PROYECTO, p.id(), p.desastreId(), anterior.toPlainString(),
                    p.costoAcumulado().toPlainString(), null);
            if (nuevoSobrecosto)   // RN-14: se marca y se notifica, sin bloquear el registro
                d.eventos().publicar("PROYECTO_CON_SOBRECOSTO", p.id(), Map.of("proyectoId", p.id().toString(),
                        "presupuesto", p.presupuesto().toPlainString(), "costoAcumulado", p.costoAcumulado().toPlainString()));
            return p;
        });
    }

    public Proyecto evidencia(Actor actor, UUID proyectoId, String descripcion) {
        autorizar(actor, Rol.RESPONSABLE);
        return ejecutarAuditado(actor, "ADJUNTAR_EVIDENCIA", PROYECTO, proyectoId, null, null, () -> {
            Proyecto p = cargarProyecto(actor, proyectoId);
            p.adjuntarEvidencia(descripcion, d.reloj().ahora(), actor.usuario());
            d.proyectos().actualizar(p);
            auditar(actor, "ADJUNTAR_EVIDENCIA", PROYECTO, p.id(), p.desastreId(), null, descripcion, null);
            return p;
        });
    }
}
