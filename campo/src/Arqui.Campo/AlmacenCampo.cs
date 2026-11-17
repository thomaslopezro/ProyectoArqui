using SQLite;

namespace Arqui.Campo;

public sealed class EvaluacionPendiente
{
    [System.Text.Json.Serialization.JsonIgnore] public long VersionServidor {get;set;}
    [System.Text.Json.Serialization.JsonIgnore(Condition=System.Text.Json.Serialization.JsonIgnoreCondition.WhenWritingNull)] public string? Activo {get;set;}
    [System.Text.Json.Serialization.JsonIgnore] public string? OrganizacionLocal {get;set;}
    [System.Text.Json.Serialization.JsonIgnore] public string? UsuarioLocal {get;set;}
    [PrimaryKey]
    public string EvaluacionId { get; set; } = Guid.NewGuid().ToString();
    public string OperacionId { get; set; } = Guid.NewGuid().ToString();
    public string EventoId { get; set; } = "DEMO-01";
    public int? NivelAfectacion { get; set; }
    public string? Estado { get; set; }
    public string? Motivo { get; set; }
    public string Descripcion { get; set; } = "";
    public double Latitud { get; set; }
    public double Longitud { get; set; }
    public string? FuenteUbicacion { get; set; }
    public string CapturadaEnUtc { get; set; } = DateTimeOffset.UtcNow.ToString("O");
    public string? EvidenciaId { get; set; }
    public string? FotoTipo { get; set; }
    public string? FotoSha256 { get; set; }
    public byte[]? Foto { get; set; }
}

public sealed class ResumenPendiente
{
    public string EvaluacionId { get; set; } = "";
    public int? NivelAfectacion { get; set; }
    public string? Estado { get; set; }
    public string? Motivo { get; set; }
    public string Descripcion { get; set; } = "";
    public string? EvidenciaId { get; set; }
}

public sealed class AlmacenCampo
{
    private readonly SQLiteAsyncConnection baseLocal;
    private readonly Lazy<Task<CreateTableResult>> inicializacion;

    public AlmacenCampo(string ruta)
    {
        baseLocal = new SQLiteAsyncConnection(ruta);
        inicializacion = new(async()=>{var result=await baseLocal.CreateTableAsync<EvaluacionPendiente>();await baseLocal.CreateTableAsync<RevisionLocal>();return result;});
    }

    public async Task<List<DesastreLocal>> DesastresAsync()
    {
        await baseLocal.CreateTableAsync<DesastreLocal>();
        return await baseLocal.Table<DesastreLocal>().Where(x=>x.OrganizacionLocal==SesionCampo.Organizacion).OrderBy(x => x.Id).ToListAsync();
    }

    public async Task GuardarCatalogoAsync(IEnumerable<DesastreLocal> desastres)
    {
        await baseLocal.CreateTableAsync<DesastreLocal>();
        var copia = desastres.ToArray();
        foreach(var d in copia)d.OrganizacionLocal=SesionCampo.Organizacion;
        await baseLocal.RunInTransactionAsync(db => {
            db.Execute("DELETE FROM DesastreLocal WHERE OrganizacionLocal = ?",SesionCampo.Organizacion);
            foreach(var d in copia)db.InsertOrReplace(d);
        });
    }

    public async Task<int> LegadoPendienteAsync(){await inicializacion.Value;return await baseLocal.ExecuteScalarAsync<int>("SELECT count(*) FROM EvaluacionPendiente WHERE OrganizacionLocal IS NULL");}
    public async Task AsignarLegadoAsync(){await inicializacion.Value;await baseLocal.ExecuteAsync("UPDATE EvaluacionPendiente SET OrganizacionLocal=?,UsuarioLocal=? WHERE OrganizacionLocal IS NULL",SesionCampo.Organizacion,SesionCampo.Usuario);}
    public async Task GuardarAsync(EvaluacionPendiente evaluacion)
    {
        await inicializacion.Value;
        // Una sola inserción confirma evaluación, operación pendiente y evidencia juntas.
        await baseLocal.InsertAsync(evaluacion);
    }

    public async Task<List<ResumenPendiente>> ListarAsync()
    {
        await inicializacion.Value;
        return await baseLocal.QueryAsync<ResumenPendiente>(
            """
            SELECT EvaluacionId, NivelAfectacion, Estado, Motivo, Descripcion, EvidenciaId
            FROM EvaluacionPendiente
            WHERE OrganizacionLocal = ? AND UsuarioLocal = ?
            ORDER BY CapturadaEnUtc DESC
            """, SesionCampo.Organizacion, SesionCampo.Usuario);
    }

    public async Task<List<RevisionLocal>> RevisionesAsync(string? id=null){await inicializacion.Value;
        return await baseLocal.QueryAsync<RevisionLocal>("SELECT * FROM RevisionLocal WHERE OrganizacionLocal=? AND UsuarioLocal=? AND (? IS NULL OR EvaluacionId=?) ORDER BY rowid DESC",SesionCampo.Organizacion,SesionCampo.Usuario,id,id);
    }
    public async Task GuardarRevisionAsync(RevisionLocal r){await inicializacion.Value;await baseLocal.InsertAsync(r);}
    public async Task ResultadoRevisionAsync(RevisionLocal r,long? version=null){await inicializacion.Value;await baseLocal.RunInTransactionAsync(db=>{db.Update(r);if(version is {} v)db.Execute("UPDATE EvaluacionPendiente SET VersionServidor=? WHERE EvaluacionId=? AND OrganizacionLocal=? AND UsuarioLocal=?",v,r.EvaluacionId,SesionCampo.Organizacion,SesionCampo.Usuario);});}
    public async Task ResolverRevisionAsync(RevisionLocal anterior,RevisionLocal nueva,long version){await inicializacion.Value;await baseLocal.RunInTransactionAsync(db=>{anterior.Estado="archivada";db.Update(anterior);db.Insert(nueva);db.Execute("UPDATE EvaluacionPendiente SET VersionServidor=? WHERE EvaluacionId=? AND OrganizacionLocal=? AND UsuarioLocal=?",version,nueva.EvaluacionId,SesionCampo.Organizacion,SesionCampo.Usuario);});}
    public async Task VersionAsync(string id,long v){await inicializacion.Value;await baseLocal.ExecuteAsync("UPDATE EvaluacionPendiente SET VersionServidor=? WHERE EvaluacionId=? AND OrganizacionLocal=? AND UsuarioLocal=?",v,id,SesionCampo.Organizacion,SesionCampo.Usuario);}
    public async Task ActualizarResultadoAsync(string id, string estado, string? motivo)
    {
        await inicializacion.Value;
        await baseLocal.ExecuteAsync(
            "UPDATE EvaluacionPendiente SET Estado = ?, Motivo = ? WHERE EvaluacionId = ?", estado, motivo, id);
    }

    public async Task AsignarNivelAnteriorAsync(string id, int nivel)
    {
        await inicializacion.Value;
        await baseLocal.ExecuteAsync(
            "UPDATE EvaluacionPendiente SET NivelAfectacion = ? WHERE EvaluacionId = ? AND NivelAfectacion IS NULL",
            nivel, id);
    }

    public async Task<EvaluacionPendiente> ObtenerAsync(string evaluacionId)
    {
        await inicializacion.Value;
        return await baseLocal.GetAsync<EvaluacionPendiente>(evaluacionId);
    }
}

public sealed class DesastreLocal
{
    public string? OrganizacionLocal {get;set;}
    [PrimaryKey] public string Id { get; set; } = "";
    public string Tipo { get; set; } = "";
    public string Ubicacion { get; set; } = "";
    public string Descripcion { get; set; } = "";
    public string Severidad { get; set; } = "";
    public string Fecha { get; set; } = "";
}

public sealed class RevisionLocal {
 [PrimaryKey] public string OperacionId {get;set;}=Guid.NewGuid().ToString();
 public string EvaluacionId {get;set;}="";
 public string OrganizacionLocal {get;set;}="";
 public string UsuarioLocal {get;set;}="";
 public long VersionBase {get;set;}
 public int NivelAfectacion {get;set;}
 public string Descripcion {get;set;}="";
 public string? Activo {get;set;}
 public double Latitud {get;set;} public double Longitud {get;set;}
 public string Motivo {get;set;}="";
 public string Estado {get;set;}="pendiente";
 public string? ServidorJson {get;set;}
 public string Fecha {get;set;}=DateTimeOffset.UtcNow.ToString("O");
}
