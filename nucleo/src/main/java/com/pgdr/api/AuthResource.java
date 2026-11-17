package com.pgdr.api;

import com.pgdr.application.AuthService;
import com.pgdr.application.Identidad;
import jakarta.ejb.EJB;
import jakarta.ws.rs.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.*;
import java.util.Map;

@Path("/auth") @Consumes(MediaType.APPLICATION_JSON) @Produces(MediaType.APPLICATION_JSON)
public class AuthResource {
    @EJB private AuthService service;
    @Context private HttpServletRequest context;
    @POST @Path("/login")
    public Response login(LoginRequest req){
      if(req==null)return Response.status(400).entity(Map.of("error","CUERPO_REQUERIDO")).build();
      AuthService.LoginExitoso s=service.login(req.usuario,req.clave);
      if(s==null)return Response.status(401).entity(Map.of("error","CREDENCIALES_INVALIDAS")).build();
      return Response.ok(Map.of("accessToken",s.token(),"tokenType","Bearer","expiraEn",s.expiraEn().toString(),"usuario",s.identidad().usuario(),"rol",s.identidad().rol(),"organizacionId",s.identidad().organizacionId().toString())).header("Cache-Control","no-store").build();
    }
    @GET @Path("/me")
    public Response me(){
      Identidad i=(Identidad)context.getAttribute("pgdr.identidad");
      return Response.ok(Map.of("usuarioId",i.usuarioId().toString(),"usuario",i.usuario(),"rol",i.rol(),"organizacionId",i.organizacionId().toString())).header("Cache-Control","no-store").build();
    }
    @POST @Path("/usuarios")
    public Response crearUsuario(NuevoUsuarioRequest r){
      Identidad i=(Identidad)context.getAttribute("pgdr.identidad");
      if(!i.puedeRegistrarDesastre())return Response.status(403).entity(Map.of("error","SIN_PERMISO")).build();
      if(r==null)return Response.status(400).entity(Map.of("error","DATOS_INVALIDOS")).build();
      try{
        boolean creado=service.crearUsuario(i,r.usuario,r.clave,r.rol);
        return creado?Response.status(201).entity(Map.of("usuario",r.usuario.trim().toLowerCase(java.util.Locale.ROOT),"rol",r.rol)).build()
          :Response.status(409).entity(Map.of("error","USUARIO_EXISTENTE")).build();
      }catch(com.pgdr.application.ValidacionException e){return Response.status(400).entity(Map.of("error","DATOS_INVALIDOS")).build();}
    }
    @POST @Path("/logout")
    public Response logout(@HeaderParam("Authorization") String header){
      service.logout(header.substring(7).trim());
      return Response.noContent().build();
    }
}
