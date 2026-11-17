package com.pgdr.reconstruccion.infraestructura;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Carga un desastre y sus evaluaciones de ejemplo en las tablas de referencia cuando están vacías,
 * para poder demostrar el módulo sin los módulos de Gestión de desastres y Evaluación de daños.
 */
@Component
@ConditionalOnProperty(name = "reconstruccion.datos-demo", havingValue = "true")
public class DatosDemo implements CommandLineRunner {
    private static final String SISMO = "11111111-1111-1111-1111-111111111111";
    private static final String INUNDACION = "22222222-2222-2222-2222-222222222222";

    private final JdbcTemplate jdbc;

    public DatosDemo(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(String... args) {
        Integer existentes = jdbc.queryForObject("SELECT COUNT(*) FROM desastres_ref", Integer.class);
        if (existentes != null && existentes > 0) return;
        jdbc.update("INSERT INTO desastres_ref (id, nombre, estado) VALUES (?,?,?)",
                SISMO, "Sismo del Eje Cafetero 2026", "CARACTERIZACION");
        jdbc.update("INSERT INTO desastres_ref (id, nombre, estado) VALUES (?,?,?)",
                INUNDACION, "Inundación del río Cauca 2026", "REGISTRO");
        evaluacion("a0000000-0000-0000-0000-000000000001", SISMO, "UNGRD", "Puente", "Puente La Vega", "DESTRUCCION_TOTAL", true);
        evaluacion("a0000000-0000-0000-0000-000000000002", SISMO, "UNGRD", "Puente", "Puente El Roble", "GRAVE", true);
        evaluacion("a0000000-0000-0000-0000-000000000003", SISMO, "UNGRD", "Escuela", "Escuela San José", "GRAVE", true);
        evaluacion("a0000000-0000-0000-0000-000000000004", SISMO, "UNGRD", "Escuela", "Escuela La Esperanza", "DESTRUCCION_TOTAL", true);
        evaluacion("a0000000-0000-0000-0000-000000000005", SISMO, "UNGRD", "Hospital", "Hospital local de Salento", "MODERADO", true);
        evaluacion("a0000000-0000-0000-0000-000000000006", SISMO, "UNGRD", "Red de agua", "Acueducto veredal", "GRAVE", false);
        evaluacion("a0000000-0000-0000-0000-000000000007", SISMO, "UNGRD", "Edificio público", "Parque central", "LEVE", true);
        evaluacion("a0000000-0000-0000-0000-000000000008", SISMO, "Alcaldia", "Carretera", "Vía Circasia - Montenegro", "GRAVE", true);
    }

    private void evaluacion(String id, String desastre, String organizacion, String activo, String descripcion,
                            String nivel, boolean validada) {
        jdbc.update("INSERT INTO evaluaciones_ref (id, desastre_id, organizacion, tipo_activo, descripcion, "
                + "nivel_dano, validada) VALUES (?,?,?,?,?,?,?)", id, desastre, organizacion, activo, descripcion,
                nivel, validada);
    }
}
