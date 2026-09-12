-- V25: columna que le faltaba a este repo desde antes de esta sesión.
--
-- `Puesto.mapaRotacion` (giro de la caseta en el plano, 0..359°) ya vivía en la entidad
-- Java con un comentario que decía "(V24)", pero ese script nunca se llegó a escribir —
-- V24 quedó libre y se usó para otra cosa (interesado_stand) antes de notar el hueco.
-- Sin esta columna, cualquier consulta que traiga un Puesto entero (fn_lista_puestos vía
-- JPA, /api/publico/feria, etc.) fallaba con "no existe la columna p1_0.mapa_rotacion".
--
-- NULL y 0 significan lo mismo (ver el comentario de la entidad), así que no hace falta
-- backfill para las filas existentes.
--
-- Es idempotente.

ALTER TABLE public.puesto ADD COLUMN IF NOT EXISTS mapa_rotacion integer;
