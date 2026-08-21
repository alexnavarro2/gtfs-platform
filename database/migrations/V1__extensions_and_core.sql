-- Fase 1: extensiones + modelo de dominio núcleo (agencia, paradas, rutas, patrones,
-- shapes, calendarios, trips, stop_times, frecuencias).
-- Los *_id de GTFS se exponen en la columna gtfs_id (business key estable, nunca
-- regenerada mientras la entidad siga representando el mismo objeto conceptual).

CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE app_user (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    email           TEXT NOT NULL UNIQUE,
    display_name    TEXT NOT NULL,
    role            TEXT NOT NULL CHECK (role IN ('ADMIN', 'EDITOR', 'VIEWER')),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE feed (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name            TEXT NOT NULL,
    description     TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      UUID REFERENCES app_user(id),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by      UUID REFERENCES app_user(id)
);

CREATE TABLE feed_version (
    id                    UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    feed_id               UUID NOT NULL REFERENCES feed(id) ON DELETE CASCADE,
    version_number        INTEGER NOT NULL,
    status                TEXT NOT NULL DEFAULT 'DRAFT'
                              CHECK (status IN ('DRAFT','VALIDATING','VALID','PUBLISHED','ARCHIVED')),
    feed_publisher_name   TEXT,
    feed_publisher_url    TEXT,
    feed_lang             TEXT,
    default_lang          TEXT,
    feed_start_date       DATE,
    feed_end_date         DATE,
    feed_version_string   TEXT,
    feed_contact_email    TEXT,
    feed_contact_url      TEXT,
    row_version           BIGINT NOT NULL DEFAULT 0, -- control de concurrencia optimista
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by            UUID REFERENCES app_user(id),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by            UUID REFERENCES app_user(id),
    UNIQUE (feed_id, version_number)
);

CREATE TABLE agency (
    id                UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    feed_version_id   UUID NOT NULL REFERENCES feed_version(id) ON DELETE CASCADE,
    gtfs_id           TEXT NOT NULL,
    agency_name       TEXT NOT NULL,
    agency_url        TEXT NOT NULL,
    agency_timezone   TEXT NOT NULL,
    agency_lang       TEXT,
    agency_phone      TEXT,
    agency_fare_url   TEXT,
    agency_email      TEXT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by        UUID REFERENCES app_user(id),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by        UUID REFERENCES app_user(id),
    UNIQUE (feed_version_id, gtfs_id)
);

CREATE TABLE stop (
    id                    UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    feed_version_id       UUID NOT NULL REFERENCES feed_version(id) ON DELETE CASCADE,
    gtfs_id               TEXT NOT NULL,
    stop_code             TEXT,
    stop_name             TEXT,
    tts_stop_name         TEXT,
    stop_desc             TEXT,
    geom                  GEOMETRY(Point, 4326) NOT NULL,
    zone_id               TEXT,
    stop_url              TEXT,
    location_type         SMALLINT NOT NULL DEFAULT 0,
    parent_station_id     UUID REFERENCES stop(id),
    stop_timezone         TEXT,
    wheelchair_boarding   SMALLINT NOT NULL DEFAULT 0,
    platform_code         TEXT,
    row_version           BIGINT NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by            UUID REFERENCES app_user(id),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by            UUID REFERENCES app_user(id),
    UNIQUE (feed_version_id, gtfs_id)
);
CREATE INDEX idx_stop_geom ON stop USING GIST (geom);
CREATE INDEX idx_stop_feed_version ON stop (feed_version_id);

CREATE TABLE route (
    id                    UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    feed_version_id       UUID NOT NULL REFERENCES feed_version(id) ON DELETE CASCADE,
    gtfs_id               TEXT NOT NULL,
    agency_id             UUID NOT NULL REFERENCES agency(id),
    route_short_name      TEXT,
    route_long_name       TEXT,
    route_desc            TEXT,
    route_type            INTEGER NOT NULL,
    route_url             TEXT,
    route_color           TEXT,
    route_text_color      TEXT,
    route_sort_order      INTEGER,
    continuous_pickup     SMALLINT,
    continuous_drop_off   SMALLINT,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by            UUID REFERENCES app_user(id),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by            UUID REFERENCES app_user(id),
    UNIQUE (feed_version_id, gtfs_id)
);
CREATE INDEX idx_route_feed_version ON route (feed_version_id);

-- RoutePattern: abstracción interna del editor (IDA/REGRESO). No es una tabla GTFS
-- directa; de ella se derivan trips.txt / stop_times.txt / shapes.txt en el export.
CREATE TABLE route_pattern (
    id                UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    route_id          UUID NOT NULL REFERENCES route(id) ON DELETE CASCADE,
    shape_gtfs_id     TEXT NOT NULL,
    name              TEXT NOT NULL,
    direction_id      SMALLINT NOT NULL DEFAULT 0 CHECK (direction_id IN (0,1)),
    trip_headsign     TEXT,
    trip_short_name   TEXT,
    geom              GEOMETRY(LineString, 4326),
    row_version       BIGINT NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by        UUID REFERENCES app_user(id),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by        UUID REFERENCES app_user(id),
    UNIQUE (route_id, shape_gtfs_id)
);
CREATE INDEX idx_route_pattern_geom ON route_pattern USING GIST (geom);
CREATE INDEX idx_route_pattern_route ON route_pattern (route_id);

CREATE TABLE shape_point (
    id                    UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    route_pattern_id     UUID NOT NULL REFERENCES route_pattern(id) ON DELETE CASCADE,
    shape_pt_sequence     INTEGER NOT NULL,
    shape_pt_lat          DOUBLE PRECISION NOT NULL,
    shape_pt_lon          DOUBLE PRECISION NOT NULL,
    shape_dist_traveled   DOUBLE PRECISION,
    UNIQUE (route_pattern_id, shape_pt_sequence)
);

CREATE TABLE pattern_stop (
    id                       UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    route_pattern_id        UUID NOT NULL REFERENCES route_pattern(id) ON DELETE CASCADE,
    stop_id                  UUID NOT NULL REFERENCES stop(id),
    stop_sequence             INTEGER NOT NULL,
    distance_along_shape      DOUBLE PRECISION,
    default_timepoint         SMALLINT NOT NULL DEFAULT 1,
    default_pickup_type       SMALLINT NOT NULL DEFAULT 0,
    default_drop_off_type     SMALLINT NOT NULL DEFAULT 0,
    stop_headsign             TEXT,
    UNIQUE (route_pattern_id, stop_sequence)
);
CREATE INDEX idx_pattern_stop_pattern ON pattern_stop (route_pattern_id);
CREATE INDEX idx_pattern_stop_stop ON pattern_stop (stop_id);

CREATE TABLE service_calendar (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    feed_version_id UUID NOT NULL REFERENCES feed_version(id) ON DELETE CASCADE,
    gtfs_id         TEXT NOT NULL,
    name            TEXT NOT NULL,
    monday          BOOLEAN NOT NULL DEFAULT false,
    tuesday         BOOLEAN NOT NULL DEFAULT false,
    wednesday       BOOLEAN NOT NULL DEFAULT false,
    thursday        BOOLEAN NOT NULL DEFAULT false,
    friday          BOOLEAN NOT NULL DEFAULT false,
    saturday        BOOLEAN NOT NULL DEFAULT false,
    sunday          BOOLEAN NOT NULL DEFAULT false,
    start_date      DATE NOT NULL,
    end_date        DATE NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (feed_version_id, gtfs_id)
);

CREATE TABLE service_exception (
    id                    UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    service_calendar_id   UUID NOT NULL REFERENCES service_calendar(id) ON DELETE CASCADE,
    exception_date        DATE NOT NULL,
    exception_type        SMALLINT NOT NULL CHECK (exception_type IN (1,2)), -- 1=added 2=removed
    UNIQUE (service_calendar_id, exception_date)
);

CREATE TABLE trip (
    id                       UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    route_pattern_id        UUID NOT NULL REFERENCES route_pattern(id) ON DELETE CASCADE,
    service_calendar_id     UUID NOT NULL REFERENCES service_calendar(id),
    gtfs_id                  TEXT NOT NULL,
    trip_headsign             TEXT,
    trip_short_name           TEXT,
    block_id                  TEXT,
    wheelchair_accessible     SMALLINT NOT NULL DEFAULT 0,
    bikes_allowed             SMALLINT NOT NULL DEFAULT 0,
    is_frequency_based        BOOLEAN NOT NULL DEFAULT false,
    created_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (route_pattern_id, gtfs_id)
);
CREATE INDEX idx_trip_pattern ON trip (route_pattern_id);
CREATE INDEX idx_trip_service ON trip (service_calendar_id);

-- Tiempos almacenados como segundos-desde-medianoche (entero), nunca java.time.LocalTime,
-- para soportar correctamente valores >= 24:00:00 sin desbordar al día siguiente.
CREATE TABLE stop_time (
    id                    UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    trip_id                UUID NOT NULL REFERENCES trip(id) ON DELETE CASCADE,
    pattern_stop_id        UUID NOT NULL REFERENCES pattern_stop(id),
    stop_sequence           INTEGER NOT NULL,
    arrival_time_sec        INTEGER NOT NULL,
    departure_time_sec      INTEGER NOT NULL,
    stop_headsign            TEXT,
    pickup_type               SMALLINT NOT NULL DEFAULT 0,
    drop_off_type             SMALLINT NOT NULL DEFAULT 0,
    shape_dist_traveled        DOUBLE PRECISION,
    timepoint                   SMALLINT NOT NULL DEFAULT 1,
    UNIQUE (trip_id, stop_sequence)
);
CREATE INDEX idx_stop_time_trip ON stop_time (trip_id);

CREATE TABLE frequency_entry (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    trip_id         UUID NOT NULL REFERENCES trip(id) ON DELETE CASCADE,
    start_time_sec  INTEGER NOT NULL,
    end_time_sec    INTEGER NOT NULL,
    headway_secs    INTEGER NOT NULL,
    exact_times     SMALLINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_frequency_trip ON frequency_entry (trip_id);
