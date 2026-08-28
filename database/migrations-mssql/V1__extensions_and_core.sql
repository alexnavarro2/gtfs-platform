-- Equivalente a database/migrations/V1 (PostgreSQL/PostGIS), reescrito para SQL Server.
-- Mismas tablas, mismas columnas, mismas relaciones — solo cambia la sintaxis:
--   UUID -> UNIQUEIDENTIFIER, uuid_generate_v4() -> NEWID() (no hace falta ninguna
--     extensión, es nativo de SQL Server)
--   TEXT -> NVARCHAR(MAX); las columnas TEXT que participan en un UNIQUE/índice se
--     acotan a NVARCHAR(255) porque SQL Server no permite indexar una columna MAX
--     (gtfs_id, email — siempre son identificadores cortos en la práctica)
--   TIMESTAMPTZ -> DATETIMEOFFSET, now() -> SYSDATETIMEOFFSET()
--   BOOLEAN -> BIT, DOUBLE PRECISION -> FLOAT
--   GEOMETRY(Point/LineString, 4326) -> GEOMETRY (tipo nativo de SQL Server, plano —
--     igual que en Postgres, aquí SRID 4326 solo es metadato; nada consulta estas
--     columnas con SQL espacial, así que no hace falta el tipo GEOGRAPHY con cálculo
--     de distancias geodésicas: StopRepository.findNear ahora filtra en Java con
--     GeoUtils.haversineMeters en vez de un @Query nativo con ST_DWithin/geography)
--   CREATE INDEX ... USING GIST -> CREATE SPATIAL INDEX (con BOUNDING_BOX: los
--     índices espaciales sobre GEOMETRY, a diferencia de GEOGRAPHY, exigen indicar
--     el rango de coordenadas — aquí el rango válido de lon/lat en grados)
-- No existe equivalente de V3 (DEFERRABLE INITIALLY DEFERRED) porque no hace falta:
-- probado contra SQL Server 2022 real que su motor de cascada resuelve solo las
-- referencias cruzadas entre ramas hermanas al borrar un feed completo, sin el
-- error de FK transitorio que sí da Postgres — ver V3 en esta carpeta.

-- CREATE SPATIAL INDEX exige estas opciones de sesión encendidas/apagadas
-- exactamente así (probado: sin esto, "CREATE INDEX failed because... QUOTED_IDENTIFIER").
SET ANSI_NULLS ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET ARITHABORT ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET QUOTED_IDENTIFIER ON;
SET NUMERIC_ROUNDABORT OFF;

CREATE TABLE app_user (
    id              UNIQUEIDENTIFIER PRIMARY KEY DEFAULT NEWID(),
    email           NVARCHAR(255) NOT NULL UNIQUE,
    display_name    NVARCHAR(MAX) NOT NULL,
    role            NVARCHAR(20) NOT NULL CHECK (role IN ('ADMIN', 'EDITOR', 'VIEWER')),
    created_at      DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET()
);

CREATE TABLE feed (
    id              UNIQUEIDENTIFIER PRIMARY KEY DEFAULT NEWID(),
    name            NVARCHAR(MAX) NOT NULL,
    description     NVARCHAR(MAX),
    created_at      DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    created_by      UNIQUEIDENTIFIER REFERENCES app_user(id),
    updated_at      DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    updated_by      UNIQUEIDENTIFIER REFERENCES app_user(id)
);

CREATE TABLE feed_version (
    id                    UNIQUEIDENTIFIER PRIMARY KEY DEFAULT NEWID(),
    feed_id               UNIQUEIDENTIFIER NOT NULL REFERENCES feed(id) ON DELETE CASCADE,
    version_number        INT NOT NULL,
    status                NVARCHAR(20) NOT NULL DEFAULT 'DRAFT'
                              CHECK (status IN ('DRAFT','VALIDATING','VALID','PUBLISHED','ARCHIVED')),
    feed_publisher_name   NVARCHAR(MAX),
    feed_publisher_url    NVARCHAR(MAX),
    feed_lang             NVARCHAR(MAX),
    default_lang          NVARCHAR(MAX),
    feed_start_date       DATE,
    feed_end_date         DATE,
    feed_version_string   NVARCHAR(MAX),
    feed_contact_email    NVARCHAR(MAX),
    feed_contact_url      NVARCHAR(MAX),
    row_version           BIGINT NOT NULL DEFAULT 0, -- control de concurrencia optimista
    created_at            DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    created_by            UNIQUEIDENTIFIER REFERENCES app_user(id),
    updated_at            DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    updated_by            UNIQUEIDENTIFIER REFERENCES app_user(id),
    UNIQUE (feed_id, version_number)
);

CREATE TABLE agency (
    id                UNIQUEIDENTIFIER PRIMARY KEY DEFAULT NEWID(),
    feed_version_id   UNIQUEIDENTIFIER NOT NULL REFERENCES feed_version(id) ON DELETE CASCADE,
    gtfs_id           NVARCHAR(255) NOT NULL,
    agency_name       NVARCHAR(MAX) NOT NULL,
    agency_url        NVARCHAR(MAX) NOT NULL,
    agency_timezone   NVARCHAR(MAX) NOT NULL,
    agency_lang       NVARCHAR(MAX),
    agency_phone      NVARCHAR(MAX),
    agency_fare_url   NVARCHAR(MAX),
    agency_email      NVARCHAR(MAX),
    created_at        DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    created_by        UNIQUEIDENTIFIER REFERENCES app_user(id),
    updated_at        DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    updated_by        UNIQUEIDENTIFIER REFERENCES app_user(id),
    UNIQUE (feed_version_id, gtfs_id)
);

CREATE TABLE stop (
    id                    UNIQUEIDENTIFIER PRIMARY KEY DEFAULT NEWID(),
    feed_version_id       UNIQUEIDENTIFIER NOT NULL REFERENCES feed_version(id) ON DELETE CASCADE,
    gtfs_id               NVARCHAR(255) NOT NULL,
    stop_code             NVARCHAR(MAX),
    stop_name             NVARCHAR(MAX),
    tts_stop_name         NVARCHAR(MAX),
    stop_desc             NVARCHAR(MAX),
    geom                  GEOMETRY NOT NULL,
    zone_id               NVARCHAR(MAX),
    stop_url              NVARCHAR(MAX),
    location_type         SMALLINT NOT NULL DEFAULT 0,
    parent_station_id     UNIQUEIDENTIFIER REFERENCES stop(id),
    stop_timezone         NVARCHAR(MAX),
    wheelchair_boarding   SMALLINT NOT NULL DEFAULT 0,
    platform_code         NVARCHAR(MAX),
    row_version           BIGINT NOT NULL DEFAULT 0,
    created_at            DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    created_by            UNIQUEIDENTIFIER REFERENCES app_user(id),
    updated_at            DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    updated_by            UNIQUEIDENTIFIER REFERENCES app_user(id),
    UNIQUE (feed_version_id, gtfs_id)
);
CREATE SPATIAL INDEX idx_stop_geom ON stop (geom)
    WITH (BOUNDING_BOX = (-180, -90, 180, 90));
CREATE INDEX idx_stop_feed_version ON stop (feed_version_id);

CREATE TABLE route (
    id                    UNIQUEIDENTIFIER PRIMARY KEY DEFAULT NEWID(),
    feed_version_id       UNIQUEIDENTIFIER NOT NULL REFERENCES feed_version(id) ON DELETE CASCADE,
    gtfs_id               NVARCHAR(255) NOT NULL,
    agency_id             UNIQUEIDENTIFIER NOT NULL REFERENCES agency(id),
    route_short_name      NVARCHAR(MAX),
    route_long_name       NVARCHAR(MAX),
    route_desc            NVARCHAR(MAX),
    route_type            INT NOT NULL,
    route_url             NVARCHAR(MAX),
    route_color           NVARCHAR(MAX),
    route_text_color      NVARCHAR(MAX),
    route_sort_order      INT,
    continuous_pickup     SMALLINT,
    continuous_drop_off   SMALLINT,
    created_at            DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    created_by            UNIQUEIDENTIFIER REFERENCES app_user(id),
    updated_at            DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    updated_by            UNIQUEIDENTIFIER REFERENCES app_user(id),
    UNIQUE (feed_version_id, gtfs_id)
);
CREATE INDEX idx_route_feed_version ON route (feed_version_id);

-- RoutePattern: abstracción interna del editor (IDA/REGRESO). No es una tabla GTFS
-- directa; de ella se derivan trips.txt / stop_times.txt / shapes.txt en el export.
CREATE TABLE route_pattern (
    id                UNIQUEIDENTIFIER PRIMARY KEY DEFAULT NEWID(),
    route_id          UNIQUEIDENTIFIER NOT NULL REFERENCES route(id) ON DELETE CASCADE,
    shape_gtfs_id     NVARCHAR(255) NOT NULL,
    name              NVARCHAR(MAX) NOT NULL,
    direction_id      SMALLINT NOT NULL DEFAULT 0 CHECK (direction_id IN (0,1)),
    trip_headsign     NVARCHAR(MAX),
    trip_short_name   NVARCHAR(MAX),
    geom              GEOMETRY,
    row_version       BIGINT NOT NULL DEFAULT 0,
    created_at        DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    created_by        UNIQUEIDENTIFIER REFERENCES app_user(id),
    updated_at        DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    updated_by        UNIQUEIDENTIFIER REFERENCES app_user(id),
    UNIQUE (route_id, shape_gtfs_id)
);
CREATE SPATIAL INDEX idx_route_pattern_geom ON route_pattern (geom)
    WITH (BOUNDING_BOX = (-180, -90, 180, 90));
CREATE INDEX idx_route_pattern_route ON route_pattern (route_id);

CREATE TABLE shape_point (
    id                    UNIQUEIDENTIFIER PRIMARY KEY DEFAULT NEWID(),
    route_pattern_id      UNIQUEIDENTIFIER NOT NULL REFERENCES route_pattern(id) ON DELETE CASCADE,
    shape_pt_sequence     INT NOT NULL,
    shape_pt_lat          FLOAT NOT NULL,
    shape_pt_lon          FLOAT NOT NULL,
    shape_dist_traveled   FLOAT,
    UNIQUE (route_pattern_id, shape_pt_sequence)
);

CREATE TABLE pattern_stop (
    id                       UNIQUEIDENTIFIER PRIMARY KEY DEFAULT NEWID(),
    route_pattern_id         UNIQUEIDENTIFIER NOT NULL REFERENCES route_pattern(id) ON DELETE CASCADE,
    stop_id                  UNIQUEIDENTIFIER NOT NULL REFERENCES stop(id),
    stop_sequence            INT NOT NULL,
    distance_along_shape     FLOAT,
    default_timepoint        SMALLINT NOT NULL DEFAULT 1,
    default_pickup_type      SMALLINT NOT NULL DEFAULT 0,
    default_drop_off_type    SMALLINT NOT NULL DEFAULT 0,
    stop_headsign            NVARCHAR(MAX),
    UNIQUE (route_pattern_id, stop_sequence)
);
CREATE INDEX idx_pattern_stop_pattern ON pattern_stop (route_pattern_id);
CREATE INDEX idx_pattern_stop_stop ON pattern_stop (stop_id);

CREATE TABLE service_calendar (
    id              UNIQUEIDENTIFIER PRIMARY KEY DEFAULT NEWID(),
    feed_version_id UNIQUEIDENTIFIER NOT NULL REFERENCES feed_version(id) ON DELETE CASCADE,
    gtfs_id         NVARCHAR(255) NOT NULL,
    name            NVARCHAR(MAX) NOT NULL,
    monday          BIT NOT NULL DEFAULT 0,
    tuesday         BIT NOT NULL DEFAULT 0,
    wednesday       BIT NOT NULL DEFAULT 0,
    thursday        BIT NOT NULL DEFAULT 0,
    friday          BIT NOT NULL DEFAULT 0,
    saturday        BIT NOT NULL DEFAULT 0,
    sunday          BIT NOT NULL DEFAULT 0,
    start_date      DATE NOT NULL,
    end_date        DATE NOT NULL,
    created_at      DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    updated_at      DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    UNIQUE (feed_version_id, gtfs_id)
);

CREATE TABLE service_exception (
    id                    UNIQUEIDENTIFIER PRIMARY KEY DEFAULT NEWID(),
    service_calendar_id   UNIQUEIDENTIFIER NOT NULL REFERENCES service_calendar(id) ON DELETE CASCADE,
    exception_date        DATE NOT NULL,
    exception_type        SMALLINT NOT NULL CHECK (exception_type IN (1,2)), -- 1=added 2=removed
    UNIQUE (service_calendar_id, exception_date)
);

CREATE TABLE trip (
    id                       UNIQUEIDENTIFIER PRIMARY KEY DEFAULT NEWID(),
    route_pattern_id         UNIQUEIDENTIFIER NOT NULL REFERENCES route_pattern(id) ON DELETE CASCADE,
    service_calendar_id      UNIQUEIDENTIFIER NOT NULL REFERENCES service_calendar(id),
    gtfs_id                  NVARCHAR(255) NOT NULL,
    trip_headsign            NVARCHAR(MAX),
    trip_short_name          NVARCHAR(MAX),
    block_id                 NVARCHAR(MAX),
    wheelchair_accessible    SMALLINT NOT NULL DEFAULT 0,
    bikes_allowed            SMALLINT NOT NULL DEFAULT 0,
    is_frequency_based       BIT NOT NULL DEFAULT 0,
    created_at               DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    updated_at               DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    UNIQUE (route_pattern_id, gtfs_id)
);
CREATE INDEX idx_trip_pattern ON trip (route_pattern_id);
CREATE INDEX idx_trip_service ON trip (service_calendar_id);

-- Tiempos almacenados como segundos-desde-medianoche (entero), nunca java.time.LocalTime,
-- para soportar correctamente valores >= 24:00:00 sin desbordar al día siguiente.
CREATE TABLE stop_time (
    id                    UNIQUEIDENTIFIER PRIMARY KEY DEFAULT NEWID(),
    trip_id               UNIQUEIDENTIFIER NOT NULL REFERENCES trip(id) ON DELETE CASCADE,
    pattern_stop_id       UNIQUEIDENTIFIER NOT NULL REFERENCES pattern_stop(id),
    stop_sequence         INT NOT NULL,
    arrival_time_sec      INT NOT NULL,
    departure_time_sec    INT NOT NULL,
    stop_headsign         NVARCHAR(MAX),
    pickup_type           SMALLINT NOT NULL DEFAULT 0,
    drop_off_type         SMALLINT NOT NULL DEFAULT 0,
    shape_dist_traveled   FLOAT,
    timepoint             SMALLINT NOT NULL DEFAULT 1,
    UNIQUE (trip_id, stop_sequence)
);
CREATE INDEX idx_stop_time_trip ON stop_time (trip_id);

CREATE TABLE frequency_entry (
    id              UNIQUEIDENTIFIER PRIMARY KEY DEFAULT NEWID(),
    trip_id         UNIQUEIDENTIFIER NOT NULL REFERENCES trip(id) ON DELETE CASCADE,
    start_time_sec  INT NOT NULL,
    end_time_sec    INT NOT NULL,
    headway_secs    INT NOT NULL,
    exact_times     SMALLINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_frequency_trip ON frequency_entry (trip_id);
