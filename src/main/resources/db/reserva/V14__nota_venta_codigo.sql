-- V14: identificador verificable de la nota de venta.
--
-- El recibo ya traia un "codigo de autenticacion", pero no servia para lo que dice su nombre:
--
--   1. Se calculaba con SHA-256 sobre `id | NIT | fecha de AHORA`. Como la fecha era el
--      instante de la impresion, CADA reimpresion daba un codigo distinto: dos copias de la
--      misma venta no coincidian, asi que el codigo no identificaba nada.
--   2. Los tres ingredientes son datos PUBLICOS (estan impresos en el propio papel), asi que
--      cualquiera podia recalcular el mismo hash y fabricar una nota que "cuadra".
--   3. No se guardaba en ningun lado, asi que no habia contra que verificarlo.
--
-- Ahora el codigo se emite UNA vez, se guarda aqui y se firma con HMAC-SHA256 usando el
-- secreto del servidor: sin ese secreto no se puede fabricar uno valido, y como queda
-- guardado, se puede comprobar si una nota presentada en papel existe de verdad.

ALTER TABLE inscripcion ADD COLUMN IF NOT EXISTS nota_codigo     VARCHAR(40);
ALTER TABLE inscripcion ADD COLUMN IF NOT EXISTS nota_emitida_en TIMESTAMP;

-- Unico: dos ventas no pueden compartir codigo. Parcial porque las ventas viejas (y las que
-- aun no imprimieron nota) lo tienen NULL, y en Postgres varios NULL no chocan entre si de
-- todos modos; el WHERE lo deja explicito y mantiene el indice pequenio.
CREATE UNIQUE INDEX IF NOT EXISTS ux_inscripcion_nota_codigo
    ON inscripcion (nota_codigo) WHERE nota_codigo IS NOT NULL;

COMMENT ON COLUMN inscripcion.nota_codigo     IS 'Codigo verificable de la nota de venta, firmado con HMAC. Se emite una vez y no cambia entre reimpresiones';
COMMENT ON COLUMN inscripcion.nota_emitida_en IS 'Cuando se emitio la nota por primera vez. Es la fecha que se imprime, no la del momento de reimprimir';
