package com.pgdr.reconstruccion.aplicacion.casosdeuso;

import com.pgdr.reconstruccion.dominio.Actor;
import com.pgdr.reconstruccion.dominio.Desastre;
import com.pgdr.reconstruccion.dominio.Proyecto;
import com.pgdr.reconstruccion.dominio.RegistroAuditoria;
import com.pgdr.reconstruccion.dominio.ReglaVioladaException;
import com.pgdr.reconstruccion.dominio.Rol;
import java.util.Arrays;
import java.util.UUID;
import java.util.function.Supplier;

/** Lo común a todos los casos de uso: permisos (RN-10), carga de entidades y auditoría (RN-17). */
abstract class CasoDeUso {
    protected static final String PROYECTO = "PROYECTO";
    protected static final String DESASTRE = "DESASTRE";

    protected final Dependencias d;

    protected CasoDeUso(Dependencias d) {
        this.d = d;
    }

    /** RN-10: el actor debe tener uno de los roles permitidos. */
    protected void autorizar(Actor actor, Rol... permitidos) {
        if (!Arrays.asList(permitidos).contains(actor.rol()))
            throw new AccesoDenegadoException("El rol " + actor.rol() + " no puede ejecutar esta operación.");
    }

    /** RN-10: cada organización ve y modifica solo sus proyectos. */
    protected Proyecto cargarProyecto(Actor actor, UUID proyectoId) {
        Proyecto p = d.proyectos().buscar(proyectoId)
                .orElseThrow(() -> new NoEncontradoException("No existe el proyecto " + proyectoId + "."));
        if (!p.organizacion().equals(actor.organizacion()))
            throw new AccesoDenegadoException("El proyecto pertenece a otra organización.");
        return p;
    }

    protected Desastre cargarDesastre(UUID desastreId) {
        return d.desastres().buscar(desastreId)
                .orElseThrow(() -> new NoEncontradoException("No existe el desastre " + desastreId + "."));
    }

    /**
     * Ejecuta el trabajo en una transacción. Si una regla lo rechaza, la transacción se deshace
     * y el intento queda auditado como RECHAZADO en una operación aparte.
     */
    protected <T> T ejecutarAuditado(Actor actor, String operacion, String entidad, UUID entidadId,
                                     UUID desastreId, String motivo, Supplier<T> trabajo) {
        try {
            return d.unidadDeTrabajo().ejecutar(trabajo);
        } catch (ReglaVioladaException e) {
            UUID desastre = desastreId;
            if (desastre == null && PROYECTO.equals(entidad))
                desastre = d.proyectos().buscar(entidadId).map(Proyecto::desastreId).orElse(null);
            d.auditoria().registrar(new RegistroAuditoria(UUID.randomUUID(), d.reloj().ahora(), actor.usuario(),
                    actor.rol(), actor.organizacion(), operacion, entidad, entidadId, desastre, null, null,
                    motivo, RegistroAuditoria.Resultado.RECHAZADO, e.regla() + ": " + e.getMessage()));
            throw e;
        }
    }

    /** Registro de auditoría exitoso; se llama dentro de la transacción del cambio. */
    protected void auditar(Actor actor, String operacion, String entidad, UUID entidadId, UUID desastreId,
                           String anterior, String nuevo, String motivo) {
        d.auditoria().registrar(new RegistroAuditoria(UUID.randomUUID(), d.reloj().ahora(), actor.usuario(),
                actor.rol(), actor.organizacion(), operacion, entidad, entidadId, desastreId, anterior, nuevo,
                motivo, RegistroAuditoria.Resultado.EXITOSO, null));
    }
}
