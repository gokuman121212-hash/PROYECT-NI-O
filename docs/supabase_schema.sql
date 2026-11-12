CREATE TABLE IF NOT EXISTS public.alertas_archivo (
    alerta_id UUID PRIMARY KEY,
    usuario_id TEXT NOT NULL,
    latitud DOUBLE PRECISION NOT NULL,
    longitud DOUBLE PRECISION NOT NULL,
    tipo TEXT NOT NULL,
    descripcion TEXT,
    nivel_bateria INTEGER,
    origen_offline_mesh BOOLEAN DEFAULT FALSE,
    estado TEXT DEFAULT 'ACTIVA',
    confianza_ia DOUBLE PRECISION,
    indice_riesgo DOUBLE PRECISION,
    timestamp TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_alertas_timestamp ON public.alertas_archivo (timestamp DESC);