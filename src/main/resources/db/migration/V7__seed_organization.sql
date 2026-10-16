-- La raiz del organigrama: el proyecto y su departamento de mantenimiento, con los jefes vacantes
-- (no se siembran personas). Los equipos no se siembran: no pueden existir sin su jefe y sus linemen.
-- Cuando se den de alta aqui con los codigos A y B, mto-maintenance adopta los suyos por el codigo.

INSERT INTO organization_unit (id, code, name, kind, discipline, parent_id) VALUES
    ('0195b000-0000-7000-8000-000000000001', 'MTO', 'MTO electrification project', 'PROJECT', NULL, NULL),
    ('0195b000-0000-7000-8000-000000000002', 'MNT', 'Maintenance', 'DEPARTMENT', 'MAINTENANCE', '0195b000-0000-7000-8000-000000000001');

-- Revision de partida de lo sembrado (source = 'BASELINE', como en mto-stock): sin ella, leer el
-- historial de una unidad que cuelga del departamento fallaria, porque Envers busca a su padre en
-- organization_unit_aud y no lo encuentra. No dice que alguien creara nada: dice "asi estaba esto
-- cuando empezo a guardarse el historial". revtype = 0 es ADD.
INSERT INTO audit_revision (id, timestamp, username, source)
VALUES (nextval('audit_revision_seq'), (EXTRACT(EPOCH FROM now()) * 1000)::bigint, 'system', 'BASELINE');

INSERT INTO organization_unit_aud (id, rev, revtype, code, name, kind, discipline, parent_id, head_person_id, default_vehicle_id, active)
SELECT id, currval('audit_revision_seq'), 0, code, name, kind, discipline, parent_id, head_person_id, default_vehicle_id, active
FROM organization_unit;
