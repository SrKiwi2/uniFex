-- V5: tamaño de dibujo de las casetas en el plano (Fase 3, modo edicion).
--
-- Asume aplicado V2 (mapa_x, mapa_y en puesto). Idempotente: se puede reejecutar.
--
-- Por que DOS columnas y no una:
--   categoria.tamano_mapa -> tamaño base de todas las casetas de la categoria, expresado como
--                            FRACCION DEL ANCHO DEL PLANO (0..1), igual que mapa_x/mapa_y.
--                            Asi redimensionar una franja de 128 casetas es una sola escritura,
--                            y el dibujo escala solo al hacer zoom.
--   puesto.mapa_escala    -> multiplicador sobre esa base para una caseta concreta (1 = igual).
--                            Sirve para la esquina que mide distinto sin partir la categoria.
--
-- El tamaño dibujado es entonces  tamano_mapa * mapa_escala.
--
-- Ojo: puesto.tamano guarda "3x3" / "6X6" — es un dato de negocio (metros), no sirve para pintar.
-- 0.012 (1.2% del ancho) equivale a ~22 px sobre el plano de 1836 px, parecido al pin actual.

ALTER TABLE categoria ADD COLUMN IF NOT EXISTS tamano_mapa double precision;
ALTER TABLE puesto    ADD COLUMN IF NOT EXISTS mapa_escala double precision;

UPDATE categoria SET tamano_mapa = 0.012 WHERE tamano_mapa IS NULL;
UPDATE puesto    SET mapa_escala = 1     WHERE mapa_escala IS NULL;

ALTER TABLE categoria ALTER COLUMN tamano_mapa SET DEFAULT 0.012;
ALTER TABLE puesto    ALTER COLUMN mapa_escala SET DEFAULT 1;
