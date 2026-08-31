-- =============================================================================
-- V8 — Lo que le falta al registro de venta desde la SPA.
--
-- 1. `responsable.es_titular`: distingue al DUENIO de la caseta de su acompanante.
--    Hasta ahora los dos responsables de una entidad eran indistinguibles: el flujo viejo
--    creaba "responsable 1" y "responsable 2" y el orden era la unica pista, que se pierde
--    en cuanto alguien reordena o edita. Se necesita explicito para emitir credenciales y
--    para saber a quien se le reclama el pago.
--
-- 2. Se marca como titular al primer responsable de cada entidad existente (el de menor id),
--    que es la convencion que seguia el registro viejo.
--
-- Idempotente.
-- =============================================================================

ALTER TABLE responsable ADD COLUMN IF NOT EXISTS es_titular boolean NOT NULL DEFAULT false;

-- El primero de cada entidad pasa a ser el titular, salvo que ya haya uno marcado.
WITH primeros AS (
    SELECT DISTINCT ON (r.id_entidad) r.id
      FROM responsable r
     WHERE r."_estado" IS NULL OR r."_estado" <> 'X'
     ORDER BY r.id_entidad, r.id
)
UPDATE responsable r
   SET es_titular = true
  FROM primeros p
 WHERE r.id = p.id
   AND NOT EXISTS (
       SELECT 1 FROM responsable r2
        WHERE r2.id_entidad = r.id_entidad AND r2.es_titular
   );

-- Las inscripciones sin edicion se asignan a la edicion activa. La columna id_edicion
-- existia pero el registro viejo nunca la rellenaba, asi que las ventas nacian huerfanas
-- y no aparecian en los listados filtrados por edicion.
UPDATE inscripcion
   SET id_edicion = (SELECT id FROM edicion WHERE activa LIMIT 1)
 WHERE id_edicion IS NULL
   AND EXISTS (SELECT 1 FROM edicion WHERE activa);

-- Comprobacion: ninguna entidad con responsables debe quedarse sin titular.
DO $$
DECLARE
    n bigint;
BEGIN
    SELECT count(*) INTO n
      FROM (SELECT r.id_entidad
              FROM responsable r
             WHERE (r."_estado" IS NULL OR r."_estado" <> 'X')
             GROUP BY r.id_entidad
            HAVING bool_or(r.es_titular) = false) x;
    IF n > 0 THEN
        RAISE EXCEPTION 'Quedan % entidades sin responsable titular', n;
    END IF;
END $$;
