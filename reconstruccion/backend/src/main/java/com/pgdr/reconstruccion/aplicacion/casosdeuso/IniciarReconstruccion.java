package com.pgdr.reconstruccion.aplicacion.casosdeuso;

import com.pgdr.reconstruccion.dominio.Actor;
import com.pgdr.reconstruccion.dominio.Desastre;
import com.pgdr.reconstruccion.dominio.EstadoDesastre;
import com.pgdr.reconstruccion.dominio.EvaluacionDano;
import com.pgdr.reconstruccion.dominio.ReglaVioladaException;
import com.pgdr.reconstruccion.dominio.Rol;
import java.util.Map;
import java.util.UUID;

/** CU-01: pasar un desastre de Caracterización a Reconstrucción (RN-01, RN-02). */
public class IniciarReconstruccion extends CasoDeUso {
    public IniciarReconstruccion(Dependencias d) {
        super(d);
    }

    public Desastre ejecutar(Actor actor, UUID desastreId) {
        autorizar(actor, Rol.AUTORIDAD);
        return ejecutarAuditado(actor, "INICIAR_RECONSTRUCCION", DESASTRE, desastreId, desastreId, null, () -> {
            Desastre desastre = cargarDesastre(desastreId);
            if (!desastre.estado().puedePasarA(EstadoDesastre.RECONSTRUCCION))
                throw new ReglaVioladaException("RN-01",
                        "Solo se entra a Reconstrucción desde Caracterización; el desastre está en "
                                + desastre.estado() + ".");
            boolean hayElegible = d.evaluaciones().porDesastre(desastreId).stream()
                    .anyMatch(EvaluacionDano::esElegible);
            if (!hayElegible)
                throw new ReglaVioladaException("RN-02",
                        "No hay evaluaciones validadas con daño moderado o superior.");
            d.desastres().cambiarEstado(desastreId, EstadoDesastre.RECONSTRUCCION);
            auditar(actor, "INICIAR_RECONSTRUCCION", DESASTRE, desastreId, desastreId,
                    desastre.estado().name(), EstadoDesastre.RECONSTRUCCION.name(), null);
            d.eventos().publicar("DESASTRE_EN_RECONSTRUCCION", desastreId, Map.of("desastreId", desastreId.toString()));
            return new Desastre(desastre.id(), desastre.nombre(), EstadoDesastre.RECONSTRUCCION);
        });
    }
}
