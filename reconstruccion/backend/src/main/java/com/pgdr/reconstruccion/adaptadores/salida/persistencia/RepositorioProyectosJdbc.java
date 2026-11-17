package com.pgdr.reconstruccion.adaptadores.salida.persistencia;

import com.pgdr.reconstruccion.aplicacion.puertos.ConflictoDeVersionException;
import com.pgdr.reconstruccion.aplicacion.puertos.RepositorioProyectos;
import com.pgdr.reconstruccion.dominio.EstadoProyecto;
import com.pgdr.reconstruccion.dominio.Proyecto;
import com.pgdr.reconstruccion.dominio.Seguimiento;
import com.pgdr.reconstruccion.dominio.TipoProyecto;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Implementa el puerto RepositorioProyectos sobre PostgreSQL (o H2 en desarrollo) con SQL directo. */
@Repository
public class RepositorioProyectosJdbc implements RepositorioProyectos {
    private static final String COLUMNAS = "id, desastre_id, organizacion, nombre, tipo, estado, presupuesto, "
            + "fuente_financiacion, responsable, contratista, inicio_plan, fin_plan, avance, costo_acumulado, version";

    private final JdbcTemplate jdbc;

    public RepositorioProyectosJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void guardarNuevo(Proyecto p) {
        jdbc.update("INSERT INTO proyectos (" + COLUMNAS + ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                p.id().toString(), p.desastreId().toString(), p.organizacion(), p.nombre(), p.tipo().name(),
                p.estado().name(), p.presupuesto(), p.fuenteFinanciacion(), p.responsable(), p.contratista(),
                fecha(p.inicioPlan()), fecha(p.finPlan()), p.avance(), p.costoAcumulado(), p.version());
        for (UUID evaluacion : p.evaluaciones())
            jdbc.update("INSERT INTO proyecto_evaluaciones (proyecto_id, evaluacion_id) VALUES (?,?)",
                    p.id().toString(), evaluacion.toString());
        guardarSeguimientoNuevo(p);
    }

    @Override
    public void actualizar(Proyecto p) {
        // Control optimista: solo actualiza si la versión guardada es la que leyó este caso de uso.
        int filas = jdbc.update("UPDATE proyectos SET estado = ?, presupuesto = ?, fuente_financiacion = ?, "
                        + "responsable = ?, contratista = ?, inicio_plan = ?, fin_plan = ?, avance = ?, "
                        + "costo_acumulado = ?, version = version + 1 WHERE id = ? AND version = ?",
                p.estado().name(), p.presupuesto(), p.fuenteFinanciacion(), p.responsable(), p.contratista(),
                fecha(p.inicioPlan()), fecha(p.finPlan()), p.avance(), p.costoAcumulado(), p.id().toString(),
                p.version());
        if (filas == 0)
            throw new ConflictoDeVersionException("Otro usuario modificó el proyecto; consulte de nuevo y reintente.");
        guardarSeguimientoNuevo(p);
        p.marcarGuardado();
    }

    /** El seguimiento solo crece: se insertan los registros que aún no están guardados. */
    private void guardarSeguimientoNuevo(Proyecto p) {
        Integer guardados = jdbc.queryForObject("SELECT COUNT(*) FROM seguimiento WHERE proyecto_id = ?",
                Integer.class, p.id().toString());
        List<Seguimiento> todos = p.seguimiento();
        for (int i = guardados == null ? 0 : guardados; i < todos.size(); i++) {
            Seguimiento s = todos.get(i);
            jdbc.update("INSERT INTO seguimiento (id, proyecto_id, orden, tipo, valor, descripcion, momento, usuario) "
                            + "VALUES (?,?,?,?,?,?,?,?)",
                    UUID.randomUUID().toString(), p.id().toString(), i, s.tipo().name(), s.valor(), s.descripcion(),
                    Timestamp.from(s.momento()), s.usuario());
        }
    }

    @Override
    public Optional<Proyecto> buscar(UUID id) {
        List<Proyecto> encontrados = jdbc.query("SELECT " + COLUMNAS + " FROM proyectos WHERE id = ?",
                (rs, n) -> leer(rs), id.toString());
        return encontrados.stream().findFirst();
    }

    @Override
    public List<Proyecto> porDesastre(UUID desastreId, String organizacion) {
        if (organizacion == null)
            return jdbc.query("SELECT " + COLUMNAS + " FROM proyectos WHERE desastre_id = ? ORDER BY nombre",
                    (rs, n) -> leer(rs), desastreId.toString());
        return jdbc.query("SELECT " + COLUMNAS + " FROM proyectos WHERE desastre_id = ? AND organizacion = ? "
                + "ORDER BY nombre", (rs, n) -> leer(rs), desastreId.toString(), organizacion);
    }

    @Override
    public boolean existeActivoParaEvaluacion(UUID evaluacionId) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM proyecto_evaluaciones pe JOIN proyectos p "
                        + "ON p.id = pe.proyecto_id WHERE pe.evaluacion_id = ? AND p.estado <> 'CANCELADO'",
                Integer.class, evaluacionId.toString());
        return n != null && n > 0;
    }

    private Proyecto leer(ResultSet rs) throws SQLException {
        String id = rs.getString("id");
        List<UUID> evaluaciones = jdbc.query(
                "SELECT evaluacion_id FROM proyecto_evaluaciones WHERE proyecto_id = ? ORDER BY evaluacion_id",
                (r, n) -> UUID.fromString(r.getString("evaluacion_id")), id);
        List<Seguimiento> seguimiento = jdbc.query(
                "SELECT tipo, valor, descripcion, momento, usuario FROM seguimiento WHERE proyecto_id = ? ORDER BY orden",
                (r, n) -> new Seguimiento(Seguimiento.Tipo.valueOf(r.getString("tipo")), r.getBigDecimal("valor"),
                        r.getString("descripcion"), r.getTimestamp("momento").toInstant(), r.getString("usuario")), id);
        return Proyecto.rehidratar(UUID.fromString(id), UUID.fromString(rs.getString("desastre_id")),
                rs.getString("organizacion"), rs.getString("nombre"), TipoProyecto.valueOf(rs.getString("tipo")),
                evaluaciones, EstadoProyecto.valueOf(rs.getString("estado")), rs.getBigDecimal("presupuesto"),
                rs.getString("fuente_financiacion"), rs.getString("responsable"), rs.getString("contratista"),
                local(rs.getDate("inicio_plan")), local(rs.getDate("fin_plan")), rs.getInt("avance"),
                rs.getBigDecimal("costo_acumulado"), seguimiento, rs.getLong("version"));
    }

    private static Date fecha(LocalDate d) {
        return d == null ? null : Date.valueOf(d);
    }

    private static LocalDate local(Date d) {
        return d == null ? null : d.toLocalDate();
    }
}
