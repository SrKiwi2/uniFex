-- =============================================================================
-- R1 — Reset de la gestion: deja la base lista para empezar una feria desde cero.
--
-- NO es una migracion de esquema (esas son las V1..V6). Es una operacion de datos,
-- pensada para el entorno de DESARROLLO, que vacia lo transaccional y conserva:
--   * el esquema completo y las stored functions,
--   * los catalogos: rol, tipo_entidad, cargo, oficina, categoria_venta,
--   * las ediciones (FEXPO 2026 sigue siendo la activa),
--   * los usuarios administradores admin1 y admin2 con sus personas.
--
-- Se borra todo lo demas: casetas, categorias, entidades expositoras, responsables,
-- inscripciones, ventas, control de acceso y los usuarios vendedores.
--
-- ⚠ DESTRUCTIVO E IRREVERSIBLE. Antes de ejecutarlo hay que tener un pg_dump.
--    Nunca contra produccion.
--
-- Ejecutar:
--   psql -h localhost -U postgres -d v2_fexpo_uap -f R1__reset_gestion.sql
--
-- Es idempotente: volver a ejecutarlo sobre una base ya resetada no falla ni cambia nada.
-- =============================================================================

BEGIN;

-- 1) Ventas: primero los hijos (inscripcion_puesto referencia inscripcion y puesto).
TRUNCATE inscripcion_puesto, inscripcion, venta_boleto RESTART IDENTITY CASCADE;

-- 2) Control de acceso y seguimiento de responsables.
TRUNCATE control_responsable, control_acceso_responsable RESTART IDENTITY CASCADE;

-- 3) Expositores: responsable une persona con entidad, asi que va antes que entidad.
TRUNCATE responsable, entidad RESTART IDENTITY CASCADE;

-- 4) El plano: puesto referencia categoria.
--    Aqui se pierden las posiciones del mapa; exportalas antes si te sirven.
TRUNCATE puesto, categoria RESTART IDENTITY CASCADE;

-- 5) Personal administrativo (la tabla se llama "admistrativo", sin la 'ni' — asi esta creada).
TRUNCATE admistrativo RESTART IDENTITY CASCADE;

-- 6) Usuarios: se conservan solo los dos administradores.
--    Se usa DELETE y no TRUNCATE para no reiniciar la secuencia: los ids que quedan
--    (1 y 2) seguirian ocupados y un RESTART IDENTITY provocaria colisiones al crear
--    el siguiente usuario.
DELETE FROM usuario WHERE username NOT IN ('admin1', 'admin2');

-- 7) Personas: se quedan solo las que siguen teniendo usuario.
--    Las 796 restantes eran responsables y promotores de las entidades ya borradas.
DELETE FROM persona
 WHERE id NOT IN (SELECT persona_id FROM usuario WHERE persona_id IS NOT NULL);

-- 8) Comprobacion: si algo quedo colgando, la transaccion se aborta y no se borra nada.
DO $$
DECLARE
    sobrantes bigint;
BEGIN
    SELECT count(*) INTO sobrantes FROM inscripcion;
    IF sobrantes > 0 THEN
        RAISE EXCEPTION 'Quedaron % inscripciones: reset abortado', sobrantes;
    END IF;

    SELECT count(*) INTO sobrantes FROM usuario u
     WHERE NOT EXISTS (SELECT 1 FROM persona p WHERE p.id = u.persona_id);
    IF sobrantes > 0 THEN
        RAISE EXCEPTION 'Hay % usuarios sin persona: reset abortado', sobrantes;
    END IF;
END $$;

COMMIT;

-- Resumen de como queda.
SELECT 'rol' AS tabla, count(*) AS filas FROM rol
UNION ALL SELECT 'edicion', count(*) FROM edicion
UNION ALL SELECT 'usuario', count(*) FROM usuario
UNION ALL SELECT 'persona', count(*) FROM persona
UNION ALL SELECT 'categoria', count(*) FROM categoria
UNION ALL SELECT 'puesto', count(*) FROM puesto
UNION ALL SELECT 'entidad', count(*) FROM entidad
UNION ALL SELECT 'responsable', count(*) FROM responsable
UNION ALL SELECT 'inscripcion', count(*) FROM inscripcion
UNION ALL SELECT 'inscripcion_puesto', count(*) FROM inscripcion_puesto
ORDER BY tabla;
