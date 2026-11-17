package com.pgdr.interop;
import co.edu.javeriana.taller4.comun.contratos.IdentidadProyectoRemota;
import com.pgdr.application.*;import jakarta.ejb.*;import jakarta.json.*;
@Stateless(name="IdentidadProyectoBean") @LocalBean @Remote(IdentidadProyectoRemota.class)
public class IdentidadProyectoBean implements IdentidadProyectoRemota {
 @EJB AuthService auth;
 public String autenticar(String usuario,String clave){var s=auth.login(usuario,clave);if(s==null)return "{\"error\":\"CREDENCIALES_INVALIDAS\"}";
  return Json.createObjectBuilder().add("accessToken",s.token()).add("usuario",s.identidad().usuario()).add("rol",s.identidad().rol()).add("organizacionId",s.identidad().organizacionId().toString()).add("expiraEn",s.expiraEn().toString()).build().toString();}
 public String consultar(String token){var i=auth.resolver(token);if(i==null)return "{\"error\":\"TOKEN_INVALIDO_O_EXPIRADO\"}";
  return Json.createObjectBuilder().add("usuario",i.usuario()).add("rol",i.rol()).add("organizacionId",i.organizacionId().toString()).build().toString();}
 public void cerrar(String token){auth.logout(token);}
}
