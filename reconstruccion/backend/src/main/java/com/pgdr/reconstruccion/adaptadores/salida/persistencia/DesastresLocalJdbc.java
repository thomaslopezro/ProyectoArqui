package com.pgdr.reconstruccion.adaptadores.salida.persistencia;

import com.pgdr.reconstruccion.aplicacion.puertos.GestionDesastres;
import com.pgdr.reconstruccion.dominio.Desastre;
import com.pgdr.reconstruccion.dominio.EstadoDesastre;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Implementa el puerto GestionDesastres con una tabla local de referencia (desastres_ref).
 * En la plataforma integrada este adaptador se reemplaza por un cliente del módulo de
 * Gestión de desastres; los casos de uso no cambian.
 */
@Repository
@ConditionalOnProperty(name = "reconstruccion.integracion.habilitada", havingValue = "false", matchIfMissing = true)
public class DesastresLocalJdbc implements GestionDesastres {
    private final JdbcTemplate jdbc;

    public DesastresLocalJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Desastre> buscar(UUID id) {
        return jdbc.query("SELECT id, nombre, estado FROM desastres_ref WHERE id = ?",
                (rs, n) -> new Desastre(UUID.fromString(rs.getString("id")), rs.getString("nombre"),
                        EstadoDesastre.valueOf(rs.getString("estado"))), id.toString()).stream().findFirst();
    }

    @Override
    public List<Desastre> listar() {
        return jdbc.query("SELECT id, nombre, estado FROM desastres_ref ORDER BY nombre",
                (rs, n) -> new Desastre(UUID.fromString(rs.getString("id")), rs.getString("nombre"),
                        EstadoDesastre.valueOf(rs.getString("estado"))));
    }

    @Override
    public void cambiarEstado(UUID id, EstadoDesastre nuevo) {
        jdbc.update("UPDATE desastres_ref SET estado = ? WHERE id = ?", nuevo.name(), id.toString());
    }
}
