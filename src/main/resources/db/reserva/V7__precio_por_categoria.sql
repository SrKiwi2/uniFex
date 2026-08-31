-- =============================================================================
-- V7 — El precio de una caseta lo manda su categoria.
--
-- Que arregla:
--
-- 1. `obtenercostopuesto` tenia un id de categoria CABLEADO:
--        IF p_id_categoria = 9 THEN RETURN 0.00;
--    Los ids de categoria no son estables (y tras un reset de la gestion se reinician),
--    asi que esa regla acabaria regalando casetas de una categoria que no tiene nada que
--    ver con la original. Una categoria gratuita ahora se expresa con precio_base = 0,
--    que es explicito y se ve en pantalla.
--
-- 2. Se retira tambien el respaldo cableado por tipo de entidad (tipos 1,2,4 -> 50;
--    3 -> 100; 5 -> 200, solo para '3x3'). No hacia falta para el historico —el costo de
--    una venta se congela en inscripcion_puesto.costo, esta funcion solo se consulta al
--    vender— y era activamente daniño: una categoria a la que se le olvidara poner precio
--    se vendia a 50 Bs, un numero plausible sacado de las reglas de otra feria. Ahora una
--    categoria sin precio devuelve 0, que se ve mal a la primera y ademas el Editor avisa.
--
-- 3. Rellena el precio y la medida de las casetas que quedaron sin ellos.
--
-- Idempotente: se puede ejecutar varias veces.
-- =============================================================================

-- Las categorias sin precio quedan en 0 explicito (nunca NULL, para que el COALESCE
-- de la funcion y el codigo Java vean siempre un numero).
UPDATE categoria SET precio_base = 0 WHERE precio_base IS NULL;

ALTER TABLE categoria ALTER COLUMN precio_base SET DEFAULT 0;
ALTER TABLE categoria ALTER COLUMN precio_base SET NOT NULL;

-- Las casetas creadas desde el Editor nacian sin medida de negocio.
UPDATE puesto SET tamano = '3x3' WHERE tamano IS NULL OR btrim(tamano) = '';

CREATE OR REPLACE FUNCTION public.obtenercostopuesto(
    p_id_tipo_entidad bigint,
    p_tamano_puesto character varying,
    p_id_categoria bigint)
  RETURNS numeric
  LANGUAGE plpgsql
AS $function$
DECLARE
    v_precio_categoria NUMERIC(10,2);
BEGIN
    -- El precio lo manda la categoria, y solo la categoria. Es lo que se ve en el Editor
    -- y en el mapa, asi que lo que se cobra coincide con lo que el vendedor tiene delante.
    -- Los parametros de tipo de entidad y tamaño se conservan en la firma para no romper
    -- a quien ya la invoca, pero ya no intervienen en el calculo.
    IF p_id_categoria IS NULL THEN
        RETURN 0.00;
    END IF;

    SELECT c.precio_base INTO v_precio_categoria
      FROM public.categoria c
     WHERE c.id = p_id_categoria;

    RETURN COALESCE(v_precio_categoria, 0.00);
END;
$function$;

-- Comprobacion: no deben quedar categorias sin precio ni casetas sin medida.
DO $$
DECLARE
    n bigint;
BEGIN
    SELECT count(*) INTO n FROM categoria WHERE precio_base IS NULL;
    IF n > 0 THEN RAISE EXCEPTION 'Quedan % categorias sin precio', n; END IF;

    SELECT count(*) INTO n FROM puesto WHERE tamano IS NULL OR btrim(tamano) = '';
    IF n > 0 THEN RAISE EXCEPTION 'Quedan % casetas sin tamaño', n; END IF;
END $$;
