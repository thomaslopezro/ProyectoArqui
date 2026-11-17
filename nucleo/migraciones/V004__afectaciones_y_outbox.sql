BEGIN;
CREATE TABLE IF NOT EXISTS afectaciones (
  id UUID PRIMARY KEY,
  desastre_id UUID NOT NULL REFERENCES desastres(id),
  organizacion_id UUID NOT NULL REFERENCES organizaciones(id),
  solicitud_id UUID NOT NULL,
  huella_solicitud CHAR(64) NOT NULL,
  latitud NUMERIC(9,6) NOT NULL CHECK (latitud BETWEEN -90 AND 90),
  longitud NUMERIC(9,6) NOT NULL CHECK (longitud BETWEEN -180 AND 180),
  nivel_afectacion INTEGER NOT NULL CHECK (nivel_afectacion BETWEEN 1 AND 5),
  observacion VARCHAR(500),
  creado_en TIMESTAMPTZ NOT NULL,
  CONSTRAINT uq_afectacion_org_solicitud UNIQUE (organizacion_id,solicitud_id)
);
CREATE INDEX IF NOT EXISTS idx_afectaciones_desastre ON afectaciones(desastre_id,creado_en DESC);
CREATE TABLE IF NOT EXISTS outbox_eventos (
  id UUID PRIMARY KEY,
  organizacion_id UUID NOT NULL REFERENCES organizaciones(id),
  tipo VARCHAR(80) NOT NULL,
  agregado_id UUID NOT NULL,
  payload_json TEXT NOT NULL,
  creado_en TIMESTAMPTZ NOT NULL,
  publicado_en TIMESTAMPTZ NULL
);
CREATE INDEX IF NOT EXISTS idx_outbox_pendientes ON outbox_eventos(creado_en) WHERE publicado_en IS NULL;
COMMIT;
