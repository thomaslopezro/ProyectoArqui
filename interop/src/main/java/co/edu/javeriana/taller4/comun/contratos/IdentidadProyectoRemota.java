package co.edu.javeriana.taller4.comun.contratos;
public interface IdentidadProyectoRemota {
 String autenticar(String usuario,String clave);
 String consultar(String token);
 void cerrar(String token);
}
