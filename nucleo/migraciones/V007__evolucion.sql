CREATE TABLE IF NOT EXISTS desastre_cambios (
 id UUID NOT NULL, organizacion_id UUID NOT NULL REFERENCES organizaciones(id),
 desastre_id UUID NOT NULL REFERENCES desastres(id), version BIGINT NOT NULL,
 anterior TEXT NOT NULL, nuevo TEXT NOT NULL, actor_id UUID NOT NULL REFERENCES usuarios(id),
 motivo VARCHAR(500) NOT NULL, huella CHAR(64) NOT NULL, ocurrido_en TIMESTAMPTZ NOT NULL DEFAULT now(),
 PRIMARY KEY(organizacion_id,id), UNIQUE(desastre_id,version)
);
ALTER TABLE solicitudes_recursos ADD COLUMN IF NOT EXISTS evaluacion_id UUID;
ALTER TABLE solicitudes_recursos ADD COLUMN IF NOT EXISTS evaluacion_version BIGINT;
ALTER TABLE solicitudes_recursos ADD COLUMN IF NOT EXISTS evaluacion_resumen TEXT;
DO $$ BEGIN IF NOT EXISTS(SELECT 1 FROM pg_constraint WHERE conname='ck_solicitud_evaluacion') THEN ALTER TABLE solicitudes_recursos ADD CONSTRAINT ck_solicitud_evaluacion CHECK (
 (evaluacion_id IS NULL AND evaluacion_version IS NULL AND evaluacion_resumen IS NULL) OR
 (evaluacion_id IS NOT NULL AND evaluacion_version IS NOT NULL AND evaluacion_resumen IS NOT NULL)); END IF; END $$;
