namespace Mapas.Api.Domain;

public sealed class Afectacion
{
    public Guid Id { get; set; } = Guid.NewGuid();
    public string Titulo { get; set; } = "";
    public string Tipo { get; set; } = "";
    public string Severidad { get; set; } = "";
    public string Descripcion { get; set; } = "";
    public double Latitud { get; set; }
    public double Longitud { get; set; }
    // Precisión de milisegundos estable entre la respuesta de creación y PostgreSQL.
    public DateTimeOffset RegistradaEn { get; set; } = DateTimeOffset.FromUnixTimeMilliseconds(DateTimeOffset.UtcNow.ToUnixTimeMilliseconds());
    public Guid Version { get; set; } = Guid.NewGuid();
}
