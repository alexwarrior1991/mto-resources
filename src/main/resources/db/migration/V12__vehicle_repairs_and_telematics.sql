-- El taller de la flota y el GPS de los vehiculos de carretera.
--
--   * vehicle_repair: una averia, una revision, una inspeccion o una modificacion de un vehiculo, con su
--     taller, sus fechas, sus contadores y su coste. PLANNED -> OPEN -> CLOSED, o CANCELLED desde
--     cualquiera de las dos primeras. Una que inmoviliza (immobilises) manda en el estado del vehiculo:
--     abrirla lo pone UNDER_REPAIR y cerrar o cancelar la ultima abierta lo devuelve a OPERATIONAL (si
--     sigue UNDER_REPAIR). Las que inmovilizan, planificadas o abiertas, viajan en el unavailable del
--     evento vehicle.*, que mto-maintenance mira a la fecha de un turno.
--   * vehicle.telematics_device_id: el dispositivo GPS del vehiculo, por el que llegan sus posiciones a
--     POST /telematics/positions. Unico; nulo, sin GPS.

CREATE TYPE repair_kind AS ENUM ('BREAKDOWN', 'SERVICE', 'INSPECTION', 'MODIFICATION', 'OTHER');

CREATE TYPE repair_status AS ENUM ('PLANNED', 'OPEN', 'CLOSED', 'CANCELLED');

CREATE SEQUENCE vehicle_repair_code_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE vehicle_repair (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    -- REP-000001, de la secuencia: nunca MAX + 1.
    code varchar(16) NOT NULL,
    vehicle_id uuid NOT NULL,
    kind repair_kind NOT NULL,
    status repair_status NOT NULL,
    immobilises boolean NOT NULL DEFAULT true,
    description varchar(500) NOT NULL,
    workshop varchar(150),
    planned_from date,
    planned_to date,
    opened_at timestamptz,
    closed_at timestamptz,
    odometer_km integer,
    engine_hours numeric(10, 1),
    cost numeric(12, 2),
    notes text,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(100) NOT NULL DEFAULT 'system',
    updated_by varchar(100) NOT NULL DEFAULT 'system',
    CONSTRAINT uq_vehicle_repair_code UNIQUE (code),
    CONSTRAINT fk_vehicle_repair_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicle (id) ON DELETE RESTRICT,
    CONSTRAINT chk_vehicle_repair_planned CHECK (planned_to IS NULL OR planned_from IS NULL OR planned_to >= planned_from),
    -- Una planificada dice desde cuando; una abierta o cerrada, cuando se abrio; una cerrada, cuando se cerro.
    CONSTRAINT chk_vehicle_repair_status_dates CHECK (
        (status <> 'PLANNED' OR planned_from IS NOT NULL)
        AND (status NOT IN ('OPEN', 'CLOSED') OR opened_at IS NOT NULL)
        AND (status <> 'CLOSED' OR closed_at IS NOT NULL)
        AND (closed_at IS NULL OR opened_at IS NULL OR closed_at >= opened_at)),
    CONSTRAINT chk_vehicle_repair_counters CHECK ((odometer_km IS NULL OR odometer_km >= 0)
        AND (engine_hours IS NULL OR engine_hours >= 0) AND (cost IS NULL OR cost >= 0))
);

CREATE INDEX idx_vehicle_repair_vehicle ON vehicle_repair (vehicle_id, status);

CREATE TABLE vehicle_repair_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    code varchar(16),
    vehicle_id uuid,
    kind repair_kind,
    status repair_status,
    immobilises boolean,
    description varchar(500),
    workshop varchar(150),
    planned_from date,
    planned_to date,
    opened_at timestamptz,
    closed_at timestamptz,
    odometer_km integer,
    engine_hours numeric(10, 1),
    cost numeric(12, 2),
    notes text,
    CONSTRAINT pk_vehicle_repair_aud PRIMARY KEY (rev, id),
    CONSTRAINT fk_vehicle_repair_aud_revision FOREIGN KEY (rev) REFERENCES audit_revision (id)
);

-- vehicle se audita: su gemela gana la columna en la misma migracion.
ALTER TABLE vehicle ADD COLUMN telematics_device_id varchar(64);
ALTER TABLE vehicle ADD CONSTRAINT uq_vehicle_telematics_device UNIQUE (telematics_device_id);
ALTER TABLE vehicle_aud ADD COLUMN telematics_device_id varchar(64);
