CREATE TABLE IF NOT EXISTS desastres (
    id UUID PRIMARY KEY,
    nombre VARCHAR(150) NOT NULL,
    descripcion TEXT,
    severidad VARCHAR(10) NOT NULL,
    creado_en TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT ck_desastres_severidad
    CHECK (
        severidad IN (
            'Nivel_1',
            'Nivel_2',
            'Nivel_3',
            'Nivel_4'
        )
    )
);