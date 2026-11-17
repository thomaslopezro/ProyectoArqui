package com.pgdr.reconstruccion.aplicacion.puertos;

import com.pgdr.reconstruccion.dominio.EvaluacionDano;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Acceso al módulo de Evaluación de daños. */
public interface ConsultaEvaluaciones {
    List<EvaluacionDano> porIds(Collection<UUID> ids);

    List<EvaluacionDano> porDesastre(UUID desastreId);
}
