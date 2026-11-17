
package com.pgdr.api;

import com.pgdr.application.AuthService;
import com.pgdr.application.Identidad;

import jakarta.annotation.Priority;
import jakarta.ejb.EJB;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

import java.io.IOException;
import java.util.Map;

@Provider
@Priority(Priorities.AUTHENTICATION)
public class TokenAuthFilter implements ContainerRequestFilter {

    @EJB
    private AuthService auth;

    @Override
    public void filter(ContainerRequestContext requestContext)
            throws IOException {
              
String ruta = requestContext.getUriInfo()
    .getRequestUri()
    .getPath();

String metodo = requestContext.getMethod();

// Rutas publicas exactas
boolean esHealth =
    "GET".equals(metodo) &&
    "/plataforma-desastres/api/health".equals(ruta);

boolean esLogin =
    "POST".equals(metodo) &&
    "/plataforma-desastres/api/auth/login".equals(ruta);

System.out.println(
    "[PGDR-AUTH] Metodo: " + metodo +
    " | Ruta: " + ruta +
    " | Publica: " + (esHealth || esLogin)
);

boolean interna = ruta.startsWith("/plataforma-desastres/api/integracion/") &&
    !ruta.endsWith("/registro");
if (interna) {
 String key=System.getenv("INTEGRACION_KEY");
 if(key==null || !key.equals(requestContext.getHeaderString("X-Integracion-Key")))
   requestContext.abortWith(Response.status(401).entity(Map.of("error","SERVICIO_NO_AUTORIZADO")).build());
 return;
}
if (esHealth || esLogin) {
    return;
}


        // Obtener encabezado Authorization
        String header = requestContext.getHeaderString("Authorization");

        if (header == null || !header.startsWith("Bearer ")) {
            requestContext.abortWith(
                Response.status(Response.Status.UNAUTHORIZED)
                    .entity(Map.of("error", "TOKEN_REQUERIDO"))
                    .build()
            );
            return;
        }

        // Validar token y recuperar identidad
        Identidad identidad = auth.resolver(
            header.substring(7).trim()
        );

        if (identidad == null) {
            requestContext.abortWith(
                Response.status(Response.Status.UNAUTHORIZED)
                    .entity(Map.of(
                        "error",
                        "TOKEN_INVALIDO_O_EXPIRADO"
                    ))
                    .build()
            );
            return;
        }

        // Compartir identidad con los recursos REST
        requestContext.setProperty(
            "pgdr.identidad",
            identidad
        );
    }
}
