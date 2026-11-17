package com.pgdr.reconstruccion.adaptadores.entrada.rest;

import com.pgdr.reconstruccion.adaptadores.entrada.rest.Vistas.AuditoriaVista;
import com.pgdr.reconstruccion.adaptadores.entrada.rest.Vistas.ConsolidadoVista;
import com.pgdr.reconstruccion.adaptadores.entrada.rest.Vistas.DesastreVista;
import com.pgdr.reconstruccion.adaptadores.entrada.rest.Vistas.EvaluacionVista;
import com.pgdr.reconstruccion.adaptadores.entrada.rest.Vistas.ProyectoVista;
import com.pgdr.reconstruccion.adaptadores.entrada.rest.Vistas.ResultadoLoteVista;
import com.pgdr.reconstruccion.aplicacion.casosdeuso.AprobarProyecto;
import com.pgdr.reconstruccion.aplicacion.casosdeuso.CambiarEstado;
import com.pgdr.reconstruccion.aplicacion.casosdeuso.CerrarDesastre;
import com.pgdr.reconstruccion.aplicacion.casosdeuso.ConsultarSeguimiento;
import com.pgdr.reconstruccion.aplicacion.casosdeuso.CrearProyectos;
import com.pgdr.reconstruccion.aplicacion.casosdeuso.IniciarEjecucion;
import com.pgdr.reconstruccion.aplicacion.casosdeuso.IniciarReconstruccion;
import com.pgdr.reconstruccion.aplicacion.casosdeuso.ModificarPresupuesto;
import com.pgdr.reconstruccion.aplicacion.casosdeuso.RegistrarSeguimiento;
import com.pgdr.reconstruccion.dominio.Actor;
import com.pgdr.reconstruccion.dominio.EstadoProyecto;
import com.pgdr.reconstruccion.dominio.Rol;
import com.pgdr.reconstruccion.dominio.TipoProyecto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Adaptador de entrada REST para la interfaz web. Solo traduce HTTP/JSON a casos de uso:
 * no contiene reglas de negocio. La identidad llega en cabeceras; en la plataforma integrada
 * las fija la fachada después de validar la sesión.
 */
@RestController
@RequestMapping("/api/reconstruccion")
public class ReconstruccionController {
    public record SolicitudProyecto(String nombre, String tipo, List<String> evaluaciones) {}
    public record SolicitudLote(List<SolicitudProyecto> proyectos) {}
    public record SolicitudAprobar(BigDecimal presupuesto, String fuenteFinanciacion, String responsable) {}
    public record SolicitudIniciar(String contratista, String inicio, String fin) {}
    public record SolicitudPresupuesto(BigDecimal presupuesto, String motivo) {}
    public record SolicitudAvance(Integer porcentaje, String descripcion, String motivoCorreccion) {}
    public record SolicitudCosto(BigDecimal valor, String descripcion) {}
    public record SolicitudEvidencia(String descripcion) {}
    public record SolicitudEstado(String estado, String motivo) {}

    private final IniciarReconstruccion iniciarReconstruccion;
    private final CrearProyectos crearProyectos;
    private final AprobarProyecto aprobarProyecto;
    private final IniciarEjecucion iniciarEjecucion;
    private final RegistrarSeguimiento registrarSeguimiento;
    private final ModificarPresupuesto modificarPresupuesto;
    private final CambiarEstado cambiarEstado;
    private final ConsultarSeguimiento consultar;
    private final CerrarDesastre cerrarDesastre;

    public ReconstruccionController(IniciarReconstruccion iniciarReconstruccion, CrearProyectos crearProyectos,
                                    AprobarProyecto aprobarProyecto, IniciarEjecucion iniciarEjecucion,
                                    RegistrarSeguimiento registrarSeguimiento,
                                    ModificarPresupuesto modificarPresupuesto, CambiarEstado cambiarEstado,
                                    ConsultarSeguimiento consultar, CerrarDesastre cerrarDesastre) {
        this.iniciarReconstruccion = iniciarReconstruccion;
        this.crearProyectos = crearProyectos;
        this.aprobarProyecto = aprobarProyecto;
        this.iniciarEjecucion = iniciarEjecucion;
        this.registrarSeguimiento = registrarSeguimiento;
        this.modificarPresupuesto = modificarPresupuesto;
        this.cambiarEstado = cambiarEstado;
        this.consultar = consultar;
        this.cerrarDesastre = cerrarDesastre;
    }

    private static Actor actor(String usuario, String rol, String organizacion) {
        return new Actor(usuario, Rol.valueOf(rol.trim().toUpperCase()), organizacion);
    }

    // ---- Desastres (CU-01, CU-08, CU-09) ----

    @GetMapping("/desastres")
    public List<DesastreVista> desastres(@RequestHeader("X-Usuario") String usuario, @RequestHeader("X-Rol") String rol,
                                         @RequestHeader("X-Organizacion") String org) {
        return consultar.desastres(actor(usuario, rol, org)).stream().map(DesastreVista::de).toList();
    }

    @PostMapping("/desastres/{id}/iniciar-reconstruccion")
    public DesastreVista iniciarReconstruccion(@PathVariable UUID id, @RequestHeader("X-Usuario") String usuario,
                                               @RequestHeader("X-Rol") String rol,
                                               @RequestHeader("X-Organizacion") String org) {
        return DesastreVista.de(iniciarReconstruccion.ejecutar(actor(usuario, rol, org), id));
    }

    @PostMapping("/desastres/{id}/cerrar")
    public DesastreVista cerrar(@PathVariable UUID id, @RequestHeader("X-Usuario") String usuario,
                                @RequestHeader("X-Rol") String rol, @RequestHeader("X-Organizacion") String org) {
        return DesastreVista.de(cerrarDesastre.ejecutar(actor(usuario, rol, org), id));
    }

    @GetMapping("/desastres/{id}/evaluaciones")
    public List<EvaluacionVista> evaluaciones(@PathVariable UUID id, @RequestHeader("X-Usuario") String usuario,
                                              @RequestHeader("X-Rol") String rol,
                                              @RequestHeader("X-Organizacion") String org) {
        return consultar.evaluaciones(actor(usuario, rol, org), id).stream().map(EvaluacionVista::de).toList();
    }

    @GetMapping("/desastres/{id}/seguimiento")
    public ConsolidadoVista seguimiento(@PathVariable UUID id, @RequestHeader("X-Usuario") String usuario,
                                        @RequestHeader("X-Rol") String rol,
                                        @RequestHeader("X-Organizacion") String org) {
        return ConsolidadoVista.de(consultar.consolidado(actor(usuario, rol, org), id));
    }

    @GetMapping("/desastres/{id}/auditoria")
    public List<AuditoriaVista> auditoriaDesastre(@PathVariable UUID id, @RequestHeader("X-Usuario") String usuario,
                                                  @RequestHeader("X-Rol") String rol,
                                                  @RequestHeader("X-Organizacion") String org) {
        return consultar.auditoriaDeDesastre(actor(usuario, rol, org), id).stream().map(AuditoriaVista::de).toList();
    }

    // ---- Proyectos (CU-02 a CU-08) ----

    @PostMapping("/desastres/{id}/proyectos")
    public List<ResultadoLoteVista> crear(@PathVariable UUID id, @RequestBody SolicitudLote lote,
                                          @RequestHeader("X-Usuario") String usuario,
                                          @RequestHeader("X-Rol") String rol,
                                          @RequestHeader("X-Organizacion") String org) {
        List<CrearProyectos.Solicitud> solicitudes = lote.proyectos() == null ? List.of() : lote.proyectos().stream()
                .map(s -> new CrearProyectos.Solicitud(s.nombre(),
                        s.tipo() == null || s.tipo().isBlank() ? null : TipoProyecto.valueOf(s.tipo()),
                        s.evaluaciones() == null ? List.of() : s.evaluaciones().stream().map(UUID::fromString).toList()))
                .toList();
        return crearProyectos.crearLote(actor(usuario, rol, org), id, solicitudes).stream()
                .map(ResultadoLoteVista::de).toList();
    }

    @GetMapping("/proyectos/{id}")
    public ProyectoVista proyecto(@PathVariable UUID id, @RequestHeader("X-Usuario") String usuario,
                                  @RequestHeader("X-Rol") String rol, @RequestHeader("X-Organizacion") String org) {
        return ProyectoVista.de(consultar.proyecto(actor(usuario, rol, org), id));
    }

    @PostMapping("/proyectos/{id}/aprobar")
    public ProyectoVista aprobar(@PathVariable UUID id, @RequestBody SolicitudAprobar s,
                                 @RequestHeader("X-Usuario") String usuario, @RequestHeader("X-Rol") String rol,
                                 @RequestHeader("X-Organizacion") String org) {
        return ProyectoVista.de(aprobarProyecto.ejecutar(actor(usuario, rol, org), id, s.presupuesto(),
                s.fuenteFinanciacion(), s.responsable()));
    }

    @PostMapping("/proyectos/{id}/iniciar")
    public ProyectoVista iniciar(@PathVariable UUID id, @RequestBody SolicitudIniciar s,
                                 @RequestHeader("X-Usuario") String usuario, @RequestHeader("X-Rol") String rol,
                                 @RequestHeader("X-Organizacion") String org) {
        return ProyectoVista.de(iniciarEjecucion.ejecutar(actor(usuario, rol, org), id, s.contratista(),
                fecha(s.inicio()), fecha(s.fin())));
    }

    @PostMapping("/proyectos/{id}/presupuesto")
    public ProyectoVista presupuesto(@PathVariable UUID id, @RequestBody SolicitudPresupuesto s,
                                     @RequestHeader("X-Usuario") String usuario, @RequestHeader("X-Rol") String rol,
                                     @RequestHeader("X-Organizacion") String org) {
        return ProyectoVista.de(modificarPresupuesto.ejecutar(actor(usuario, rol, org), id, s.presupuesto(), s.motivo()));
    }

    @PostMapping("/proyectos/{id}/avance")
    public ProyectoVista avance(@PathVariable UUID id, @RequestBody SolicitudAvance s,
                                @RequestHeader("X-Usuario") String usuario, @RequestHeader("X-Rol") String rol,
                                @RequestHeader("X-Organizacion") String org) {
        if (s.porcentaje() == null) throw new IllegalArgumentException("Falta el porcentaje de avance.");
        return ProyectoVista.de(registrarSeguimiento.avance(actor(usuario, rol, org), id, s.porcentaje(),
                s.descripcion(), s.motivoCorreccion()));
    }

    @PostMapping("/proyectos/{id}/costo")
    public ProyectoVista costo(@PathVariable UUID id, @RequestBody SolicitudCosto s,
                               @RequestHeader("X-Usuario") String usuario, @RequestHeader("X-Rol") String rol,
                               @RequestHeader("X-Organizacion") String org) {
        return ProyectoVista.de(registrarSeguimiento.costo(actor(usuario, rol, org), id, s.valor(), s.descripcion()));
    }

    @PostMapping("/proyectos/{id}/evidencia")
    public ProyectoVista evidencia(@PathVariable UUID id, @RequestBody SolicitudEvidencia s,
                                   @RequestHeader("X-Usuario") String usuario, @RequestHeader("X-Rol") String rol,
                                   @RequestHeader("X-Organizacion") String org) {
        return ProyectoVista.de(registrarSeguimiento.evidencia(actor(usuario, rol, org), id, s.descripcion()));
    }

    @PostMapping("/proyectos/{id}/estado")
    public ProyectoVista estado(@PathVariable UUID id, @RequestBody SolicitudEstado s,
                                @RequestHeader("X-Usuario") String usuario, @RequestHeader("X-Rol") String rol,
                                @RequestHeader("X-Organizacion") String org) {
        if (s.estado() == null) throw new IllegalArgumentException("Falta el estado de destino.");
        return ProyectoVista.de(cambiarEstado.ejecutar(actor(usuario, rol, org), id,
                EstadoProyecto.valueOf(s.estado()), s.motivo()));
    }

    @GetMapping("/proyectos/{id}/auditoria")
    public List<AuditoriaVista> auditoria(@PathVariable UUID id, @RequestHeader("X-Usuario") String usuario,
                                          @RequestHeader("X-Rol") String rol,
                                          @RequestHeader("X-Organizacion") String org) {
        return consultar.auditoriaDeProyecto(actor(usuario, rol, org), id).stream().map(AuditoriaVista::de).toList();
    }

    private static LocalDate fecha(String texto) {
        return texto == null || texto.isBlank() ? null : LocalDate.parse(texto);
    }
}
