using System.Text.Json;
using Mapas.Api.Infrastructure;
namespace Mapas.Api.Application;
public static class EvolucionEndpoints{
 public static void MapearEvolucion(this WebApplication app){
  app.MapMethods("/api/desastres/{id}/{accion}",["GET","PATCH"],async(string id,string accion,HttpContext ctx,AccesoActual acceso,IHttpClientFactory factory,CancellationToken ct)=>{
   if(accion is not ("historial" or "gravedad" or "fase"))return Results.NotFound();
   using var req=new HttpRequestMessage(new HttpMethod(ctx.Request.Method),"eventos/"+Uri.EscapeDataString(id)+"/"+accion);req.Headers.TryAddWithoutValidation("Authorization",acceso.Token);
   if(ctx.Request.Method=="PATCH"){req.Content=new StreamContent(ctx.Request.Body);req.Content.Headers.ContentType=new("application/json");}
   using var response=await factory.CreateClient("nucleo").SendAsync(req,ct);return Results.Content(await response.Content.ReadAsStringAsync(ct),"application/json",statusCode:(int)response.StatusCode);
  });
  app.MapPost("/api/campo/evaluaciones/{id:guid}/solicitudes",async(Guid id,JsonElement input,CampoRepository campo,EvaluacionesVersionadas versiones,AccesoActual acceso,IHttpClientFactory factory,IConfiguration config,CancellationToken ct)=>{
   if(!input.TryGetProperty("solicitudId",out var solicitud) || solicitud.ValueKind!=JsonValueKind.String || !solicitud.TryGetGuid(out _) || !input.TryGetProperty("tipoRecurso",out _) || !input.TryGetProperty("cantidad",out _) || !input.TryGetProperty("prioridad",out _))return Results.BadRequest(new{error="Faltan los datos de la solicitud."});
   if(await campo.Buscar(id,ct) is null)return Results.NotFound();
   using(var previaReq=new HttpRequestMessage(HttpMethod.Get,"solicitudes/por-clave/"+solicitud.GetGuid())){
    previaReq.Headers.TryAddWithoutValidation("Authorization",acceso.Token);
    using var previa=await factory.CreateClient("nucleo").SendAsync(previaReq,ct);
    if(previa.IsSuccessStatusCode){
     var existente=await previa.Content.ReadFromJsonAsync<JsonElement>(ct);
     if(existente.GetProperty("evaluacionId").ValueKind==JsonValueKind.String && existente.GetProperty("evaluacionId").GetGuid()==id && input.TryGetProperty("versionBase",out var baseAnterior) && baseAnterior.ValueKind==JsonValueKind.Number && baseAnterior.TryGetInt64(out var anterior) && existente.GetProperty("evaluacionVersion").GetInt64()==anterior && input.GetProperty("tipoRecurso").ValueKind==JsonValueKind.String && existente.GetProperty("tipoRecurso").GetString()==input.GetProperty("tipoRecurso").GetString()?.Trim() && input.GetProperty("cantidad").ValueKind==JsonValueKind.Number && input.GetProperty("cantidad").TryGetInt32(out var cantidad) && existente.GetProperty("cantidad").GetInt32()==cantidad && input.GetProperty("prioridad").ValueKind==JsonValueKind.String && existente.GetProperty("prioridad").GetString()==input.GetProperty("prioridad").GetString())return Results.Json(existente,statusCode:200);
     return Results.Conflict(new{error="La solicitudId ya corresponde a otro contenido u origen."});
    }
    if(previa.StatusCode!=System.Net.HttpStatusCode.NotFound)return Results.StatusCode((int)previa.StatusCode);
   }
   var v=await versiones.Version(id,ct);var op=await campo.Buscar(id,ct);if(op is null)return Results.NotFound();
   if(v!=await versiones.Version(id,ct))return Results.Conflict(new{error="La evaluación cambió durante la consulta."});if(!input.TryGetProperty("versionBase",out var b) || b.ValueKind!=JsonValueKind.Number || !b.TryGetInt64(out var baseVersion) || baseVersion!=v)return Results.Conflict(new{error="La evaluación cambió. Revisa su versión actual antes de solicitar recursos."});
   var vinculo=await campo.ConsultarVinculo(id,ct);if(vinculo.EstadoVinculo!="VINCULADA")return Results.BadRequest(new{error="Vincula primero la evaluación a un desastre registrado."});
   var body=new{solicitudId=input.GetProperty("solicitudId"),eventoId=vinculo.EventoId,tipoRecurso=input.GetProperty("tipoRecurso"),cantidad=input.GetProperty("cantidad"),prioridad=input.GetProperty("prioridad"),evaluacionId=id,evaluacionVersion=v,evaluacionResumen=new{op.NivelAfectacion,op.Descripcion,op.Activo,op.Latitud,op.Longitud,op.CapturadaEnUtc}};
   using var req=new HttpRequestMessage(HttpMethod.Post,"solicitudes/desde-evaluacion");req.Headers.TryAddWithoutValidation("Authorization",acceso.Token);req.Headers.Add("X-Integracion-Key",config["Integracion:Key"]);req.Content=JsonContent.Create(body);
   using var response=await factory.CreateClient("nucleo").SendAsync(req,ct);return Results.Content(await response.Content.ReadAsStringAsync(ct),"application/json",statusCode:(int)response.StatusCode);
  });
 }
}
