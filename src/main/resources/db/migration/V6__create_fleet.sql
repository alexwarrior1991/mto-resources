-- Flota: los vehiculos de via, de carretera y biviales, sus modelos y donde esta cada uno.
--
-- Tres familias, con su norma y su forma de llegar al tajo:
--   * RAIL_ONLY (EN 14033, y EN 13977 las vagonetas): dresinas y castilletes de catenaria, dresinas
--     de via, tren de tendido, auscultacion, locomotoras, vagones remolcados y vagonetas. Llegan por la
--     via; unas pueden ir por via abierta como un tren (transport_mode_authorised), otras solo dentro
--     del corte.
--   * ROAD_RAIL (EN 15746): camiones, plataformas, excavadoras y tractores biviales. Van por carretera,
--     encarrilan en un punto de acceso, trabajan y desencarrilan. Su equipo ferroviario es 9A, 9B o 9C
--     (rail_gear_type), y un 9B sin mejorar no deberia ir a pendientes de 1/75 o mas.
--   * ROAD_ONLY: furgonetas, pick-ups, autobuses de personal, camiones grua y gondolas.
--
-- vehicle_type es el modelo (lo comun a todas sus unidades) y vehicle, cada unidad. No hay base fija:
-- un vehiculo se aparca donde se puede, y parking_site es solo el catalogo de los sitios que ya se
-- conocen. Donde ha estado lo guarda vehicle_location_event, append-only; donde esta ahora,
-- vehicle_position, una fila por vehiculo escrita con SQL nativo y con la marca de agua de observed_at
-- dentro de la sentencia, para que lo que llega tarde no mueva la posicion.

CREATE TYPE vehicle_mobility AS ENUM ('RAIL_ONLY', 'ROAD_RAIL', 'ROAD_ONLY');

CREATE TYPE vehicle_category AS ENUM (
    'CATENARY_TOWER_CAR', 'WIRING_TRAIN', 'TRACK_MAINTENANCE_CAR', 'INSPECTION_CAR', 'LOCOMOTIVE', 'RAIL_WAGON', 'TROLLEY',
    'ROAD_RAIL_MEWP', 'ROAD_RAIL_TRUCK', 'ROAD_RAIL_EXCAVATOR', 'ROAD_RAIL_TRACTOR', 'ROAD_RAIL_TRAILER',
    'VAN', 'PICKUP', 'CREW_BUS', 'CRANE_TRUCK', 'TRUCK', 'LOW_LOADER', 'CAR'
);

CREATE TYPE vehicle_power AS ENUM ('DIESEL', 'ELECTRIC', 'BATTERY', 'HYBRID', 'MANUAL');

-- 9A: ruedas de carril con traccion propia; 9B (high ride): traccion y freno por friccion del
-- neumatico sobre la rueda de carril; 9C (low ride): el neumatico sobre el carril con guias abatibles.
CREATE TYPE rail_gear_type AS ENUM ('RAIL_WHEEL_DRIVE', 'FRICTION_DRIVE', 'ROAD_WHEEL_DRIVE');

CREATE TYPE driving_licence AS ENUM ('B', 'C', 'C_E');

CREATE TYPE vehicle_capability AS ENUM (
    'CRANE', 'ELEVATING_PLATFORM', 'INSULATED_PLATFORM', 'ROTATING_PLATFORM', 'PANTOGRAPH', 'CONTACT_WIRE_HOLDER',
    'WIRE_DRUM', 'OCS_MEASURING', 'TRACK_MEASURING', 'EARTHING', 'TOWING', 'CREW_CABIN', 'WORKSHOP'
);

CREATE TYPE vehicle_owner AS ENUM ('OWN', 'LEASED', 'SUBCONTRACTOR');

CREATE TYPE vehicle_operational_status AS ENUM ('OPERATIONAL', 'LIMITED', 'UNDER_REPAIR', 'OUT_OF_SERVICE');

CREATE TYPE parking_site_kind AS ENUM ('SIDING', 'STATION_TRACK', 'DEPOT', 'WORKSHOP', 'ROAD_PARKING', 'ACCESS_AREA');

CREATE TYPE vehicle_location_kind AS ENUM ('PARKED', 'ON_TRACK', 'ON_ROAD', 'IN_WORKSHOP', 'ATTACHED', 'UNKNOWN');

CREATE TYPE vehicle_attachment AS ENUM ('TOWED', 'CARRIED');

CREATE TYPE vehicle_location_source AS ENUM ('MANUAL', 'SHIFT', 'FIELD', 'TELEMATICS');

CREATE TABLE vehicle_type (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code varchar(32) NOT NULL,
    name varchar(150) NOT NULL,
    manufacturer varchar(100),
    model varchar(100),
    mobility vehicle_mobility NOT NULL,
    category vehicle_category NOT NULL,
    self_propelled boolean NOT NULL DEFAULT true,
    portable boolean NOT NULL DEFAULT false,
    length_m numeric(6, 2),
    width_m numeric(5, 2),
    height_m numeric(5, 2),
    tare_t numeric(8, 2),
    max_gross_t numeric(8, 2),
    max_axle_load_t numeric(6, 2),
    axles integer,
    seats integer,
    payload_t numeric(8, 2),
    power vehicle_power,
    -- En via.
    track_gauge_mm integer,
    max_speed_track_working_kmh integer,
    max_speed_track_transport_kmh integer,
    max_gradient_permille numeric(5, 1),
    min_curve_radius_m integer,
    max_working_cant_mm integer,
    transport_mode_authorised boolean NOT NULL DEFAULT false,
    -- Bivial.
    rail_gear_type rail_gear_type,
    on_tracking_minutes integer,
    needs_prepared_access boolean,
    -- Carretera.
    max_speed_road_kmh integer,
    required_licence driving_licence,
    -- Las cifras de sus capacidades.
    crane_capacity_t numeric(6, 2),
    crane_reach_m numeric(5, 2),
    platform_height_m numeric(5, 2),
    platform_load_kg integer,
    platform_insulation_kv numeric(5, 1),
    towing_capacity_t numeric(8, 2),
    notes text,
    active boolean NOT NULL DEFAULT true,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(100) NOT NULL DEFAULT 'system',
    updated_by varchar(100) NOT NULL DEFAULT 'system',
    CONSTRAINT uq_vehicle_type_code UNIQUE (code),
    CONSTRAINT chk_vehicle_type_rail_gear CHECK (mobility = 'ROAD_RAIL' OR (rail_gear_type IS NULL AND on_tracking_minutes IS NULL AND needs_prepared_access IS NULL)),
    CONSTRAINT chk_vehicle_type_road_only CHECK (mobility <> 'ROAD_ONLY' OR (track_gauge_mm IS NULL AND NOT transport_mode_authorised)),
    CONSTRAINT chk_vehicle_type_rail_only CHECK (mobility <> 'RAIL_ONLY' OR (max_speed_road_kmh IS NULL AND required_licence IS NULL)),
    CONSTRAINT chk_vehicle_type_seats CHECK (seats IS NULL OR seats >= 0),
    CONSTRAINT chk_vehicle_type_axles CHECK (axles IS NULL OR axles > 0)
);

CREATE TABLE vehicle_type_capability (
    type_id uuid NOT NULL,
    capability vehicle_capability NOT NULL,
    CONSTRAINT pk_vehicle_type_capability PRIMARY KEY (type_id, capability),
    CONSTRAINT fk_vehicle_type_capability_type FOREIGN KEY (type_id) REFERENCES vehicle_type (id) ON DELETE CASCADE
);

CREATE TABLE parking_site (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code varchar(32) NOT NULL,
    name varchar(150) NOT NULL,
    kind parking_site_kind NOT NULL,
    -- Ids de mto-configuration, sin clave ajena.
    execution_package_id bigint,
    station_id bigint,
    track_id bigint,
    kp numeric(12, 3),
    rail_access boolean NOT NULL,
    road_access boolean NOT NULL,
    address varchar(255),
    latitude numeric(9, 6),
    longitude numeric(9, 6),
    notes text,
    active boolean NOT NULL DEFAULT true,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(100) NOT NULL DEFAULT 'system',
    updated_by varchar(100) NOT NULL DEFAULT 'system',
    CONSTRAINT uq_parking_site_code UNIQUE (code),
    CONSTRAINT chk_parking_site_access CHECK (rail_access OR road_access),
    -- Un sitio con via la nombra: es donde se estaciona un vehiculo de solo via.
    CONSTRAINT chk_parking_site_rail_track CHECK (NOT rail_access OR track_id IS NOT NULL),
    CONSTRAINT chk_parking_site_coordinates CHECK ((latitude IS NULL) = (longitude IS NULL)),
    CONSTRAINT chk_parking_site_latitude CHECK (latitude IS NULL OR latitude BETWEEN -90 AND 90),
    CONSTRAINT chk_parking_site_longitude CHECK (longitude IS NULL OR longitude BETWEEN -180 AND 180)
);

CREATE TABLE vehicle (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    -- El numero de flota (DR-02, BIV-07).
    code varchar(32) NOT NULL,
    name varchar(150),
    type_id uuid NOT NULL,
    registration_plate varchar(20),
    -- El numero de vehiculo ferroviario con el que esta autorizado a circular como tren.
    rail_vehicle_number varchar(30),
    serial_number varchar(60),
    manufacture_year integer,
    owner vehicle_owner NOT NULL DEFAULT 'OWN',
    owner_company varchar(150),
    operational_status vehicle_operational_status NOT NULL DEFAULT 'OPERATIONAL',
    status_reason varchar(500),
    odometer_km integer,
    engine_hours numeric(10, 1),
    notes text,
    active boolean NOT NULL DEFAULT true,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(100) NOT NULL DEFAULT 'system',
    updated_by varchar(100) NOT NULL DEFAULT 'system',
    CONSTRAINT uq_vehicle_code UNIQUE (code),
    CONSTRAINT fk_vehicle_type FOREIGN KEY (type_id) REFERENCES vehicle_type (id) ON DELETE RESTRICT,
    CONSTRAINT chk_vehicle_owner_company CHECK (owner = 'OWN' OR owner_company IS NOT NULL),
    CONSTRAINT chk_vehicle_year CHECK (manufacture_year IS NULL OR manufacture_year BETWEEN 1900 AND 2200),
    CONSTRAINT chk_vehicle_counters CHECK ((odometer_km IS NULL OR odometer_km >= 0) AND (engine_hours IS NULL OR engine_hours >= 0))
);

CREATE UNIQUE INDEX uq_vehicle_registration_plate ON vehicle (upper(registration_plate)) WHERE registration_plate IS NOT NULL;
CREATE UNIQUE INDEX uq_vehicle_rail_number ON vehicle (upper(rail_vehicle_number)) WHERE rail_vehicle_number IS NOT NULL;
CREATE INDEX idx_vehicle_type ON vehicle (type_id);

ALTER TABLE organization_unit
    ADD CONSTRAINT fk_organization_unit_default_vehicle FOREIGN KEY (default_vehicle_id) REFERENCES vehicle (id) ON DELETE RESTRICT;

CREATE TABLE vehicle_location_event (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    vehicle_id uuid NOT NULL,
    kind vehicle_location_kind NOT NULL,
    parking_site_id uuid,
    execution_package_id bigint,
    track_id bigint,
    kp numeric(12, 3),
    latitude numeric(9, 6),
    longitude numeric(9, 6),
    -- Donde encarrilo o desencarrilo una bivial; texto hasta que mto-configuration publique los puntos
    -- de acceso a via.
    access_point varchar(150),
    description varchar(500),
    carrier_vehicle_id uuid,
    attachment vehicle_attachment,
    source vehicle_location_source NOT NULL,
    -- Que la provoco: mto-maintenance:shift:<id> en las que salen de un turno.
    source_reference varchar(150),
    observed_at timestamptz NOT NULL,
    recorded_at timestamptz NOT NULL DEFAULT now(),
    recorded_by varchar(100) NOT NULL,
    CONSTRAINT fk_vehicle_location_event_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicle (id) ON DELETE RESTRICT,
    CONSTRAINT fk_vehicle_location_event_site FOREIGN KEY (parking_site_id) REFERENCES parking_site (id) ON DELETE RESTRICT,
    CONSTRAINT fk_vehicle_location_event_carrier FOREIGN KEY (carrier_vehicle_id) REFERENCES vehicle (id) ON DELETE RESTRICT,
    CONSTRAINT chk_vehicle_location_on_track CHECK (kind <> 'ON_TRACK' OR track_id IS NOT NULL),
    CONSTRAINT chk_vehicle_location_parked CHECK (kind <> 'PARKED'
        OR parking_site_id IS NOT NULL OR track_id IS NOT NULL OR description IS NOT NULL OR latitude IS NOT NULL),
    CONSTRAINT chk_vehicle_location_attached CHECK ((kind = 'ATTACHED') = (carrier_vehicle_id IS NOT NULL AND attachment IS NOT NULL)),
    CONSTRAINT chk_vehicle_location_attached_not_self CHECK (carrier_vehicle_id IS NULL OR carrier_vehicle_id <> vehicle_id),
    CONSTRAINT chk_vehicle_location_coordinates CHECK ((latitude IS NULL) = (longitude IS NULL))
);

CREATE INDEX idx_vehicle_location_event_vehicle ON vehicle_location_event (vehicle_id, observed_at DESC);
CREATE INDEX idx_vehicle_location_event_source_reference ON vehicle_location_event (source_reference)
    WHERE source_reference IS NOT NULL;

-- Donde esta cada vehiculo ahora: el ultimo evento observado, una fila por vehiculo.
CREATE TABLE vehicle_position (
    vehicle_id uuid PRIMARY KEY,
    location_event_id uuid NOT NULL,
    kind vehicle_location_kind NOT NULL,
    parking_site_id uuid,
    execution_package_id bigint,
    track_id bigint,
    kp numeric(12, 3),
    carrier_vehicle_id uuid,
    source vehicle_location_source NOT NULL,
    source_reference varchar(150),
    observed_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT fk_vehicle_position_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicle (id) ON DELETE CASCADE,
    CONSTRAINT fk_vehicle_position_event FOREIGN KEY (location_event_id) REFERENCES vehicle_location_event (id) ON DELETE RESTRICT
);

CREATE INDEX idx_vehicle_position_ep ON vehicle_position (execution_package_id);
CREATE INDEX idx_vehicle_position_track ON vehicle_position (track_id);
CREATE INDEX idx_vehicle_position_carrier ON vehicle_position (carrier_vehicle_id) WHERE carrier_vehicle_id IS NOT NULL;

CREATE TABLE vehicle_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    code varchar(32),
    name varchar(150),
    type_id uuid,
    registration_plate varchar(20),
    rail_vehicle_number varchar(30),
    serial_number varchar(60),
    manufacture_year integer,
    owner vehicle_owner,
    owner_company varchar(150),
    operational_status vehicle_operational_status,
    status_reason varchar(500),
    odometer_km integer,
    engine_hours numeric(10, 1),
    notes text,
    active boolean,
    CONSTRAINT pk_vehicle_aud PRIMARY KEY (rev, id),
    CONSTRAINT fk_vehicle_aud_revision FOREIGN KEY (rev) REFERENCES audit_revision (id)
);
