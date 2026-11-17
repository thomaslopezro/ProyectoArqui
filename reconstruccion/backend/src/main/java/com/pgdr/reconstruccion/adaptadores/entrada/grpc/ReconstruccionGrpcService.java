package com.pgdr.reconstruccion.adaptadores.entrada.grpc;

import com.pgdr.reconstruccion.aplicacion.casosdeuso.AccesoDenegadoException;
import com.pgdr.reconstruccion.aplicacion.casosdeuso.AprobarProyecto;
import com.pgdr.reconstruccion.aplicacion.casosdeuso.CambiarEstado;
import com.pgdr.reconstruccion.aplicacion.casosdeuso.CerrarDesastre;
import com.pgdr.reconstruccion.aplicacion.casosdeuso.ConsultarSeguimiento;
import com.pgdr.reconstruccion.aplicacion.casosdeuso.CrearProyectos;
import com.pgdr.reconstruccion.aplicacion.casosdeuso.IniciarEjecucion;
import com.pgdr.reconstruccion.aplicacion.casosdeuso.IniciarReconstruccion;
import com.pgdr.reconstruccion.aplicacion.casosdeuso.ModificarPresupuesto;
import com.pgdr.reconstruccion.aplicacion.casosdeuso.NoEncontradoException;
import com.pgdr.reconstruccion.aplicacion.casosdeuso.RegistrarSeguimiento;
import com.pgdr.reconstruccion.aplicacion.puertos.ConflictoDeVersionException;
import com.pgdr.reconstruccion.adaptadores.plataforma.SesionActual;
import com.pgdr.reconstruccion.dominio.Actor;
import com.pgdr.reconstruccion.dominio.Desastre;
import com.pgdr.reconstruccion.dominio.EstadoProyecto;
import com.pgdr.reconstruccion.dominio.Proyecto;
import com.pgdr.reconstruccion.dominio.RegistroAuditoria;
import com.pgdr.reconstruccion.dominio.ReglaVioladaException;
import com.pgdr.reconstruccion.dominio.Rol;
import com.pgdr.reconstruccion.dominio.Seguimiento;
import com.pgdr.reconstruccion.dominio.TipoProyecto;
import com.pgdr.reconstruccion.grpc.AdjuntarEvidenciaRequest;
import com.pgdr.reconstruccion.grpc.AprobarProyectoRequest;
import com.pgdr.reconstruccion.grpc.AuditoriaMsg;
import com.pgdr.reconstruccion.grpc.AuditoriaResponse;
import com.pgdr.reconstruccion.grpc.CambiarEstadoRequest;
import com.pgdr.reconstruccion.grpc.ConsultarAuditoriaRequest;
import com.pgdr.reconstruccion.grpc.CrearLoteRequest;
import com.pgdr.reconstruccion.grpc.CrearLoteResponse;
import com.pgdr.reconstruccion.grpc.CrearProyectoRequest;
import com.pgdr.reconstruccion.grpc.DesastreMsg;
import com.pgdr.reconstruccion.grpc.DesastreRequest;
import com.pgdr.reconstruccion.grpc.Identidad;
import com.pgdr.reconstruccion.grpc.IniciarEjecucionRequest;
import com.pgdr.reconstruccion.grpc.ModificarPresupuestoRequest;
import com.pgdr.reconstruccion.grpc.NuevoProyecto;
import com.pgdr.reconstruccion.grpc.ProyectoMsg;
import com.pgdr.reconstruccion.grpc.ReconstruccionServiceGrpc;
import com.pgdr.reconstruccion.grpc.RegistrarAvanceRequest;
import com.pgdr.reconstruccion.grpc.RegistrarCostoRequest;
import com.pgdr.reconstruccion.grpc.ResultadoLote;
import com.pgdr.reconstruccion.grpc.SeguimientoMsg;
import com.pgdr.reconstruccion.grpc.SeguimientoResponse;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import net.devh.boot.grpc.server.service.GrpcService;

/**
 * Adaptador de entrada gRPC: el contrato con el que los demás módulos de la plataforma usan
 * Reconstrucción. Solo traduce mensajes a casos de uso; no contiene reglas de negocio.
 */
@GrpcService
public class ReconstruccionGrpcService extends ReconstruccionServiceGrpc.ReconstruccionServiceImplBase {
    private final IniciarReconstruccion iniciarReconstruccion;
    private final CrearProyectos crearProyectos;
    private final AprobarProyecto aprobarProyecto;
    private final IniciarEjecucion iniciarEjecucion;
    private final RegistrarSeguimiento registrarSeguimiento;
    private final ModificarPresupuesto modificarPresupuesto;
    private final CambiarEstado cambiarEstado;
    private final ConsultarSeguimiento consultar;
    private final CerrarDesastre cerrarDesastre;

    public ReconstruccionGrpcService(IniciarReconstruccion iniciarReconstruccion, CrearProyectos crearProyectos,
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

    @Override
    public void iniciarReconstruccion(DesastreRequest r, StreamObserver<DesastreMsg> o) {
        responder(o, () -> desastre(iniciarReconstruccion.ejecutar(actor(r.getIdentidad()), uuid(r.getDesastreId()))));
    }

    @Override
    public void crearProyecto(CrearProyectoRequest r, StreamObserver<ProyectoMsg> o) {
        responder(o, () -> proyecto(crearProyectos.crearUno(actor(r.getIdentidad()), uuid(r.getDesastreId()),
                solicitud(r.getProyecto()))));
    }

    @Override
    public void crearProyectosEnLote(CrearLoteRequest r, StreamObserver<CrearLoteResponse> o) {
        responder(o, () -> {
            List<CrearProyectos.Solicitud> solicitudes = r.getProyectosList().stream().map(this::solicitud).toList();
            CrearLoteResponse.Builder respuesta = CrearLoteResponse.newBuilder();
            for (CrearProyectos.Resultado x : crearProyectos.crearLote(actor(r.getIdentidad()),
                    uuid(r.getDesastreId()), solicitudes))
                respuesta.addResultados(ResultadoLote.newBuilder().setIndice(x.indice()).setNombre(texto(x.nombre()))
                        .setCreado(x.creado()).setProyectoId(x.creado() ? x.proyecto().id().toString() : "")
                        .setRegla(texto(x.regla())).setError(texto(x.error())).build());
            return respuesta.build();
        });
    }

    @Override
    public void aprobarProyecto(AprobarProyectoRequest r, StreamObserver<ProyectoMsg> o) {
        responder(o, () -> proyecto(aprobarProyecto.ejecutar(actor(r.getIdentidad()), uuid(r.getProyectoId()),
                dinero(r.getPresupuesto()), r.getFuenteFinanciacion(), r.getResponsable())));
    }

    @Override
    public void iniciarEjecucion(IniciarEjecucionRequest r, StreamObserver<ProyectoMsg> o) {
        responder(o, () -> proyecto(iniciarEjecucion.ejecutar(actor(r.getIdentidad()), uuid(r.getProyectoId()),
                r.getContratista(), fecha(r.getInicio()), fecha(r.getFin()))));
    }

    @Override
    public void registrarAvance(RegistrarAvanceRequest r, StreamObserver<ProyectoMsg> o) {
        responder(o, () -> proyecto(registrarSeguimiento.avance(actor(r.getIdentidad()), uuid(r.getProyectoId()),
                r.getPorcentaje(), r.getDescripcion(), r.getMotivoCorreccion())));
    }

    @Override
    public void registrarCosto(RegistrarCostoRequest r, StreamObserver<ProyectoMsg> o) {
        responder(o, () -> proyecto(registrarSeguimiento.costo(actor(r.getIdentidad()), uuid(r.getProyectoId()),
                dinero(r.getValor()), r.getDescripcion())));
    }

    @Override
    public void adjuntarEvidencia(AdjuntarEvidenciaRequest r, StreamObserver<ProyectoMsg> o) {
        responder(o, () -> proyecto(registrarSeguimiento.evidencia(actor(r.getIdentidad()), uuid(r.getProyectoId()),
                r.getDescripcion())));
    }

    @Override
    public void modificarPresupuesto(ModificarPresupuestoRequest r, StreamObserver<ProyectoMsg> o) {
        responder(o, () -> proyecto(modificarPresupuesto.ejecutar(actor(r.getIdentidad()), uuid(r.getProyectoId()),
                dinero(r.getPresupuesto()), r.getMotivo())));
    }

    @Override
    public void cambiarEstado(CambiarEstadoRequest r, StreamObserver<ProyectoMsg> o) {
        responder(o, () -> proyecto(cambiarEstado.ejecutar(actor(r.getIdentidad()), uuid(r.getProyectoId()),
                EstadoProyecto.valueOf(r.getEstado()), r.getMotivo())));
    }

    @Override
    public void consultarSeguimiento(DesastreRequest r, StreamObserver<SeguimientoResponse> o) {
        responder(o, () -> {
            ConsultarSeguimiento.Consolidado c = consultar.consolidado(actor(r.getIdentidad()), uuid(r.getDesastreId()));
            SeguimientoResponse.Builder respuesta = SeguimientoResponse.newBuilder().setDesastre(desastre(c.desastre()))
                    .setPresupuestoTotal(c.presupuestoTotal().toPlainString())
                    .setCostoTotal(c.costoTotal().toPlainString()).setAvancePromedio(c.avancePromedio())
                    .setConSobrecosto(c.conSobrecosto());
            c.proyectos().forEach(p -> respuesta.addProyectos(proyecto(p)));
            c.porEstado().forEach((estado, cantidad) -> respuesta.putPorEstado(estado.name(), cantidad));
            return respuesta.build();
        });
    }

    @Override
    public void consultarAuditoria(ConsultarAuditoriaRequest r, StreamObserver<AuditoriaResponse> o) {
        responder(o, () -> {
            Actor actor = actor(r.getIdentidad());
            List<RegistroAuditoria> registros = r.getProyectoId().isBlank()
                    ? consultar.auditoriaDeDesastre(actor, uuid(r.getDesastreId()))
                    : consultar.auditoriaDeProyecto(actor, uuid(r.getProyectoId()));
            AuditoriaResponse.Builder respuesta = AuditoriaResponse.newBuilder();
            for (RegistroAuditoria a : registros)
                respuesta.addRegistros(AuditoriaMsg.newBuilder().setMomento(a.momento().toString())
                        .setUsuario(a.usuario()).setRol(a.rol().name()).setOrganizacion(a.organizacion())
                        .setOperacion(a.operacion()).setEntidad(a.entidad())
                        .setEntidadId(a.entidadId() == null ? "" : a.entidadId().toString())
                        .setValorAnterior(texto(a.valorAnterior())).setValorNuevo(texto(a.valorNuevo()))
                        .setMotivo(texto(a.motivo())).setResultado(a.resultado().name())
                        .setDetalle(texto(a.detalle())).build());
            return respuesta.build();
        });
    }

    @Override
    public void cerrarDesastre(DesastreRequest r, StreamObserver<DesastreMsg> o) {
        responder(o, () -> desastre(cerrarDesastre.ejecutar(actor(r.getIdentidad()), uuid(r.getDesastreId()))));
    }

    // ---- traducción de errores a estados gRPC ----

    private static <T> void responder(StreamObserver<T> observador, Supplier<T> trabajo) {
        try {
            observador.onNext(trabajo.get());
            observador.onCompleted();
        } catch (ReglaVioladaException e) {
            observador.onError(Status.FAILED_PRECONDITION.withDescription(e.regla() + ": " + e.getMessage()).asRuntimeException());
        } catch (AccesoDenegadoException e) {
            observador.onError(Status.PERMISSION_DENIED.withDescription(e.getMessage()).asRuntimeException());
        } catch (NoEncontradoException e) {
            observador.onError(Status.NOT_FOUND.withDescription(e.getMessage()).asRuntimeException());
        } catch (ConflictoDeVersionException e) {
            observador.onError(Status.ABORTED.withDescription(e.getMessage()).asRuntimeException());
        } catch (IllegalArgumentException | DateTimeParseException e) {
            observador.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
        } catch (RuntimeException e) {
            observador.onError(Status.INTERNAL.withDescription("Error interno del módulo de Reconstrucción.").asRuntimeException());
        }
    }

    // ---- traducción de mensajes ----

    private static Actor actor(Identidad i) {
        // En la plataforma integrada la identidad es la de la sesión validada por el interceptor.
        SesionActual.Sesion sesion = SesionActual.actual();
        if (sesion != null) return new Actor(sesion.usuario(), Rol.valueOf(sesion.rol()), sesion.organizacion());
        return new Actor(i.getUsuario(), Rol.valueOf(i.getRol().trim().toUpperCase()), i.getOrganizacion());
    }

    private CrearProyectos.Solicitud solicitud(NuevoProyecto n) {
        return new CrearProyectos.Solicitud(n.getNombre(), n.getTipo().isBlank() ? null : TipoProyecto.valueOf(n.getTipo()),
                n.getEvaluacionesList().stream().map(UUID::fromString).toList());
    }

    private static UUID uuid(String s) {
        return UUID.fromString(s);
    }

    private static BigDecimal dinero(String s) {
        return s == null || s.isBlank() ? null : new BigDecimal(s);
    }

    private static LocalDate fecha(String s) {
        return s == null || s.isBlank() ? null : LocalDate.parse(s);
    }

    private static String texto(String s) {
        return s == null ? "" : s;
    }

    private static DesastreMsg desastre(Desastre d) {
        return DesastreMsg.newBuilder().setId(d.id().toString()).setNombre(d.nombre()).setEstado(d.estado().name()).build();
    }

    private static ProyectoMsg proyecto(Proyecto p) {
        ProyectoMsg.Builder m = ProyectoMsg.newBuilder().setId(p.id().toString())
                .setDesastreId(p.desastreId().toString()).setOrganizacion(p.organizacion()).setNombre(p.nombre())
                .setTipo(p.tipo().name()).setEstado(p.estado().name())
                .setPresupuesto(p.presupuesto() == null ? "" : p.presupuesto().toPlainString())
                .setFuenteFinanciacion(texto(p.fuenteFinanciacion())).setResponsable(texto(p.responsable()))
                .setContratista(texto(p.contratista()))
                .setInicioPlan(p.inicioPlan() == null ? "" : p.inicioPlan().toString())
                .setFinPlan(p.finPlan() == null ? "" : p.finPlan().toString()).setAvance(p.avance())
                .setCostoAcumulado(p.costoAcumulado().toPlainString()).setSobrecosto(p.tieneSobrecosto())
                .setVersion(p.version());
        p.evaluaciones().forEach(e -> m.addEvaluaciones(e.toString()));
        for (Seguimiento s : p.seguimiento())
            m.addSeguimiento(SeguimientoMsg.newBuilder().setTipo(s.tipo().name())
                    .setValor(s.valor() == null ? "" : s.valor().toPlainString()).setDescripcion(texto(s.descripcion()))
                    .setMomento(s.momento().toString()).setUsuario(s.usuario()).build());
        return m.build();
    }
}
