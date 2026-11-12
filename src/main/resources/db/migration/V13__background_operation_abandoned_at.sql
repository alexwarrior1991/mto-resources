-- Una operacion de fondo dada por abandonada (su latido se paro) puede seguir trabajando: su replica no
-- murio, solo se retraso el latido. Si su trabajo termina bien, el resultado manda (la operacion pasa a
-- SUCCEEDED, porque decir FAILED haria que el cliente lo repitiera), y esta columna deja escrito que
-- hubo un abandono: mientras tanto otra del mismo ambito pudo arrancar. Nula si nunca se abandono.

ALTER TABLE background_operation ADD COLUMN abandoned_at timestamptz;
