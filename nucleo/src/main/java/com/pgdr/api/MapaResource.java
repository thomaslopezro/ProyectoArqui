package com.pgdr.api;

import com.pgdr.application.AfectacionService;
import com.pgdr.application.Identidad;
import jakarta.ejb.EJB;
import jakarta.ws.rs.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.*;
import java.util.*;

@Path("/mapa/afectaciones") @Produces(MediaType.APPLICATION_JSON)
public class MapaResource {
    @EJB private AfectacionService service;
    @Context private HttpServletRequest req;
    @GET
    public Response puntos(@QueryParam("desastreId") String desastreId,@QueryParam("limit") @DefaultValue("200") int limit){
      if(desastreId==null||limit<1||limit>200)return Response.status(400).entity(Map.of("error","PARAMETROS_INVALIDOS")).build();
      try{
        Identidad actor=(Identidad)req.getAttribute("pgdr.identidad");
        var puntos=service.listar(actor,UUID.fromString(desastreId),limit).stream().map(a->Map.of(
          "latitud",(Object)a.getLatitud(),"longitud",a.getLongitud(),"nivelAfectacion",a.getNivelAfectacion(),"id",a.getId().toString())).toList();
        return Response.ok(Map.of("desastreId",desastreId,"puntos",puntos)).build();
      }catch(IllegalArgumentException e){return Response.status(400).entity(Map.of("error","ID_INVALIDO")).build();}
    }
}
