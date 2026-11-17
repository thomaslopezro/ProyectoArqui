using System.Text.Json;
using Mapas.Api.Domain;

namespace Mapas.Api.Application;

public static class GeoJson
{
    public static object Exportar(IEnumerable<Afectacion> items) => new {
        type = "FeatureCollection",
        features = items.Select(x => new {
            type = "Feature", id = x.Id,
            geometry = new { type = "Point", coordinates = new[] { x.Longitud, x.Latitud } },
            properties = new { x.Titulo, x.Tipo, x.Severidad, x.Descripcion }
        })
    };
    public static List<AfectacionInput> Leer(JsonElement document)
    {
        try
        {
            if (document.TryGetProperty("crs", out _)) throw new FormatException();
            if (document.GetProperty("type").GetString() != "FeatureCollection") throw new FormatException();
            var features = document.GetProperty("features");
            if (features.GetArrayLength() is < 1 or > 500) throw new FormatException();
            var result = new List<AfectacionInput>();
            foreach (var feature in features.EnumerateArray())
            {
                if (feature.GetProperty("type").GetString() != "Feature") throw new FormatException();
                var geometry = feature.GetProperty("geometry");
                if (geometry.GetProperty("type").GetString() != "Point") throw new FormatException();
                var coordinates = geometry.GetProperty("coordinates");
                if (coordinates.GetArrayLength() != 2) throw new FormatException();
                var properties = feature.GetProperty("properties");
                if (Text(properties, "clase") == "EVALUACION")
                    throw new ValidationError(new() { ["geojson"] = ["Este archivo contiene evaluaciones. La importación de afectaciones no transforma evaluaciones ni elimina sus identidades. Importa únicamente afectaciones."] });
                result.Add(new(Text(properties, "titulo"), Text(properties, "tipo"), Text(properties, "severidad"),
                    Text(properties, "descripcion"), coordinates[1].GetDouble(), coordinates[0].GetDouble()));
            }
            return result;
        }
        catch (Exception e) when (e is KeyNotFoundException or InvalidOperationException or FormatException or IndexOutOfRangeException)
        {
            throw new ValidationError(new() { ["geojson"] = ["Se requiere FeatureCollection con 1–500 Features Point, coordenadas [longitud, latitud] y properties: titulo, tipo, severidad, descripcion opcional."] });
        }
    }
    private static string? Text(JsonElement value, string name) => value.TryGetProperty(name, out var property) ? property.GetString() : null;
}
