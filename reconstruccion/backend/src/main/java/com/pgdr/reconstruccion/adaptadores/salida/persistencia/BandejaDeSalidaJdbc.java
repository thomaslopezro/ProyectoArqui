package com.pgdr.reconstruccion.adaptadores.salida.persistencia;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pgdr.reconstruccion.aplicacion.puertos.PublicadorEventos;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Implementa PublicadorEventos como bandeja de salida: el evento se guarda en la misma transacción
 * del cambio y queda pendiente (publicado_en nulo) hasta que un despachador lo envíe a la mensajería.
 * Así un evento nunca se pierde ni se publica sin que el cambio exista.
 */
@Repository
public class BandejaDeSalidaJdbc implements PublicadorEventos {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public BandejaDeSalidaJdbc(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public void publicar(String tipo, UUID agregadoId, Map<String, String> datos) {
        try {
            jdbc.update("INSERT INTO outbox_eventos (id, tipo, agregado_id, payload_json, creado_en, publicado_en) "
                            + "VALUES (?,?,?,?,?,NULL)",
                    UUID.randomUUID().toString(), tipo, agregadoId.toString(), json.writeValueAsString(datos),
                    Timestamp.from(Instant.now()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo serializar el evento " + tipo, e);
        }
    }
}
