package com.pgdr.reconstruccion.adaptadores.salida.mensajeria;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Envía a RabbitMQ los avisos pendientes de la bandeja de salida. Toma cada aviso con bloqueo
 * por fila, de modo que varias instancias no envían el mismo a la vez, y lo marca como
 * publicado en la misma transacción. Si RabbitMQ no está, el aviso queda pendiente y se
 * reintenta en el siguiente ciclo. La entrega es al menos una vez: el identificador del aviso
 * viaja como identificador del mensaje para que el consumidor descarte repetidos.
 */
@Component
@ConditionalOnProperty(name = "reconstruccion.mensajeria.habilitada", havingValue = "true")
public class DespachadorBandeja {

    public static final String INTERCAMBIO = "reconstruccion.eventos";

    private static final Logger LOG = LoggerFactory.getLogger(DespachadorBandeja.class);

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaccion;
    private final RabbitTemplate rabbit;

    public DespachadorBandeja(JdbcTemplate jdbc, PlatformTransactionManager gestor, RabbitTemplate rabbit) {
        this.jdbc = jdbc;
        this.transaccion = new TransactionTemplate(gestor);
        this.rabbit = rabbit;
    }

    @Scheduled(fixedDelay = 1000)
    public void despachar() {
        try {
            transaccion.executeWithoutResult(estado -> {
                List<Map<String, Object>> pendientes = jdbc.queryForList(
                        "SELECT id, tipo, agregado_id, payload_json FROM outbox_eventos "
                                + "WHERE publicado_en IS NULL ORDER BY creado_en LIMIT 20 FOR UPDATE SKIP LOCKED");
                for (Map<String, Object> fila : pendientes) {
                    String id = String.valueOf(fila.get("id"));
                    String tipo = String.valueOf(fila.get("tipo"));
                    Message mensaje = MessageBuilder
                            .withBody(String.valueOf(fila.get("payload_json")).getBytes(StandardCharsets.UTF_8))
                            .setContentType("application/json")
                            .setMessageId(id)
                            .setType(tipo)
                            .setHeader("agregadoId", String.valueOf(fila.get("agregado_id")))
                            .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                            .build();
                    rabbit.send(INTERCAMBIO, tipo, mensaje);
                    jdbc.update("UPDATE outbox_eventos SET publicado_en = ? WHERE id = ?",
                            Timestamp.from(Instant.now()), id);
                }
            });
        } catch (RuntimeException e) {
            LOG.warn("Avisos pendientes en la bandeja de salida; se reintentará: {}", e.getClass().getSimpleName());
        }
    }
}
