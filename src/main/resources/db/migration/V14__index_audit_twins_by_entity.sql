-- El historial de una fila (GET /<recurso>/{id}/revisions) filtra sus gemelas de Envers por id, y su
-- clave primaria es (rev, id): el id no es la primera columna, asi que el recuento leia la tabla
-- entera y la pagina recorria el indice de la clave de punta a punta. Con 300 000 revisiones de
-- personas: el recuento, 15,6 ms -> 0,1 ms; la pagina, 1,5 ms -> 0,4 ms (EXPLAIN ANALYZE). Los mismos
-- indices que mto-stock y mto-maintenance tienen en las suyas.
--
-- Sin CONCURRENTLY: Flyway corre la migracion en una transaccion y estas tablas son pequenas. Sobre
-- una ya grande, crea antes cada indice a mano con CREATE INDEX CONCURRENTLY y el mismo nombre, y
-- esta migracion (IF NOT EXISTS) ya no hara nada.

CREATE INDEX IF NOT EXISTS idx_person_aud_id_rev ON person_aud (id, rev);
CREATE INDEX IF NOT EXISTS idx_organization_unit_aud_id_rev ON organization_unit_aud (id, rev);
CREATE INDEX IF NOT EXISTS idx_team_member_aud_id_rev ON team_member_aud (id, rev);
CREATE INDEX IF NOT EXISTS idx_vehicle_aud_id_rev ON vehicle_aud (id, rev);
CREATE INDEX IF NOT EXISTS idx_person_qualification_aud_id_rev ON person_qualification_aud (id, rev);
CREATE INDEX IF NOT EXISTS idx_vehicle_document_aud_id_rev ON vehicle_document_aud (id, rev);
CREATE INDEX IF NOT EXISTS idx_instrument_aud_id_rev ON instrument_aud (id, rev);
CREATE INDEX IF NOT EXISTS idx_person_absence_aud_id_rev ON person_absence_aud (id, rev);
CREATE INDEX IF NOT EXISTS idx_vehicle_repair_aud_id_rev ON vehicle_repair_aud (id, rev);
