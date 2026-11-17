using Microsoft.Maui.Storage;
using System.Net.Http.Json;
namespace Arqui.Campo;
internal static class SesionCampo {
 public static string? Token {get;private set;}
 public static string Organizacion {get;private set;}="";
 public static string Usuario {get;private set;}="";
 public static string Rol {get;private set;}="";
 public static string Servidor {get;private set;}="";
 public static async Task Restaurar(){
  var raw=await SecureStorage.Default.GetAsync("arqui-sesion");
  if(raw is null)return;
  var s=System.Text.Json.JsonSerializer.Deserialize(raw,CampoJson.Default.SesionLocal);
  if(s is not null)Asignar(s);
 }
 private static void Asignar(SesionLocal s){Token=s.Token;Organizacion=s.Organizacion;Usuario=s.Usuario;Rol=s.Rol;Servidor=s.Servidor;}
 public static async Task Entrar(Uri destino,string usuario,string clave){
  using var http=new HttpClient{Timeout=TimeSpan.FromSeconds(15)};
  using var res=await http.PostAsJsonAsync(new Uri(destino,"/api/auth/login"),new CredencialesCampo(usuario,clave),CampoJson.Default.CredencialesCampo);
  if(!res.IsSuccessStatusCode)throw new InvalidOperationException(res.StatusCode==System.Net.HttpStatusCode.Unauthorized?"Usuario o contraseña incorrectos.":"Servicio de identidad no disponible.");
  var s=await res.Content.ReadFromJsonAsync(CampoJson.Default.LoginCampo) ?? throw new InvalidDataException();
  var local=new SesionLocal(s.AccessToken,s.OrganizacionId,s.Usuario,s.Rol,destino.GetLeftPart(UriPartial.Authority));
  // Persist credentials before changing the active account.
  await SecureStorage.Default.SetAsync("arqui-sesion",System.Text.Json.JsonSerializer.Serialize(local,CampoJson.Default.SesionLocal));Asignar(local);
 }
 public static void Autorizar(HttpRequestMessage req){
  if(string.IsNullOrEmpty(Token) || req.RequestUri!.GetLeftPart(UriPartial.Authority)!=Servidor)
   throw new InvalidOperationException("Inicia sesión en este servidor antes de sincronizar.");
  req.Headers.Authorization=new System.Net.Http.Headers.AuthenticationHeaderValue("Bearer",Token);
 }
 public static async Task Salir(Uri destino){
  using var http=new HttpClient{Timeout=TimeSpan.FromSeconds(15)};
  using var req=new HttpRequestMessage(HttpMethod.Post,new Uri(destino,"/api/auth/logout"));Autorizar(req);req.Content=new StringContent("{}",System.Text.Encoding.UTF8,"application/json");
  using var res=await http.SendAsync(req);
  if(!res.IsSuccessStatusCode && res.StatusCode!=System.Net.HttpStatusCode.Unauthorized)throw new InvalidOperationException("No se pudo cerrar sesión. Reintenta con conexión.");
  SecureStorage.Default.Remove("arqui-sesion");Token=null;Organizacion=Usuario=Rol=Servidor="";
 }
}
internal sealed record CredencialesCampo(string Usuario,string Clave);
internal sealed record LoginCampo(string AccessToken,string OrganizacionId,string Usuario,string Rol);
internal sealed record SesionLocal(string Token,string Organizacion,string Usuario,string Rol,string Servidor);
