package com.pgdr.api;
import com.pgdr.application.*;
import jakarta.ejb.EJB;import jakarta.json.*;import jakarta.ws.rs.*;import jakarta.ws.rs.core.*;import jakarta.servlet.http.HttpServletRequest;import java.util.*;
@Path("/eventos") @Produces(MediaType.APPLICATION_JSON) @Consumes(MediaType.APPLICATION_JSON)
public class EvolucionDesastresResource {
 @EJB RegistroIntegrado registro;@Context HttpServletRequest req;
 Identidad actor(){return (Identidad)req.getAttribute("pgdr.identidad");}
 @GET @Path("{id}/historial") public JsonArray historial(@PathParam("id") String id){return registro.historial(actor(),id);}
 @PATCH @Path("{id}/gravedad") public Response cambiar(@PathParam("id") String id,JsonObject b){
  if(!actor().puedeRegistrarDesastre())return Response.status(403).build();
  try{return Response.ok(registro.cambiar(actor(),id,UUID.fromString(b.getString("cambioId")),b.getJsonNumber("versionBase").longValueExact(),b.getString("severidad"),b.getString("motivo"))).build();}
  catch(RegistroIntegrado.CambioConflicto e){return Response.status(409).entity(Map.of("error","VERSION_O_CAMBIO_EN_CONFLICTO")).build();}
  catch(ValidacionException | IllegalArgumentException | NullPointerException | ClassCastException | ArithmeticException e){return Response.status(400).entity(Map.of("error","Datos del cambio inválidos")).build();}
 }
 @PATCH @Path("{id}/fase") public Response cambiarFase(@PathParam("id") String id,JsonObject b){
  if(!actor().puedeRegistrarDesastre())return Response.status(403).build();
  try{return Response.ok(registro.cambiarFase(actor(),id,UUID.fromString(b.getString("cambioId")),b.getJsonNumber("versionBase").longValueExact(),b.getString("fase"),b.getString("motivo"))).build();}
  catch(RegistroIntegrado.CambioConflicto e){return Response.status(409).entity(Map.of("error","VERSION_O_CAMBIO_EN_CONFLICTO")).build();}
  catch(ValidacionException | IllegalArgumentException | NullPointerException | ClassCastException | ArithmeticException e){return Response.status(400).entity(Map.of("error",e.getMessage()==null?"Datos del cambio inválidos":e.getMessage())).build();}
 }
}
