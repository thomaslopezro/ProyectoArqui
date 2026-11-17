using System.Text.Json;
using Mapas.Api.Infrastructure;

namespace Mapas.Api.Application;

// Modelo de consulta compartido: una evaluación no se convierte en un desastre.
public sealed record PuntoPanorama(Guid Id, string Clase, string Titulo, string? Tipo,
    string? Severidad, int? NivelAfectacion, string? EventoId, string Descripcion,
    double Latitud, double Longitud, Guid? Version, DateTimeOffset RegistradaEn,
    string? Evaluador = null, string? Organizacion = null, string? Activo = null,
    Guid? EvidenciaId = null, string? EstadoVinculo = null, EventoDesastre? Desastre = null);

public static class PanoramaEndpoints
{
    public static void MapearPanorama(this WebApplication app)
    {
        app.MapPut("/api/campo/evaluaciones/{id:guid}",(Guid id,RevisionCampo input,EvaluacionesVersionadas versiones,CancellationToken ct)=>versiones.Actualizar(id,input,ct));
        app.MapGet("/api/campo/evaluaciones/{id:guid}/historial",(Guid id,EvaluacionesVersionadas versiones,CancellationToken ct)=>versiones.Historial(id,ct));
        app.MapGet("/api/panorama", async (string? clase, string? tipo, string? severidad,
            string? eventoId, int? nivel, AfectacionesService afectaciones, CampoRepository campo, CancellationToken ct) =>
            Results.Ok(await Consultar(clase, tipo, severidad, eventoId, nivel, afectaciones, campo, ct)));

        app.MapGet("/api/panorama/geojson", async (string? clase, string? tipo, string? severidad,
            string? eventoId, int? nivel, AfectacionesService afectaciones, CampoRepository campo, CancellationToken ct) =>
        {
            var puntos = await Consultar(clase, tipo, severidad, eventoId, nivel, afectaciones, campo, ct);
            return Results.Json(new {
                type = "FeatureCollection",
                features = puntos.Select(p => new {
                    type = "Feature", id = p.Id,
                    geometry = new { type = "Point", coordinates = new[] { p.Longitud, p.Latitud } },
                    properties = new { p.Clase, p.Titulo, p.Tipo, p.Severidad, p.NivelAfectacion,
                        p.EventoId, p.Descripcion, p.RegistradaEn, p.Evaluador, p.Organizacion, p.Activo, p.EvidenciaId, p.EstadoVinculo, p.Desastre }
                })
            }, contentType: "application/geo+json");
        });

        app.MapGet("/api/campo/evaluaciones/{id:guid}", async (Guid id, CampoRepository campo, EvaluacionesVersionadas versiones, CancellationToken ct) =>
        {
            var op = await campo.Buscar(id, ct);
            if (op is null) return Results.NotFound();
            var vinculo = await campo.ConsultarVinculo(id, ct);
            return Results.Ok(new {
                version = await versiones.Version(id,ct), op.EvaluacionId, vinculo.EventoId, eventoOriginalId = op.EventoId, vinculo.EstadoVinculo, vinculo.Desastre, op.NivelAfectacion, op.Latitud, op.Longitud,
                op.CapturadaEnUtc, op.Descripcion, op.FuenteUbicacion, op.EvidenciaId,
                op.Evaluador, op.Organizacion, op.Activo
            });
        });
        app.MapGet("/api/campo/evaluaciones/{id:guid}/foto", async (Guid id, CampoRepository campo, CancellationToken ct) =>
        {
            var op = await campo.Buscar(id, ct);
            return op?.Foto is { } foto ? Results.File(foto, op.FotoTipo ?? "application/octet-stream") : Results.NotFound();
        });
    }

    private static async Task<List<PuntoPanorama>> Consultar(string? clase, string? tipo, string? severidad,
        string? eventoId, int? nivel, AfectacionesService afectaciones, CampoRepository campo, CancellationToken ct)
    {
        if (clase is not (null or "AFECTACION" or "EVALUACION") || nivel is < 1 or > 5 || eventoId?.Length > 100)
            throw new ValidationError(new() { ["filtros"] = ["Clase inválida, evento demasiado largo o nivel fuera de 1 a 5."] });
        // Validar filtros de afectaciones incluso cuando se consulta únicamente campo.
        if (tipo is not null && !AfectacionesService.Tipos.Contains(tipo) ||
            severidad is not null && !AfectacionesService.Severidades.Contains(severidad))
            throw new ValidationError(new() { ["filtros"] = ["Tipo o severidad desconocidos."] });
        var puntos = new List<PuntoPanorama>();
        if (clase != "EVALUACION" && eventoId is null && nivel is null)
            puntos.AddRange((await afectaciones.Listar(tipo, severidad, ct)).Select(a => new PuntoPanorama(
                a.Id, "AFECTACION", a.Titulo, a.Tipo, a.Severidad, null, null, a.Descripcion,
                a.Latitud, a.Longitud, a.Version, a.RegistradaEn)));
        if (clase != "AFECTACION" && tipo is null && severidad is null)
            puntos.AddRange((await campo.ConsultarPanorama(eventoId, ct)).Where(p => nivel is null || p.NivelAfectacion == nivel));
        return puntos.OrderByDescending(p => p.RegistradaEn).ThenBy(p => p.Id).ToList();
    }
}
