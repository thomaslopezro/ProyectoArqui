package com.pgdr.reconstruccion.aplicacion.casosdeuso;

import com.pgdr.reconstruccion.dominio.Actor;
import com.pgdr.reconstruccion.dominio.EstadoProyecto;
import com.pgdr.reconstruccion.dominio.Proyecto;
import com.pgdr.reconstruccion.dominio.Rol;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/** CU-04: iniciar la ejecución con contratista y cronograma (RN-11). */
public class IniciarEjecucion extends CasoDeUso {
    public IniciarEjecucion(Dependencias d) {
        super(d);
    }

    public Proyecto ejecutar(Actor actor, UUID proyectoId, String contratista, LocalDate inicio, LocalDate fin) {
        autorizar(actor, Rol.RESPONSABLE);
        return ejecutarAuditado(actor, "INICIAR_EJECUCION", PROYECTO, proyectoId, null, null, () -> {
            Proyecto p = cargarProyecto(actor, proyectoId);
            EstadoProyecto anterior = p.estado();
            p.iniciarEjecucion(contratista, inicio, fin);
            d.proyectos().actualizar(p);
            auditar(actor, "CAMBIAR_ESTADO", PROYECTO, p.id(), p.desastreId(), anterior.name(), p.estado().name(), null);
            d.eventos().publicar("PROYECTO_ESTADO_CAMBIADO", p.id(),
                    Map.of("proyectoId", p.id().toString(), "estado", p.estado().name()));
            return p;
        });
    }
}
