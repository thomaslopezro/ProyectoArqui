package com.pgdr.reconstruccion.infraestructura;

import com.pgdr.reconstruccion.aplicacion.casosdeuso.AprobarProyecto;
import com.pgdr.reconstruccion.aplicacion.casosdeuso.CambiarEstado;
import com.pgdr.reconstruccion.aplicacion.casosdeuso.CerrarDesastre;
import com.pgdr.reconstruccion.aplicacion.casosdeuso.ConsultarSeguimiento;
import com.pgdr.reconstruccion.aplicacion.casosdeuso.CrearProyectos;
import com.pgdr.reconstruccion.aplicacion.casosdeuso.Dependencias;
import com.pgdr.reconstruccion.aplicacion.casosdeuso.IniciarEjecucion;
import com.pgdr.reconstruccion.aplicacion.casosdeuso.IniciarReconstruccion;
import com.pgdr.reconstruccion.aplicacion.casosdeuso.ModificarPresupuesto;
import com.pgdr.reconstruccion.aplicacion.casosdeuso.RegistrarSeguimiento;
import com.pgdr.reconstruccion.aplicacion.puertos.ConsultaEvaluaciones;
import com.pgdr.reconstruccion.aplicacion.puertos.GestionDesastres;
import com.pgdr.reconstruccion.aplicacion.puertos.PublicadorEventos;
import com.pgdr.reconstruccion.aplicacion.puertos.RegistroDeAuditoria;
import com.pgdr.reconstruccion.aplicacion.puertos.Reloj;
import com.pgdr.reconstruccion.aplicacion.puertos.RepositorioProyectos;
import com.pgdr.reconstruccion.aplicacion.puertos.UnidadDeTrabajo;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Único lugar donde se conectan los casos de uso (capa 2) con sus adaptadores (capa 3).
 * Las capas 1 y 2 no tienen anotaciones ni dependencias de Spring.
 */
@Configuration
public class ConfiguracionCasosDeUso {
    @Bean
    Dependencias dependencias(RepositorioProyectos proyectos, RegistroDeAuditoria auditoria,
                              ConsultaEvaluaciones evaluaciones, GestionDesastres desastres,
                              PublicadorEventos eventos, Reloj reloj, UnidadDeTrabajo unidadDeTrabajo) {
        return new Dependencias(proyectos, auditoria, evaluaciones, desastres, eventos, reloj, unidadDeTrabajo);
    }

    @Bean IniciarReconstruccion iniciarReconstruccion(Dependencias d) { return new IniciarReconstruccion(d); }
    @Bean CrearProyectos crearProyectos(Dependencias d) { return new CrearProyectos(d); }
    @Bean AprobarProyecto aprobarProyecto(Dependencias d) { return new AprobarProyecto(d); }
    @Bean IniciarEjecucion iniciarEjecucion(Dependencias d) { return new IniciarEjecucion(d); }
    @Bean RegistrarSeguimiento registrarSeguimiento(Dependencias d) { return new RegistrarSeguimiento(d); }
    @Bean ModificarPresupuesto modificarPresupuesto(Dependencias d) { return new ModificarPresupuesto(d); }
    @Bean CambiarEstado cambiarEstado(Dependencias d) { return new CambiarEstado(d); }
    @Bean ConsultarSeguimiento consultarSeguimiento(Dependencias d) { return new ConsultarSeguimiento(d); }
    @Bean CerrarDesastre cerrarDesastre(Dependencias d) { return new CerrarDesastre(d); }
}
