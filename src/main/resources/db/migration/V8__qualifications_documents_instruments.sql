-- Cualificaciones y caducidades: lo que una persona, un vehiculo o un instrumento necesita tener en
-- vigor para entrar en un turno. Aqui solo se guardan las fechas (ni diagnosticos ni copias de nada):
-- quien decide si algo caducado entra en un turno es mto-maintenance, con lo que le cuentan los
-- eventos de este servicio.
--
--   * qualification_type: el catalogo de cualificaciones (permisos de conducir, habilitaciones para
--     conducir un vehiculo auxiliar o una bivial, seguridad...). No se audita: catalogo de cambio raro.
--   * person_qualification: las de cada persona, una por tipo; renovar es cambiar expires_on.
--   * vehicle_document: la ITV, la inspeccion ferroviaria, el seguro, los certificados de la grua y de
--     la plataforma de cada vehiculo, uno por tipo.
--   * vehicle_type.required_driver_qualification_type_id: la habilitacion que exige conducir un modelo.
--   * instrument: las pertigas de puesta a tierra, los detectores de tension y los equipos de medida,
--     con su calibracion.
--
-- Las tres con fecha de caducidad llevan dos marcas, expiring_notified_for y expired_notified_for: la
-- fecha de caducidad de la que ya se aviso. Las escribe solo ExpiryWatchService con SQL nativo y una
-- condicion dentro de la sentencia (is distinct from), de modo que se avisa una vez por fecha aunque
-- corran varias instancias, y otra vez cuando se renueva. No se auditan ni las escribe Hibernate.

CREATE TYPE qualification_kind AS ENUM ('DRIVING_LICENCE', 'VEHICLE_OPERATION', 'SAFETY', 'OTHER');

CREATE TYPE vehicle_document_kind AS ENUM (
    'ROAD_INSPECTION', 'RAIL_INSPECTION', 'INSURANCE', 'CRANE_CERTIFICATE', 'PLATFORM_CERTIFICATE', 'OTHER'
);

CREATE TYPE instrument_kind AS ENUM ('EARTHING_POLE', 'VOLTAGE_DETECTOR', 'MEASURING', 'OTHER');

CREATE TABLE qualification_type (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    -- En mayusculas: es lo que viaja en los eventos y lo que mto-maintenance compara.
    code varchar(32) NOT NULL,
    name varchar(120) NOT NULL,
    kind qualification_kind NOT NULL,
    -- Solo en un permiso de conducir: cual.
    driving_licence driving_licence,
    -- La vigencia por defecto: la caducidad de una nueva que no la diga es su expedicion mas estos meses.
    validity_months integer,
    description varchar(500),
    active boolean NOT NULL DEFAULT true,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(100) NOT NULL DEFAULT 'system',
    updated_by varchar(100) NOT NULL DEFAULT 'system',
    CONSTRAINT uq_qualification_type_code UNIQUE (code),
    CONSTRAINT chk_qualification_type_code_upper CHECK (code = upper(code)),
    CONSTRAINT chk_qualification_type_driving_licence CHECK ((kind = 'DRIVING_LICENCE') = (driving_licence IS NOT NULL)),
    CONSTRAINT chk_qualification_type_validity CHECK (validity_months IS NULL OR validity_months > 0)
);

-- Las que exigen los modelos de la flota (FleetRules.defaultDriverQualification). Ids fijos: los
-- nombran el relleno de abajo y, por su codigo, el servicio al dar de alta un modelo.
INSERT INTO qualification_type (id, code, name, kind, driving_licence) VALUES
    ('0195b000-0000-7000-8000-000000000101', 'LIC-B', 'Driving licence B', 'DRIVING_LICENCE', 'B'),
    ('0195b000-0000-7000-8000-000000000102', 'LIC-C', 'Driving licence C', 'DRIVING_LICENCE', 'C'),
    ('0195b000-0000-7000-8000-000000000103', 'LIC-CE', 'Driving licence C+E', 'DRIVING_LICENCE', 'C_E'),
    ('0195b000-0000-7000-8000-000000000104', 'RAIL-DRIVER', 'Rail auxiliary vehicle driver', 'VEHICLE_OPERATION', NULL),
    ('0195b000-0000-7000-8000-000000000105', 'RR-OPERATOR', 'Road-rail machine operator', 'VEHICLE_OPERATION', NULL);

CREATE TABLE person_qualification (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    person_id uuid NOT NULL,
    qualification_type_id uuid NOT NULL,
    -- El numero del permiso o del certificado.
    reference varchar(60),
    issued_on date,
    -- Nula: no caduca.
    expires_on date,
    notes varchar(500),
    expiring_notified_for date,
    expired_notified_for date,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(100) NOT NULL DEFAULT 'system',
    updated_by varchar(100) NOT NULL DEFAULT 'system',
    CONSTRAINT fk_person_qualification_person FOREIGN KEY (person_id) REFERENCES person (id) ON DELETE RESTRICT,
    CONSTRAINT fk_person_qualification_type FOREIGN KEY (qualification_type_id) REFERENCES qualification_type (id) ON DELETE RESTRICT,
    -- Una por tipo: renovar es cambiar la caducidad.
    CONSTRAINT uq_person_qualification UNIQUE (person_id, qualification_type_id),
    CONSTRAINT chk_person_qualification_dates CHECK (expires_on IS NULL OR issued_on IS NULL OR expires_on >= issued_on)
);

CREATE INDEX idx_person_qualification_type ON person_qualification (qualification_type_id);
CREATE INDEX idx_person_qualification_expires_on ON person_qualification (expires_on) WHERE expires_on IS NOT NULL;

CREATE TABLE vehicle_document (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    vehicle_id uuid NOT NULL,
    kind vehicle_document_kind NOT NULL,
    reference varchar(60),
    issued_on date,
    -- Un documento siempre caduca: es lo que se le pide.
    expires_on date NOT NULL,
    notes varchar(500),
    expiring_notified_for date,
    expired_notified_for date,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(100) NOT NULL DEFAULT 'system',
    updated_by varchar(100) NOT NULL DEFAULT 'system',
    CONSTRAINT fk_vehicle_document_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicle (id) ON DELETE RESTRICT,
    -- Uno por tipo: renovar es cambiar la caducidad.
    CONSTRAINT uq_vehicle_document UNIQUE (vehicle_id, kind),
    CONSTRAINT chk_vehicle_document_dates CHECK (issued_on IS NULL OR expires_on >= issued_on)
);

CREATE INDEX idx_vehicle_document_expires_on ON vehicle_document (expires_on);

-- La habilitacion que exige conducir un modelo (nula: no exige ninguna). vehicle_type no se audita, asi
-- que no hay gemela que cambiar.
ALTER TABLE vehicle_type ADD COLUMN required_driver_qualification_type_id uuid;
ALTER TABLE vehicle_type
    ADD CONSTRAINT fk_vehicle_type_required_driver_qualification FOREIGN KEY (required_driver_qualification_type_id)
        REFERENCES qualification_type (id) ON DELETE RESTRICT;

-- Los modelos que ya existen, con la misma regla que aplica el servicio al dar de alta uno que no la
-- dice: una de solo via autopropulsada, conductor de vehiculo auxiliar; una bivial, operador de bivial;
-- una de carretera, el permiso que pide. La version sube: la fila ha cambiado.
UPDATE vehicle_type
SET required_driver_qualification_type_id = CASE
        WHEN mobility = 'RAIL_ONLY' AND self_propelled THEN '0195b000-0000-7000-8000-000000000104'::uuid
        WHEN mobility = 'ROAD_RAIL' THEN '0195b000-0000-7000-8000-000000000105'::uuid
        WHEN mobility = 'ROAD_ONLY' AND required_licence = 'B' THEN '0195b000-0000-7000-8000-000000000101'::uuid
        WHEN mobility = 'ROAD_ONLY' AND required_licence = 'C' THEN '0195b000-0000-7000-8000-000000000102'::uuid
        WHEN mobility = 'ROAD_ONLY' AND required_licence = 'C_E' THEN '0195b000-0000-7000-8000-000000000103'::uuid
    END,
    version = version + 1
WHERE required_driver_qualification_type_id IS NULL
  AND ((mobility = 'RAIL_ONLY' AND self_propelled)
    OR mobility = 'ROAD_RAIL'
    OR (mobility = 'ROAD_ONLY' AND required_licence IS NOT NULL));

CREATE TABLE instrument (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    -- En mayusculas, como el de un vehiculo: PT-012, DT-03.
    code varchar(32) NOT NULL,
    name varchar(120) NOT NULL,
    kind instrument_kind NOT NULL,
    serial_number varchar(60),
    calibrated_on date,
    -- Nula: sin calibracion.
    calibration_expires_on date,
    notes varchar(500),
    active boolean NOT NULL DEFAULT true,
    expiring_notified_for date,
    expired_notified_for date,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(100) NOT NULL DEFAULT 'system',
    updated_by varchar(100) NOT NULL DEFAULT 'system',
    CONSTRAINT uq_instrument_code UNIQUE (code),
    CONSTRAINT chk_instrument_code_upper CHECK (code = upper(code)),
    CONSTRAINT chk_instrument_calibration_dates CHECK (calibration_expires_on IS NULL OR calibrated_on IS NULL
        OR calibration_expires_on >= calibrated_on)
);

CREATE INDEX idx_instrument_calibration_expires_on ON instrument (calibration_expires_on) WHERE calibration_expires_on IS NOT NULL;

-- Gemelas de Envers: nullable y sin restricciones, salvo su clave y la revision. Las marcas de aviso
-- no estan: no son historia (@NotAudited).
CREATE TABLE person_qualification_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    person_id uuid,
    qualification_type_id uuid,
    reference varchar(60),
    issued_on date,
    expires_on date,
    notes varchar(500),
    CONSTRAINT pk_person_qualification_aud PRIMARY KEY (rev, id),
    CONSTRAINT fk_person_qualification_aud_revision FOREIGN KEY (rev) REFERENCES audit_revision (id)
);

CREATE TABLE vehicle_document_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    vehicle_id uuid,
    kind vehicle_document_kind,
    reference varchar(60),
    issued_on date,
    expires_on date,
    notes varchar(500),
    CONSTRAINT pk_vehicle_document_aud PRIMARY KEY (rev, id),
    CONSTRAINT fk_vehicle_document_aud_revision FOREIGN KEY (rev) REFERENCES audit_revision (id)
);

CREATE TABLE instrument_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    code varchar(32),
    name varchar(120),
    kind instrument_kind,
    serial_number varchar(60),
    calibrated_on date,
    calibration_expires_on date,
    notes varchar(500),
    active boolean,
    CONSTRAINT pk_instrument_aud PRIMARY KEY (rev, id),
    CONSTRAINT fk_instrument_aud_revision FOREIGN KEY (rev) REFERENCES audit_revision (id)
);
