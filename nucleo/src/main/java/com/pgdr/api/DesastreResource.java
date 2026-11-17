package com.pgdr.api;

import com.pgdr.application.DesastreService;
import com.pgdr.application.Identidad;
import com.pgdr.domain.Desastre;
import jakarta.ejb.EJB;
import jakarta.ws.rs.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.*;
import java.util.*;

@Path("/desastres") @Produces(MediaType.APPLICATION_JSON)
public class DesastreResource {
    @EJB private DesastreService service;
    @Context private HttpServletRequest req;
    private Identidad identidad(){return (Identidad)req.getAttribute("pgdr.identidad");}
    @POST @Consumes(MediaType.APPLICATION_JSON)
    public Response registrar(CrearDesastreRequest r){
      if(r==null||r.solicitudId==null||r.nombre==null||r.nombre.isBlank()||r.nombre.length()>150||r.nivel==null||r.nivel<1||r.nivel>4)
        return Response.status(400).entity(Map.of("error","DATOS_INVALIDOS","detalle","solicitudId, nombre y nivel 1-4 obligatorios")).build();
      if(!identidad().puedeRegistrarDesastre())return Response.status(403).entity(Map.of("error","SIN_PERMISO")).build();
      DesastreService.Resultado result=service.registrar(identidad(),r.solicitudId,r.nombre,r.descripcion,r.nivel);
      if(result.conflicto())return Response.status(409).entity(Map.of("error","SOLICITUD_ID_REUTILIZADA_CON_OTRO_CONTENIDO")).build();
      return Response.status(result.creado()?201:200).entity(ver(result.desastre())).build();
    }
    @GET
    public Response listar(@QueryParam("limit") @DefaultValue("100") int max){
      if(max<1||max>200)return Response.status(400).entity(Map.of("error","LIMIT_INVALIDO")).build();
      return Response.ok(service.listar(identidad(),max).stream().map(this::ver).toList()).build();
    }
    @GET @Path("/{id}")
    public Response obtener(@PathParam("id") String id){
      UUID uuid;
      try{uuid=UUID.fromString(id);}catch(Exception e){return Response.status(400).entity(Map.of("error","ID_INVALIDO")).build();}
      Desastre d=service.buscar(uuid,identidad());
      return d==null?Response.status(404).entity(Map.of("error","NO_ENCONTRADO")).build():Response.ok(ver(d)).build();
    }
    private Map<String,Object> ver(Desastre d){
      Map<String,Object> m=new LinkedHashMap<>();
      m.put("id",d.getId().toString());m.put("nombre",d.getNombre());m.put("descripcion",d.getDescripcion());
      m.put("severidad",d.getSeveridad());m.put("creadoEn",d.getCreadoEn().toString());m.put("version",d.getVersion());
      m.put("solicitudId",d.getSolicitudId().toString());return m;
    }
}
