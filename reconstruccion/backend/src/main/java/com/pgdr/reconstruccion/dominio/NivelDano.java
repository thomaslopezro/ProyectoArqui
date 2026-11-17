package com.pgdr.reconstruccion.dominio;

/** Categorías de daño del enunciado (§4.2), de menor a mayor. */
public enum NivelDano {
    SIN_DANO, LEVE, MODERADO, GRAVE, DESTRUCCION_TOTAL;

    /** RN-02: desde daño moderado la evaluación puede originar un proyecto. */
    public boolean requiereIntervencion() {
        return this.ordinal() >= MODERADO.ordinal();
    }
}
