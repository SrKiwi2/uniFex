-- V6: Listados por EDICION y etiqueta de edicion en venta_boleto.
-- Corre sobre V1..V5. Se aplica a mano (no hay Flyway), en la BD de turno:
--   psql -U postgres -h localhost -d v2_fexpo_uap -f V6__edicion_filtros.sql
--
-- Que hace:
--   1. venta_boleto queda etiquetado por edicion (id_edicion, FK a edicion).
--      Las filas existentes se reparten a la edicion ACTIVA (la venta en curso);
--      el DEFAULT se fija a la activa de hoy (cambiar cuando rote la edicion).
--   2. fn_get_inscripciones y fn_inscripciones_por_categoria aceptan un
--      p_id_edicion opcional; NULL = la edicion ACTIVA. Con esto "Mis ventas"
--      y los listados quedan por edicion y el historico (FEXPO 2025) no se mezcla.

-- ===== 1. venta_boleto por edicion =====
ALTER TABLE public.venta_boleto ADD COLUMN IF NOT EXISTS id_edicion bigint;

UPDATE public.venta_boleto vb
SET id_edicion = (SELECT id FROM public.edicion WHERE activa ORDER BY id LIMIT 1)
WHERE vb.id_edicion IS NULL;

DO $$
DECLARE
  v_activa bigint;
BEGIN
  SELECT id INTO v_activa FROM public.edicion WHERE activa ORDER BY id LIMIT 1;
  IF v_activa IS NULL THEN
    SELECT id INTO v_activa FROM public.edicion ORDER BY id LIMIT 1;
  END IF;
  IF v_activa IS NOT NULL THEN
    EXECUTE format('ALTER TABLE public.venta_boleto ALTER COLUMN id_edicion SET DEFAULT %s', v_activa);
  END IF;
END $$;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_venta_boleto_edicion') THEN
    ALTER TABLE public.venta_boleto
      ADD CONSTRAINT fk_venta_boleto_edicion FOREIGN KEY (id_edicion) REFERENCES public.edicion(id);
  END IF;
END $$;

-- ===== 2. fn_get_inscripciones(uid [, id_edicion]) =====
-- NULL en p_id_edicion = edicion activa; si no hay activa, no filtra.
CREATE OR REPLACE FUNCTION public.fn_get_inscripciones(
    p_usuario_id bigint,
    p_id_edicion bigint DEFAULT NULL::bigint)
 RETURNS TABLE(id_usuario bigint, id_entidad bigint, id_inscripcion bigint,
               nombre_entidad character varying, objeto character varying,
               representante_legal character varying, nit character varying,
               descripcion character varying, ci_representante character varying,
               tipo_entidad character varying, pago_contado boolean,
               num_comprobante text, entidad_bancaria text, img_comprobante text,
               categoria character varying, total_costo numeric,
               fecha_registro timestamp without time zone)
 LANGUAGE plpgsql
AS $function$
BEGIN
    RETURN QUERY
    SELECT
        u.id                                     AS id_usuario,
        e.id                                     AS id_entidad,
        i.id                                     AS id_inscripcion,
        e.nombre                                 AS nombre_entidad,
        e.objeto,
        e.representante_legal,
        e.nit,
        e.descripcion,
        e.ci_representante,
        te.nombre                                AS tipo_entidad,
        i.pago_contado,
        COALESCE(i.num_comprobante::text, 'SIN DATO')                  AS num_comprobante,
        COALESCE(NULLIF(i.entidad_bancaria, ''), 'SIN DATO')           AS entidad_bancaria,
        COALESCE(NULLIF(i.img_comprobante, ''), 'SIN DATO')            AS img_comprobante,
        c.nombre                                AS categoria,
        SUM(ip.costo)                            AS total_costo,
        i."_fecha_registro"                      AS fecha_registro
    FROM usuario u
    INNER JOIN entidad e         ON e."_registro_id_usuario" = u.id
    INNER JOIN tipo_entidad te   ON te.id = e.id_tipo_entidad
    INNER JOIN inscripcion i     ON i.id_entidad = e.id
    INNER JOIN inscripcion_puesto ip ON ip.id_inscripcion = i.id
    INNER JOIN puesto p          ON p.id = ip.id_puesto
    INNER JOIN categoria c       ON c.id = p.id_categoria
    WHERE
        u.id = p_usuario_id
        AND u."_estado" <> 'X'
        AND e."_estado" <> 'X'
        AND te."_estado" <> 'X'
        AND i."_estado" <> 'X'
        AND ip."_estado" <> 'X'
        AND p."_estado" <> 'X'
        AND c."_estado" <> 'X'
        AND p.estado_puesto = 'O'
        AND i.id_edicion = COALESCE(
            p_id_edicion,
            (SELECT id FROM public.edicion WHERE activa ORDER BY id LIMIT 1),
            i.id_edicion)
    GROUP BY
        u.id, e.id, i.id, e.nombre, e.objeto, e.representante_legal, e.nit,
        e.descripcion, e.ci_representante, te.nombre, i.pago_contado,
        i.num_comprobante, i.entidad_bancaria, i.img_comprobante, c.nombre,
        i."_fecha_registro"
    ORDER BY i."_fecha_registro" DESC;
END;
$function$;

-- ===== 3. fn_inscripciones_por_categoria([id_edicion]) =====
CREATE OR REPLACE FUNCTION public.fn_inscripciones_por_categoria(
    p_id_edicion bigint DEFAULT NULL::bigint)
 RETURNS TABLE(id_categoria bigint, id_inscripcion bigint,
               nombre_entidad character varying,
               representante_legal character varying)
 LANGUAGE plpgsql
AS $function$
BEGIN
    RETURN QUERY
    SELECT
        c.id,
        i2.id,
        e.nombre,
        e.representante_legal
    FROM inscripcion_puesto i
    INNER JOIN inscripcion i2 ON i2.id = i.id_inscripcion
    INNER JOIN entidad e ON e.id = i2.id_entidad
    INNER JOIN puesto p ON p.id = i.id_puesto
    INNER JOIN categoria c ON c.id = p.id_categoria
    WHERE i._estado <> 'X'
      AND i2._estado <> 'X'
      AND e._estado <> 'X'
      AND p._estado <> 'X'
      AND c._estado <> 'X'
      AND p.estado_puesto = 'O'
      AND i2.id_edicion = COALESCE(
          p_id_edicion,
          (SELECT id FROM public.edicion WHERE activa ORDER BY id LIMIT 1),
          i2.id_edicion)
    GROUP BY i2.id, e.nombre, e.representante_legal, c.id
    ORDER BY e.representante_legal ASC;
END;
$function$;
