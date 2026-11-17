package com.pgdr.reconstruccion.adaptadores.salida.plataforma;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pgdr.reconstruccion.adaptadores.plataforma.NoDisponibleException;
import com.pgdr.reconstruccion.adaptadores.plataforma.PlataformaHttp;
import com.pgdr.reconstruccion.adaptadores.plataforma.SesionActual;
import com.pgdr.reconstruccion.aplicacion.casosdeuso.AccesoDenegadoException;
import com.pgdr.reconstruccion.aplicacion.casosdeuso.NoEncontradoException;
import com.pgdr.reconstruccion.aplicacion.puertos.ConflictoDeVersionException;
import com.pgdr.reconstruccion.aplicacion.puertos.GestionDesastres;
import com.pgdr.reconstruccion.dominio.Desastre;
import com.pgdr.reconstruccion.dominio.EstadoDesastre;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/**
 * Implementa el puerto GestionDesastres contra el núcleo, que es el dueño del desastre y de
 * su fase. Reconstrucción no guarda copia: lee el catálogo del núcleo y le pide el cambio de
 * fase en nombre del usuario. El núcleo identifica el desastre por su referencia; el módulo
 * usa un UUID derivado de esa referencia, siempre el mismo.
 */
@Repository
@ConditionalOnProperty(name = "reconstruccion.integracion.habilitada", havingValue = "true")
public class DesastresNucleo implements GestionDesastres {

    /** Un desastre del catálogo del núcleo, con lo necesario para pedirle un cambio de fase. */
    public record Entrada(UUID id, String referencia, String nombre, EstadoDesastre estado, long version) {}

    private final PlataformaHttp plataforma;
    private final ObjectMapper json;

    public DesastresNucleo(PlataformaHttp plataforma, ObjectMapper json) {
        this.plataforma = plataforma;
        this.json = json;
    }

    public static UUID idDe(String referencia) {
        return UUID.nameUUIDFromBytes(("desastre:" + referencia).getBytes(StandardCharsets.UTF_8));
    }

    /** Desastres del organismo de la sesión, tal como los tiene el núcleo en este momento. */
    public List<Entrada> catalogo() {
        SesionActual.Sesion sesion = SesionActual.requerida();
        PlataformaHttp.Respuesta r = plataforma.nucleoInterno("/integracion/catalogo");
        if (r.estado() != 200 || r.cuerpo() == null || !r.cuerpo().isArray())
            throw new NoDisponibleException("El núcleo no entregó el catálogo de desastres.");
        List<Entrada> entradas = new ArrayList<>();
        for (JsonNode fila : r.cuerpo()) {
            if (!sesion.organizacion().equals(fila.path("organizacionId").asText())) continue;
            JsonNode d = fila.path("desastre");
            String referencia = d.path("id").asText("");
            if (referencia.isBlank()) continue;
            String nombre = d.path("tipo").asText("Desastre") + " · " + d.path("ubicacion").asText(referencia);
            entradas.add(new Entrada(idDe(referencia), referencia, nombre, fase(d.path("fase").asText("REGISTRO")),
                    d.path("version").asLong(0)));
        }
        return entradas;
    }

    public Optional<Entrada> entrada(UUID id) {
        return catalogo().stream().filter(e -> e.id().equals(id)).findFirst();
    }

    @Override
    public Optional<Desastre> buscar(UUID id) {
        return entrada(id).map(DesastresNucleo::aDesastre);
    }

    @Override
    public List<Desastre> listar() {
        return catalogo().stream().map(DesastresNucleo::aDesastre).toList();
    }

    @Override
    public void cambiarEstado(UUID id, EstadoDesastre nuevo) {
        Entrada actual = entrada(id)
                .orElseThrow(() -> new NoEncontradoException("No existe el desastre " + id + "."));
        ObjectNode cuerpo = json.createObjectNode();
        cuerpo.put("cambioId", UUID.randomUUID().toString());
        cuerpo.put("versionBase", actual.version());
        cuerpo.put("fase", nuevo.name());
        cuerpo.put("motivo", "Cambio pedido desde el módulo de Reconstrucción");
        String ruta = "/eventos/" + URLEncoder.encode(actual.referencia(), StandardCharsets.UTF_8) + "/fase";
        PlataformaHttp.Respuesta r = plataforma.nucleo("PATCH", ruta, SesionActual.requerida().token(),
                cuerpo.toString());
        if (r.estado() == 200) return;
        if (r.estado() == 409)
            throw new ConflictoDeVersionException("El desastre cambió mientras se procesaba; vuelve a intentarlo.");
        if (r.estado() == 403)
            throw new AccesoDenegadoException("El núcleo no permite a este usuario cambiar la fase del desastre.");
        if (r.estado() == 404)
            throw new NoEncontradoException("El núcleo no encontró el desastre " + actual.referencia() + ".");
        throw new NoDisponibleException("El núcleo rechazó el cambio de fase (" + r.estado() + ").");
    }

    private static Desastre aDesastre(Entrada e) {
        return new Desastre(e.id(), e.nombre(), e.estado());
    }

    private static EstadoDesastre fase(String texto) {
        try {
            return EstadoDesastre.valueOf(texto);
        } catch (IllegalArgumentException e) {
            return EstadoDesastre.REGISTRO;
        }
    }
}
