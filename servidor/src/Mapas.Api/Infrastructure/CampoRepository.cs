using Npgsql;
using System.Text.Json;
using Mapas.Api.Application;

namespace Mapas.Api.Infrastructure;

public sealed class CampoRepository(NpgsqlDataSource dataSource, AccesoActual acceso)
{
    public async Task Inicializar(CancellationToken ct)
    {
        await using var command = dataSource.CreateCommand("""
            CREATE TABLE IF NOT EXISTS campo_operaciones (
                operacion_id uuid PRIMARY KEY,
                evaluacion_id uuid NOT NULL UNIQUE,
                datos jsonb NOT NULL,
                incorporada boolean NOT NULL,
                recibida_en timestamptz NOT NULL DEFAULT now()
            );
            ALTER TABLE campo_operaciones ADD COLUMN IF NOT EXISTS organizacion_id text;
            ALTER TABLE campo_operaciones ADD COLUMN IF NOT EXISTS usuario text;
            ALTER TABLE "Afectaciones" ADD COLUMN IF NOT EXISTS "OrganizacionId" text;
            CREATE INDEX IF NOT EXISTS ix_campo_pendientes
                ON campo_operaciones (recibida_en) WHERE NOT incorporada;
            """);
        await command.ExecuteNonQueryAsync(ct);
    }

    public async Task<string> Recibir(Guid operacionId, Guid evaluacionId, string datos, bool inmediata, CancellationToken ct)
    {
        await using var connection = await dataSource.OpenConnectionAsync(ct);
        await using var transaction = await connection.BeginTransactionAsync(ct);
        await using var insert = new NpgsqlCommand("""
            INSERT INTO campo_operaciones (operacion_id, evaluacion_id, datos, incorporada, organizacion_id, usuario)
            VALUES (@operacion, @evaluacion, CAST(@datos AS jsonb), @inmediata, @org, @usuario)
            ON CONFLICT DO NOTHING
            """, connection, transaction);
        insert.Parameters.AddWithValue("org", acceso.Organizacion);
        insert.Parameters.AddWithValue("usuario", acceso.Usuario);
        insert.Parameters.AddWithValue("operacion", operacionId);
        insert.Parameters.AddWithValue("evaluacion", evaluacionId);
        insert.Parameters.AddWithValue("datos", datos);
        insert.Parameters.AddWithValue("inmediata", inmediata);
        await insert.ExecuteNonQueryAsync(ct);

        await using var query = new NpgsqlCommand("""
            SELECT CASE
                WHEN datos <> CAST(@datos AS jsonb) THEN 'conflicto'
                WHEN incorporada THEN 'incorporada'
                ELSE 'recibida'
            END
            FROM campo_operaciones WHERE operacion_id = @operacion AND organizacion_id = @org
            """, connection, transaction);
        query.Parameters.AddWithValue("org", acceso.Organizacion);
        query.Parameters.AddWithValue("operacion", operacionId);
        query.Parameters.AddWithValue("datos", datos);
        var estado = (string?)await query.ExecuteScalarAsync(ct) ?? "conflicto";
        await transaction.CommitAsync(ct);
        return estado;
    }

    public async Task IncorporarPendientes(CancellationToken ct)
    {
        await using var command = dataSource.CreateCommand("""
            WITH pendientes AS (
                SELECT operacion_id FROM campo_operaciones WHERE NOT incorporada
                ORDER BY recibida_en, operacion_id LIMIT 100 FOR UPDATE SKIP LOCKED
            )
            UPDATE campo_operaciones SET incorporada = true
            FROM pendientes WHERE campo_operaciones.operacion_id = pendientes.operacion_id
            """);
        await command.ExecuteNonQueryAsync(ct);
    }

    public async Task<List<PuntoPanorama>> ConsultarPanorama(string? evento, CancellationToken ct)
    {
        await using var command = dataSource.CreateCommand("""
            SELECT o.evaluacion_id, (COALESCE(r.datos,o.datos) - 'foto') || jsonb_build_object('evaluador',o.usuario,'organizacion',o.organizacion_id), o.recibida_en,
                   COALESCE(v.desastre_id, o.datos->>'eventoId'), c.datos->'desastre'
            FROM campo_operaciones o
            LEFT JOIN campo_revisiones r USING(evaluacion_id)
            LEFT JOIN campo_vinculos v ON v.evaluacion_id = o.evaluacion_id
            LEFT JOIN desastres_catalogo c ON c.id = COALESCE(v.desastre_id, o.datos->>'eventoId') AND c.datos->>'organizacionId'=o.organizacion_id
            WHERE o.organizacion_id = @org AND o.incorporada AND (@evento = '' OR COALESCE(v.desastre_id, o.datos->>'eventoId') = @evento)
            ORDER BY o.recibida_en, o.evaluacion_id
            """);
        command.Parameters.AddWithValue("org", acceso.Organizacion);
        command.Parameters.AddWithValue("evento", evento ?? "");
        await using var rows = await command.ExecuteReaderAsync(ct);
        var puntos = new List<PuntoPanorama>();
        while (await rows.ReadAsync(ct))
        {
            var op = JsonSerializer.Deserialize<OperacionCampo>(rows.GetString(1), new JsonSerializerOptions(JsonSerializerDefaults.Web))!;
            var eventoId = rows.IsDBNull(3) ? null : rows.GetString(3);
            var desastre = rows.IsDBNull(4) ? null : JsonSerializer.Deserialize<EventoDesastre>(rows.GetString(4), new JsonSerializerOptions(JsonSerializerDefaults.Web));
            puntos.Add(new(rows.GetGuid(0), "EVALUACION", op.Activo is { Length: > 0 } ? op.Activo : "Evaluación de daños",
                null, null, op.NivelAfectacion, eventoId, op.Descripcion ?? "",
                op.Latitud!.Value, op.Longitud!.Value, null, rows.GetFieldValue<DateTimeOffset>(2),
                op.Evaluador, op.Organizacion, op.Activo, op.EvidenciaId,
                EstadoVinculo(eventoId, desastre), desastre));
        }
        return puntos;
    }

    public static string EstadoVinculo(string? eventoId, EventoDesastre? desastre) =>
        string.IsNullOrWhiteSpace(eventoId) ? "PRELIMINAR" : desastre is null ? "PENDIENTE_DE_EVENTO" : "VINCULADA";

    public async Task<VinculoEvaluacion> ConsultarVinculo(Guid id, CancellationToken ct)
    {
        await using var command = dataSource.CreateCommand("""
            SELECT COALESCE(v.desastre_id, o.datos->>'eventoId'), c.datos->'desastre'
            FROM campo_operaciones o LEFT JOIN campo_revisiones r USING(evaluacion_id) LEFT JOIN campo_vinculos v ON v.evaluacion_id = o.evaluacion_id
            LEFT JOIN desastres_catalogo c ON c.id = COALESCE(v.desastre_id, o.datos->>'eventoId') AND c.datos->>'organizacionId'=o.organizacion_id
            WHERE o.organizacion_id = @org AND o.evaluacion_id = @id AND o.incorporada
            """);
        if(command.CommandText.Contains("@org")) command.Parameters.AddWithValue("org", acceso.Organizacion);
        command.Parameters.AddWithValue("id", id);
        await using var row = await command.ExecuteReaderAsync(ct);
        if (!await row.ReadAsync(ct)) return new(null, "PRELIMINAR", null);
        var eventoId = row.IsDBNull(0) ? null : row.GetString(0);
        var desastre = row.IsDBNull(1) ? null : JsonSerializer.Deserialize<EventoDesastre>(row.GetString(1), new JsonSerializerOptions(JsonSerializerDefaults.Web));
        return new(eventoId, EstadoVinculo(eventoId, desastre), desastre);
    }

    public async Task Vincular(Guid id, string eventoId, CancellationToken ct)
    {
        await using var command = dataSource.CreateCommand("""
            INSERT INTO campo_vinculos(evaluacion_id, desastre_id) VALUES (@id, @evento)
            ON CONFLICT (evaluacion_id) DO UPDATE SET desastre_id = EXCLUDED.desastre_id, vinculada_en = now()
            """);
        if(command.CommandText.Contains("@org")) command.Parameters.AddWithValue("org", acceso.Organizacion);
        command.Parameters.AddWithValue("id", id);
        command.Parameters.AddWithValue("evento", eventoId);
        await command.ExecuteNonQueryAsync(ct);
    }

    public async Task<OperacionCampo?> Buscar(Guid id, CancellationToken ct)
    {
        await using var command = dataSource.CreateCommand("SELECT (COALESCE(r.datos,o.datos) || jsonb_build_object('evaluador',o.usuario,'organizacion',o.organizacion_id))::text FROM campo_operaciones o LEFT JOIN campo_revisiones r USING(evaluacion_id) WHERE o.organizacion_id = @org AND o.evaluacion_id = @id AND o.incorporada");
        if(command.CommandText.Contains("@org")) command.Parameters.AddWithValue("org", acceso.Organizacion);
        command.Parameters.AddWithValue("id", id);
        var datos = (string?)await command.ExecuteScalarAsync(ct);
        return datos is null ? null : JsonSerializer.Deserialize<OperacionCampo>(datos, new JsonSerializerOptions(JsonSerializerDefaults.Web));
    }

    public async Task<List<SitioMapa>> Consultar(string? evento, CancellationToken ct)
    {
        await using var command = dataSource.CreateCommand("""
            SELECT o.evaluacion_id, COALESCE(v.desastre_id, o.datos->>'eventoId', ''), (COALESCE(r.datos,o.datos)->>'latitud')::double precision,
                   (COALESCE(r.datos,o.datos)->>'longitud')::double precision, (COALESCE(r.datos,o.datos)->>'nivelAfectacion')::integer
            FROM campo_operaciones o LEFT JOIN campo_revisiones r USING(evaluacion_id) LEFT JOIN campo_vinculos v ON v.evaluacion_id = o.evaluacion_id
            WHERE o.organizacion_id = @org AND o.incorporada AND (@evento = '' OR COALESCE(v.desastre_id, o.datos->>'eventoId') = @evento)
            ORDER BY o.recibida_en, o.evaluacion_id
            """);
        command.Parameters.AddWithValue("org", acceso.Organizacion);
        command.Parameters.AddWithValue("evento", evento ?? "");
        await using var rows = await command.ExecuteReaderAsync(ct);
        var sitios = new List<SitioMapa>();
        while (await rows.ReadAsync(ct))
            sitios.Add(new(rows.GetGuid(0), rows.GetString(1), rows.GetDouble(2), rows.GetDouble(3), rows.GetInt32(4)));
        return sitios;
    }
}

public sealed record VinculoEvaluacion(string? EventoId, string EstadoVinculo, EventoDesastre? Desastre);

public sealed record SitioMapa(Guid EvaluacionId, string EventoId, double Latitud, double Longitud, int NivelAfectacion);

public sealed class CampoWorker(CampoRepository repository, ILogger<CampoWorker> logger) : BackgroundService
{
    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        using var timer = new PeriodicTimer(TimeSpan.FromSeconds(2));
        while (await timer.WaitForNextTickAsync(stoppingToken))
        {
            try
            {
                await repository.IncorporarPendientes(stoppingToken);
            }
            catch (NpgsqlException exception)
            {
                logger.LogWarning(exception, "El lote de campo se reintentará en el siguiente ciclo.");
            }
        }
    }
}
