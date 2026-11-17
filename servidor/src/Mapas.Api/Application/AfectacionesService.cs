using Mapas.Api.Domain;

namespace Mapas.Api.Application;

public sealed record AfectacionInput(string? Titulo, string? Tipo, string? Severidad,
    string? Descripcion, double? Latitud, double? Longitud);
public sealed record ActualizarInput(string? Titulo, string? Tipo, string? Severidad,
    string? Descripcion, double? Latitud, double? Longitud, Guid Version);

public sealed class ValidationError(Dictionary<string, string[]> errors) : Exception("Datos inválidos")
{
    public Dictionary<string, string[]> Errors { get; } = errors;
}

public interface IAfectacionesRepository
{
    Task<List<Afectacion>> Listar(string? tipo, string? severidad, CancellationToken ct);
    Task<Afectacion?> Buscar(Guid id, CancellationToken ct);
    Task Agregar(IReadOnlyCollection<Afectacion> items, CancellationToken ct);
    Task Guardar(CancellationToken ct);
    Task Eliminar(Afectacion item, CancellationToken ct);
}

public sealed class AfectacionesService(IAfectacionesRepository repository)
{
    public static readonly string[] Tipos = ["INUNDACION", "INCENDIO", "DESLIZAMIENTO", "TERREMOTO", "OTRO"];
    public static readonly string[] Severidades = ["BAJA", "MEDIA", "ALTA", "CRITICA"];

    public static Afectacion Validar(AfectacionInput input)
    {
        var errors = new Dictionary<string, string[]>();
        var titulo = input.Titulo?.Trim() ?? "";
        var descripcion = input.Descripcion?.Trim() ?? "";
        var tipo = input.Tipo?.Trim().ToUpperInvariant() ?? "";
        var severidad = input.Severidad?.Trim().ToUpperInvariant() ?? "";
        if (titulo.Length is < 3 or > 120) errors["titulo"] = ["Escribe entre 3 y 120 caracteres."];
        if (descripcion.Length > 2000) errors["descripcion"] = ["Máximo 2000 caracteres."];
        if (!Tipos.Contains(tipo)) errors["tipo"] = ["Tipo de afectación desconocido."];
        if (!Severidades.Contains(severidad)) errors["severidad"] = ["Severidad desconocida."];
        if (input.Latitud is not double lat || !double.IsFinite(lat) || lat < -90 || lat > 90)
            errors["latitud"] = ["Latitud obligatoria entre -90 y 90."];
        if (input.Longitud is not double lon || !double.IsFinite(lon) || lon < -180 || lon > 180)
            errors["longitud"] = ["Longitud obligatoria entre -180 y 180."];
        if (errors.Count > 0) throw new ValidationError(errors);
        return new Afectacion { Titulo = titulo, Descripcion = descripcion, Tipo = tipo,
            Severidad = severidad, Latitud = input.Latitud!.Value, Longitud = input.Longitud!.Value };
    }

    public Task<List<Afectacion>> Listar(string? tipo, string? severidad, CancellationToken ct)
    {
        var errors = new Dictionary<string, string[]>();
        if (tipo is not null && !Tipos.Contains(tipo)) errors["tipo"] = ["Filtro de tipo desconocido."];
        if (severidad is not null && !Severidades.Contains(severidad)) errors["severidad"] = ["Filtro de severidad desconocido."];
        if (errors.Count > 0) throw new ValidationError(errors);
        return repository.Listar(tipo, severidad, ct);
    }
    public Task<Afectacion?> Buscar(Guid id, CancellationToken ct) => repository.Buscar(id, ct);
    public async Task<Afectacion> Crear(AfectacionInput input, CancellationToken ct)
    {
        var item = Validar(input);
        await repository.Agregar([item], ct);
        return item;
    }
    public async Task<Afectacion?> Actualizar(Guid id, ActualizarInput input, CancellationToken ct)
    {
        var validated = Validar(new(input.Titulo, input.Tipo, input.Severidad, input.Descripcion, input.Latitud, input.Longitud));
        var item = await repository.Buscar(id, ct);
        if (item is null) return null;
        if (item.Version != input.Version) throw new VersionConflict();
        item.Titulo = validated.Titulo; item.Tipo = validated.Tipo; item.Severidad = validated.Severidad;
        item.Descripcion = validated.Descripcion; item.Latitud = validated.Latitud; item.Longitud = validated.Longitud;
        item.Version = Guid.NewGuid();
        await repository.Guardar(ct);
        return item;
    }
    public async Task<bool> Eliminar(Guid id, Guid version, CancellationToken ct)
    {
        var item = await repository.Buscar(id, ct);
        if (item is null) return false;
        if (item.Version != version) throw new VersionConflict();
        await repository.Eliminar(item, ct);
        return true;
    }
    public async Task<int> Importar(IReadOnlyList<AfectacionInput> inputs, CancellationToken ct)
    {
        if (inputs.Count is < 1 or > 500) throw new ValidationError(new() { ["features"] = ["Importa entre 1 y 500 puntos."] });
        // Se valida el lote completo antes de iniciar una única transacción de guardado.
        var items = new List<Afectacion>();
        var errors = new Dictionary<string, string[]>();
        for (var i = 0; i < inputs.Count; i++)
        {
            try { items.Add(Validar(inputs[i])); }
            catch (ValidationError e) { foreach (var error in e.Errors) errors[$"features[{i}].{error.Key}"] = error.Value; }
        }
        if (errors.Count > 0) throw new ValidationError(errors);
        await repository.Agregar(items, ct);
        return items.Count;
    }
}
public sealed class VersionConflict : Exception;
