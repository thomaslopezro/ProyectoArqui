CREATE TABLE IF NOT EXISTS auditoria_desastres (
    id UUID PRIMARY KEY,
    desastre_id UUID NOT NULL,
    accion VARCHAR(40) NOT NULL,
    actor_id VARCHAR(100),
    detalle TEXT,
    ocurrido_en TIMESTAMPTZ NOT NULL,

    CONSTRAINT fk_auditoria_desastre
        FOREIGN KEY (desastre_id)
        REFERENCES desastres(id),

    CONSTRAINT ck_auditoria_accion
        CHECK (accion IN ('DESASTRE_REGISTRADO'))
);

CREATE INDEX IF NOT EXISTS idx_auditoria_desastre
    ON auditoria_desastres(desastre_id);