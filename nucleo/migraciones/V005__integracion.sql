CREATE TABLE IF NOT EXISTS desastres_integrados (
 desastre_id UUID PRIMARY KEY REFERENCES desastres(id),
 referencia VARCHAR(100) NOT NULL UNIQUE,
 datos_json TEXT NOT NULL
);
