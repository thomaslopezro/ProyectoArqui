using System.Text.Json;
using Mapas.Api.Application;
using Mapas.Api.Infrastructure;
using Microsoft.EntityFrameworkCore;
using Npgsql;

var builder = WebApplication.CreateBuilder(args);
builder.Services.AddProblemDetails();
builder.Services.AddHttpContextAccessor();
builder.Services.AddSingleton<AccesoActual>();
builder.Services.AddHttpClient("nucleo", client => {
 client.BaseAddress=new Uri(builder.Configuration["Nucleo:Url"]!);client.Timeout=TimeSpan.FromSeconds(5);
});
builder.WebHost.ConfigureKestrel(options => options.Limits.MaxRequestBodySize = 2 * 1024 * 1024);
var connection = builder.Configuration.GetConnectionString("Mapas")
    ?? throw new InvalidOperationException("Configura ConnectionStrings__Mapas.");
builder.Services.AddDbContext<MapasDb>(options => options.UseNpgsql(connection));
builder.Services.AddScoped<IAfectacionesRepository, EfAfectacionesRepository>();
builder.Services.AddScoped<AfectacionesService>();
var diferida = builder.Configuration.GetValue<bool>("Campo:Diferida");
builder.Services.AddSingleton(_ => NpgsqlDataSource.Create(connection));
builder.Services.AddSingleton<CampoRepository>();
builder.Services.AddSingleton<EvaluacionesVersionadas>();
builder.Services.AddHttpClient("desastres", client => {
    client.BaseAddress = new Uri(builder.Configuration["Desastres:Url"] ?? "http://desastres:8080/gestion-emergencias/api/");
    client.Timeout = TimeSpan.FromSeconds(3);
});
builder.Services.AddSingleton<DesastresCatalogo>();
builder.Services.AddHostedService<DesastresWorker>();
// El trabajador también recupera pendientes si se cambia al modo inmediato.
builder.Services.AddHostedService<CampoWorker>();
var app = builder.Build();
app.UseExceptionHandler();
app.Use((ctx,next)=>Autenticacion.Verificar(ctx,next,app.Services.GetRequiredService<IHttpClientFactory>()));
app.Use(async (context, next) =>
{
    if (context.Request.Path == "/api/campo/lotes" &&
        context.Features.Get<Microsoft.AspNetCore.Http.Features.IHttpMaxRequestBodySizeFeature>() is { IsReadOnly: false } limit)
        limit.MaxRequestBodySize = 8 * 1024 * 1024;
    await next(context);
});
app.Use(async (context, next) => {
    try { await next(context); }
    catch (ValidationError e) { await Results.ValidationProblem(e.Errors).ExecuteAsync(context); }
    catch (Exception e) when (e is VersionConflict or DbUpdateConcurrencyException)
    { await Results.Problem(statusCode: 409, title: "El registro cambió. Actualiza la lista antes de volver a intentarlo.").ExecuteAsync(context); }
});
// Esquema inicial de la entrega académica. Para evolución del esquema: migraciones versionadas.
using (var scope = app.Services.CreateScope())
{
    var db = scope.ServiceProvider.GetRequiredService<MapasDb>();
    for (var attempt = 0; !await db.Database.CanConnectAsync(); attempt++)
    {
        if (attempt >= 29) throw new InvalidOperationException("PostgreSQL no está disponible tras 30 intentos.");
        await Task.Delay(TimeSpan.FromSeconds(1));
    }
    await db.Database.EnsureCreatedAsync();
}
await app.Services.GetRequiredService<CampoRepository>().Inicializar(app.Lifetime.ApplicationStopping);
await app.Services.GetRequiredService<DesastresCatalogo>().Inicializar(app.Lifetime.ApplicationStopping);
await app.Services.GetRequiredService<EvaluacionesVersionadas>().Inicializar(app.Lifetime.ApplicationStopping);
app.MapearAutenticacion();
app.MapearSolicitudes();
app.MapearEvolucion();
app.MapearDesastres();
app.MapearCampo(diferida);
app.MapearPanorama();
app.MapGet("/api/salud", async (MapasDb db, CancellationToken ct) =>
    await db.Database.CanConnectAsync(ct) ? Results.Ok(new { estado = "LISTO" }) : Results.Problem(statusCode: 503));
app.MapGet("/api/catalogos", () => new { tipos = AfectacionesService.Tipos, severidades = AfectacionesService.Severidades });
app.MapGet("/api/afectaciones", async (string? tipo, string? severidad, AfectacionesService service, CancellationToken ct) =>
    Results.Ok(await service.Listar(tipo, severidad, ct)));
app.MapGet("/api/afectaciones/{id:guid}", async (Guid id, AfectacionesService service, CancellationToken ct) =>
    await service.Buscar(id, ct) is { } item ? Results.Ok(item) : Results.NotFound());
app.MapPost("/api/afectaciones", async (AfectacionInput input, AfectacionesService service, CancellationToken ct) => {
    var item = await service.Crear(input, ct);
    return Results.Created($"/api/afectaciones/{item.Id}", item);
});
app.MapPut("/api/afectaciones/{id:guid}", async (Guid id, ActualizarInput input, AfectacionesService service, CancellationToken ct) =>
    await service.Actualizar(id, input, ct) is { } item ? Results.Ok(item) : Results.NotFound());
app.MapDelete("/api/afectaciones/{id:guid}", async (Guid id, Guid version, AfectacionesService service, CancellationToken ct) =>
    await service.Eliminar(id, version, ct) ? Results.NoContent() : Results.NotFound());
app.MapGet("/api/geojson", async (string? tipo, string? severidad, AfectacionesService service, CancellationToken ct) =>
    Results.Json(GeoJson.Exportar(await service.Listar(tipo, severidad, ct)), contentType: "application/geo+json"));
app.MapPost("/api/importaciones/geojson", async (JsonElement document, AfectacionesService service, CancellationToken ct) =>
    Results.Ok(new { importadas = await service.Importar(GeoJson.Leer(document), ct) }));
app.Run();
