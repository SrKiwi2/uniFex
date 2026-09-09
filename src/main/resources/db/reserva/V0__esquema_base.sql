-- =============================================================================
-- V0 -- Esquema base del sistema: las 17 tablas originales, 13 funciones y 1
-- trigger con las que arranco el proyecto ANTES de que existieran las
-- migraciones V1..V14 de esta carpeta.
--
-- Por que existe este archivo: hasta el 2026-09-08 este esquema base no vivia
-- en ningun lado del repositorio -- solo existia "ya construido" dentro de la
-- base de datos real (virtual.uap.edu.bo/v2_fexpo_uap). Eso significaba que
-- levantar un servidor nuevo desde cero era imposible sin conectarse a esa
-- base y sacarle el esquema a mano. Se capturo una vez con:
--
--   pg_dump --schema-only --no-owner --no-privileges \
--     -h virtual.uap.edu.bo -U <usuario> -d v2_fexpo_uap -f V0__esquema_base.sql
--
-- y se guarda aqui para que no vuelva a hacer falta: de ahora en adelante,
-- una base de Postgres recien creada y vacia + este archivo + V1..V14 (o el
-- arranque automatico de UniFexApplication) es TODO lo que hace falta para
-- tener un uniFex funcionando, sin depender de ningun respaldo externo.
--
-- Contenido: SOLO estructura (tablas, columnas, indices, funciones, trigger).
-- CERO datos -- no trae usuarios, entidades, casetas ni ventas reales. Eso es
-- a proposito: es el punto de partida para una instalacion nueva, no una copia
-- de la produccion existente.
--
-- NO es idempotente (a diferencia de V1..V14): son CREATE TABLE/FUNCTION
-- lisos, tal cual los emite pg_dump, sin guardas IF NOT EXISTS. Correrlo dos
-- veces sobre la misma base, o sobre una que ya tenga estas tablas, falla.
-- Es exactamente el mismo trato que un baseline de Flyway: se aplica una
-- unica vez, al principio -- y de hecho UniFexApplication.aplicarEsquemaSiFalta
-- lo hace automaticamente si detecta que la base esta vacia.
-- =============================================================================

--
-- PostgreSQL database dump
--


-- Dumped from database version 12.22 (Ubuntu 12.22-0ubuntu0.20.04.4)
-- Dumped by pg_dump version 18.6 (Ubuntu 18.6-1.pgdg24.04+2)

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
-- OJO: aqui pg_dump 18 escribia "SET transaction_timeout = 0;". Ese parametro solo
-- existe desde PostgreSQL 17, y los servidores donde corre esto son PostgreSQL 12,
-- que aborta con 'unrecognized configuration parameter "transaction_timeout"'.
-- Se quita: no hace falta para nada, solo era un limite de seguridad de la sesion.
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
-- pg_dump deja esto vacio por seguridad (fuerza a calificar todo con public.).
-- Se cambia a 'public' por si acaso, pero la causa real de fondo era otra (ver
-- abajo): las funciones fn_norm_text/fn_norm_fullname/fn_sort_words llamaban
-- unas a otras (y a unaccent()) SIN calificar el esquema. Con check_function_bodies
-- apagado (la linea de abajo) eso no falla al CREAR las funciones -- revienta
-- recien al crear el indice trigram de mas abajo, que fuerza a Postgres a
-- "inlinear" (expandir) el cuerpo SQL de verdad, y esa expansion no respeta el
-- search_path de la sesion como una consulta normal. Se calificaron los tres
-- cuerpos con public. explicitamente para no depender de esto.
SELECT pg_catalog.set_config('search_path', 'public', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: public; Type: SCHEMA; Schema: -; Owner: -
--

-- *not* creating schema, since initdb creates it


--
-- Name: pg_trgm; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS pg_trgm WITH SCHEMA public;


--
-- Name: EXTENSION pg_trgm; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON EXTENSION pg_trgm IS 'text similarity measurement and index searching based on trigrams';


--
-- Name: unaccent; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS unaccent WITH SCHEMA public;


--
-- Name: EXTENSION unaccent; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON EXTENSION unaccent IS 'text search dictionary that removes accents';


--
-- Name: fn_get_inscripciones(bigint); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.fn_get_inscripciones(p_usuario_id bigint) RETURNS TABLE(id_usuario bigint, id_entidad bigint, id_inscripcion bigint, nombre_entidad character varying, objeto character varying, representante_legal character varying, nit character varying, descripcion character varying, ci_representante character varying, tipo_entidad character varying, pago_contado boolean, num_comprobante text, entidad_bancaria text, img_comprobante text, categoria character varying, total_costo numeric, fecha_registro timestamp without time zone)
    LANGUAGE plpgsql
    AS $$
BEGIN
    RETURN QUERY
    SELECT 
        u.id                                     AS id_usuario, 
        e.id                                     AS id_entidad,
        i.id                                     AS id_inscripcion,   -- 👈 NUEVO
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
    GROUP BY
        u.id, 
        e.id,
        i.id,                    -- 👈 NUEVO para agrupar por inscripción
        e.nombre, 
        e.objeto, 
        e.representante_legal, 
        e.nit, 
        e.descripcion, 
        e.ci_representante,
        te.nombre,
        i.pago_contado,
        i.num_comprobante,
        i.entidad_bancaria,
        i.img_comprobante,
        c.nombre,
        i."_fecha_registro"
    ORDER BY i."_fecha_registro" DESC;
END;
$$;


--
-- Name: fn_inscripciones_por_categoria(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.fn_inscripciones_por_categoria() RETURNS TABLE(id_categoria bigint, id_inscripcion bigint, nombre_entidad character varying, representante_legal character varying)
    LANGUAGE plpgsql
    AS $$
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
    GROUP BY i2.id, e.nombre, e.representante_legal, c.id
    ORDER BY e.representante_legal ASC;
END;
$$;


--
-- Name: fn_lista_puestos(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.fn_lista_puestos() RETURNS TABLE(codigo integer, tamano character varying, categoria character varying, estado_puesto character varying)
    LANGUAGE plpgsql
    AS $$
begin
    return query
    select p.codigo::integer,
           p.tamano,
           c.nombre,
           p.estado_puesto
    from puesto p
    inner join categoria c on c.id = p.id_categoria
    where p.estado_puesto <> 'X' and c.id not in (1,4,8,10,13,14,15,16,17,20,21,22,24,25) 
    order by c.nombre, p.codigo::integer;
end;
$$;


--
-- Name: fn_norm_text(text); Type: FUNCTION; Schema: public; Owner: -
--
-- Reordenada antes de fn_norm_fullname (que la usa): el dump original las traia
-- al reves y PostgreSQL valida la existencia de la funcion referenciada al crear
-- una funcion SQL, asi que fallaba "no existe la funcion fn_norm_text(text)".

CREATE FUNCTION public.fn_norm_text(p_text text) RETURNS text
    LANGUAGE sql IMMUTABLE
    AS $$
  SELECT regexp_replace(public.unaccent(lower(coalesce(p_text,''))), '\s+', ' ', 'g')::text
$$;


--
-- Name: fn_norm_fullname(text, text, text); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.fn_norm_fullname(p_nombre text, p_paterno text, p_materno text) RETURNS text
    LANGUAGE sql IMMUTABLE
    AS $$
  SELECT trim(both ' ' FROM public.fn_norm_text(
           trim(both ' ' FROM coalesce(p_nombre,'') || ' ' ||
                           coalesce(p_paterno,'') || ' ' ||
                           coalesce(p_materno,''))))
$$;


--
-- Name: fn_sort_words(text); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.fn_sort_words(p text) RETURNS text
    LANGUAGE sql IMMUTABLE
    AS $$
  WITH toks AS (
    SELECT unnest(regexp_split_to_array(public.fn_norm_text(p), '\s+')) AS w
  )
  SELECT string_agg(w, ' ' ORDER BY w)
  FROM toks
$$;


--
-- Name: obtener_inscripcion_detalle(bigint); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.obtener_inscripcion_detalle(p_id_inscripcion bigint DEFAULT NULL::bigint) RETURNS TABLE(id bigint, fecha_compra timestamp without time zone, nombre_entidad character varying, representante_legal character varying, ci_representante character varying, ci_persona character varying, nit character varying, descripcion character varying, objeto character varying, nombre_persona character varying, paterno_persona character varying, materno_persona character varying, datoqr text, foto_persona character varying)
    LANGUAGE plpgsql
    AS $$
BEGIN
    RETURN QUERY
    SELECT 
        i.id, 
        i.fecha_compra, 
        e.nombre, 
        e.representante_legal, 
        e.ci_representante,
		p.ci, 
        e.nit, 
        e.descripcion, 
        e.objeto,
        p.nombre,
        p.paterno,
        p.materno,
        (
            'RESPONSABLE: '||p.nombre|| E'\n' ||
            'CI: '||p.ci|| E'\n' ||
            'ENTIDAD: '||e.nombre
        ) AS datoqr,
		p.foto
    FROM inscripcion i 
    INNER JOIN inscripcion_puesto i2 ON i2.id_inscripcion = i.id
    INNER JOIN entidad e ON e.id = i.id_entidad
    INNER JOIN responsable r ON r.id_entidad = e.id
    INNER JOIN persona p ON p.id = r.id_persona
    WHERE 
        i._estado <> 'X' 
        AND i2._estado <> 'X' 
        AND e._estado <> 'X'
        AND i.id = p_id_inscripcion
	GROUP BY 
		i.id, 
        i.fecha_compra, 
        e.nombre, 
        e.representante_legal, 
        e.ci_representante,
		p.ci, 
        e.nit, 
        e.descripcion, 
        e.objeto,
        p.nombre,
        p.paterno,
        p.materno,
        datoqr,
		p.foto;
END;
$$;


--
-- Name: obtener_puestos_libres(bigint); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.obtener_puestos_libres(p_id_categoria bigint DEFAULT NULL::bigint) RETURNS TABLE(id bigint, codigo character varying, tamano character varying, categoria_id bigint, categoria_nombre character varying)
    LANGUAGE plpgsql
    AS $$
BEGIN
    RETURN QUERY
    SELECT p.id, p.codigo, p.tamano, c.id, c.nombre
    FROM puesto p
    JOIN categoria c ON c.id = p.id_categoria
    WHERE p.estado_puesto = 'L'
      AND (p_id_categoria IS NULL OR c.id = p_id_categoria)
    ORDER BY p.codigo ASC;
END;
$$;


--
-- Name: obtener_puestos_por_inscripcion(bigint); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.obtener_puestos_por_inscripcion(p_id_inscripcion bigint) RETURNS TABLE(codigo character varying, tamano character varying, costo numeric)
    LANGUAGE plpgsql
    AS $$
BEGIN
    RETURN QUERY
    SELECT 
        p.codigo, 
        p.tamano, 
        ip.costo
    FROM inscripcion_puesto ip
    INNER JOIN puesto p ON p.id = ip.id_puesto
    WHERE ip.id_inscripcion = p_id_inscripcion
    ORDER BY p.codigo ASC;
END;
$$;


--
-- Name: obtenercostopuesto(bigint, character varying, bigint); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.obtenercostopuesto(p_id_tipo_entidad bigint, p_tamano_puesto character varying, p_id_categoria bigint) RETURNS numeric
    LANGUAGE plpgsql
    AS $$
DECLARE
    v_precio_categoria NUMERIC(10,2);
    v_costo            NUMERIC(10,2);
    v_tam              TEXT;
BEGIN
    -- normaliza tamaño: "3x3", "3 X 3", "3X3" -> "3x3"
    v_tam := replace(lower(trim(p_tamano_puesto)), ' ', '');

	IF p_id_categoria = 9 THEN
        RETURN 0.00;
    END IF;

    -- 1) Si la categoría tiene precio fijo (> 0), devolverlo
    IF p_id_categoria IS NOT NULL THEN
        SELECT c.precio_base
          INTO v_precio_categoria
          FROM public.categoria c
         WHERE c.id = p_id_categoria;

        IF COALESCE(v_precio_categoria, 0) > 0 THEN
            RETURN v_precio_categoria;
        END IF;
    END IF;

    -- 2) Caso contrario, aplicar tu lógica por tipo/tamaño
    v_costo := CASE
        WHEN v_tam = '3x3' AND p_id_tipo_entidad IN (1, 2, 4) THEN  50.00
        WHEN v_tam = '3x3' AND p_id_tipo_entidad = 3              THEN 100.00
        WHEN v_tam = '3x3' AND p_id_tipo_entidad = 5              THEN 200.00
        ELSE 0.00
    END;

    RETURN v_costo;
END;
$$;


--
-- Name: verificar_duplicado_id_puesto(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.verificar_duplicado_id_puesto() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    -- Revisamos si ya existe otro registro con el mismo id_puesto
    IF EXISTS (
        SELECT 1
        FROM inscripcion_puesto
        WHERE id_puesto = NEW.id_puesto
          AND id <> NEW.id
    ) THEN
        RAISE EXCEPTION 'Ya existe un registro con id_puesto=%', NEW.id_puesto;
    END IF;

    RETURN NEW;
END;
$$;


--
-- Name: verificar_inscripcion_puesto(text, integer); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.verificar_inscripcion_puesto(p_ci text, p_id_entidad integer) RETURNS integer
    LANGUAGE plpgsql
    AS $$
declare
    v_result integer;
begin
    select case 
               when exists (
                   select 1
                   from inscripcion_puesto i 
                   inner join inscripcion i2 on i2.id = i.id_inscripcion
                   inner join responsable r on r.id_entidad = i2.id_entidad
                   inner join persona p on p.id = r.id_persona
                   where 
                       i._estado <> 'X'
                       and i2._estado <> 'X'
                       and p.ci = p_ci
                       and i2.id_entidad = p_id_entidad
                   limit 1
               ) 
               then 1 
               else 0 
           end
    into v_result;

    return v_result;
end;
$$;


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: admistrativo; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.admistrativo (
    _fecha_modificacion timestamp(6) without time zone,
    _fecha_registro timestamp(6) without time zone,
    _modificacion_id_usuario bigint,
    _registro_id_usuario bigint,
    id bigint NOT NULL,
    id_cargo bigint,
    id_oficina bigint,
    id_persona bigint,
    _estado character varying(255),
    codigo_funcionario character varying(255)
);


--
-- Name: admistrativo_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.admistrativo ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.admistrativo_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: cargo; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.cargo (
    _fecha_modificacion timestamp(6) without time zone,
    _fecha_registro timestamp(6) without time zone,
    _modificacion_id_usuario bigint,
    _registro_id_usuario bigint,
    id_cargo bigint NOT NULL,
    _estado character varying(255),
    descripcion character varying(255),
    nombre character varying(255)
);


--
-- Name: cargo_id_cargo_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.cargo ALTER COLUMN id_cargo ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.cargo_id_cargo_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: categoria; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.categoria (
    _fecha_modificacion timestamp(6) without time zone,
    _fecha_registro timestamp(6) without time zone,
    _modificacion_id_usuario bigint,
    _registro_id_usuario bigint,
    id bigint NOT NULL,
    _estado character varying(255),
    color character varying(255),
    descripcion character varying(255),
    nombre character varying(255),
    precio_base numeric(10,2) DEFAULT 0
);


--
-- Name: categoria_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.categoria ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.categoria_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: categoria_venta; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.categoria_venta (
    id_categoria_venta bigint NOT NULL,
    _estado character varying(255),
    _fecha_modificacion timestamp(6) without time zone,
    _modificacion_id_usuario bigint,
    _fecha_registro timestamp(6) without time zone,
    _registro_id_usuario bigint,
    nombre character varying(255),
    precio character varying(255)
);


--
-- Name: categoria_venta_id_categoria_venta_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.categoria_venta ALTER COLUMN id_categoria_venta ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.categoria_venta_id_categoria_venta_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: control_acceso_responsable; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.control_acceso_responsable (
    id bigint NOT NULL,
    _estado character varying(255),
    _fecha_modificacion timestamp(6) without time zone,
    _modificacion_id_usuario bigint,
    _fecha_registro timestamp(6) without time zone,
    _registro_id_usuario bigint,
    ci character varying(255),
    _creado_en timestamp(6) without time zone,
    fecha_entrada timestamp(6) without time zone,
    fecha_salida timestamp(6) without time zone,
    id_persona bigint,
    observacion character varying(255)
);


--
-- Name: control_acceso_responsable_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.control_acceso_responsable ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.control_acceso_responsable_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: control_responsable; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.control_responsable (
    id bigint NOT NULL,
    _estado character varying(255),
    estado_actual character varying(255),
    evento character varying(255),
    fecha_evento timestamp(6) without time zone,
    id_responsable bigint,
    observacion character varying(255)
);


--
-- Name: control_responsable_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.control_responsable ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.control_responsable_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: entidad; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.entidad (
    _fecha_modificacion timestamp(6) without time zone,
    _fecha_registro timestamp(6) without time zone,
    _modificacion_id_usuario bigint,
    _registro_id_usuario bigint,
    id bigint NOT NULL,
    id_tipo_entidad bigint,
    _estado character varying(255),
    ci_representante character varying(255),
    descripcion character varying(255),
    nit character varying(255),
    nombre character varying(255),
    objeto character varying(255),
    representante_legal character varying(255)
);


--
-- Name: entidad_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.entidad ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.entidad_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: inscripcion; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.inscripcion (
    pago_contado boolean NOT NULL,
    _fecha_modificacion timestamp(6) without time zone,
    _fecha_registro timestamp(6) without time zone,
    _modificacion_id_usuario bigint,
    _registro_id_usuario bigint,
    fecha_compra timestamp(6) without time zone,
    fecha_fin timestamp(6) without time zone,
    fecha_inicio timestamp(6) without time zone,
    id bigint NOT NULL,
    id_entidad bigint,
    num_comprobante bigint,
    _estado character varying(255),
    entidad_bancaria character varying(255),
    img_comprobante character varying(255),
    inscripcion_estado character varying(255)
);


--
-- Name: inscripcion_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.inscripcion ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.inscripcion_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: inscripcion_puesto; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.inscripcion_puesto (
    costo numeric(38,2),
    _fecha_modificacion timestamp(6) without time zone,
    _fecha_registro timestamp(6) without time zone,
    _modificacion_id_usuario bigint,
    _registro_id_usuario bigint,
    id bigint NOT NULL,
    id_inscripcion bigint,
    id_puesto bigint,
    _estado character varying(255)
);


--
-- Name: inscripcion_puesto_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.inscripcion_puesto ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.inscripcion_puesto_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: oficina; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.oficina (
    _fecha_modificacion timestamp(6) without time zone,
    _fecha_registro timestamp(6) without time zone,
    _modificacion_id_usuario bigint,
    _registro_id_usuario bigint,
    id bigint NOT NULL,
    _estado character varying(255),
    nombre character varying(255)
);


--
-- Name: oficina_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.oficina ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.oficina_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: persona; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.persona (
    _fecha_modificacion timestamp(6) without time zone,
    _fecha_registro timestamp(6) without time zone,
    _modificacion_id_usuario bigint,
    _registro_id_usuario bigint,
    id bigint NOT NULL,
    _estado character varying(255),
    celular character varying(255),
    ci character varying(255),
    correo character varying(255),
    foto character varying(255),
    materno character varying(255),
    nombre character varying(255),
    paterno character varying(255)
);


--
-- Name: persona_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.persona ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.persona_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: puesto; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.puesto (
    _fecha_modificacion timestamp(6) without time zone,
    _fecha_registro timestamp(6) without time zone,
    _modificacion_id_usuario bigint,
    _registro_id_usuario bigint,
    id bigint NOT NULL,
    id_categoria bigint,
    _estado character varying(255),
    codigo character varying(255),
    estado_puesto character varying(255),
    tamano character varying(255)
);


--
-- Name: puesto_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.puesto ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.puesto_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: responsable; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.responsable (
    _fecha_modificacion timestamp(6) without time zone,
    _fecha_registro timestamp(6) without time zone,
    _modificacion_id_usuario bigint,
    _registro_id_usuario bigint,
    id bigint NOT NULL,
    id_entidad bigint,
    id_persona bigint,
    _estado character varying(255)
);


--
-- Name: responsable_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.responsable ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.responsable_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: rol; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.rol (
    _fecha_modificacion timestamp(6) without time zone,
    _fecha_registro timestamp(6) without time zone,
    _modificacion_id_usuario bigint,
    _registro_id_usuario bigint,
    id bigint NOT NULL,
    _estado character varying(255),
    descripcion character varying(255),
    nombre character varying(255)
);


--
-- Name: rol_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.rol ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.rol_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: tipo_entidad; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tipo_entidad (
    _fecha_modificacion timestamp(6) without time zone,
    _fecha_registro timestamp(6) without time zone,
    _modificacion_id_usuario bigint,
    _registro_id_usuario bigint,
    id bigint NOT NULL,
    _estado character varying(255),
    nombre character varying(255)
);


--
-- Name: tipo_entidad_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.tipo_entidad ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.tipo_entidad_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: usuario; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.usuario (
    _fecha_modificacion timestamp(6) without time zone,
    _fecha_registro timestamp(6) without time zone,
    _modificacion_id_usuario bigint,
    _registro_id_usuario bigint,
    id bigint NOT NULL,
    persona_id bigint NOT NULL,
    rol_id bigint NOT NULL,
    username character varying(50) NOT NULL,
    password character varying(120) NOT NULL,
    _estado character varying(255)
);


--
-- Name: usuario_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.usuario ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.usuario_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: venta_boleto; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.venta_boleto (
    id bigint NOT NULL,
    _estado character varying(255),
    _fecha_modificacion timestamp(6) without time zone,
    _modificacion_id_usuario bigint,
    _fecha_registro timestamp(6) without time zone,
    _registro_id_usuario bigint,
    cantidad integer NOT NULL,
    fecha_hora timestamp(6) without time zone NOT NULL,
    precio_unitario numeric(12,2) NOT NULL,
    total numeric(14,2) NOT NULL,
    categoria_id bigint NOT NULL,
    persona_id bigint NOT NULL
);


--
-- Name: venta_boleto_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.venta_boleto ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.venta_boleto_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: admistrativo admistrativo_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.admistrativo
    ADD CONSTRAINT admistrativo_pkey PRIMARY KEY (id);


--
-- Name: cargo cargo_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cargo
    ADD CONSTRAINT cargo_pkey PRIMARY KEY (id_cargo);


--
-- Name: categoria categoria_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.categoria
    ADD CONSTRAINT categoria_pkey PRIMARY KEY (id);


--
-- Name: categoria_venta categoria_venta_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.categoria_venta
    ADD CONSTRAINT categoria_venta_pkey PRIMARY KEY (id_categoria_venta);


--
-- Name: control_acceso_responsable control_acceso_responsable_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.control_acceso_responsable
    ADD CONSTRAINT control_acceso_responsable_pkey PRIMARY KEY (id);


--
-- Name: control_responsable control_responsable_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.control_responsable
    ADD CONSTRAINT control_responsable_pkey PRIMARY KEY (id);


--
-- Name: entidad entidad_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.entidad
    ADD CONSTRAINT entidad_pkey PRIMARY KEY (id);


--
-- Name: inscripcion inscripcion_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inscripcion
    ADD CONSTRAINT inscripcion_pkey PRIMARY KEY (id);


--
-- Name: inscripcion_puesto inscripcion_puesto_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inscripcion_puesto
    ADD CONSTRAINT inscripcion_puesto_pkey PRIMARY KEY (id);


--
-- Name: oficina oficina_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.oficina
    ADD CONSTRAINT oficina_pkey PRIMARY KEY (id);


--
-- Name: persona persona_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.persona
    ADD CONSTRAINT persona_pkey PRIMARY KEY (id);


--
-- Name: puesto puesto_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.puesto
    ADD CONSTRAINT puesto_pkey PRIMARY KEY (id);


--
-- Name: responsable responsable_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.responsable
    ADD CONSTRAINT responsable_pkey PRIMARY KEY (id);


--
-- Name: rol rol_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rol
    ADD CONSTRAINT rol_pkey PRIMARY KEY (id);


--
-- Name: tipo_entidad tipo_entidad_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tipo_entidad
    ADD CONSTRAINT tipo_entidad_pkey PRIMARY KEY (id);


--
-- Name: usuario usuario_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.usuario
    ADD CONSTRAINT usuario_pkey PRIMARY KEY (id);


--
-- Name: usuario usuario_username_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.usuario
    ADD CONSTRAINT usuario_username_key UNIQUE (username);


--
-- Name: venta_boleto venta_boleto_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.venta_boleto
    ADD CONSTRAINT venta_boleto_pkey PRIMARY KEY (id);


--
-- Name: idx_persona_fullname_trgm; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_persona_fullname_trgm ON public.persona USING gin (public.fn_norm_fullname((nombre)::text, (paterno)::text, (materno)::text) public.gin_trgm_ops);


--
-- Name: inscripcion_puesto trg_no_duplicados_id_puesto; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_no_duplicados_id_puesto BEFORE INSERT OR UPDATE ON public.inscripcion_puesto FOR EACH ROW EXECUTE FUNCTION public.verificar_duplicado_id_puesto();


--
-- Name: admistrativo fk1ilj5jwr4gsjdfa4mn6y9b26d; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.admistrativo
    ADD CONSTRAINT fk1ilj5jwr4gsjdfa4mn6y9b26d FOREIGN KEY (id_cargo) REFERENCES public.cargo(id_cargo);


--
-- Name: admistrativo fk2pagciuthecvved7xmm5oj1iy; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.admistrativo
    ADD CONSTRAINT fk2pagciuthecvved7xmm5oj1iy FOREIGN KEY (id_oficina) REFERENCES public.oficina(id);


--
-- Name: inscripcion fkc34jloi4md3jw4mj9fcmqlrpy; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inscripcion
    ADD CONSTRAINT fkc34jloi4md3jw4mj9fcmqlrpy FOREIGN KEY (id_entidad) REFERENCES public.entidad(id);


--
-- Name: admistrativo fkd51756me5gd89eerf6exa02f4; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.admistrativo
    ADD CONSTRAINT fkd51756me5gd89eerf6exa02f4 FOREIGN KEY (id_persona) REFERENCES public.persona(id);


--
-- Name: responsable fkg6ik67qqypimgevl771ekaj2; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.responsable
    ADD CONSTRAINT fkg6ik67qqypimgevl771ekaj2 FOREIGN KEY (id_persona) REFERENCES public.persona(id);


--
-- Name: responsable fkhqnwk1ef6ctamp9xdhevl43rw; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.responsable
    ADD CONSTRAINT fkhqnwk1ef6ctamp9xdhevl43rw FOREIGN KEY (id_entidad) REFERENCES public.entidad(id);


--
-- Name: venta_boleto fkhr4k12yydrb7mkr1akttowki9; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.venta_boleto
    ADD CONSTRAINT fkhr4k12yydrb7mkr1akttowki9 FOREIGN KEY (categoria_id) REFERENCES public.categoria_venta(id_categoria_venta);


--
-- Name: venta_boleto fki5tnxbyavwha7ss5amm1g5n6i; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.venta_boleto
    ADD CONSTRAINT fki5tnxbyavwha7ss5amm1g5n6i FOREIGN KEY (persona_id) REFERENCES public.persona(id);


--
-- Name: puesto fkisjt5ecadohyxbvm2n8h0v5x6; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.puesto
    ADD CONSTRAINT fkisjt5ecadohyxbvm2n8h0v5x6 FOREIGN KEY (id_categoria) REFERENCES public.categoria(id);


--
-- Name: entidad fkjxan3wlyg8ruqw2h9ufunnn6c; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.entidad
    ADD CONSTRAINT fkjxan3wlyg8ruqw2h9ufunnn6c FOREIGN KEY (id_tipo_entidad) REFERENCES public.tipo_entidad(id);


--
-- Name: inscripcion_puesto fkkfam8gl3w4pfnsieufylijvh5; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inscripcion_puesto
    ADD CONSTRAINT fkkfam8gl3w4pfnsieufylijvh5 FOREIGN KEY (id_inscripcion) REFERENCES public.inscripcion(id);


--
-- Name: inscripcion_puesto fklbny80abvdrud0vcekflwmj0a; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inscripcion_puesto
    ADD CONSTRAINT fklbny80abvdrud0vcekflwmj0a FOREIGN KEY (id_puesto) REFERENCES public.puesto(id);


--
-- Name: usuario fklse7lqghmt3r1sp298ss9s5bc; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.usuario
    ADD CONSTRAINT fklse7lqghmt3r1sp298ss9s5bc FOREIGN KEY (persona_id) REFERENCES public.persona(id);


--
-- Name: usuario fkshkwj12wg6vkm6iuwhvcfpct8; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.usuario
    ADD CONSTRAINT fkshkwj12wg6vkm6iuwhvcfpct8 FOREIGN KEY (rol_id) REFERENCES public.rol(id);


--
-- Name: inscripcion fkt4svkp4s76su1ehfiddnhit2c; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inscripcion
    ADD CONSTRAINT fkt4svkp4s76su1ehfiddnhit2c FOREIGN KEY (_registro_id_usuario) REFERENCES public.usuario(id);


--
-- PostgreSQL database dump complete
--


