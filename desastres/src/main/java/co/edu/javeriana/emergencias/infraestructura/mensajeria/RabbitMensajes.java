package co.edu.javeriana.emergencias.infraestructura.mensajeria;

import co.edu.javeriana.emergencias.aplicacion.puerto.NotificadorMensajes;
import co.edu.javeriana.emergencias.dominio.desastres.*;
import co.edu.javeriana.emergencias.infraestructura.persistencia.Json;
import com.rabbitmq.client.*;
import jakarta.annotation.PreDestroy;
import jakarta.ejb.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;
import java.util.logging.Logger;

/** Adaptador AMQP. El temporizador Jakarta controla el consumo y las confirmaciones. */
@Singleton
@LocalBean
@Local(NotificadorMensajes.class)
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class RabbitMensajes implements NotificadorMensajes {
    public static final String ENTRADA = "desastres.entrada";
    public static final String REGISTRO = "desastres.registro";
    public static final String SALIDA = "desastres.notificaciones";
    public static final String CORREO = "desastres.correo";
    private static final Logger LOG = Logger.getLogger(RabbitMensajes.class.getName());
    private Connection conexion;

    private Connection conectar() throws Exception {
        if (conexion == null || !conexion.isOpen()) {
            ConnectionFactory f = new ConnectionFactory();
            f.setHost(System.getenv().getOrDefault("RABBIT_HOST", "rabbitmq"));
            f.setUsername(System.getenv().getOrDefault("RABBIT_USER", "taller"));
            f.setPassword(System.getenv().getOrDefault("RABBIT_PASSWORD", "taller-local"));
            f.setConnectionTimeout(4000);
            f.setHandshakeTimeout(4000);
            f.setAutomaticRecoveryEnabled(false);
            conexion = f.newConnection("gestion-emergencias");
        }
        return conexion;
    }
    private Channel abrir() throws Exception {
        Channel c = conectar().createChannel();
        try {
            c.confirmSelect();
            c.exchangeDeclare(ENTRADA, "topic", true);
            c.exchangeDeclare(SALIDA, "topic", true);
            declararCola(c, REGISTRO);
            declararCola(c, CORREO);
            c.queueBind(REGISTRO, ENTRADA, "desastre.reportado");
            return c;
        } catch (Exception e) { c.abort(); throw e; }
    }
    private void declararCola(Channel c, String cola) throws Exception {
        c.queueDeclare(cola, true, false, false, null);
        c.queueDeclare(cola + ".errores", true, false, false, null);
        c.queueDeclare(cola + ".reintento", true, false, false, Map.of(
                "x-message-ttl", 5000, "x-dead-letter-exchange", "", "x-dead-letter-routing-key", cola));
    }
    private AMQP.BasicProperties propiedades(String id, Map<String,Object> headers) {
        return new AMQP.BasicProperties.Builder().contentType("application/json").contentEncoding("UTF-8")
                .deliveryMode(2).messageId(id).headers(headers).build();
    }
    private void confirmar(Channel c) throws Exception { c.waitForConfirmsOrDie(5000); }

    public void preparar() {
        try (Channel c = abrir()) { /* Declarar la topología antes de aceptar mensajes externos. */ }
        catch (Exception e) { throw new IllegalStateException("RabbitMQ no disponible", e); }
    }
    public void recibir(Desastre desastre) {
        try (Channel c = abrir()) {
            c.basicPublish(ENTRADA, "desastre.reportado", propiedades(desastre.id, Map.of()),
                    Json.escribir(desastre).getBytes(StandardCharsets.UTF_8));
            confirmar(c);
        } catch (Exception e) { throw new IllegalStateException("No se pudo confirmar la recepcion en RabbitMQ", e); }
    }
    @Override public void suscribir(Sistema sistema) {
        try (Channel c = abrir()) {
            c.queueDeclare(sistema.cola(), true, false, false, null);
            c.queueBind(sistema.cola(), SALIDA, sistema.cola());
        } catch (Exception e) { throw new IllegalStateException("No se pudo crear la suscripcion", e); }
    }
    @Override public void publicar(Registro registro) {
        try (Channel c = abrir()) {
            byte[] evento = Json.escribir(registro.desastre).getBytes(StandardCharsets.UTF_8);
            for (Sistema sistema : registro.destinatarios) {
                c.queueDeclare(sistema.cola(), true, false, false, null);
                c.queueBind(sistema.cola(), SALIDA, sistema.cola());
                c.basicPublish(SALIDA, sistema.cola(), propiedades(registro.desastre.id, Map.of()), evento);
            }
            c.basicPublish("", CORREO, propiedades(registro.desastre.id, Map.of()), evento);
            confirmar(c);
        } catch (Exception e) { throw new IllegalStateException("No se pudo confirmar la notificacion", e); }
    }
    public void consumir(String cola, Consumer<String> procesador) {
        try (Channel c = abrir()) {
            // Una entrega por ciclo limita trabajo y evita reintentos continuos de una dependencia caída.
            GetResponse entrega = c.basicGet(cola, false);
            if (entrega == null) return;
            String texto = new String(entrega.getBody(), StandardCharsets.UTF_8);
            try {
                procesador.accept(texto);
                c.basicAck(entrega.getEnvelope().getDeliveryTag(), false);
            } catch (Exception error) {
                Map<String,Object> headers = new HashMap<>();
                if (entrega.getProps().getHeaders() != null) headers.putAll(entrega.getProps().getHeaders());
                int intento = headers.get("intentos") instanceof Number n ? n.intValue() + 1 : 1;
                headers.put("intentos", intento);
                boolean invalido = causaInvalida(error);
                String destino = cola + (invalido || intento >= 5 ? ".errores" : ".reintento");
                headers.put("motivo", invalido ? "Mensaje invalido" : "Fallo de procesamiento");
                c.basicPublish("", destino, propiedades(entrega.getProps().getMessageId(), headers), entrega.getBody());
                confirmar(c);
                c.basicAck(entrega.getEnvelope().getDeliveryTag(), false);
                LOG.warning("Mensaje enviado a " + destino + ": " + error.getClass().getSimpleName());
            }
        } catch (Exception e) {
            // Cerrar el canal devuelve automáticamente cualquier entrega sin ACK a su cola.
            LOG.warning("Consumo pendiente en " + cola + ": " + e.getClass().getSimpleName());
        }
    }
    private boolean causaInvalida(Throwable error) {
        for (Throwable e = error; e != null; e = e.getCause())
            if (e instanceof IllegalArgumentException) return true;
        return false;
    }
    @PreDestroy public void cerrar() {
        try { if (conexion != null && conexion.isOpen()) conexion.close(); }
        catch (Exception e) { LOG.fine("Conexion AMQP cerrada"); }
    }
}
