package com.pgdr.reconstruccion.aplicacion.casosdeuso;

import com.pgdr.reconstruccion.aplicacion.puertos.ConsultaEvaluaciones;
import com.pgdr.reconstruccion.aplicacion.puertos.GestionDesastres;
import com.pgdr.reconstruccion.aplicacion.puertos.PublicadorEventos;
import com.pgdr.reconstruccion.aplicacion.puertos.RegistroDeAuditoria;
import com.pgdr.reconstruccion.aplicacion.puertos.Reloj;
import com.pgdr.reconstruccion.aplicacion.puertos.RepositorioProyectos;
import com.pgdr.reconstruccion.aplicacion.puertos.UnidadDeTrabajo;

/** Los puertos que usan los casos de uso. Las implementaciones viven en la capa de adaptadores. */
public record Dependencias(RepositorioProyectos proyectos, RegistroDeAuditoria auditoria,
                           ConsultaEvaluaciones evaluaciones, GestionDesastres desastres,
                           PublicadorEventos eventos, Reloj reloj, UnidadDeTrabajo unidadDeTrabajo) {}
