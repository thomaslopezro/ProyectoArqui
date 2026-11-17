using Mapas.Api.Infrastructure;
namespace Mapas.Api.Application;
public static class SolicitudesEndpoints {
 public static void MapearSolicitudes(this WebApplication app){
  app.MapMethods("/api/solicitudes/{**ruta}",["GET","POST","PATCH"],async (HttpContext context,AccesoActual acceso,IHttpClientFactory factory,CancellationToken ct)=>{
   var path=context.Request.Path.Value!.Substring("/api/".Length)+context.Request.QueryString;
   using var req=new HttpRequestMessage(new HttpMethod(context.Request.Method),path);
   req.Headers.TryAddWithoutValidation("Authorization",acceso.Token);
   if(context.Request.ContentLength is >0 || context.Request.Headers.ContainsKey("Transfer-Encoding")){
    req.Content=new StreamContent(context.Request.Body);
    req.Content.Headers.ContentType=new System.Net.Http.Headers.MediaTypeHeaderValue("application/json");
   }
   try{using var response=await factory.CreateClient("nucleo").SendAsync(req,ct);
    return Results.Content(await response.Content.ReadAsStringAsync(ct),"application/json",statusCode:(int)response.StatusCode);
   }catch(Exception e) when(e is HttpRequestException || e is TaskCanceledException && !ct.IsCancellationRequested){
    return Results.Problem(statusCode:503,title:"No se pudo confirmar la operación. Conserva la misma solicitud al reintentar.");
   }
  });
 }
}
