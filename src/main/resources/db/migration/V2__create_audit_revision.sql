-- Historial de cambios (Hibernate Envers).
--
-- Envers escribe una fila en audit_revision por TRANSACCION que toca una entidad auditada, y una
-- fila en <tabla>_aud por entidad cambiada dentro de ella. El "quien y cuando" esta en
-- audit_revision; las gemelas _aud guardan solo el estado de negocio en esa revision, y por eso no
-- repiten created_by / updated_by / created_at / updated_at.
--
-- Se auditan: person, organization_unit (+ organization_unit_execution_package), team_member y
-- vehicle. NO se auditan:
--   * vehicle_location_event, que ya es un historico append-only, y vehicle_position, que se escribe
--     con SQL nativo (Envers no lo veria).
--   * inbox_message, outbox_message y background_operation: su historia es ellas mismas.
--   * vehicle_type y parking_site: catalogos de cambio raro.
-- Cada tabla auditada crea su gemela _aud en la migracion que crea la tabla.
--
-- REGLA DE MANTENIMIENTO: toda migracion futura que anada, quite, renombre o cambie de tipo una
-- columna de una de las tablas base auditadas tiene que aplicar el mismo cambio a su gemela _aud en
-- esa misma migracion, con dos diferencias: en la gemela la columna es siempre nullable y no lleva
-- CHECK, UNIQUE ni clave ajena. Hibernate construye el mapeo de Envers desde la entidad, asi que
-- una columna anadida sin su gemela hace fallar a ddl-auto: validate y la aplicacion no arranca.

-- INCREMENT BY 1 tiene que coincidir con allocationSize = 1 en AuditRevision. No hay revision de
-- partida: las tablas nacen vacias con el historial ya instalado.
CREATE SEQUENCE audit_revision_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE audit_revision (
    id integer PRIMARY KEY,
    -- Milisegundos desde epoch, no timestamptz: es el tipo que @RevisionTimestamp admite en
    -- cualquier version de Envers. AuditRevision.getRevisionInstant() lo devuelve como Instant.
    timestamp bigint NOT NULL,
    username varchar(100),
    user_id varchar(100),
    -- HTTP | MESSAGING | SYSTEM | BASELINE. Dice de que canal vino la escritura y, por tanto, a que
    -- espacio de identificadores pertenece correlation_id.
    source varchar(20),
    correlation_id varchar(200),
    ip_address varchar(100),
    user_agent varchar(500),
    request_method varchar(20),
    request_uri varchar(500)
);

CREATE INDEX idx_audit_revision_timestamp ON audit_revision (timestamp);
CREATE INDEX idx_audit_revision_username ON audit_revision (username);
CREATE INDEX idx_audit_revision_correlation_id ON audit_revision (correlation_id)
    WHERE correlation_id IS NOT NULL;
