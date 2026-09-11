-- =============================================================================
-- V22 — Cupo de venta por vendedor y categoria.
--
-- Hasta ahora, lo que un vendedor podia vender era exactamente lo que tenia
-- asignado: 20 casetas asignadas = 20 ventas posibles. El cupo separa las dos
-- cosas, que es lo que hace falta para repartir la feria entre varios:
--
--   "Te habilito las casetas 1 a 20 de PYMES, pero solo puedes vender 15."
--
-- Sirve para dar margen de eleccion al vendedor (ensena veinte, coloca quince)
-- sin que se lleve mas cuota de la que le toca.
--
-- NULL = sin limite. Es el valor por defecto a proposito: las asignaciones que ya
-- existan siguen comportandose igual que antes de este script, sin sorpresas.
--
-- Es idempotente.
-- =============================================================================

BEGIN;

ALTER TABLE vendedor_categoria ADD COLUMN IF NOT EXISTS cupo INTEGER;

COMMENT ON COLUMN vendedor_categoria.cupo IS
    'Maximo de casetas de esta categoria que el vendedor puede VENDER en la edicion activa. NULL = sin limite. No limita cuantas ve, solo cuantas coloca.';

-- Un cupo negativo no significa nada; cero si (habilitado para mirar, no para vender).
ALTER TABLE vendedor_categoria DROP CONSTRAINT IF EXISTS ck_vendedor_categoria_cupo;
ALTER TABLE vendedor_categoria ADD CONSTRAINT ck_vendedor_categoria_cupo
    CHECK (cupo IS NULL OR cupo >= 0);

COMMIT;
