-- Compatible con V001 y V002 existentes. No elimina registros previos.
BEGIN;
CREATE TABLE IF NOT EXISTS organizaciones (
  id UUID PRIMARY KEY,
  nombre VARCHAR(120) NOT NULL
);
CREATE TABLE IF NOT EXISTS usuarios (
  id UUID PRIMARY KEY,
  usuario VARCHAR(80) NOT NULL UNIQUE,
  password_hash VARCHAR(128) NOT NULL,
  password_salt VARCHAR(64) NOT NULL,
  rol VARCHAR(25) NOT NULL CHECK (rol IN ('COORDINADOR','EVALUADOR','CONSULTA')),
  organizacion_id UUID NOT NULL REFERENCES organizaciones(id),
  activo BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE TABLE IF NOT EXISTS sesiones (
  token_hash VARCHAR(64) PRIMARY KEY,
  usuario_id UUID NOT NULL REFERENCES usuarios(id),
  expira_en TIMESTAMPTZ NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_sesiones_expira ON sesiones(expira_en);
ALTER TABLE desastres ADD COLUMN IF NOT EXISTS organizacion_id UUID REFERENCES organizaciones(id);
ALTER TABLE desastres ADD COLUMN IF NOT EXISTS solicitud_id UUID;
ALTER TABLE desastres ADD COLUMN IF NOT EXISTS huella_solicitud CHAR(64);
CREATE UNIQUE INDEX IF NOT EXISTS uq_desastre_org_solicitud ON desastres(organizacion_id,solicitud_id);
-- Filas anteriores a esta migracion permanecen sin organizacion_id.
-- No se exponen a consultas autenticadas hasta completar su reconciliacion.
COMMIT;
