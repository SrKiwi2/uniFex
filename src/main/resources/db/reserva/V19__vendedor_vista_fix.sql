-- =============================================================================
-- V19 — Corrige la vista v_puestos_por_vendedor que creo V18.
--
-- La vista tenia el mismo defecto que la consulta Java que la acompanaba: unia
-- vendedor_puesto con `vp.id_usuario = vc.id_usuario`, es decir, colgando la
-- asignacion individual del vendedor que tuviera la CATEGORIA. Consecuencias:
--
--   * una caseta asignada por numero a un vendedor que NO tiene su categoria
--     no aparecia nunca (y asignar "que numeros" es justo lo que se pedia);
--   * la columna vendedor_id salia de vc, asi que para una caseta asignada
--     individualmente venia NULL: la fila no se podia atribuir a nadie.
--
-- Se reescribe como UNION de las dos vias de asignacion, cada una con su propio
-- id_usuario. Asi una caseta puede aparecer una vez por cada vendedor que la
-- tenga, que es lo que la vista dice ser.
--
-- Nota: hoy la vista NO la consulta el codigo Java (el DAO lleva su propia
-- sentencia). Se arregla igual para que no quede una trampa esperando al
-- siguiente que la use creyendola correcta.
--
-- Es idempotente.
-- =============================================================================

BEGIN;

-- DROP + CREATE y no CREATE OR REPLACE: reemplazar solo vale si la lista de
-- columnas coincide exactamente, y aqui cambia el origen de vendedor_id.
DROP VIEW IF EXISTS v_puestos_por_vendedor;

CREATE VIEW v_puestos_por_vendedor AS
-- Via 1: la caseta pertenece a una categoria asignada al vendedor.
SELECT p.*, vc.id_usuario AS vendedor_id
  FROM puesto p
  INNER JOIN categoria c ON c.id = p.id_categoria AND c._estado <> 'X'
  INNER JOIN vendedor_categoria vc ON vc.id_categoria = c.id
 WHERE p._estado <> 'X'
   AND p.estado_puesto <> 'X'
UNION
-- Via 2: la caseta esta asignada al vendedor por numero, sin importar su categoria.
SELECT p.*, vp.id_usuario AS vendedor_id
  FROM puesto p
  INNER JOIN categoria c ON c.id = p.id_categoria AND c._estado <> 'X'
  INNER JOIN vendedor_puesto vp ON vp.id_puesto = p.id
 WHERE p._estado <> 'X'
   AND p.estado_puesto <> 'X';

COMMENT ON VIEW v_puestos_por_vendedor IS
    'Casetas visibles para cada vendedor: por categoria asignada o por caseta asignada. Una fila por (caseta, vendedor).';

COMMIT;
