package co.edu.javeriana.taller4.comun.remoto;

import java.util.Properties;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

/**
 * Conexión RPC (EJB remoto) hacia otro servidor WildFly.
 *
 * Encapsula los detalles técnicos de JNDI para que el resto del código
 * solo haga: conexion.buscar(NOMBRE_JNDI, Interfaz.class).
 * La usan la presentación (Tomcat -> Lógica), los servicios (-> Lógica)
 * y la lógica (-> Datos).
 *
 * Se crea UNA vez por aplicación y se cierra al apagarla (close()).
 */
public class ConexionEjbRemota implements AutoCloseable {

    private final Context contexto;

    /**
     * @param url     por ejemplo "remote+http://logica:8080"
     * @param usuario usuario de aplicación creado en el WildFly destino (add-user.sh -a)
     * @param clave   clave de ese usuario
     */
    public ConexionEjbRemota(String url, String usuario, String clave) throws NamingException {
        Properties propiedades = new Properties();
        propiedades.put(Context.INITIAL_CONTEXT_FACTORY, "org.wildfly.naming.client.WildFlyInitialContextFactory");
        propiedades.put(Context.PROVIDER_URL, url);
        propiedades.put(Context.SECURITY_PRINCIPAL, usuario);
        propiedades.put(Context.SECURITY_CREDENTIALS, clave);
        this.contexto = new InitialContext(propiedades);
    }

    /** Busca el EJB remoto y devuelve un proxy: llamarlo es una llamada por la red. */
    public <T> T buscar(String nombreJndi, Class<T> tipo) throws NamingException {
        Object proxy = contexto.lookup(nombreJndi);
        return tipo.cast(proxy);
    }

    @Override
    public void close() {
        try {
            contexto.close();
        } catch (NamingException e) {
            System.err.println("No se pudo cerrar la conexión EJB: " + e.getMessage());
        }
    }

    /**
     * Lee una variable de entorno obligatoria (las define docker-compose).
     * Si falta, falla de una vez con un mensaje claro.
     */
    public static String variableObligatoria(String nombre) {
        String valor = System.getenv(nombre);
        if (valor == null || valor.isBlank()) {
            throw new IllegalStateException("Falta la variable de entorno " + nombre);
        }
        return valor;
    }
}
