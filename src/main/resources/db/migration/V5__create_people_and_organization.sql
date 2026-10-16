-- Personal y organizacion: quien trabaja y a quien dirige.
--
-- La jerarquia del dominio es operario -> jefe de equipo -> supervisor -> jefe de supervisores ->
-- jefe de mantenimiento (o de construccion) -> jefe de proyecto. La categoria de cada persona dice
-- que es; el organigrama (organization_unit) dice a quien dirige y en que EP. Son dos cosas a
-- proposito, y las dos son de este servicio. Lo que puede hacer cada uno en las aplicaciones lo dicen
-- sus perfiles de Keycloak, que no se guardan aqui.
--
-- Un equipo es una unidad TEAM: su jefe (head_person_id) y de 2 a 6 linemen (team_member). El tope
-- y el minimo los comprueba el servicio con la fila del equipo bloqueada; aqui va lo que la base
-- puede decidir sola: un equipo siempre tiene jefe, una persona dirige como mucho un equipo activo y
-- esta como mucho en un equipo a la vez.

CREATE TYPE person_category AS ENUM (
    'WORKER',
    'TEAM_LEADER',
    'SUPERVISOR',
    'CHIEF_SUPERVISOR',
    'CHIEF_MAINTENANCE',
    'CHIEF_CONSTRUCTION',
    'PROJECT_MANAGER'
);

CREATE TYPE person_employer AS ENUM ('OWN', 'SUBCONTRACTOR');

CREATE TYPE organization_unit_kind AS ENUM ('PROJECT', 'DEPARTMENT', 'CHIEF_SUPERVISION', 'SUPERVISION', 'TEAM');

CREATE TYPE work_discipline AS ENUM ('MAINTENANCE', 'CONSTRUCTION');

-- PER-000001, nunca MAX + 1.
CREATE SEQUENCE person_code_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE person (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code varchar(16) NOT NULL,
    -- El numero de empleado de la empresa, si lo tiene; un subcontratado puede no tenerlo.
    employee_number varchar(40),
    first_name varchar(100) NOT NULL,
    last_name varchar(150) NOT NULL,
    -- El preferred_username de Keycloak de quien entra en las aplicaciones. Un operario sin login no
    -- lo tiene: la ficha existe igual.
    username varchar(100),
    keycloak_user_id varchar(100),
    category person_category NOT NULL,
    employer person_employer NOT NULL DEFAULT 'OWN',
    company varchar(150),
    -- El telefono de contacto en el tajo. Es el unico dato personal ademas del nombre: ni direccion ni
    -- datos de salud.
    phone varchar(40),
    active boolean NOT NULL DEFAULT true,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(100) NOT NULL DEFAULT 'system',
    updated_by varchar(100) NOT NULL DEFAULT 'system',
    CONSTRAINT uq_person_code UNIQUE (code),
    CONSTRAINT chk_person_subcontractor_company CHECK (employer = 'OWN' OR company IS NOT NULL)
);

CREATE UNIQUE INDEX uq_person_employee_number ON person (employee_number) WHERE employee_number IS NOT NULL;
CREATE UNIQUE INDEX uq_person_username ON person (lower(username)) WHERE username IS NOT NULL;
CREATE UNIQUE INDEX uq_person_keycloak_user_id ON person (keycloak_user_id) WHERE keycloak_user_id IS NOT NULL;
CREATE INDEX idx_person_category ON person (category);

CREATE TABLE organization_unit (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    -- En mayusculas y de 16 como mucho: el de un equipo es el mismo que el de maintenance_team, que
    -- lo adopta por el.
    code varchar(16) NOT NULL,
    name varchar(120) NOT NULL,
    kind organization_unit_kind NOT NULL,
    discipline work_discipline,
    parent_id uuid,
    head_person_id uuid,
    -- Solo en un equipo: el vehiculo con el que suele trabajar. La clave ajena la anade V6, con la
    -- tabla vehicle.
    default_vehicle_id uuid,
    active boolean NOT NULL DEFAULT true,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(100) NOT NULL DEFAULT 'system',
    updated_by varchar(100) NOT NULL DEFAULT 'system',
    CONSTRAINT uq_organization_unit_code UNIQUE (code),
    CONSTRAINT fk_organization_unit_parent FOREIGN KEY (parent_id) REFERENCES organization_unit (id) ON DELETE RESTRICT,
    CONSTRAINT fk_organization_unit_head FOREIGN KEY (head_person_id) REFERENCES person (id) ON DELETE RESTRICT,
    CONSTRAINT chk_organization_unit_code_upper CHECK (code = upper(code)),
    -- Un proyecto es la raiz y no tiene disciplina; todo lo demas cuelga de algo y es de una.
    CONSTRAINT chk_organization_unit_root CHECK ((kind = 'PROJECT') = (parent_id IS NULL)),
    CONSTRAINT chk_organization_unit_discipline CHECK ((kind = 'PROJECT') = (discipline IS NULL)),
    -- Un equipo tiene siempre su jefe.
    CONSTRAINT chk_organization_unit_team_head CHECK (kind <> 'TEAM' OR head_person_id IS NOT NULL),
    CONSTRAINT chk_organization_unit_default_vehicle CHECK (kind = 'TEAM' OR default_vehicle_id IS NULL)
);

-- Una persona dirige como mucho un equipo activo.
CREATE UNIQUE INDEX uq_organization_unit_active_team_head ON organization_unit (head_person_id)
    WHERE kind = 'TEAM' AND active;
CREATE INDEX idx_organization_unit_parent ON organization_unit (parent_id);
CREATE INDEX idx_organization_unit_head ON organization_unit (head_person_id);

-- Los EP (ids de mto-configuration) de una supervision o de un equipo.
CREATE TABLE organization_unit_execution_package (
    unit_id uuid NOT NULL,
    execution_package_id bigint NOT NULL,
    CONSTRAINT pk_organization_unit_execution_package PRIMARY KEY (unit_id, execution_package_id),
    CONSTRAINT fk_organization_unit_execution_package_unit FOREIGN KEY (unit_id) REFERENCES organization_unit (id) ON DELETE CASCADE
);

CREATE INDEX idx_organization_unit_execution_package_ep ON organization_unit_execution_package (execution_package_id);

-- Los linemen de un equipo, con su fecha de entrada y de salida. El jefe no se repite aqui.
CREATE TABLE team_member (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    team_id uuid NOT NULL,
    person_id uuid NOT NULL,
    joined_on date NOT NULL,
    left_on date,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(100) NOT NULL DEFAULT 'system',
    updated_by varchar(100) NOT NULL DEFAULT 'system',
    CONSTRAINT fk_team_member_team FOREIGN KEY (team_id) REFERENCES organization_unit (id) ON DELETE RESTRICT,
    CONSTRAINT fk_team_member_person FOREIGN KEY (person_id) REFERENCES person (id) ON DELETE RESTRICT,
    CONSTRAINT chk_team_member_dates CHECK (left_on IS NULL OR left_on >= joined_on)
);

-- Una persona esta como mucho en un equipo a la vez.
CREATE UNIQUE INDEX uq_team_member_open_person ON team_member (person_id) WHERE left_on IS NULL;
CREATE INDEX idx_team_member_team_open ON team_member (team_id) WHERE left_on IS NULL;

-- Gemelas de Envers: nullable y sin restricciones, salvo su clave y la revision.
CREATE TABLE person_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    code varchar(16),
    employee_number varchar(40),
    first_name varchar(100),
    last_name varchar(150),
    username varchar(100),
    keycloak_user_id varchar(100),
    category person_category,
    employer person_employer,
    company varchar(150),
    phone varchar(40),
    active boolean,
    CONSTRAINT pk_person_aud PRIMARY KEY (rev, id),
    CONSTRAINT fk_person_aud_revision FOREIGN KEY (rev) REFERENCES audit_revision (id)
);

CREATE TABLE organization_unit_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    code varchar(16),
    name varchar(120),
    kind organization_unit_kind,
    discipline work_discipline,
    parent_id uuid,
    head_person_id uuid,
    default_vehicle_id uuid,
    active boolean,
    CONSTRAINT pk_organization_unit_aud PRIMARY KEY (rev, id),
    CONSTRAINT fk_organization_unit_aud_revision FOREIGN KEY (rev) REFERENCES audit_revision (id)
);

CREATE TABLE organization_unit_execution_package_aud (
    rev integer NOT NULL,
    unit_id uuid NOT NULL,
    execution_package_id bigint NOT NULL,
    revtype smallint,
    CONSTRAINT pk_organization_unit_execution_package_aud PRIMARY KEY (rev, unit_id, execution_package_id),
    CONSTRAINT fk_organization_unit_execution_package_aud_revision FOREIGN KEY (rev) REFERENCES audit_revision (id)
);

CREATE TABLE team_member_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    team_id uuid,
    person_id uuid,
    joined_on date,
    left_on date,
    CONSTRAINT pk_team_member_aud PRIMARY KEY (rev, id),
    CONSTRAINT fk_team_member_aud_revision FOREIGN KEY (rev) REFERENCES audit_revision (id)
);
