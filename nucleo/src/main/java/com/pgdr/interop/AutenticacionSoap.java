package com.pgdr.interop;
import jakarta.ejb.*;import jakarta.jws.*;
@Stateless @WebService(serviceName="AutenticacionProyectoService",portName="AutenticacionProyectoPort",targetNamespace="urn:arqui:identidad")
public class AutenticacionSoap {
 @EJB IdentidadProyectoBean identidad;
 @WebMethod public String autenticar(@WebParam(name="usuario") String usuario,@WebParam(name="clave") String clave){return identidad.autenticar(usuario,clave);}
 @WebMethod public String consultar(@WebParam(name="token") String token){return identidad.consultar(token);}
 @WebMethod public void cerrar(@WebParam(name="token") String token){identidad.cerrar(token);}
}
