package com.pgdr.api;
import com.pgdr.application.*;
import jakarta.ejb.EJB;
import jakarta.json.*;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

@Path("/integracion") @Produces(MediaType.APPLICATION_JSON) @Consumes(MediaType.APPLICATION_JSON)
public class IntegracionResource {
 @EJB RegistroIntegrado registro;
 @Context HttpServletRequest request;
 @GET @Path("/catalogo") public JsonArray catalogo(){return registro.catalogo();}
 @GET @Path("/organizacion-legado") public Map<String,String> org(){return Map.of("organizacionId",registro.migrador().organizacionId().toString());}
 @POST @Path("/importar") public Response importar(JsonObject body){return crear(body,registro.migrador());}
 @POST @Path("/registro") public Response crear(JsonObject body){
  Identidad actor=(Identidad)request.getAttribute("pgdr.identidad");
  if(!actor.puedeRegistrarDesastre())return Response.status(403).entity(Map.of("error","SIN_PERMISO")).build();
  return crear(body,actor);
 }
 private Response crear(JsonObject body,Identidad actor){
  try{var r=registro.registrar(actor,body);return Response.status(r.conflicto()?409:r.creado()?201:200).entity(r.registro()).build();}
  catch(RegistroIntegrado.ReferenciaOcupada e){return Response.status(409).entity(Map.of("error","REFERENCIA_OCUPADA")).build();}
  catch(ValidacionException e){return Response.status(400).entity(Map.of("error",e.getMessage())).build();}
 }
}
