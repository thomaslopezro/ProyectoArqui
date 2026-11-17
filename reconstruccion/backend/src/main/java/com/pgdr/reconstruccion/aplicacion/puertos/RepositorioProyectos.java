package com.pgdr.reconstruccion.aplicacion.puertos;

import com.pgdr.reconstruccion.dominio.Proyecto;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RepositorioProyectos {
    void guardarNuevo(Proyecto proyecto);

    /** Guarda los cambios si nadie más modificó el proyecto; si no, lanza ConflictoDeVersionException. */
    void actualizar(Proyecto proyecto);

    Optional<Proyecto> buscar(UUID id);

    /** Proyectos de un desastre. Con organizacion == null devuelve los de todas las organizaciones. */
    List<Proyecto> porDesastre(UUID desastreId, String organizacion);

    /** RN-09: ¿la evaluación ya originó un proyecto que no está cancelado? */
    boolean existeActivoParaEvaluacion(UUID evaluacionId);
}
