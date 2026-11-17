package com.pgdr.api;
import com.pgdr.application.*;
import com.pgdr.domain.SolicitudRecurso;
import jakarta.ejb.EJB;
import jakarta.json.*;
import co.edu.javeriana.emergencias.dominio.modelo.Prioridad;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;

@Path("/solicitudes") @Produces(MediaType.APPLICATION_JSON) @Consumes(MediaType.APPLICATION_JSON)
public class SolicitudesResource {
 @EJB SolicitudesProyecto service;
 @Context HttpServletRequest req;
 Identidad actor(){return (Identidad)req.getAttribute("pgdr.identidad");}
 @POST @Path("desde-evaluacion") public Response desdeEvaluacion(JsonObject body,@HeaderParam("X-Integracion-Key") String key){
  if(key==null || !key.equals(System.getenv("INTEGRACION_KEY")))return Response.status(403).build();
  return crearInterno(body,true);
 }
 @POST public Response crear(JsonObject body){return crearInterno(body,false);}
 private Response crearInterno(JsonObject body,boolean desdeEvaluacion){
  if(!actor().puedeRegistrarAfectacion())return Response.status(403).build();
  if(body==null)throw new BadRequestException("Datos requeridos");
  SolicitudesProyecto.Nueva input;
  try{
   input=new SolicitudesProyecto.Nueva(
    body.isNull("solicitudId")?null:UUID.fromString(body.getString("solicitudId","")),
    body.getString("eventoId",null),body.getString("tipoRecurso",null),
    !body.containsKey("cantidad") || body.isNull("cantidad")?null:body.getJsonNumber("cantidad").intValueExact(),
    !body.containsKey("prioridad") || body.isNull("prioridad")?null:Prioridad.valueOf(body.getString("prioridad")));
  }catch(ClassCastException | IllegalArgumentException | ArithmeticException | NullPointerException e){
   throw new BadRequestException("Identificador, cantidad o prioridad inválidos");
  }
  var r=desdeEvaluacion?service.desdeEvaluacion(actor(),input,UUID.fromString(body.getString("evaluacionId")),body.getJsonNumber("evaluacionVersion").longValueExact(),body.getJsonObject("evaluacionResumen").toString()):service.crear(actor(),input);return Response.status(r.creada()?201:200).entity(ver(r.solicitud())).build();
 }
 @GET public List<?> listar(@QueryParam("eventoId") String evento){return service.listar(actor(),false,evento).stream().map(this::ver).toList();}
 @GET @Path("pendientes") public List<?> pendientes(@QueryParam("eventoId") String evento){return service.listar(actor(),true,evento).stream().map(this::ver).toList();}
 @GET @Path("por-clave/{clave}") public Response porClave(@PathParam("clave") String clave){
  UUID id;try{id=UUID.fromString(clave);}catch(IllegalArgumentException e){throw new BadRequestException();}
  var s=service.porClave(actor(),id);return s.isPresent()?Response.ok(ver(s.get())).build():Response.status(404).build();
 }
 @GET @Path("{id}") public Map<String,Object> buscar(@PathParam("id") String id){return ver(service.buscar(actor(),id(id)));}
 @GET @Path("{id}/historial") public List<?> historial(@PathParam("id") String id){return service.historial(actor(),id(id));}
 @PATCH @Path("{id}/atender") public Response atender(@PathParam("id") String id){
  if(!actor().puedeRegistrarDesastre())return Response.status(403).build();
  return Response.ok(ver(service.atender(actor(),id(id)))).build();
 }
 Long id(String raw){try{return Long.valueOf(raw);}catch(NumberFormatException e){throw new BadRequestException("ID inválido");}}
 Map<String,Object> ver(SolicitudRecurso s){var m=new LinkedHashMap<String,Object>();
  m.put("evaluacionId",s.evaluacionId==null?null:s.evaluacionId.toString());m.put("evaluacionVersion",s.evaluacionVersion);m.put("evaluacionResumen",s.evaluacionResumen);
  m.put("id",s.id);m.put("solicitudId",s.solicitudId.toString());m.put("eventoId",service.referencia(s.desastreId));
  m.put("descripcionDesastre",s.descripcionDesastre);m.put("tipoRecurso",s.tipoRecurso);m.put("cantidad",s.cantidad);
  m.put("prioridad",s.prioridad);m.put("estado",s.estado);m.put("creadaEn",s.creadaEn.toString());m.put("creadaPor",s.creadaPor.toString());
  m.put("atendidaEn",s.atendidaEn==null?null:s.atendidaEn.toString());m.put("atendidaPor",s.atendidaPor==null?null:s.atendidaPor.toString());return m;
 }
}
