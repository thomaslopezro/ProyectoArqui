package co.edu.javeriana.emergencias.infraestructura.entrada;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import jakarta.ws.rs.ext.*;
import java.util.Map;
import java.util.logging.Logger;

@Provider
public class ErroresMapper implements ExceptionMapper<Exception> {
    @Override public Response toResponse(Exception error) {
        if (error instanceof WebApplicationException web) {
            return respuesta(web.getResponse().getStatus(), web.getMessage());
        }
        for (Throwable causa = error; causa != null; causa = causa.getCause()) {
            if (causa instanceof IllegalArgumentException) return respuesta(400, causa.getMessage());
        }
        Logger.getLogger(getClass().getName()).warning("Operacion pendiente por fallo de dependencia: " + error.getClass().getSimpleName());
        return respuesta(503, "Servicio temporalmente no disponible; reintente conservando el mismo ID");
    }
    private Response respuesta(int estado, String mensaje) {
        return Response.status(estado).type(MediaType.APPLICATION_JSON)
                .entity(Map.of("error", mensaje == null ? "Peticion no valida" : mensaje)).build();
    }
}
