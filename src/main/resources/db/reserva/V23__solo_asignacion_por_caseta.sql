-- =============================================================================
-- V23 — Un solo mecanismo: las casetas que se le seleccionan al vendedor.
--
-- Habia DOS formas de habilitar a un vendedor y se pisaban entre si:
--   * por CATEGORIA (vendedor_categoria): le daba la categoria entera;
--   * por CASETA    (vendedor_puesto):    le daba casetas sueltas.
--
-- El resultado en la practica era desconcertante: a un vendedor con la categoria
-- asignada se le seleccionaban 10 casetas y le seguian saliendo TODAS, porque la
-- categoria pesaba mas que la seleccion. Y el cupo era una tercera forma de decir
-- lo mismo: si se le habilitan 10 casetas, puede vender 10; no hace falta un
-- numero aparte que pueda contradecir a la seleccion.
--
-- Se queda solo la asignacion por caseta. Lo que ve y lo que puede vender es
-- exactamente lo que se le selecciono, ni una mas.
--
-- Las filas de vendedor_categoria NO se convierten en asignaciones de caseta a
-- proposito: convertirlas le daria a cada vendedor la categoria entera, que es
-- justo el comportamiento que se esta quitando. Tras este script hay que
-- seleccionarle las casetas a cada vendedor, y hasta entonces no ve ninguna
-- (la pantalla se lo explica).
--
-- Es idempotente.
-- =============================================================================

BEGIN;

-- La vista combinaba las dos vias; ahora solo hay una.
DROP VIEW IF EXISTS v_puestos_por_vendedor;

CREATE VIEW v_puestos_por_vendedor AS
SELECT p.*, vp.id_usuario AS vendedor_id
  FROM puesto p
  INNER JOIN categoria c ON c.id = p.id_categoria AND c._estado <> 'X'
  INNER JOIN vendedor_puesto vp ON vp.id_puesto = p.id
 WHERE p._estado <> 'X'
   AND p.estado_puesto <> 'X';

COMMENT ON VIEW v_puestos_por_vendedor IS
    'Casetas habilitadas a cada vendedor. Una caseta es de un solo vendedor (ux_vendedor_puesto_caseta).';

-- El cupo vivia aqui, asi que se va con la tabla.
DROP TABLE IF EXISTS vendedor_categoria;

COMMIT;
