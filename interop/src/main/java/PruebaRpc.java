import co.edu.javeriana.taller4.comun.remoto.ConexionEjbRemota;
import co.edu.javeriana.taller4.comun.contratos.IdentidadProyectoRemota;
import java.net.*;import java.net.http.*;import java.util.regex.*;
public class PruebaRpc {
 public static void main(String[] args)throws Exception{
  try(var c=new ConexionEjbRemota("remote+http://nucleo:8080",System.getenv("EJB_USUARIO"),System.getenv("EJB_CLAVE"))){
   var bean=c.buscar("ejb:/plataforma-desastres/IdentidadProyectoBean!co.edu.javeriana.taller4.comun.contratos.IdentidadProyectoRemota",IdentidadProyectoRemota.class);
   if(!bean.autenticar(System.getenv("PGDR_BOOTSTRAP_USER"),"incorrecta").contains("CREDENCIALES_INVALIDAS"))throw new AssertionError();
   String json=bean.autenticar(System.getenv("PGDR_BOOTSTRAP_USER"),System.getenv("PGDR_BOOTSTRAP_PASSWORD"));
   Matcher m=Pattern.compile("\"accessToken\":\"([^\"]+)\"").matcher(json);if(!m.find())throw new AssertionError("Login RPC falló");String token=m.group(1);
   try{
    if(!bean.consultar(token).contains("organizacionId"))throw new AssertionError();
    var http=HttpClient.newHttpClient();var req=HttpRequest.newBuilder(URI.create("http://api:8080/api/panorama")).header("Authorization","Bearer "+token).build();
    if(http.send(req,HttpResponse.BodyHandlers.discarding()).statusCode()!=200)throw new AssertionError("Token RPC no funciona en REST/mapas");
    System.out.println("OK: EJB remoto real reutiliza identidad y token de la plataforma REST.");
   }finally{bean.cerrar(token);}
   if(!bean.consultar(token).contains("TOKEN_INVALIDO"))throw new AssertionError();System.out.println("OK: cierre RPC revoca la misma sesión.");
  }
 }
}
