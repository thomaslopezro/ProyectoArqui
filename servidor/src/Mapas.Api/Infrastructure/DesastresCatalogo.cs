using System.Net;
using System.Net.Http.Json;
using System.Text.Json;
using Mapas.Api.Application;
using Npgsql;

namespace Mapas.Api.Infrastructure;

public sealed record EventoDesastre(string Id, string Tipo, string Ubicacion, string Descripcion, string Severidad, string Fecha, long Version=0, string? EventoId=null, string? Clase=null, string? Fase=null);
public sealed record SistemaNotificacion(string Id, string Nombre, string Correo);
public sealed record RegistroDesastre(EventoDesastre Desastre, string? RegistradoEn,
    bool NotificacionPublicada, SistemaNotificacion[] Destinatarios, string[] CorreosEnviados, string? OrganizacionId = null);
public sealed record CatalogoDesastres(bool Disponible, DateTimeOffset? ActualizadoEn, RegistroDesastre[] Desastres);

// Java sigue siendo la autoridad. Esta copia de consulta no crea ni modifica desastres.
public sealed class DesastresCatalogo(NpgsqlDataSource dataSource, IHttpClientFactory factory, AccesoActual acceso, IConfiguration config)
{
    private static readonly JsonSerializerOptions Json = new(JsonSerializerDefaults.Web);
    private readonly SemaphoreSlim puerta = new(1);
    private DateTimeOffset ultimoIntento;
    public bool Disponible { get; private set; }

    public async Task Inicializar(CancellationToken ct)
    {
        await using var command = dataSource.CreateCommand("""
            CREATE TABLE IF NOT EXISTS desastres_catalogo (
                id text PRIMARY KEY, datos jsonb NOT NULL, actualizado_en timestamptz NOT NULL
            );
            CREATE TABLE IF NOT EXISTS desastres_catalogo_estado (
                unico boolean PRIMARY KEY CHECK (unico), actualizado_en timestamptz NOT NULL
            );
            CREATE TABLE IF NOT EXISTS campo_vinculos (
                evaluacion_id uuid PRIMARY KEY REFERENCES campo_operaciones(evaluacion_id),
                desastre_id text NOT NULL REFERENCES desastres_catalogo(id),
                vinculada_en timestamptz NOT NULL DEFAULT now()
            );
            """);
        await command.ExecuteNonQueryAsync(ct);
    }

    public async Task Refrescar(CancellationToken ct)
    {
        await puerta.WaitAsync(ct);
        try
        {
            if (DateTimeOffset.UtcNow - ultimoIntento < TimeSpan.FromSeconds(1)) return;
            ultimoIntento = DateTimeOffset.UtcNow;
            RegistroDesastre[] registros;
            try
            {
                using var req=new HttpRequestMessage(HttpMethod.Get,"integracion/catalogo");
                req.Headers.Add("X-Integracion-Key",config["Integracion:Key"]);
                using var response=await factory.CreateClient("nucleo").SendAsync(req,ct);
                response.EnsureSuccessStatusCode();
                registros = await response.Content.ReadFromJsonAsync<RegistroDesastre[]>(Json, ct)
                    ?? throw new JsonException("Respuesta vacía de desastres");
                if (registros.Any(r => r.Desastre is null || string.IsNullOrWhiteSpace(r.Desastre.Id)))
                    throw new JsonException("Registro de desastres inválido");
            }
            catch (Exception e) when (e is HttpRequestException or JsonException || e is TaskCanceledException && !ct.IsCancellationRequested)
            {
                Disponible = false;
                return; // Conservar la copia previa; nunca borrar evaluaciones por una caída de Java.
            }
            // Taller 3 is now the notification delivery projection, never the event authority.
            try {
                var avisos=await factory.CreateClient("desastres").GetFromJsonAsync<RegistroDesastre[]>("desastres",Json,ct) ?? [];
                registros=registros.Select(r=>avisos.FirstOrDefault(a=>(a.Desastre.EventoId??a.Desastre.Id)==r.Desastre.Id && a.Desastre.Version==r.Desastre.Version && a.OrganizacionId==r.OrganizacionId) is {} a
                    ? r with {NotificacionPublicada=a.NotificacionPublicada,Destinatarios=a.Destinatarios,CorreosEnviados=a.CorreosEnviados}:r).ToArray();
            } catch(Exception e) when(e is HttpRequestException or JsonException or TaskCanceledException) { }
            await using var connection = await dataSource.OpenConnectionAsync(ct);
            await using var transaction = await connection.BeginTransactionAsync(ct);
            var ahora = DateTimeOffset.UtcNow;
            foreach (var registro in registros)
            {
                await using var insert = new NpgsqlCommand("""
                    INSERT INTO desastres_catalogo(id, datos, actualizado_en) VALUES (@id, CAST(@datos AS jsonb), @ahora)
                    ON CONFLICT (id) DO UPDATE SET datos = EXCLUDED.datos, actualizado_en = EXCLUDED.actualizado_en
                    """, connection, transaction);
                insert.Parameters.AddWithValue("id", registro.Desastre.Id);
                insert.Parameters.AddWithValue("datos", JsonSerializer.Serialize(registro, Json));
                insert.Parameters.AddWithValue("ahora", ahora);
                await insert.ExecuteNonQueryAsync(ct);
            }
            // El taller no permite borrar desastres. Una desaparición de la autoridad no borra su copia histórica.
            await using var estado = new NpgsqlCommand("""
                INSERT INTO desastres_catalogo_estado VALUES (true, @ahora)
                ON CONFLICT (unico) DO UPDATE SET actualizado_en = EXCLUDED.actualizado_en
                """, connection, transaction);
            estado.Parameters.AddWithValue("ahora", ahora);
            await estado.ExecuteNonQueryAsync(ct);
            await transaction.CommitAsync(ct);
            Disponible = true;
        }
        finally { puerta.Release(); }
    }

    public async Task<CatalogoDesastres> Consultar(CancellationToken ct)
    {
        await using var command = dataSource.CreateCommand("SELECT datos FROM desastres_catalogo WHERE datos->>'organizacionId'=@org ORDER BY id");
        command.Parameters.AddWithValue("org",acceso.Organizacion);
        var registros = new List<RegistroDesastre>();
        await using (var rows = await command.ExecuteReaderAsync(ct))
            while (await rows.ReadAsync(ct))
                registros.Add(JsonSerializer.Deserialize<RegistroDesastre>(rows.GetString(0), Json)!);
        await using var estado = dataSource.CreateCommand("SELECT actualizado_en FROM desastres_catalogo_estado WHERE unico");
        var actualizado = await estado.ExecuteScalarAsync(ct);
        return new(Disponible, actualizado is DateTime d ? new DateTimeOffset(d) : null, registros.ToArray());
    }

    public async Task<bool> Contiene(string id, CancellationToken ct)
    {
        await using var command = dataSource.CreateCommand("SELECT EXISTS(SELECT 1 FROM desastres_catalogo WHERE id = @id AND datos->>'organizacionId'=@org)");
        command.Parameters.AddWithValue("org",acceso.Organizacion);
        command.Parameters.AddWithValue("id", id);
        return (bool)(await command.ExecuteScalarAsync(ct))!;
    }

    public async Task<IResult> Enviar(string ruta, JsonElement body, CancellationToken ct)
    {
        try
        {
            using var req=new HttpRequestMessage(HttpMethod.Post,ruta=="desastres"?"integracion/registro":"sistemas");
            var client=factory.CreateClient(ruta=="desastres"?"nucleo":"desastres");
            if(ruta=="desastres")req.Headers.TryAddWithoutValidation("Authorization",acceso.Token);
            else {var obj=JsonSerializer.Deserialize<Dictionary<string,JsonElement>>(body.GetRawText())!;
                obj["organizacionId"]=JsonSerializer.SerializeToElement(acceso.Organizacion);body=JsonSerializer.SerializeToElement(obj);}
            req.Content=JsonContent.Create(body);
            using var response = await client.SendAsync(req,ct);
            // El 202 del Taller 3 significa recibido en cola; no se anuncia incorporación.
            return Results.Content(await response.Content.ReadAsStringAsync(ct), "application/json", statusCode: (int)response.StatusCode);
        }
        catch (Exception e) when (e is HttpRequestException || e is TaskCanceledException && !ct.IsCancellationRequested)
        {
            Disponible = false;
            return Results.Problem(statusCode: 503, title: "No se pudo confirmar la recepción. Conserva el mismo identificador al reintentar.");
        }
    }

    public async Task<IResult> Sistemas(CancellationToken ct)
    {
        try
        {
            using var response = await factory.CreateClient("desastres").GetAsync("sistemas", ct);
            response.EnsureSuccessStatusCode();
            var rows=await response.Content.ReadFromJsonAsync<JsonElement[]>(ct) ?? [];
            return Results.Ok(rows.Where(s=>s.TryGetProperty("organizacionId",out var org) && org.GetString()==acceso.Organizacion));
        }
        catch (Exception e) when (e is HttpRequestException || e is TaskCanceledException && !ct.IsCancellationRequested)
        { return Results.Problem(statusCode: 503, title: "El registro de sistemas no está disponible."); }
    }
}

public sealed class DesastresWorker(DesastresCatalogo catalogo, ILogger<DesastresWorker> logger) : BackgroundService
{
    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        using var timer = new PeriodicTimer(TimeSpan.FromSeconds(5));
        do
        {
            try { await catalogo.Refrescar(stoppingToken); }
            catch (NpgsqlException e) { logger.LogWarning(e, "Se reintentará la actualización del catálogo de desastres."); }
        } while (await timer.WaitForNextTickAsync(stoppingToken));
    }
}
