-- La importación de paradas desde KML llama al geocoder una vez por punto,
-- secuencial — con archivos grandes (cientos de puntos) esto tarda varios
-- minutos en una sola petición HTTP, y una conexión tan larga sin datos
-- fluyendo puede cortarse en silencio (red local, Docker Desktop, proxies)
-- dejando al navegador esperando una respuesta que nunca llega. Se vuelve
-- asíncrona: la petición inicial solo crea este job y responde de inmediato,
-- el trabajo real corre en segundo plano y el frontend consulta el progreso.
CREATE TABLE kml_stop_import_job (
    id                UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    feed_version_id   UUID NOT NULL REFERENCES feed_version(id) ON DELETE CASCADE,
    status            TEXT NOT NULL DEFAULT 'RUNNING' CHECK (status IN ('RUNNING','DONE','FAILED')),
    total_points      INTEGER NOT NULL DEFAULT 0,
    processed_count   INTEGER NOT NULL DEFAULT 0,
    geocoded_count    INTEGER NOT NULL DEFAULT 0,
    error_message     TEXT,
    started_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at       TIMESTAMPTZ
);
