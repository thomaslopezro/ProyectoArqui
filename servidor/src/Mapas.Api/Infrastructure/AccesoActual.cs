using System.Net.Http.Headers;
using System.Text.Json;
namespace Mapas.Api.Infrastructure;
public sealed class AccesoActual(IHttpContextAccessor accessor) {
 public string Organizacion => accessor.HttpContext?.Items["organizacionId"]?.ToString() ?? "";
 public string Usuario => accessor.HttpContext?.Items["usuario"]?.ToString() ?? "";
 public string Rol => accessor.HttpContext?.Items["rol"]?.ToString() ?? "";
 public string Token => accessor.HttpContext?.Request.Headers.Authorization.ToString() ?? "";
}
public static class Autenticacion {
 public static void MapearAutenticacion(this WebApplication app) {
  app.MapPost("/api/auth/login", async (JsonElement body,IHttpClientFactory factory,CancellationToken ct) => {
   try { using var r=await factory.CreateClient("nucleo").PostAsJsonAsync("auth/login",body,ct);
    return Results.Content(await r.Content.ReadAsStringAsync(ct),"application/json",statusCode:(int)r.StatusCode);
   } catch(HttpRequestException) { return Results.Problem(statusCode:503,title:"El servicio de identidad no está disponible."); }
  });
  app.MapMethods("/api/auth/{accion}",["GET","POST"],async (string accion,HttpContext ctx,AccesoActual acceso,IHttpClientFactory factory,CancellationToken ct)=>{
   if(accion is not ("me" or "logout" or "usuarios"))return Results.NotFound();
   using var req=new HttpRequestMessage(new HttpMethod(ctx.Request.Method),"auth/"+accion);
   req.Headers.TryAddWithoutValidation("Authorization",acceso.Token);
   if(ctx.Request.Method=="POST") { req.Content=new StreamContent(ctx.Request.Body);req.Content.Headers.ContentType=new MediaTypeHeaderValue("application/json"); }
   using var response=await factory.CreateClient("nucleo").SendAsync(req,ct);
   return Results.Content(await response.Content.ReadAsStringAsync(ct),"application/json",statusCode:(int)response.StatusCode);
  });
 }
 public static async Task Verificar(HttpContext ctx,RequestDelegate next,IHttpClientFactory factory) {
  var path=ctx.Request.Path.Value ?? "";
  if(!path.StartsWith("/api/") || path is "/api/salud" or "/api/auth/login"){await next(ctx);return;}
  var token=ctx.Request.Headers.Authorization.ToString();
  if(!token.StartsWith("Bearer ")) {ctx.Response.StatusCode=401;await ctx.Response.WriteAsJsonAsync(new {error="INICIA_SESION"});return;}
  try {
   using var req=new HttpRequestMessage(HttpMethod.Get,"auth/me");req.Headers.TryAddWithoutValidation("Authorization",token);
   using var response=await factory.CreateClient("nucleo").SendAsync(req,ctx.RequestAborted);
   if(!response.IsSuccessStatusCode){ctx.Response.StatusCode=(int)response.StatusCode;return;}
   var actor=await response.Content.ReadFromJsonAsync<JsonElement>(ctx.RequestAborted);
   foreach(var key in new[]{"organizacionId","usuario","rol"})ctx.Items[key]=actor.GetProperty(key).GetString();
   bool write=ctx.Request.Method is not ("GET" or "HEAD");
   var rol=ctx.Items["rol"]?.ToString();
   bool propia=path is "/api/auth/logout";
   bool evaluacion=path.StartsWith("/api/campo/") || path=="/api/solicitudes" && ctx.Request.Method=="POST";
   if(write && !propia && (rol=="CONSULTA" || !evaluacion && rol!="COORDINADOR")) {ctx.Response.StatusCode=403;return;}
  }catch(Exception e) when(e is HttpRequestException or TaskCanceledException or JsonException){ctx.Response.StatusCode=503;return;}
  await next(ctx);
 }
}
