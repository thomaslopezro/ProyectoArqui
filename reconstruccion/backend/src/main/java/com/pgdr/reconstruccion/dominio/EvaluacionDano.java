package com.pgdr.reconstruccion.dominio;

import java.util.UUID;

/** Evaluación de daño tal como la entrega el módulo de Evaluación de daños. */
public record EvaluacionDano(UUID id, UUID desastreId, String organizacion, String tipoActivo,
                             String descripcion, NivelDano nivel, boolean validada) {

    /** RN-02: solo una evaluación validada con daño moderado o superior origina proyectos. */
    public boolean esElegible() {
        return validada && nivel.requiereIntervencion();
    }
}
