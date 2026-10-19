-- Las ausencias de una persona (vacaciones, baja, formacion...): cuando no esta disponible. Es lo que
-- mto-maintenance mira a la fecha de un turno, igual que las caducidades: el evento person.* lleva
-- unavailable [{from, to}] con las que terminan hoy o despues, sin la clase (una baja medica no sale de
-- este servicio: mto-notification copia los values del evento en su registro).
--
-- Las dos fechas van incluidas. Dos ausencias de la misma persona no se solapan (409 ABS-001): lo decide
-- el servicio con la fila de la persona bloqueada, la misma que bloquean sus cualificaciones, porque su
-- evento lleva la lista entera.

CREATE TYPE absence_kind AS ENUM ('VACATION', 'SICK_LEAVE', 'TRAINING', 'PERSONAL', 'OTHER');

CREATE TABLE person_absence (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    person_id uuid NOT NULL,
    kind absence_kind NOT NULL,
    starts_on date NOT NULL,
    ends_on date NOT NULL,
    notes varchar(500),
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(100) NOT NULL DEFAULT 'system',
    updated_by varchar(100) NOT NULL DEFAULT 'system',
    CONSTRAINT fk_person_absence_person FOREIGN KEY (person_id) REFERENCES person (id) ON DELETE RESTRICT,
    CONSTRAINT chk_person_absence_dates CHECK (ends_on >= starts_on)
);

CREATE INDEX idx_person_absence_person ON person_absence (person_id, starts_on);

-- Gemela de Envers, como las de V8.
CREATE TABLE person_absence_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    person_id uuid,
    kind absence_kind,
    starts_on date,
    ends_on date,
    notes varchar(500),
    CONSTRAINT pk_person_absence_aud PRIMARY KEY (rev, id),
    CONSTRAINT fk_person_absence_aud_revision FOREIGN KEY (rev) REFERENCES audit_revision (id)
);
