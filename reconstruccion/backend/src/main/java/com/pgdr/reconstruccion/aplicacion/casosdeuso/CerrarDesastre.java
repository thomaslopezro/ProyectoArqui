package com.pgdr.reconstruccion.aplicacion.casosdeuso;

import com.pgdr.reconstruccion.dominio.Actor;
import com.pgdr.reconstruccion.dominio.Desastre;
import com.pgdr.reconstruccion.dominio.EstadoDesastre;
import com.pgdr.reconstruccion.dominio.Proyecto;
import com.pgdr.reconstruccion.dominio.ReglaVioladaException;
import com.pgdr.reconstruccion.dominio.Rol;
import java.util.Map;
import java.util.UUID;

/** CU-09: cerrar el desastre al terminar la reconstrucción (RN-04, RN-05). */
public class CerrarDesastre extends CasoDeUso {
    public CerrarDesastre(Dependencias d) {
        super(d);
    }

    public Desastre ejecutar(Actor actor, UUID desastreId) {
        autorizar(actor, Rol.AUTORIDAD);
        return ejecutarAuditado(actor, "CERRAR_DESASTRE", DESASTRE, desastreId, desastreId, null, () -> {
            Desastre desastre = cargarDesastre(desastreId);
            if (desastre.estado() == EstadoDesastre.CIERRE)
                throw new ReglaVioladaException("RN-05", "El desastre ya está cerrado; Cierre es terminal.");
            if (desastre.estado() != EstadoDesastre.RECONSTRUCCION)
                throw new ReglaVioladaException("RN-03", "El desastre no está en Reconstrucción; su cierre directo "
                        + "le corresponde al módulo de Gestión de desastres.");
            long pendientes = d.proyectos().porDesastre(desastreId, null).stream()
                    .filter(p -> !p.estado().esTerminal()).count();
            if (pendientes > 0)
                throw new ReglaVioladaException("RN-04",
                        "Quedan " + pendientes + " proyectos sin finalizar ni cancelar.");
            d.desastres().cambiarEstado(desastreId, EstadoDesastre.CIERRE);
            auditar(actor, "CERRAR_DESASTRE", DESASTRE, desastreId, desastreId,
                    desastre.estado().name(), EstadoDesastre.CIERRE.name(), null);
            d.eventos().publicar("DESASTRE_CERRADO", desastreId, Map.of("desastreId", desastreId.toString()));
            return new Desastre(desastre.id(), desastre.nombre(), EstadoDesastre.CIERRE);
        });
    }
}
