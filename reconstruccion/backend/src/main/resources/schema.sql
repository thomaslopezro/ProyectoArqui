-- Esquema del módulo de Reconstrucción. Compatible con PostgreSQL y con H2 en modo PostgreSQL.

-- Tablas de referencia: datos que en la plataforma integrada pertenecen a otros módulos.
CREATE TABLE IF NOT EXISTS desastres_ref (
  id VARCHAR(36) PRIMARY KEY,
  nombre VARCHAR(200) NOT NULL,
  estado VARCHAR(20) NOT NULL
);

CREATE TABLE IF NOT EXISTS evaluaciones_ref (
  id VARCHAR(36) PRIMARY KEY,
  desastre_id VARCHAR(36) NOT NULL REFERENCES desastres_ref(id),
  organizacion VARCHAR(80) NOT NULL,
  tipo_activo VARCHAR(80) NOT NULL,
  descripcion VARCHAR(300) NOT NULL,
  nivel_dano VARCHAR(20) NOT NULL,
  validada BOOLEAN NOT NULL
);

-- Tablas propias del módulo.
CREATE TABLE IF NOT EXISTS proyectos (
  id VARCHAR(36) PRIMARY KEY,
  desastre_id VARCHAR(36) NOT NULL,
  organizacion VARCHAR(80) NOT NULL,
  nombre VARCHAR(200) NOT NULL,
  tipo VARCHAR(20) NOT NULL,
  estado VARCHAR(20) NOT NULL,
  presupuesto NUMERIC(18,2),
  fuente_financiacion VARCHAR(200),
  responsable VARCHAR(120),
  contratista VARCHAR(200),
  inicio_plan DATE,
  fin_plan DATE,
  avance INT NOT NULL,
  costo_acumulado NUMERIC(18,2) NOT NULL,
  version BIGINT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_proyectos_desastre ON proyectos(desastre_id, organizacion);

CREATE TABLE IF NOT EXISTS proyecto_evaluaciones (
  proyecto_id VARCHAR(36) NOT NULL REFERENCES proyectos(id),
  evaluacion_id VARCHAR(36) NOT NULL,
  PRIMARY KEY (proyecto_id, evaluacion_id)
);
CREATE INDEX IF NOT EXISTS idx_proyecto_evaluaciones_eval ON proyecto_evaluaciones(evaluacion_id);

CREATE TABLE IF NOT EXISTS seguimiento (
  id VARCHAR(36) PRIMARY KEY,
  proyecto_id VARCHAR(36) NOT NULL REFERENCES proyectos(id),
  orden INT NOT NULL,
  tipo VARCHAR(20) NOT NULL,
  valor NUMERIC(18,2),
  descripcion VARCHAR(500),
  momento TIMESTAMP NOT NULL,
  usuario VARCHAR(80) NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_seguimiento_proyecto ON seguimiento(proyecto_id, orden);

-- RN-17: la aplicación solo inserta y lee esta tabla; nunca la actualiza ni la borra.
CREATE TABLE IF NOT EXISTS auditoria (
  id VARCHAR(36) PRIMARY KEY,
  momento TIMESTAMP NOT NULL,
  usuario VARCHAR(80) NOT NULL,
  rol VARCHAR(20) NOT NULL,
  organizacion VARCHAR(80) NOT NULL,
  operacion VARCHAR(40) NOT NULL,
  entidad VARCHAR(20) NOT NULL,
  entidad_id VARCHAR(36),
  desastre_id VARCHAR(36),
  valor_anterior VARCHAR(500),
  valor_nuevo VARCHAR(500),
  motivo VARCHAR(500),
  resultado VARCHAR(20) NOT NULL,
  detalle VARCHAR(500)
);
CREATE INDEX IF NOT EXISTS idx_auditoria_entidad ON auditoria(entidad_id, momento);
CREATE INDEX IF NOT EXISTS idx_auditoria_desastre ON auditoria(desastre_id, organizacion, momento);

-- Bandeja de salida: eventos pendientes de publicar en la mensajería.
CREATE TABLE IF NOT EXISTS outbox_eventos (
  id VARCHAR(36) PRIMARY KEY,
  tipo VARCHAR(80) NOT NULL,
  agregado_id VARCHAR(36) NOT NULL,
  payload_json VARCHAR(2000) NOT NULL,
  creado_en TIMESTAMP NOT NULL,
  publicado_en TIMESTAMP
);
