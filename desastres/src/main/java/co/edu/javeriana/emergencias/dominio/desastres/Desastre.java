package co.edu.javeriana.emergencias.dominio.desastres;

import java.time.Instant;
import java.util.Set;

/** Contrato del mensaje recibido. El productor conserva el ID en los reintentos. */
public class Desastre {
    public String organizacionId;
    public String eventoId;
    public Long version;
    public String clase;
    public String motivo;
    public String id;
    public String tipo;
    public String ubicacion;
    public String descripcion;
    public String severidad;
    public String fecha;

    public void validar() {
        if (id == null || !id.matches("[a-zA-Z0-9_-]{1,100}"))
            throw new IllegalArgumentException("id obligatorio: 1 a 100 letras, numeros, guiones o guiones bajos");
        validarTexto(ubicacion, "ubicacion", 200);
        validarTexto(descripcion, "descripcion", 2000);
        if (tipo == null || tipo.isBlank() || tipo.length()>80)
            throw new IllegalArgumentException("tipo: INUNDACION, TERREMOTO, INCENDIO, DESLIZAMIENTO u OTRO");
        if (!Set.of("BAJA", "MEDIA", "ALTA", "CRITICA").contains(severidad == null ? "" : severidad))
            throw new IllegalArgumentException("severidad: BAJA, MEDIA, ALTA o CRITICA");
        try { Instant.parse(fecha); }
        catch (Exception error) { throw new IllegalArgumentException("fecha obligatoria en formato ISO-8601 UTC", error); }
    }

    public static void validarTexto(String valor, String campo, int maximo) {
        if (valor == null || valor.isBlank() || valor.length() > maximo)
            throw new IllegalArgumentException(campo + " obligatorio; maximo " + maximo + " caracteres");
    }
}
