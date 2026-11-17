package com.pgdr.reconstruccion.adaptadores.salida.persistencia;

import com.pgdr.reconstruccion.aplicacion.puertos.ConsultaEvaluaciones;
import com.pgdr.reconstruccion.dominio.EvaluacionDano;
import com.pgdr.reconstruccion.dominio.NivelDano;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Implementa el puerto ConsultaEvaluaciones con una tabla local de referencia (evaluaciones_ref).
 * En la plataforma integrada se reemplaza por un cliente del módulo de Evaluación de daños.
 */
@Repository
@ConditionalOnProperty(name = "reconstruccion.integracion.habilitada", havingValue = "false", matchIfMissing = true)
public class EvaluacionesLocalJdbc implements ConsultaEvaluaciones {
    private static final String CONSULTA = "SELECT id, desastre_id, organizacion, tipo_activo, descripcion, "
            + "nivel_dano, validada FROM evaluaciones_ref ";

    private final JdbcTemplate jdbc;

    public EvaluacionesLocalJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<EvaluacionDano> porIds(Collection<UUID> ids) {
        List<EvaluacionDano> resultado = new ArrayList<>();
        for (UUID id : ids)
            resultado.addAll(jdbc.query(CONSULTA + "WHERE id = ?", (rs, n) -> leer(rs), id.toString()));
        return resultado;
    }

    @Override
    public List<EvaluacionDano> porDesastre(UUID desastreId) {
        return jdbc.query(CONSULTA + "WHERE desastre_id = ? ORDER BY tipo_activo, descripcion",
                (rs, n) -> leer(rs), desastreId.toString());
    }

    private static EvaluacionDano leer(ResultSet rs) throws SQLException {
        return new EvaluacionDano(UUID.fromString(rs.getString("id")), UUID.fromString(rs.getString("desastre_id")),
                rs.getString("organizacion"), rs.getString("tipo_activo"), rs.getString("descripcion"),
                NivelDano.valueOf(rs.getString("nivel_dano")), rs.getBoolean("validada"));
    }
}
