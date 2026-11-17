package co.edu.javeriana.emergencias.infraestructura.persistencia;

import jakarta.json.bind.JsonbBuilder;

/** Cada operación tiene su propia instancia para no compartir un serializador mutable. */
public final class Json {
    private Json() { }
    public static String escribir(Object dato) {
        try (var json = JsonbBuilder.create()) { return json.toJson(dato); }
        catch (Exception e) { throw new IllegalArgumentException("No se pudo serializar JSON", e); }
    }
    public static <T> T leer(String texto, Class<T> tipo) {
        try (var json = JsonbBuilder.create()) { return json.fromJson(texto, tipo); }
        catch (Exception e) { throw new IllegalArgumentException("JSON invalido", e); }
    }
}
