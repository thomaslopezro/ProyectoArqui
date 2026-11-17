package com.pgdr.reconstruccion.adaptadores.plataforma;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Cliente HTTP hacia las otras unidades: el núcleo (identidad y desastres) y servidor
 * (evaluaciones). Tiempos de espera de 2 s de conexión y 5 s de respuesta.
 */
@Component
@ConditionalOnProperty(name = "reconstruccion.integracion.habilitada", havingValue = "true")
public class PlataformaHttp {

    public record Respuesta(int estado, JsonNode cuerpo) {}

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private final ObjectMapper json;
    private final String nucleo;
    private final String servidor;
    private final String clave;

    public PlataformaHttp(ObjectMapper json,
                          @Value("${reconstruccion.nucleo.url}") String nucleo,
                          @Value("${reconstruccion.servidor.url}") String servidor,
                          @Value("${reconstruccion.integracion.clave}") String clave) {
        this.json = json;
        this.nucleo = sinBarraFinal(nucleo);
        this.servidor = sinBarraFinal(servidor);
        this.clave = clave;
    }

    /** Llamada al núcleo en nombre del usuario: lleva su token. */
    public Respuesta nucleo(String metodo, String ruta, String token, String cuerpo) {
        HttpRequest.Builder solicitud = HttpRequest.newBuilder(URI.create(nucleo + ruta))
                .header("Authorization", "Bearer " + token);
        return enviar(solicitud, metodo, cuerpo, "El núcleo");
    }

    /** Llamada interna al núcleo: lleva la clave de integración, no un token de usuario. */
    public Respuesta nucleoInterno(String ruta) {
        HttpRequest.Builder solicitud = HttpRequest.newBuilder(URI.create(nucleo + ruta))
                .header("X-Integracion-Key", clave);
        return enviar(solicitud, "GET", null, "El núcleo");
    }

    /** Consulta a servidor (evaluaciones) en nombre del usuario. */
    public Respuesta servidor(String ruta, String token) {
        HttpRequest.Builder solicitud = HttpRequest.newBuilder(URI.create(servidor + ruta))
                .header("Authorization", "Bearer " + token);
        return enviar(solicitud, "GET", null, "El servicio de evaluaciones");
    }

    private Respuesta enviar(HttpRequest.Builder solicitud, String metodo, String cuerpo, String quien) {
        try {
            HttpRequest.BodyPublisher contenido = cuerpo == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(cuerpo);
            if (cuerpo != null) solicitud.header("Content-Type", "application/json");
            HttpResponse<String> respuesta = http.send(
                    solicitud.timeout(Duration.ofSeconds(5)).method(metodo, contenido).build(),
                    HttpResponse.BodyHandlers.ofString());
            if (respuesta.statusCode() >= 500)
                throw new NoDisponibleException(quien + " respondió con error " + respuesta.statusCode() + ".");
            return new Respuesta(respuesta.statusCode(), leer(respuesta.body()));
        } catch (IOException e) {
            throw new NoDisponibleException(quien + " no responde.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new NoDisponibleException(quien + " no responde.");
        }
    }

    private JsonNode leer(String texto) {
        if (texto == null || texto.isBlank()) return null;
        try {
            return json.readTree(texto);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private static String sinBarraFinal(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
