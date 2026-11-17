using System.Security.Cryptography;
using System.Text.Json;
using System.Text.Json.Serialization;
using Mapas.Api.Infrastructure;

namespace Mapas.Api.Application;

public static class CampoEndpoints
{
    private static readonly JsonSerializerOptions Json = new(JsonSerializerDefaults.Web);

    public static void MapearCampo(this WebApplication app, bool diferida)
    {
        app.MapPost("/api/campo/lotes", async (LoteCampo lote, CampoRepository repo, CancellationToken ct) =>
        {
            if (lote.Operaciones is not { Length: >= 1 and <= 20 } operaciones ||
                operaciones.Any(op => op is null) ||
                operaciones.Select(op => op.OperacionId).Distinct().Count() != operaciones.Length)
                return Results.BadRequest(new { error = "El lote requiere 1 a 20 operaciones con identificadores únicos." });

            var resultados = new List<ResultadoCampo>();
            foreach (var op in operaciones)
            {
                var error = Validar(op);
                if (error is not null)
                {
                    resultados.Add(new(op.OperacionId, "rechazada", error));
                    continue;
                }

                var estado = await repo.Recibir(op.OperacionId, op.EvaluacionId,
                    JsonSerializer.Serialize(op, Json), !diferida, ct);
                resultados.Add(new(op.OperacionId, estado,
                    estado == "conflicto" ? "El identificador ya se usó para otro registro o contenido." : null));
            }

            return Results.Json(new { resultados },
                statusCode: resultados.Any(r => r.Estado == "recibida") ? 202 : 200);
        });

        app.MapGet("/api/campo/sitios", (string? eventoId, CampoRepository repo, CancellationToken ct) =>
            repo.Consultar(eventoId, ct));
    }

    private static string? Validar(OperacionCampo op)
    {
        if (op.OperacionId == Guid.Empty || op.EvaluacionId == Guid.Empty ||
            op.EventoId?.Length > 100)
            return "Se requieren identificadores y una referencia de evento de hasta 100 caracteres.";
        if (op.Evaluador?.Length > 120 || op.Organizacion?.Length > 120 || op.Activo?.Length > 120)
            return "Evaluador, organización y activo admiten hasta 120 caracteres.";
        if (op.NivelAfectacion is not (>= 1 and <= 5) ||
            op.Latitud is not (>= -90 and <= 90) || op.Longitud is not (>= -180 and <= 180))
            return "Se requieren coordenadas válidas y nivel de afectación de 1 a 5.";
        if (op.FuenteUbicacion is not (null or "MANUAL" or "DISPOSITIVO"))
            return "Fuente de ubicación desconocida.";
        if (op.CapturadaEnUtc is null || op.Descripcion?.Length > 1000)
            return "Fecha de captura ausente o descripción demasiado larga.";
        if (op.Foto is null)
            return op.EvidenciaId is null && op.FotoSha256 is null && op.FotoTipo is null
                ? null : "Metadatos de foto sin archivo.";
        if (op.Foto.Length is < 1 or > 5 * 1024 * 1024 || op.EvidenciaId is null || op.EvidenciaId == Guid.Empty ||
            op.FotoTipo is not ("image/jpeg" or "image/png" or "image/webp" or "image/heic" or "image/heif") ||
            !string.Equals(op.FotoSha256, Convert.ToHexString(SHA256.HashData(op.Foto)), StringComparison.OrdinalIgnoreCase))
            return "Foto inválida o hash distinto.";
        return null;
    }
}

public sealed record LoteCampo(OperacionCampo[]? Operaciones);
public sealed record OperacionCampo(Guid OperacionId, Guid EvaluacionId, string? EventoId,
    int? NivelAfectacion, double? Latitud, double? Longitud, DateTimeOffset? CapturadaEnUtc,
    string? Descripcion, string? FuenteUbicacion, Guid? EvidenciaId, string? FotoTipo, string? FotoSha256, byte[]? Foto,
    [property: JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)] string? Evaluador = null,
    [property: JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)] string? Organizacion = null,
    [property: JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)] string? Activo = null);
public sealed record ResultadoCampo(Guid OperacionId, string Estado, string? Motivo);
