-- Equivalente a database/migrations/V2 (PostgreSQL), reescrito para SQL Server.
-- Mismo criterio de tipos que V1 — ver el comentario de cabecera de ese archivo.

CREATE TABLE rider_category (
    id                          UNIQUEIDENTIFIER PRIMARY KEY DEFAULT NEWID(),
    feed_version_id             UNIQUEIDENTIFIER NOT NULL REFERENCES feed_version(id) ON DELETE CASCADE,
    gtfs_id                     NVARCHAR(255) NOT NULL,
    rider_category_name         NVARCHAR(MAX) NOT NULL,
    is_default_fare_category    SMALLINT NOT NULL DEFAULT 0,
    UNIQUE (feed_version_id, gtfs_id)
);

CREATE TABLE fare_media (
    id               UNIQUEIDENTIFIER PRIMARY KEY DEFAULT NEWID(),
    feed_version_id  UNIQUEIDENTIFIER NOT NULL REFERENCES feed_version(id) ON DELETE CASCADE,
    gtfs_id          NVARCHAR(255) NOT NULL,
    fare_media_name  NVARCHAR(MAX),
    fare_media_type  SMALLINT NOT NULL, -- 0 none, 1 paper, 2 transit card, 3 cEMV, 4 mobile app
    UNIQUE (feed_version_id, gtfs_id)
);

CREATE TABLE fare_product (
    id                  UNIQUEIDENTIFIER PRIMARY KEY DEFAULT NEWID(),
    feed_version_id     UNIQUEIDENTIFIER NOT NULL REFERENCES feed_version(id) ON DELETE CASCADE,
    gtfs_id             NVARCHAR(255) NOT NULL,
    fare_product_name   NVARCHAR(MAX) NOT NULL,
    rider_category_id   UNIQUEIDENTIFIER REFERENCES rider_category(id),
    fare_media_id       UNIQUEIDENTIFIER REFERENCES fare_media(id),
    amount              NUMERIC(10,2) NOT NULL,
    currency            VARCHAR(3) NOT NULL,
    UNIQUE (feed_version_id, gtfs_id)
);

-- Regla de tarifa por tramo. network_id NULL = aplica a toda la red (caso simple / demo).
CREATE TABLE fare_leg_rule (
    id                UNIQUEIDENTIFIER PRIMARY KEY DEFAULT NEWID(),
    feed_version_id   UNIQUEIDENTIFIER NOT NULL REFERENCES feed_version(id) ON DELETE CASCADE,
    gtfs_leg_group_id NVARCHAR(MAX),
    network_id        NVARCHAR(MAX),
    fare_product_id   UNIQUEIDENTIFIER NOT NULL REFERENCES fare_product(id)
);

-- Regla de transbordo (Fares V2). free_transfer_window_secs = duración de gracia.
CREATE TABLE fare_transfer_rule (
    id                        UNIQUEIDENTIFIER PRIMARY KEY DEFAULT NEWID(),
    feed_version_id           UNIQUEIDENTIFIER NOT NULL REFERENCES feed_version(id) ON DELETE CASCADE,
    from_leg_group_id         NVARCHAR(MAX),
    to_leg_group_id           NVARCHAR(MAX),
    transfer_count            SMALLINT,
    duration_limit_secs       INT,
    duration_limit_type       SMALLINT,
    fare_transfer_type        SMALLINT NOT NULL DEFAULT 0,
    fare_product_id           UNIQUEIDENTIFIER REFERENCES fare_product(id)
);

CREATE TABLE transfer_rule (
    id                    UNIQUEIDENTIFIER PRIMARY KEY DEFAULT NEWID(),
    feed_version_id       UNIQUEIDENTIFIER NOT NULL REFERENCES feed_version(id) ON DELETE CASCADE,
    from_stop_id          UNIQUEIDENTIFIER REFERENCES stop(id),
    to_stop_id            UNIQUEIDENTIFIER REFERENCES stop(id),
    transfer_type         SMALLINT NOT NULL DEFAULT 0,
    min_transfer_time_sec INT
);

CREATE TABLE validation_run (
    id                UNIQUEIDENTIFIER PRIMARY KEY DEFAULT NEWID(),
    feed_version_id   UNIQUEIDENTIFIER NOT NULL REFERENCES feed_version(id) ON DELETE CASCADE,
    source            NVARCHAR(20) NOT NULL CHECK (source IN ('INTERNAL','MOBILITYDATA')),
    status            NVARCHAR(20) NOT NULL DEFAULT 'RUNNING' CHECK (status IN ('RUNNING','DONE','FAILED')),
    started_at        DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    finished_at       DATETIMEOFFSET,
    error_message     NVARCHAR(MAX)
);

CREATE TABLE validation_notice (
    id                 UNIQUEIDENTIFIER PRIMARY KEY DEFAULT NEWID(),
    validation_run_id  UNIQUEIDENTIFIER NOT NULL REFERENCES validation_run(id) ON DELETE CASCADE,
    severity           NVARCHAR(20) NOT NULL CHECK (severity IN ('ERROR','WARNING','INFO')),
    category           NVARCHAR(30) NOT NULL CHECK (category IN ('GTFS_SPEC','GTFS_BEST_PRACTICE','LOCAL_QUALITY_RULE')),
    code               NVARCHAR(MAX) NOT NULL,
    title              NVARCHAR(MAX) NOT NULL,
    description        NVARCHAR(MAX),
    entity_type        NVARCHAR(MAX),
    entity_id          NVARCHAR(MAX),
    lat                FLOAT,
    lon                FLOAT
);
CREATE INDEX idx_validation_notice_run ON validation_notice (validation_run_id);

CREATE TABLE export_artifact (
    id                UNIQUEIDENTIFIER PRIMARY KEY DEFAULT NEWID(),
    feed_version_id   UNIQUEIDENTIFIER NOT NULL REFERENCES feed_version(id) ON DELETE CASCADE,
    file_path         NVARCHAR(MAX) NOT NULL,
    sha256            NVARCHAR(64) NOT NULL,
    size_bytes        BIGINT NOT NULL,
    generated_at      DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET()
);

CREATE TABLE import_job (
    id                      UNIQUEIDENTIFIER PRIMARY KEY DEFAULT NEWID(),
    feed_id                 UNIQUEIDENTIFIER NOT NULL REFERENCES feed(id) ON DELETE CASCADE,
    file_name               NVARCHAR(MAX) NOT NULL,
    status                  NVARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','RUNNING','DONE','FAILED')),
    started_at              DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    finished_at             DATETIMEOFFSET,
    error_message           NVARCHAR(MAX),
    result_feed_version_id  UNIQUEIDENTIFIER REFERENCES feed_version(id)
);
