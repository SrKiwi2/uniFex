-- =============================================================================
-- V9 — Fotos y referencia de ubicacion de una caseta.
--
-- Para que el vendedor pueda enseñarle al cliente como es la caseta en la vida real y
-- donde queda, sin moverse del mapa.
--
-- Tabla aparte y no una columna en `puesto` porque son VARIAS fotos por caseta y con
-- orden: meterlas como texto separado por comas obliga a parsear en todas partes y hace
-- imposible borrar una sola.
--
-- `orden` decide cual se enseña primero; la de menor orden es la portada.
--
-- Idempotente.
-- =============================================================================

CREATE TABLE IF NOT EXISTS puesto_foto (
    id                        bigserial PRIMARY KEY,
    id_puesto                 bigint NOT NULL REFERENCES puesto (id),
    -- Ruta relativa devuelta por FileStorageService, p. ej. "puestos/2026/08/uuid-ZONA-1.jpg".
    -- Se guarda relativa a proposito: mover la carpeta de subidas no invalida la base.
    ruta                      varchar(300) NOT NULL,
    descripcion               varchar(200),
    orden                     integer NOT NULL DEFAULT 0,
    -- Auditoria. OJO CON LOS NOMBRES: en `AuditoriaConfig` el campo se anota como
    -- "_modificacion_idUsuario", pero la estrategia de nombres de Hibernate lo parte en
    -- snake_case y termina buscando `_modificacion_id_usuario` — que es como estan las
    -- demas tablas. Crear la columna entrecomillada con la mayuscula la deja inaccesible
    -- para Hibernate y todo INSERT falla con "no existe la columna".
    "_estado"                varchar(20) DEFAULT 'A',
    "_fecha_registro"        timestamp,
    "_registro_id_usuario"   bigint,
    "_fecha_modificacion"    timestamp,
    "_modificacion_id_usuario" bigint
);

-- Corrige la primera version de este script, que creo esas dos columnas con la mayuscula.
-- Condicional para que el script siga siendo idempotente en una base nueva, donde ya nacen
-- con el nombre correcto y no hay nada que renombrar.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
                WHERE table_name = 'puesto_foto' AND column_name = '_registro_idUsuario') THEN
        ALTER TABLE puesto_foto RENAME COLUMN "_registro_idUsuario" TO "_registro_id_usuario";
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns
                WHERE table_name = 'puesto_foto' AND column_name = '_modificacion_idUsuario') THEN
        ALTER TABLE puesto_foto RENAME COLUMN "_modificacion_idUsuario" TO "_modificacion_id_usuario";
    END IF;
END $$;

-- El mapa pide las fotos de una caseta constantemente: sin este indice serian escaneos
-- completos de la tabla segun crezca.
CREATE INDEX IF NOT EXISTS ix_puesto_foto_puesto ON puesto_foto (id_puesto, orden);

-- Referencia en texto de donde esta la caseta ("frente a la puerta 3", "esquina norte").
-- Complementa al plano: el mapa dice donde, esto lo dice con palabras que el cliente
-- entiende por telefono.
ALTER TABLE puesto ADD COLUMN IF NOT EXISTS referencia varchar(200);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_name = 'puesto' AND column_name = 'referencia') THEN
        RAISE EXCEPTION 'No se creo puesto.referencia';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables
                    WHERE table_name = 'puesto_foto') THEN
        RAISE EXCEPTION 'No se creo la tabla puesto_foto';
    END IF;
END $$;
