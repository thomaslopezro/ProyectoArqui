package com.pgdr.reconstruccion.adaptadores.entrada.grpc;

import com.pgdr.reconstruccion.adaptadores.plataforma.NoDisponibleException;
import com.pgdr.reconstruccion.adaptadores.plataforma.SesionActual;
import com.pgdr.reconstruccion.adaptadores.plataforma.ValidadorSesion;
import io.grpc.ForwardingServerCallListener;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * En la plataforma integrada, toda llamada gRPC debe traer el token en el metadato
 * "authorization". Se valida contra el núcleo y la identidad sale de esa respuesta, no del
 * campo identidad del mensaje.
 */
@GrpcGlobalServerInterceptor
@ConditionalOnProperty(name = "reconstruccion.integracion.habilitada", havingValue = "true")
public class InterceptorSesion implements ServerInterceptor {

    private static final Metadata.Key<String> AUTORIZACION =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);

    private final ValidadorSesion validador;

    public InterceptorSesion(ValidadorSesion validador) {
        this.validador = validador;
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> llamada, Metadata metadatos,
                                                                 ServerCallHandler<ReqT, RespT> siguiente) {
        String cabecera = metadatos.get(AUTORIZACION);
        String token = cabecera != null && cabecera.startsWith("Bearer ") ? cabecera.substring(7).trim() : null;
        SesionActual.Sesion sesion;
        try {
            sesion = validador.validar(token);
        } catch (NoDisponibleException e) {
            llamada.close(Status.UNAVAILABLE.withDescription("El servicio de identidad no está disponible."),
                    new Metadata());
            return new ServerCall.Listener<ReqT>() {};
        }
        if (sesion == null) {
            llamada.close(Status.UNAUTHENTICATED.withDescription("Se requiere una sesión válida de la plataforma."),
                    new Metadata());
            return new ServerCall.Listener<ReqT>() {};
        }
        final SesionActual.Sesion validada = sesion;
        ServerCall.Listener<ReqT> escucha = siguiente.startCall(llamada, metadatos);
        return new ForwardingServerCallListener.SimpleForwardingServerCallListener<ReqT>(escucha) {
            @Override
            public void onHalfClose() {
                SesionActual.fijar(validada);
                try {
                    super.onHalfClose();
                } finally {
                    SesionActual.limpiar();
                }
            }
        };
    }
}
