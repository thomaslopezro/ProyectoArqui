package com.pgdr.reconstruccion.adaptadores.salida.plataforma;

import com.fasterxml.jackson.databind.JsonNode;
import com.pgdr.reconstruccion.adaptadores.plataforma.NoDisponibleException;
import com.pgdr.reconstruccion.adaptadores.plataforma.PlataformaHttp;
import com.pgdr.reconstruccion.adaptadores.plataforma.SesionActual;
import com.pgdr.reconstruccion.aplicacion.puertos.ConsultaEvaluaciones;
import com.pgdr.reconstruccion.dominio.EvaluacionDano;
import com.pgdr.reconstruccion.dominio.NivelDano;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/**
 * Implementa el puerto ConsultaEvaluaciones contra servidor, la unidad dueña de las
 * evaluaciones de daño. Las consulta con el token del usuario, así que servidor aplica su
 * propio filtro por organismo. Una evaluación cuenta como validada cuando está vinculada a
 * un desastre registrado. El nivel de afectación de 1 a 5 corresponde, en orden, a las cinco
 * categorías de daño.
 */
@Repository
@ConditionalOnProperty(name = "reconstruccion.integracion.habilitada", havingValue = "true")
public class EvaluacionesServidor implements ConsultaEvaluaciones {

    private static final UUID SIN_DESASTRE = new UUID(0L, 0L);

    private final PlataformaHttp plataforma;
    private final DesastresNucleo desastres;

    public EvaluacionesServidor(PlataformaHttp plataforma, DesastresNucleo desastres) {
        this.plataforma = plataforma;
        this.desastres = desastres;
    }

    @Override
    public List<EvaluacionDano> porDesastre(UUID desastreId) {
        Optional<DesastresNucleo.Entrada> desastre = desastres.entrada(desastreId);
        if (desastre.isEmpty()) return List.of();
        SesionActual.Sesion sesion = SesionActual.requerida();
        String ruta = "/api/panorama?clase=EVALUACION&eventoId="
                + URLEncoder.encode(desastre.get().referencia(), StandardCharsets.UTF_8);
        PlataformaHttp.Respuesta r = plataforma.servidor(ruta, sesion.token());
        if (r.estado() != 200 || r.cuerpo() == null || !r.cuerpo().isArray())
            throw new NoDisponibleException("El servicio de evaluaciones no entregó las evaluaciones del desastre.");
        List<EvaluacionDano> evaluaciones = new ArrayList<>();
        for (JsonNode punto : r.cuerpo()) {
            if (!"EVALUACION".equals(punto.path("clase").asText())) continue;
            UUID id = uuid(punto.path("id").asText(""));
            if (id != null) evaluaciones.add(evaluacion(id, desastreId, sesion.organizacion(), punto));
        }
        return evaluaciones;
    }

    @Override
    public List<EvaluacionDano> porIds(Collection<UUID> ids) {
        SesionActual.Sesion sesion = SesionActual.requerida();
        List<EvaluacionDano> evaluaciones = new ArrayList<>();
        for (UUID id : ids) {
            PlataformaHttp.Respuesta r = plataforma.servidor("/api/campo/evaluaciones/" + id, sesion.token());
            if (r.estado() == 404) continue;
            if (r.estado() != 200 || r.cuerpo() == null)
                throw new NoDisponibleException("El servicio de evaluaciones no entregó la evaluación " + id + ".");
            String referencia = r.cuerpo().path("eventoId").asText("");
            UUID desastreId = referencia.isBlank() ? SIN_DESASTRE : DesastresNucleo.idDe(referencia);
            evaluaciones.add(evaluacion(id, desastreId, sesion.organizacion(), r.cuerpo()));
        }
        return evaluaciones;
    }

    private static EvaluacionDano evaluacion(UUID id, UUID desastreId, String organizacion, JsonNode datos) {
        String activo = datos.path("activo").asText("");
        return new EvaluacionDano(id, desastreId, organizacion,
                activo.isBlank() ? "Sin clasificar" : activo,
                datos.path("descripcion").asText(""),
                nivel(datos.path("nivelAfectacion").asInt(1)),
                "VINCULADA".equals(datos.path("estadoVinculo").asText("")));
    }

    private static NivelDano nivel(int afectacion) {
        NivelDano[] niveles = NivelDano.values();
        int indice = Math.max(1, Math.min(niveles.length, afectacion)) - 1;
        return niveles[indice];
    }

    private static UUID uuid(String texto) {
        try {
            return UUID.fromString(texto);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
