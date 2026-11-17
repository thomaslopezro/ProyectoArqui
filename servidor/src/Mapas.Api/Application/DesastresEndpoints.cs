using System.Text.Json;
using Mapas.Api.Infrastructure;

namespace Mapas.Api.Application;

public static class DesastresEndpoints
{
    public static void MapearDesastres(this WebApplication app)
    {
        app.MapPost("/api/administracion/migrar-notificaciones", async (AccesoActual acceso,IHttpClientFactory factory,IConfiguration config,CancellationToken ct)=>{
            using var req=new HttpRequestMessage(HttpMethod.Post,"migrar-organizacion");
            req.Headers.Add("X-Integracion-Key",config["Integracion:Key"]);
            req.Content=JsonContent.Create(new {organizacionId=acceso.Organizacion});
            using var response=await factory.CreateClient("desastres").SendAsync(req,ct);
            return Results.StatusCode((int)response.StatusCode);
        });
        app.MapGet("/api/desastres", async (DesastresCatalogo catalogo, CancellationToken ct) => {
            await catalogo.Refrescar(ct);
            return Results.Ok(await catalogo.Consultar(ct));
        });
        app.MapPost("/api/desastres", (JsonElement body, DesastresCatalogo catalogo, CancellationToken ct) =>
            catalogo.Enviar("desastres", body, ct));
        app.MapGet("/api/sistemas", (DesastresCatalogo catalogo, CancellationToken ct) => catalogo.Sistemas(ct));
        app.MapPost("/api/sistemas", (JsonElement body, DesastresCatalogo catalogo, CancellationToken ct) =>
            catalogo.Enviar("sistemas", body, ct));
        app.MapPut("/api/campo/evaluaciones/{id:guid}/desastre", async (Guid id, VincularDesastre input,
            CampoRepository campo, DesastresCatalogo catalogo, CancellationToken ct) => {
            if (string.IsNullOrWhiteSpace(input.EventoId) || input.EventoId.Length > 100)
                return Results.BadRequest(new { error = "Selecciona un desastre registrado." });
            if (await campo.Buscar(id, ct) is null) return Results.NotFound();
            await catalogo.Refrescar(ct);
            if (!await catalogo.Contiene(input.EventoId, ct))
                return catalogo.Disponible ? Results.NotFound(new { error = "El desastre no está registrado." }) :
                    Results.Problem(statusCode: 503, title: "El desastre no está en la copia local. Reintenta cuando vuelva el registro.");
            await campo.Vincular(id, input.EventoId, ct);
            return Results.Ok(new { evaluacionId = id, eventoId = input.EventoId, estadoVinculo = "VINCULADA" });
        });
    }
}
public sealed record VincularDesastre(string? EventoId);
