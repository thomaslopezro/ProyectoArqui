package com.pgdr.reconstruccion.aplicacion.casosdeuso;

import com.pgdr.reconstruccion.dominio.Actor;
import com.pgdr.reconstruccion.dominio.EstadoProyecto;
import com.pgdr.reconstruccion.dominio.Proyecto;
import com.pgdr.reconstruccion.dominio.Rol;
import java.util.Map;
import java.util.UUID;

/** CU-07: suspender, reanudar, finalizar o cancelar (RN-15 a RN-17). */
public class CambiarEstado extends CasoDeUso {
    public CambiarEstado(Dependencias d) {
        super(d);
    }

    public Proyecto ejecutar(Actor actor, UUID proyectoId, EstadoProyecto destino, String motivo) {
        autorizar(actor, Rol.AUTORIDAD, Rol.RESPONSABLE);
        return ejecutarAuditado(actor, "CAMBIAR_ESTADO", PROYECTO, proyectoId, null, motivo, () -> {
            Proyecto p = cargarProyecto(actor, proyectoId);
            EstadoProyecto anterior = p.cambiarEstado(destino, motivo);
            d.proyectos().actualizar(p);
            auditar(actor, "CAMBIAR_ESTADO", PROYECTO, p.id(), p.desastreId(), anterior.name(), p.estado().name(), motivo);
            d.eventos().publicar("PROYECTO_ESTADO_CAMBIADO", p.id(),
                    Map.of("proyectoId", p.id().toString(), "estado", p.estado().name()));
            return p;
        });
    }
}
