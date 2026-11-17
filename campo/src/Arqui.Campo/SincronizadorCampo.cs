using System.Text.Json;

using System.Diagnostics.CodeAnalysis;
using System.Net.Http.Json;
using System.Text.Json.Serialization;

namespace Arqui.Campo;

internal static class SincronizadorCampo
{
    private static readonly HttpClient Cliente = new(new HttpClientHandler { AllowAutoRedirect = false })
    {
        Timeout = TimeSpan.FromSeconds(15)
    };

    public static bool TryCrearDestino(string? texto, [NotNullWhen(true)] out Uri? destino)
    {
        destino = null;
        if (!Uri.TryCreate(texto?.Trim(), UriKind.Absolute, out var servidor) ||
            !string.IsNullOrEmpty(servidor.UserInfo))
            return false;

        if (servidor.Scheme != Uri.UriSchemeHttps &&
            (servidor.Scheme != Uri.UriSchemeHttp || servidor.Host is not ("127.0.0.1" or "10.0.2.2")))
            return false;

        destino = new Uri(servidor, "/api/campo/lotes");
        return true;
    }

    public static async Task<CatalogoCampo> DescargarCatalogoAsync(AlmacenCampo almacen, Uri destino)
    {
        using var req=new HttpRequestMessage(HttpMethod.Get,new Uri(destino,"/api/desastres"));SesionCampo.Autorizar(req);
        using var response=await Cliente.SendAsync(req);response.EnsureSuccessStatusCode();
        var catalogo = await response.Content.ReadFromJsonAsync(CampoJson.Default.CatalogoCampo)
            ?? throw new InvalidDataException("Respuesta sin catálogo.");
        await almacen.GuardarCatalogoAsync(catalogo.Desastres.Select(r => r.Desastre));
        return catalogo;
    }

    public static async Task<int> EnviarAsync(AlmacenCampo almacen, Uri destino)
    {
        var pendientes = (await almacen.ListarAsync())
            .Where(r => r.NivelAfectacion is >= 1 and <= 5 && r.Estado is null or "recibida")
            .Reverse().Take(20).ToArray();

        int procesadas = 0;
        foreach (var lote in pendientes.Where(r => r.EvidenciaId is null).Chunk(20))
            procesadas += await EnviarLoteAsync(almacen, destino, lote);

        // Una foto por petición para mantenerse dentro del límite HTTP, incluso con base64.
        foreach (var registro in pendientes.Where(r => r.EvidenciaId is not null))
            procesadas += await EnviarLoteAsync(almacen, destino, [registro]);
        procesadas+=await EnviarRevisionesAsync(almacen,destino);
        return procesadas;
    }

    private static async Task<int> EnviarRevisionesAsync(AlmacenCampo almacen,Uri destino){
        int total=0;
        foreach(var r in (await almacen.RevisionesAsync()).Where(r=>r.Estado=="pendiente").Reverse()){
            using var req=new HttpRequestMessage(HttpMethod.Put,new Uri(destino,"/api/campo/evaluaciones/"+r.EvaluacionId));SesionCampo.Autorizar(req);
            req.Content=JsonContent.Create(new RevisionPeticion(r.OperacionId,r.VersionBase,r.NivelAfectacion,r.Descripcion,r.Activo,r.Latitud,r.Longitud,r.Motivo),CampoJson.Default.RevisionPeticion);
            using var response=await Cliente.SendAsync(req);
            if(response.StatusCode==System.Net.HttpStatusCode.Conflict){
                using var get=new HttpRequestMessage(HttpMethod.Get,req.RequestUri);SesionCampo.Autorizar(get);using var actual=await Cliente.SendAsync(get);actual.EnsureSuccessStatusCode();
                r.ServidorJson=await actual.Content.ReadAsStringAsync();r.Estado="conflicto";await almacen.ResultadoRevisionAsync(r);total++;continue;
            }
            response.EnsureSuccessStatusCode();var body=await response.Content.ReadFromJsonAsync(CampoJson.Default.RespuestaRevision) ?? throw new InvalidDataException();
            r.Estado="incorporada";await almacen.ResultadoRevisionAsync(r,body.Version);total++;
        }return total;
    }

    private static async Task<int> EnviarLoteAsync(AlmacenCampo almacen, Uri destino, ResumenPendiente[] lote)
    {
        var operaciones = await Task.WhenAll(lote.Select(r => almacen.ObtenerAsync(r.EvaluacionId)));
        using var req=new HttpRequestMessage(HttpMethod.Post,destino);SesionCampo.Autorizar(req);
        req.Content=JsonContent.Create(new LoteCampo(operaciones),CampoJson.Default.LoteCampo);
        using var respuesta = await Cliente.SendAsync(req);
        respuesta.EnsureSuccessStatusCode();

        var cuerpo = await respuesta.Content.ReadFromJsonAsync(CampoJson.Default.RespuestaCampo)
            ?? throw new InvalidDataException("Respuesta sin resultados.");
        foreach (var resultado in cuerpo.Resultados)
        {
            var operacion = operaciones.FirstOrDefault(op => op.OperacionId == resultado.OperacionId);
            if (operacion is not null && resultado.Estado is "incorporada" or "recibida" or "conflicto" or "rechazada")
                await almacen.ActualizarResultadoAsync(operacion.EvaluacionId, resultado.Estado, resultado.Motivo);
        }
        return cuerpo.Resultados.Length;
    }
}

internal sealed record LoteCampo(EvaluacionPendiente[] Operaciones);
internal sealed record RespuestaCampo(ResultadoCampo[] Resultados);
internal sealed record ResultadoCampo(string OperacionId, string Estado, string? Motivo);

[JsonSourceGenerationOptions(JsonSerializerDefaults.Web)]
[JsonSerializable(typeof(LoteCampo))]
[JsonSerializable(typeof(RespuestaCampo))]
[JsonSerializable(typeof(CatalogoCampo))]
[JsonSerializable(typeof(CredencialesCampo))]
[JsonSerializable(typeof(LoginCampo))]
[JsonSerializable(typeof(SesionLocal))]
[JsonSerializable(typeof(RevisionPeticion))]
[JsonSerializable(typeof(RespuestaRevision))]
[JsonSerializable(typeof(VersionCampo))]
internal partial class CampoJson : JsonSerializerContext { }

internal sealed record CatalogoCampo(bool Disponible, DateTimeOffset? ActualizadoEn, RegistroCatalogo[] Desastres);
internal sealed record RegistroCatalogo(DesastreLocal Desastre);

internal sealed record RevisionPeticion(string OperacionId,long VersionBase,int NivelAfectacion,string Descripcion,string? Activo,double Latitud,double Longitud,string Motivo);
internal sealed record RespuestaRevision(string Estado,long Version);
internal sealed record VersionCampo(long Version,int NivelAfectacion,string? Descripcion,string? Activo,double Latitud,double Longitud);
