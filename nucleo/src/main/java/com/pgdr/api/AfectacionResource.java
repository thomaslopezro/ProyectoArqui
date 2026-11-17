package com.pgdr.api;

import com.pgdr.application.AfectacionService;
import com.pgdr.application.Identidad;
import com.pgdr.domain.Afectacion;
import jakarta.ejb.EJB;
import jakarta.ws.rs.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.*;
import java.util.*;

@Path("/afectaciones") @Produces(MediaType.APPLICATION_JSON)
public class AfectacionResource {
    @EJB private AfectacionService service;
    @Context private HttpServletRequest req;
    private Identidad identidad(){return (Identidad)req.getAttribute("pgdr.identidad");}
    @POST @Consumes(MediaType.APPLICATION_JSON)
    public Response registrar(AfectacionRequest r){
      if(!identidad().puedeRegistrarAfectacion())return Response.status(403).entity(Map.of("error","SIN_PERMISO")).build();
      try{
        AfectacionService.Resultado x=service.registrar(identidad(),r);
        if(x.desastreInexistente())return Response.status(404).entity(Map.of("error","DESASTRE_NO_ENCONTRADO")).build();
        if(x.conflicto())return Response.status(409).entity(Map.of("error","SOLICITUD_ID_REUTILIZADA_CON_OTRO_CONTENIDO")).build();
        return Response.status(x.creado()?201:200).entity(ver(x.afectacion())).build();
      }catch(com.pgdr.application.ValidacionException e){return Response.status(400).entity(Map.of("error","DATOS_INVALIDOS","detalle",e.getMessage())).build();}
    }
    @GET
    public Response listar(@QueryParam("desastreId") String desastreId,@QueryParam("limit") @DefaultValue("200") int limit){
      if(desastreId==null||limit<1||limit>200)return Response.status(400).entity(Map.of("error","PARAMETROS_INVALIDOS")).build();
      try{return Response.ok(service.listar(identidad(),UUID.fromString(desastreId),limit).stream().map(AfectacionResource::ver).toList()).build();}
      catch(IllegalArgumentException e){return Response.status(400).entity(Map.of("error","ID_INVALIDO")).build();}
    }
    public static Map<String,Object> ver(Afectacion a){
      Map<String,Object> m=new LinkedHashMap<>();
      m.put("id",a.getId().toString());m.put("desastreId",a.getDesastreId().toString());m.put("solicitudId",a.getSolicitudId().toString());
      m.put("latitud",a.getLatitud());m.put("longitud",a.getLongitud());m.put("nivelAfectacion",a.getNivelAfectacion());
      m.put("observacion",a.getObservacion());m.put("creadoEn",a.getCreadoEn().toString());return m;
    }
}
