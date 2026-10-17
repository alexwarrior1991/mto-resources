-- Los puntos de acceso a la via de mto-configuration (su V28): por donde un vehiculo bivial sale de la
-- carretera y se encarrila. El maestro es de mto-configuration; aqui solo la instantanea que piden las
-- reglas de la flota al registrar una ubicacion con su punto de acceso, y lo que ofrece el dialogo de
-- ubicacion (GET /track-access-points).
--
-- Solo la escribe TrackAccessPointRepository con SQL nativo, desde el evento track-access-point de
-- mto.master-data.exchange (cola mto.resources.master-data.queue): un upsert con la marca de agua
-- (source_sequence_number, la cabecera sequenceNumber del outbox de mto-configuration, global) dentro
-- del WHERE, nunca leer y luego escribir. Un mensaje sin secuencia se aplica y conserva la guardada; un
-- DELETED (borrado logico alli) la desactiva y tambien avanza la marca. No se audita: Envers no ve el SQL
-- nativo, y su historia es la de mto-configuration.
--
--   - id es el de mto-configuration, no uno propio: es lo que viaja en accessPointId;
--   - kp en metros con tres decimales, como lo publica mto-configuration (110+176 son 110176.000);
--   - kind (PREPARED_ACCESS, LEVEL_CROSSING, DEPOT) y side (LEFT, RIGHT, BOTH) como texto y no como
--     enumerado de PostgreSQL: son de otro servicio, y un valor nuevo alli no debe tumbar el upsert aqui;
--   - execution_package_id es el de su via (track.executionPackageId del evento), para filtrar por EP.

CREATE TABLE track_access_point (
    id bigint PRIMARY KEY,
    name varchar(200) NOT NULL,
    -- Ids de mto-configuration, sin clave ajena.
    track_id bigint NOT NULL,
    execution_package_id bigint,
    station_id bigint,
    kp numeric(12, 3),
    kind varchar(30) NOT NULL,
    side varchar(10) NOT NULL,
    road_rail_access boolean NOT NULL,
    max_axle_load_t numeric(8, 3),
    max_gross_weight_t numeric(8, 3),
    latitude numeric(9, 6),
    longitude numeric(9, 6),
    enabled boolean NOT NULL,
    source_sequence_number bigint,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT chk_track_access_point_coordinates CHECK ((latitude IS NULL) = (longitude IS NULL))
);

-- Lo que pide el dialogo: los de una via, por kp; y los de un EP.
CREATE INDEX idx_track_access_point_track ON track_access_point (track_id, kp);
CREATE INDEX idx_track_access_point_ep ON track_access_point (execution_package_id);

-- Donde encarrilo o desencarrilo una bivial, del catalogo. El texto access_point se queda: lo que ya se
-- escribio, y lo que se diga de un sitio que no esta en el catalogo. vehicle_location_event no se audita
-- (es la historia), asi que no hay gemela _aud que tocar.
ALTER TABLE vehicle_location_event ADD COLUMN access_point_id bigint;
ALTER TABLE vehicle_location_event
    ADD CONSTRAINT fk_vehicle_location_event_access_point FOREIGN KEY (access_point_id) REFERENCES track_access_point (id) ON DELETE RESTRICT;
CREATE INDEX idx_vehicle_location_event_access_point ON vehicle_location_event (access_point_id) WHERE access_point_id IS NOT NULL;
