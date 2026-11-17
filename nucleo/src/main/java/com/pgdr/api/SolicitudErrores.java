package com.pgdr.api;
import co.edu.javeriana.emergencias.aplicacion.excepcion.*;
import com.pgdr.infrastructure.SolicitudRepositorioJpa;
import jakarta.ws.rs.ext.*;
import jakarta.ws.rs.core.*;
import java.util.Map;
@Provider
public class SolicitudErrores implements ExceptionMapper<ReglaNegocioException>{
 public Response toResponse(ReglaNegocioException e){int status=e instanceof SolicitudNoEncontradaException?404:e instanceof SolicitudYaAtendidaException || e instanceof SolicitudRepositorioJpa.ConflictoSolicitud?409:400;
 return Response.status(status).entity(Map.of("error",e.getMessage())).build();}
}
