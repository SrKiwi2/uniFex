-- Fase 3: posicion de cada caseta sobre el mapa (coordenadas normalizadas 0..1
-- respecto a la imagen del plano). NULL = aun sin ubicar.
ALTER TABLE public.puesto
    ADD COLUMN IF NOT EXISTS mapa_x double precision,
    ADD COLUMN IF NOT EXISTS mapa_y double precision;
