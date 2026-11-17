using Mapas.Api.Application;
using Mapas.Api.Domain;
using Microsoft.EntityFrameworkCore;

namespace Mapas.Api.Infrastructure;

public sealed class MapasDb(DbContextOptions<MapasDb> options, AccesoActual acceso) : DbContext(options)
{
    public override Task<int> SaveChangesAsync(CancellationToken ct=default) {
        foreach(var e in ChangeTracker.Entries<Afectacion>().Where(e=>e.State==EntityState.Added))
            e.Property("OrganizacionId").CurrentValue=acceso.Organizacion;
        return base.SaveChangesAsync(ct);
    }
    public DbSet<Afectacion> Afectaciones => Set<Afectacion>();
    protected override void OnModelCreating(ModelBuilder model)
    {
        var entity = model.Entity<Afectacion>();
        entity.HasKey(x => x.Id);
        entity.Property<string>("OrganizacionId");
        entity.HasQueryFilter(x=>EF.Property<string>(x,"OrganizacionId")==acceso.Organizacion);
        entity.Property(x => x.Titulo).HasMaxLength(120).IsRequired();
        entity.Property(x => x.Descripcion).HasMaxLength(2000).IsRequired();
        entity.Property(x => x.Tipo).HasMaxLength(30).IsRequired();
        entity.Property(x => x.Severidad).HasMaxLength(10).IsRequired();
        entity.Property(x => x.Version).IsConcurrencyToken();
        entity.HasIndex(x => new { x.Tipo, x.Severidad });
        entity.ToTable("Afectaciones", table => {
            table.HasCheckConstraint("CK_Latitud", "\"Latitud\" BETWEEN -90 AND 90");
            table.HasCheckConstraint("CK_Longitud", "\"Longitud\" BETWEEN -180 AND 180");
        });
    }
}

public sealed class EfAfectacionesRepository(MapasDb db) : IAfectacionesRepository
{
    public Task<List<Afectacion>> Listar(string? tipo, string? severidad, CancellationToken ct)
    {
        // LINQ se traduce a SQL parametrizado; el filtrado ocurre en PostgreSQL.
        var query = db.Afectaciones.AsNoTracking();
        if (tipo is not null) query = query.Where(x => x.Tipo == tipo);
        if (severidad is not null) query = query.Where(x => x.Severidad == severidad);
        return query.OrderByDescending(x => x.RegistradaEn).ThenBy(x => x.Id).ToListAsync(ct);
    }
    public Task<Afectacion?> Buscar(Guid id, CancellationToken ct) => db.Afectaciones.FirstOrDefaultAsync(x => x.Id == id, ct);
    public async Task Agregar(IReadOnlyCollection<Afectacion> items, CancellationToken ct)
    {
        db.Afectaciones.AddRange(items);
        await db.SaveChangesAsync(ct);
    }
    public async Task Guardar(CancellationToken ct) => await db.SaveChangesAsync(ct);
    public async Task Eliminar(Afectacion item, CancellationToken ct)
    {
        db.Afectaciones.Remove(item);
        await db.SaveChangesAsync(ct);
    }
}
