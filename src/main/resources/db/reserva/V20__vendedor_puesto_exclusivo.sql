-- =============================================================================
-- V20 — Una caseta asignada pertenece a UN solo vendedor.
--
-- V18 puso UNIQUE (id_usuario, id_puesto), que solo impide repetir la MISMA pareja:
-- la caseta 7 podia estar asignada a Ana y a Beto a la vez, y los dos la veian en su
-- mapa como suya. Eso es exactamente el escenario que el sistema entero trata de
-- evitar: dos vendedores creyendo que pueden vender lo mismo.
--
-- La regla pasa a ser: si una caseta esta asignada a alguien, es de esa persona, y a
-- los demas no les aparece ni aunque tengan asignada su categoria (la asignacion
-- individual recorta la categoria). La consulta del mapa aplica esa misma regla.
--
-- Antes de crear el indice hay que deshacer los duplicados que ya existan: se conserva
-- la asignacion MAS ANTIGUA de cada caseta, que es la que lleva mas tiempo en pie.
--
-- Es idempotente.
-- =============================================================================

BEGIN;

-- 1) Duplicados: de cada caseta se queda la asignacion mas antigua (id mas bajo a
--    igualdad de fecha) y se borran las demas.
DELETE FROM vendedor_puesto vp
 WHERE EXISTS (
        SELECT 1 FROM vendedor_puesto otro
         WHERE otro.id_puesto = vp.id_puesto
           AND (otro._fecha_registro, otro.id) < (vp._fecha_registro, vp.id));

-- 2) El UNIQUE viejo, por pareja, ya no dice nada que el nuevo no diga.
ALTER TABLE vendedor_puesto DROP CONSTRAINT IF EXISTS vendedor_puesto_id_usuario_id_puesto_key;

-- 3) Una caseta, un vendedor. La garantia vive aqui: dos asignaciones simultaneas de la
--    misma caseta chocan contra el indice en vez de convivir.
CREATE UNIQUE INDEX IF NOT EXISTS ux_vendedor_puesto_caseta ON vendedor_puesto (id_puesto);

-- 4) La vista, con la misma regla: una caseta individualmente asignada se recorta de la
--    categoria, asi que solo la ve su duenio.
DROP VIEW IF EXISTS v_puestos_por_vendedor;

CREATE VIEW v_puestos_por_vendedor AS
-- Via 1: por categoria asignada, salvo las casetas que ya tienen duenio individual.
SELECT p.*, vc.id_usuario AS vendedor_id
  FROM puesto p
  INNER JOIN categoria c ON c.id = p.id_categoria AND c._estado <> 'X'
  INNER JOIN vendedor_categoria vc ON vc.id_categoria = c.id
 WHERE p._estado <> 'X'
   AND p.estado_puesto <> 'X'
   AND NOT EXISTS (SELECT 1 FROM vendedor_puesto vp WHERE vp.id_puesto = p.id)
UNION
-- Via 2: por caseta asignada, sin importar la categoria.
SELECT p.*, vp.id_usuario AS vendedor_id
  FROM puesto p
  INNER JOIN categoria c ON c.id = p.id_categoria AND c._estado <> 'X'
  INNER JOIN vendedor_puesto vp ON vp.id_puesto = p.id
 WHERE p._estado <> 'X'
   AND p.estado_puesto <> 'X';

COMMENT ON VIEW v_puestos_por_vendedor IS
    'Casetas visibles para cada vendedor. Una caseta asignada individualmente es solo de su duenio y se recorta de la categoria.';

COMMIT;
