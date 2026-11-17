CREATE TABLE IF NOT EXISTS solicitudes_recursos (
 id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 solicitud_id UUID NOT NULL,
 organizacion_id UUID NOT NULL REFERENCES organizaciones(id),
 desastre_id UUID NOT NULL REFERENCES desastres(id),
 descripcion_desastre TEXT NOT NULL,
 tipo_recurso VARCHAR(120) NOT NULL,
 cantidad INTEGER NOT NULL CHECK(cantidad>0),
 prioridad VARCHAR(10) NOT NULL CHECK(prioridad IN ('BAJA','MEDIA','ALTA')),
 estado VARCHAR(12) NOT NULL CHECK(estado IN ('PENDIENTE','ATENDIDA')),
 huella CHAR(64) NOT NULL,
 creada_por UUID NOT NULL REFERENCES usuarios(id),
 creada_en TIMESTAMPTZ NOT NULL DEFAULT now(),
 atendida_por UUID REFERENCES usuarios(id), atendida_en TIMESTAMPTZ,
 UNIQUE(organizacion_id,solicitud_id),
 CHECK((estado='PENDIENTE' AND atendida_por IS NULL AND atendida_en IS NULL) OR
       (estado='ATENDIDA' AND atendida_por IS NOT NULL AND atendida_en IS NOT NULL))
);
CREATE INDEX IF NOT EXISTS ix_solicitudes_org_estado ON solicitudes_recursos(organizacion_id,estado,id);
CREATE TABLE IF NOT EXISTS solicitudes_historial (
 id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 solicitud_id BIGINT NOT NULL REFERENCES solicitudes_recursos(id),
 actor_id UUID NOT NULL REFERENCES usuarios(id),
 accion VARCHAR(30) NOT NULL CHECK(accion IN ('SOLICITUD_REGISTRADA','SOLICITUD_ATENDIDA')),
 ocurrida_en TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(solicitud_id,accion)
);
