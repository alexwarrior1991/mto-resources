-- Lo que no se mueve solo no se conduce: un vagon, un remolque bivial (ROAD_RAIL_TRAILER) o uno de
-- carretera lo lleva otro, y mto-maintenance solo busca conductor habilitado para los vehiculos
-- autopropulsados. Desde ahora FleetRules.defaultDriverQualification no pone habilitacion a un modelo
-- que no es autopropulsado; V8 y el alta de antes de este cambio si se la ponian a uno bivial
-- (RR-OPERATOR) o de carretera con permiso (LIC-B, LIC-C, LIC-CE), y aqui se vacia.
--
-- Solo se vacia el valor que habria puesto esa regla: una habilitacion distinta elegida a mano se queda.
-- Una elegida a mano igual a la de la regla no se distingue y tambien se vacia; se puede volver a poner
-- en el modelo. La version sube: la fila ha cambiado. vehicle_type no se audita, y el SQL no publica
-- nada: el siguiente cambio de un vehiculo, o el republicado, lleva el modelo sin ella a mantenimiento,
-- que de un vehiculo que no se mueve solo no la mira. Se puede pasar dos veces: la segunda no cambia nada.
UPDATE vehicle_type
SET required_driver_qualification_type_id = NULL,
    version = version + 1
WHERE NOT self_propelled
  AND required_driver_qualification_type_id IS NOT NULL
  AND required_driver_qualification_type_id = CASE
        WHEN mobility = 'ROAD_RAIL' THEN '0195b000-0000-7000-8000-000000000105'::uuid
        WHEN mobility = 'ROAD_ONLY' AND required_licence = 'B' THEN '0195b000-0000-7000-8000-000000000101'::uuid
        WHEN mobility = 'ROAD_ONLY' AND required_licence = 'C' THEN '0195b000-0000-7000-8000-000000000102'::uuid
        WHEN mobility = 'ROAD_ONLY' AND required_licence = 'C_E' THEN '0195b000-0000-7000-8000-000000000103'::uuid
    END;
