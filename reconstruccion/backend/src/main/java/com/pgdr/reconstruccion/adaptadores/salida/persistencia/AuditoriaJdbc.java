package com.pgdr.reconstruccion.adaptadores.salida.persistencia;

import com.pgdr.reconstruccion.aplicacion.puertos.RegistroDeAuditoria;
import com.pgdr.reconstruccion.dominio.RegistroAuditoria;
import com.pgdr.reconstruccion.dominio.Rol;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Auditoría en la misma base de datos que los proyectos: así el cambio y su registro comparten transacción. */
@Repository
public class AuditoriaJdbc implements RegistroDeAuditoria {
    private static final String COLUMNAS = "id, momento, usuario, rol, organizacion, operacion, entidad, entidad_id, "
            + "desastre_id, valor_anterior, valor_nuevo, motivo, resultado, detalle";

    private final JdbcTemplate jdbc;

    public AuditoriaJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void registrar(RegistroAuditoria r) {
        jdbc.update("INSERT INTO auditoria (" + COLUMNAS + ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                r.id().toString(), Timestamp.from(r.momento()), r.usuario(), r.rol().name(), r.organizacion(),
                r.operacion(), r.entidad(), texto(r.entidadId()), texto(r.desastreId()), r.valorAnterior(),
                r.valorNuevo(), r.motivo(), r.resultado().name(), r.detalle());
    }

    @Override
    public List<RegistroAuditoria> porEntidad(UUID entidadId) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM auditoria WHERE entidad_id = ? ORDER BY momento",
                (rs, n) -> leer(rs), entidadId.toString());
    }

    @Override
    public List<RegistroAuditoria> porDesastre(UUID desastreId, String organizacion) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM auditoria WHERE desastre_id = ? AND organizacion = ? "
                + "ORDER BY momento", (rs, n) -> leer(rs), desastreId.toString(), organizacion);
    }

    private static RegistroAuditoria leer(ResultSet rs) throws SQLException {
        return new RegistroAuditoria(UUID.fromString(rs.getString("id")), rs.getTimestamp("momento").toInstant(),
                rs.getString("usuario"), Rol.valueOf(rs.getString("rol")), rs.getString("organizacion"),
                rs.getString("operacion"), rs.getString("entidad"), uuid(rs.getString("entidad_id")),
                uuid(rs.getString("desastre_id")), rs.getString("valor_anterior"), rs.getString("valor_nuevo"),
                rs.getString("motivo"), RegistroAuditoria.Resultado.valueOf(rs.getString("resultado")),
                rs.getString("detalle"));
    }

    private static String texto(UUID id) {
        return id == null ? null : id.toString();
    }

    private static UUID uuid(String s) {
        return s == null ? null : UUID.fromString(s);
    }
}
