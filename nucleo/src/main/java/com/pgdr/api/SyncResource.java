package com.pgdr.api;

import com.pgdr.application.AfectacionService;
import com.pgdr.application.Identidad;
import jakarta.ejb.EJB;
import jakarta.ws.rs.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.*;
import java.util.*;

@Path("/sync/afectaciones") @Consumes(MediaType.APPLICATION_JSON) @Produces(MediaType.APPLICATION_JSON)
public class SyncResource {
    @EJB private AfectacionService service;
    @Context private HttpServletRequest req;
    @POST
    public Response sincronizar(SyncRequest body){
      Identidad actor=(Identidad)req.getAttribute("pgdr.identidad");
      if(!actor.puedeRegistrarAfectacion())return Response.status(403).entity(Map.of("error","SIN_PERMISO")).build();
      if(body==null||body.registros==null||body.registros.isEmpty()||body.registros.size()>100)
        return Response.status(400).entity(Map.of("error","LOTE_INVALIDO","detalle","Enviar entre 1 y 100 registros")).build();
      List<Map<String,Object>> resultados=new ArrayList<>();
      for(AfectacionRequest r:body.registros){
        Map<String,Object> v=new LinkedHashMap<>();
        v.put("solicitudId",r==null||r.solicitudId==null?null:r.solicitudId.toString());
        try{
          AfectacionService.Resultado x=service.registrar(actor,r);
          if(x.desastreInexistente())v.put("estado","DESASTRE_NO_ENCONTRADO");
          else if(x.conflicto())v.put("estado","CONFLICTO");
          else{v.put("estado",x.creado()?"CREADO":"YA_EXISTIA");v.put("afectacionId",x.afectacion().getId().toString());}
        }catch(com.pgdr.application.ValidacionException e){v.put("estado","INVALIDO");v.put("detalle",e.getMessage());}
         catch(Exception e){v.put("estado","REINTENTAR");}
        resultados.add(v);
      }
      return Response.ok(Map.of("resultados",resultados)).build();
    }
}
