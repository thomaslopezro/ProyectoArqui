using System.Text.Json;
using Mapas.Api.Application;
using Npgsql;
namespace Mapas.Api.Infrastructure;
public sealed class EvaluacionesVersionadas(NpgsqlDataSource db,AccesoActual acceso){
 static readonly JsonSerializerOptions Json=new(JsonSerializerDefaults.Web);
 public async Task Inicializar(CancellationToken ct){await using var c=db.CreateCommand("""
 CREATE TABLE IF NOT EXISTS campo_revisiones(evaluacion_id uuid PRIMARY KEY REFERENCES campo_operaciones(evaluacion_id), version bigint NOT NULL,datos jsonb NOT NULL,actualizada_en timestamptz NOT NULL DEFAULT now());
 CREATE TABLE IF NOT EXISTS campo_cambios(operacion_id uuid PRIMARY KEY,evaluacion_id uuid NOT NULL REFERENCES campo_operaciones(evaluacion_id),organizacion_id text NOT NULL,version bigint NOT NULL,peticion jsonb NOT NULL,anterior jsonb NOT NULL,nuevo jsonb NOT NULL,usuario text NOT NULL,fecha timestamptz NOT NULL DEFAULT now(),UNIQUE(evaluacion_id,version));
 """);await c.ExecuteNonQueryAsync(ct);}
 public async Task<long> Version(Guid id,CancellationToken ct){await using var c=db.CreateCommand("SELECT COALESCE(r.version,0) FROM campo_operaciones o LEFT JOIN campo_revisiones r USING(evaluacion_id) WHERE o.evaluacion_id=@id AND o.organizacion_id=@org AND o.incorporada");c.Parameters.AddWithValue("id",id);c.Parameters.AddWithValue("org",acceso.Organizacion);return Convert.ToInt64(await c.ExecuteScalarAsync(ct)??-1L);}
 public async Task<IResult> Actualizar(Guid id,RevisionCampo input,CancellationToken ct){
  if(input.OperacionId==Guid.Empty || input.VersionBase<0 || input.NivelAfectacion is <1 or >5 || input.Latitud is not (>=-90 and <=90) || input.Longitud is not (>=-180 and <=180) || string.IsNullOrWhiteSpace(input.Motivo) || input.Motivo.Length>500 || input.Descripcion?.Length>1000 || input.Activo?.Length>120)
   return Results.BadRequest(new{error="Datos o motivo de corrección inválidos."});
  await using var cn=await db.OpenConnectionAsync(ct);await using var tx=await cn.BeginTransactionAsync(ct);
  await using var get=new NpgsqlCommand("SELECT (COALESCE(r.datos,o.datos) || jsonb_build_object('evaluador',o.usuario,'organizacion',o.organizacion_id))::text,COALESCE(r.version,0) FROM campo_operaciones o LEFT JOIN campo_revisiones r USING(evaluacion_id) WHERE o.evaluacion_id=@id AND o.organizacion_id=@org AND o.incorporada FOR UPDATE OF o",cn,tx);
  get.Parameters.AddWithValue("id",id);get.Parameters.AddWithValue("org",acceso.Organizacion);
  string original;long version;
  await using(var row=await get.ExecuteReaderAsync(ct)){if(!await row.ReadAsync(ct))return Results.NotFound();original=row.GetString(0);version=row.GetInt64(1);}
  var peticion=JsonSerializer.Serialize(input,Json);
  await using var anterior=new NpgsqlCommand("SELECT version,peticion=CAST(@p AS jsonb) AND evaluacion_id=@id AND organizacion_id=@org FROM campo_cambios WHERE operacion_id=@op",cn,tx);
  anterior.Parameters.AddWithValue("p",peticion);anterior.Parameters.AddWithValue("id",id);anterior.Parameters.AddWithValue("org",acceso.Organizacion);anterior.Parameters.AddWithValue("op",input.OperacionId);
  await using(var row=await anterior.ExecuteReaderAsync(ct)){if(await row.ReadAsync(ct))return row.GetBoolean(1)?Results.Ok(new{estado="incorporada",version=row.GetInt64(0)}):Results.Conflict(new{estado="conflicto",versionActual=version,error="El identificador de corrección ya se usó."});}
  if(version!=input.VersionBase)return Results.Conflict(new{estado="conflicto",versionActual=version,error="La evaluación cambió. Revisa la versión del servidor antes de reenviar."});
  var op=JsonSerializer.Deserialize<OperacionCampo>(original,Json)!;
  var nueva=op with{NivelAfectacion=input.NivelAfectacion,Descripcion=input.Descripcion??"",Activo=input.Activo,Latitud=input.Latitud,Longitud=input.Longitud};
  var datos=JsonSerializer.Serialize(nueva,Json);
  await using var guardar=new NpgsqlCommand("INSERT INTO campo_revisiones VALUES(@id,@v,CAST(@d AS jsonb),now()) ON CONFLICT(evaluacion_id) DO UPDATE SET version=EXCLUDED.version,datos=EXCLUDED.datos,actualizada_en=now(); INSERT INTO campo_cambios(operacion_id,evaluacion_id,organizacion_id,version,peticion,anterior,nuevo,usuario) VALUES(@op,@id,@org,@v,CAST(@p AS jsonb),CAST(@a AS jsonb)-'foto',CAST(@d AS jsonb)-'foto',@u)",cn,tx);
  guardar.Parameters.AddWithValue("id",id);guardar.Parameters.AddWithValue("v",version+1);guardar.Parameters.AddWithValue("d",datos);guardar.Parameters.AddWithValue("op",input.OperacionId);guardar.Parameters.AddWithValue("org",acceso.Organizacion);guardar.Parameters.AddWithValue("p",peticion);guardar.Parameters.AddWithValue("a",original);guardar.Parameters.AddWithValue("u",acceso.Usuario);
  await guardar.ExecuteNonQueryAsync(ct);await tx.CommitAsync(ct);return Results.Ok(new{estado="incorporada",version=version+1});
 }
 public async Task<IResult> Historial(Guid id,CancellationToken ct){
  if(await Version(id,ct)<0)return Results.NotFound();
  await using var c=db.CreateCommand("SELECT jsonb_build_object('version',version,'actor',usuario,'fecha',fecha,'anterior',anterior,'nuevo',nuevo,'motivo',peticion->>'motivo')::text FROM campo_cambios WHERE evaluacion_id=@id AND organizacion_id=@org ORDER BY version");
  c.Parameters.AddWithValue("id",id);c.Parameters.AddWithValue("org",acceso.Organizacion);var lista=new List<JsonElement>();await using var row=await c.ExecuteReaderAsync(ct);while(await row.ReadAsync(ct))lista.Add(JsonSerializer.Deserialize<JsonElement>(row.GetString(0)));return Results.Ok(lista);
 }
}
public sealed record RevisionCampo(Guid OperacionId,long VersionBase,int NivelAfectacion,string? Descripcion,string? Activo,double Latitud,double Longitud,string Motivo);
