package com.pgdr.reconstruccion.aplicacion.casosdeuso;

import com.pgdr.reconstruccion.dominio.Actor;
import com.pgdr.reconstruccion.dominio.Desastre;
import com.pgdr.reconstruccion.dominio.EstadoDesastre;
import com.pgdr.reconstruccion.dominio.EvaluacionDano;
import com.pgdr.reconstruccion.dominio.Proyecto;
import com.pgdr.reconstruccion.dominio.ReglaVioladaException;
import com.pgdr.reconstruccion.dominio.Rol;
import com.pgdr.reconstruccion.dominio.TipoProyecto;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** CU-02: crear proyectos desde evaluaciones, uno o en lote (RN-06 a RN-09, RN-18). */
public class CrearProyectos extends CasoDeUso {
    public record Solicitud(String nombre, TipoProyecto tipo, List<UUID> evaluaciones) {}

    /** Resultado por ítem del lote: el proyecto creado o la regla que lo impidió. */
    public record Resultado(int indice, String nombre, Proyecto proyecto, String regla, String error) {
        public boolean creado() {
            return proyecto != null;
        }
    }

    public CrearProyectos(Dependencias d) {
        super(d);
    }

    /** Crea un solo proyecto; si una regla lo impide, lanza la excepción. */
    public Proyecto crearUno(Actor actor, UUID desastreId, Solicitud solicitud) {
        autorizar(actor, Rol.AUTORIDAD);
        UUID id = UUID.randomUUID();
        return ejecutarAuditado(actor, "CREAR_PROYECTO", PROYECTO, id, desastreId, null,
                () -> crear(actor, id, desastreId, solicitud));
    }

    /** RN-18: un proyecto por ítem, cada uno en su transacción; un ítem inválido no impide los demás. */
    public List<Resultado> crearLote(Actor actor, UUID desastreId, List<Solicitud> solicitudes) {
        autorizar(actor, Rol.AUTORIDAD);
        if (solicitudes == null || solicitudes.isEmpty())
            throw new ReglaVioladaException("RN-18", "El lote no trae proyectos.");
        List<Resultado> resultados = new ArrayList<>();
        for (int i = 0; i < solicitudes.size(); i++) {
            Solicitud s = solicitudes.get(i);
            try {
                resultados.add(new Resultado(i, s.nombre(), crearUno(actor, desastreId, s), null, null));
            } catch (ReglaVioladaException e) {
                resultados.add(new Resultado(i, s.nombre(), null, e.regla(), e.getMessage()));
            } catch (NoEncontradoException e) {
                resultados.add(new Resultado(i, s.nombre(), null, "RN-07", e.getMessage()));
            } catch (AccesoDenegadoException e) {   // p. ej. una evaluación de otra organización
                resultados.add(new Resultado(i, s.nombre(), null, "RN-10", e.getMessage()));
            }
        }
        return resultados;
    }

    private Proyecto crear(Actor actor, UUID id, UUID desastreId, Solicitud s) {
        Desastre desastre = cargarDesastre(desastreId);
        if (desastre.estado() != EstadoDesastre.RECONSTRUCCION)
            throw new ReglaVioladaException("RN-06",
                    "Solo se crean proyectos con el desastre en Reconstrucción; está en " + desastre.estado() + ".");
        List<UUID> ids = s.evaluaciones() == null ? List.of() : s.evaluaciones();
        if (new HashSet<>(ids).size() != ids.size())
            throw new ReglaVioladaException("RN-07", "El proyecto repite una evaluación.");
        List<EvaluacionDano> evaluaciones = d.evaluaciones().porIds(ids);
        if (evaluaciones.size() != ids.size())
            throw new ReglaVioladaException("RN-07", "Alguna evaluación indicada no existe.");
        for (EvaluacionDano e : evaluaciones) {
            if (!e.organizacion().equals(actor.organizacion()))
                throw new AccesoDenegadoException("La evaluación " + e.id() + " es de otra organización.");
            if (d.proyectos().existeActivoParaEvaluacion(e.id()))
                throw new ReglaVioladaException("RN-09", "La evaluación " + e.id() + " ya tiene un proyecto activo.");
        }
        Proyecto proyecto = Proyecto.proponer(id, desastreId, actor.organizacion(), s.nombre(), s.tipo(), evaluaciones);
        d.proyectos().guardarNuevo(proyecto);
        auditar(actor, "CREAR_PROYECTO", PROYECTO, id, desastreId, null, proyecto.estado().name(), null);
        d.eventos().publicar("PROYECTO_CREADO", id,
                Map.of("proyectoId", id.toString(), "desastreId", desastreId.toString(), "tipo", proyecto.tipo().name()));
        return proyecto;
    }
}
