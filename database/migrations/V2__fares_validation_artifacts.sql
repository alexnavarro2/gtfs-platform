-- Fase 1: Fares V2 (caso simple), validación y artefactos de import/export.

CREATE TABLE rider_category (
    id                          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    feed_version_id             UUID NOT NULL REFERENCES feed_version(id) ON DELETE CASCADE,
    gtfs_id                     TEXT NOT NULL,
    rider_category_name         TEXT NOT NULL,
    is_default_fare_category    SMALLINT NOT NULL DEFAULT 0,
    UNIQUE (feed_version_id, gtfs_id)
);

CREATE TABLE fare_media (
    id               UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    feed_version_id  UUID NOT NULL REFERENCES feed_version(id) ON DELETE CASCADE,
    gtfs_id          TEXT NOT NULL,
    fare_media_name  TEXT,
    fare_media_type  SMALLINT NOT NULL, -- 0 none, 1 paper, 2 transit card, 3 cEMV, 4 mobile app
    UNIQUE (feed_version_id, gtfs_id)
);

CREATE TABLE fare_product (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    feed_version_id     UUID NOT NULL REFERENCES feed_version(id) ON DELETE CASCADE,
    gtfs_id             TEXT NOT NULL,
    fare_product_name   TEXT NOT NULL,
    rider_category_id   UUID REFERENCES rider_category(id),
    fare_media_id       UUID REFERENCES fare_media(id),
    amount              NUMERIC(10,2) NOT NULL,
    currency            VARCHAR(3) NOT NULL,
    UNIQUE (feed_version_id, gtfs_id)
);

-- Regla de tarifa por tramo. network_id NULL = aplica a toda la red (caso simple / demo).
CREATE TABLE fare_leg_rule (
    id                UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    feed_version_id   UUID NOT NULL REFERENCES feed_version(id) ON DELETE CASCADE,
    gtfs_leg_group_id TEXT,
    network_id        TEXT,
    fare_product_id   UUID NOT NULL REFERENCES fare_product(id)
);

-- Regla de transbordo (Fares V2). free_transfer_window_secs = duración de gracia.
CREATE TABLE fare_transfer_rule (
    id                        UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    feed_version_id           UUID NOT NULL REFERENCES feed_version(id) ON DELETE CASCADE,
    from_leg_group_id         TEXT,
    to_leg_group_id           TEXT,
    transfer_count             SMALLINT,
    duration_limit_secs        INTEGER,
    duration_limit_type         SMALLINT,
    fare_transfer_type          SMALLINT NOT NULL DEFAULT 0,
    fare_product_id              UUID REFERENCES fare_product(id)
);

CREATE TABLE transfer_rule (
    id                    UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    feed_version_id       UUID NOT NULL REFERENCES feed_version(id) ON DELETE CASCADE,
    from_stop_id          UUID REFERENCES stop(id),
    to_stop_id            UUID REFERENCES stop(id),
    transfer_type         SMALLINT NOT NULL DEFAULT 0,
    min_transfer_time_sec INTEGER
);

CREATE TABLE validation_run (
    id                UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    feed_version_id   UUID NOT NULL REFERENCES feed_version(id) ON DELETE CASCADE,
    source            TEXT NOT NULL CHECK (source IN ('INTERNAL','MOBILITYDATA')),
    status            TEXT NOT NULL DEFAULT 'RUNNING' CHECK (status IN ('RUNNING','DONE','FAILED')),
    started_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at       TIMESTAMPTZ,
    error_message     TEXT
);

CREATE TABLE validation_notice (
    id                 UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    validation_run_id  UUID NOT NULL REFERENCES validation_run(id) ON DELETE CASCADE,
    severity           TEXT NOT NULL CHECK (severity IN ('ERROR','WARNING','INFO')),
    category           TEXT NOT NULL CHECK (category IN ('GTFS_SPEC','GTFS_BEST_PRACTICE','LOCAL_QUALITY_RULE')),
    code               TEXT NOT NULL,
    title              TEXT NOT NULL,
    description        TEXT,
    entity_type        TEXT,
    entity_id          TEXT,
    lat                DOUBLE PRECISION,
    lon                DOUBLE PRECISION
);
CREATE INDEX idx_validation_notice_run ON validation_notice (validation_run_id);

CREATE TABLE export_artifact (
    id                UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    feed_version_id   UUID NOT NULL REFERENCES feed_version(id) ON DELETE CASCADE,
    file_path         TEXT NOT NULL,
    sha256            TEXT NOT NULL,
    size_bytes        BIGINT NOT NULL,
    generated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE import_job (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    feed_id         UUID NOT NULL REFERENCES feed(id) ON DELETE CASCADE,
    file_name       TEXT NOT NULL,
    status          TEXT NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','RUNNING','DONE','FAILED')),
    started_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at     TIMESTAMPTZ,
    error_message   TEXT,
    result_feed_version_id UUID REFERENCES feed_version(id)
);
