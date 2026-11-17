#if CAMPO_PRUEBAS
using Android.App;
using Android.OS;
using System.Text.Json;
using System.Text.Json.Nodes;
using Microsoft.Maui.Storage;
namespace Arqui.Campo;
[Instrumentation(Name="co.edu.arqui.campo.PruebasCampo",TargetPackage="co.edu.arqui.campo")]
[Android.Runtime.Preserve(AllMembers=true)]
public class PruebasCampo:Instrumentation {
 public PruebasCampo(){}
 protected PruebasCampo(nint handle,Android.Runtime.JniHandleOwnership ownership):base(handle,ownership){}
 string usuario="",clave="";
 public override void OnCreate(Bundle? args){base.OnCreate(args);usuario=args?.GetString("usuario")??"";clave=args?.GetString("clave")??"";Start();}
 void Estado(string text){var b=new Bundle();b.PutString("fase",text);SendStatus(0,b);}
 void Check(bool ok,string msg){if(!ok)throw new Exception(msg);Estado("OK: "+msg);}
 public override void OnStart(){var result=new Bundle();try{Ejecutar().GetAwaiter().GetResult();result.PutString("stream","APROBADO: SQLite, login, desconexión real y resolución de conflictos en Android\n");Finish(Result.Ok,result);}catch(Exception e){result.PutString("stream","FALLO: "+e.GetType().Name+" "+e.Message+"\n");Finish(Result.Canceled,result);}}
 async Task<JsonObject> Http(HttpMethod method,Uri uri,JsonObject? body=null){using var http=new HttpClient{Timeout=TimeSpan.FromSeconds(3)};using var req=new HttpRequestMessage(method,uri);SesionCampo.Autorizar(req);if(body is not null)req.Content=new StringContent(body.ToJsonString(),System.Text.Encoding.UTF8,"application/json");using var res=await http.SendAsync(req);res.EnsureSuccessStatusCode();return JsonNode.Parse(await res.Content.ReadAsStringAsync())!.AsObject();}
 async Task Ejecutar(){
  var respaldo=await SecureStorage.Default.GetAsync("arqui-sesion");var destino=new Uri("http://127.0.0.1:8084/api/campo/lotes");
  try{
   var existente=System.IO.Path.Combine(FileSystem.AppDataDirectory,"campo.db3");
   if(System.IO.File.Exists(existente)){
    var originalDb=new SQLite.SQLiteAsyncConnection(existente,SQLite.SQLiteOpenFlags.ReadOnly|SQLite.SQLiteOpenFlags.FullMutex);
    var cantidad=await originalDb.ExecuteScalarAsync<int>("SELECT count(*) FROM EvaluacionPendiente");var copia=System.IO.Path.Combine(FileSystem.CacheDirectory,"migracion-prueba-"+Guid.NewGuid().ToString("N")+".sqlite");
    await originalDb.BackupAsync(copia);var migrada=new AlmacenCampo(copia);await migrada.LegadoPendienteAsync();
    var comprobar=new SQLite.SQLiteAsyncConnection(copia);Check(await comprobar.ExecuteScalarAsync<int>("SELECT count(*) FROM EvaluacionPendiente")==cantidad,"migración conserva capturas de una copia de la base anterior");
   }
   await SesionCampo.Entrar(destino,usuario,clave);Check(!string.IsNullOrEmpty(SesionCampo.Token),"login real del APK");
   var id="ANDROID-"+Guid.NewGuid().ToString("N");
   await Http(HttpMethod.Post,new Uri(destino,"/api/desastres"),new JsonObject{{"id",id},{"tipo","INUNDACION"},{"ubicacion","Zona sintética Android"},{"descripcion","Prueba instrumentada"},{"severidad","BAJA"},{"fecha",DateTimeOffset.UtcNow.ToString("O")}});
   var ruta=System.IO.Path.Combine(FileSystem.CacheDirectory,"prueba-evolucion-"+Guid.NewGuid().ToString("N")+".sqlite");var almacen=new AlmacenCampo(ruta);
   await SincronizadorCampo.DescargarCatalogoAsync(almacen,destino);
   var original=new EvaluacionPendiente{OrganizacionLocal=SesionCampo.Organizacion,UsuarioLocal=SesionCampo.Usuario,EventoId=id,NivelAfectacion=2,Activo="Escuela",Descripcion="Captura original Android",Latitud=4.6,Longitud=-74.1};await almacen.GuardarAsync(original);
   await SincronizadorCampo.EnviarAsync(almacen,destino);Check((await almacen.ObtenerAsync(original.EvaluacionId)).Estado=="incorporada","captura Android sincronizada");
   var revision=new RevisionLocal{EvaluacionId=original.EvaluacionId,OrganizacionLocal=SesionCampo.Organizacion,UsuarioLocal=SesionCampo.Usuario,VersionBase=0,NivelAfectacion=5,Descripcion="Propuesta offline Android",Activo="Escuela",Latitud=4.6,Longitud=-74.1,Motivo="Visita de campo"};
   Estado("DETENER_API");bool desconectado=false;
   for(int n=0;n<30;n++){try{await Http(HttpMethod.Get,new Uri(destino,"/api/auth/me"));}catch(HttpRequestException){desconectado=true;break;}catch(TaskCanceledException){desconectado=true;break;}catch(System.Net.WebException){desconectado=true;break;}await Task.Delay(250);}
   Check(desconectado,"servidor desconectado de verdad");await almacen.GuardarRevisionAsync(revision);
   var reabierto=new AlmacenCampo(ruta);Check((await reabierto.RevisionesAsync(original.EvaluacionId)).Any(r=>r.Estado=="pendiente" && r.Descripcion==revision.Descripcion),"corrección persiste al reabrir SQLite sin conexión");
   try{await SincronizadorCampo.EnviarAsync(reabierto,destino);}catch(HttpRequestException){}catch(TaskCanceledException){}catch(System.Net.WebException){}
   Check((await reabierto.RevisionesAsync(original.EvaluacionId))[0].Estado=="pendiente","fallo de red conserva propuesta pendiente");
   Estado("RESTAURAR_API");bool conectado=false;
   for(int n=0;n<30;n++){try{await Http(HttpMethod.Get,new Uri(destino,"/api/auth/me"));conectado=true;break;}catch(HttpRequestException){}catch(TaskCanceledException){}catch(System.Net.WebException){}await Task.Delay(250);}
   Check(conectado,"conexión restaurada");
   var patch=new JsonObject{{"operacionId",Guid.NewGuid().ToString()},{"versionBase",0},{"nivelAfectacion",3},{"descripcion","Cambio desde otro dispositivo"},{"activo","Escuela"},{"latitud",4.6},{"longitud",-74.1},{"motivo","Otra inspección"}};
   await Http(HttpMethod.Put,new Uri(destino,"/api/campo/evaluaciones/"+original.EvaluacionId),patch);
   await SincronizadorCampo.EnviarAsync(reabierto,destino);
   var conflicto=(await reabierto.RevisionesAsync(original.EvaluacionId))[0];Check(conflicto.Estado=="conflicto" && conflicto.Descripcion==revision.Descripcion && conflicto.ServidorJson is not null,"conflicto conserva servidor y propuesta local");
   var remoto=JsonSerializer.Deserialize(conflicto.ServidorJson!,CampoJson.Default.VersionCampo)!;Check(remoto.Version==1,"versión remota identificada");
   var resuelta=new RevisionLocal{EvaluacionId=original.EvaluacionId,OrganizacionLocal=SesionCampo.Organizacion,UsuarioLocal=SesionCampo.Usuario,VersionBase=remoto.Version,NivelAfectacion=5,Descripcion=revision.Descripcion,Activo="Escuela",Latitud=4.6,Longitud=-74.1,Motivo="Propuesta revisada explícitamente"};
   await reabierto.ResolverRevisionAsync(conflicto,resuelta,remoto.Version);await SincronizadorCampo.EnviarAsync(reabierto,destino);
   var final=await Http(HttpMethod.Get,new Uri(destino,"/api/campo/evaluaciones/"+original.EvaluacionId));Check(final["version"]!.GetValue<long>()==2 && final["descripcion"]!.GetValue<string>()==revision.Descripcion,"resolución sincronizada como nueva versión");
   Check((await reabierto.ObtenerAsync(original.EvaluacionId)).Descripcion==original.Descripcion,"captura original local inmutable");
   await SesionCampo.Salir(destino);Check(SesionCampo.Token is null,"logout del APK");
  }finally{if(respaldo is null)SecureStorage.Default.Remove("arqui-sesion");else await SecureStorage.Default.SetAsync("arqui-sesion",respaldo);}
 }
}
#endif
