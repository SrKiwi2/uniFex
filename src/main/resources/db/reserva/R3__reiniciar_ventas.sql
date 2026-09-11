-- =============================================================================
-- R3 — Borra las ventas de prueba y deja los numeros empezando en 1.
--
-- NO es una migracion de esquema (esas son las V*). Es una operacion de datos, y
-- `aplicar-migraciones.sh` nunca la ejecuta: se corre a mano, a proposito.
--
-- Para que sirve: se probo el sistema en un servidor y quedaron ventas, entidades
-- y responsables de mentira, con los numeros de nota ya por el 190. Esto lo deja
-- como recien instalado, PERO sin perder la configuracion que costo armar.
--
--   SE CONSERVA
--     * el plano entero: categorias, casetas, posiciones, tamaños, fotos
--     * las casetas habilitadas a cada vendedor (vendedor_puesto)
--     * usuarios, roles y las personas del sistema
--     * ediciones y catalogos (tipo_entidad, cargo, oficina, categoria_venta)
--     * el registro de migraciones aplicadas
--
--   SE BORRA, y su numeracion vuelve a empezar en 1
--     * inscripciones (ventas) y su detalle de casetas
--     * entidades expositoras, sus responsables y las personas creadas para ellos
--     * ventas de boleteria
--     * solicitudes de cancelacion y la auditoria de esos movimientos
--     * notificaciones
--     * control de acceso
--
--   Y ademas: todas las casetas vivas vuelven a quedar LIBRES.
--
-- Se diferencia de R1 en que R1 ademas TRUNCA puesto y categoria, o sea que tira
-- el plano; eso sirve para empezar una feria distinta, no para limpiar pruebas.
--
-- ⚠ DESTRUCTIVO E IRREVERSIBLE. Saca el respaldo ANTES:
--     ./respaldo.sh crear antes-de-limpiar
--
-- Ejecutar:
--     psql -h <host> -U postgres -d <base> -v ON_ERROR_STOP=1 -f R3__reiniciar_ventas.sql
--
-- Es idempotente: volver a ejecutarlo sobre una base ya limpia no falla ni cambia nada.
-- =============================================================================

BEGIN;

-- 1) Todo lo transaccional, de una sola vez.
--
--    Van TODAS las tablas en un mismo TRUNCATE y SIN CASCADE a proposito: si me
--    hubiera olvidado alguna que apunte a estas, PostgreSQL para aqui con un error
--    en vez de arrastrar en silencio una tabla que queriamos conservar.
--
--    RESTART IDENTITY es lo que hace que la proxima venta vuelva a ser la N.º 1.
TRUNCATE
    inscripcion_puesto,
    notificacion,
    inscripcion,
    responsable,
    entidad,
    venta_boleto,
    solicitud_cancelacion,
    auditoria,
    control_responsable,
    control_acceso_responsable
  RESTART IDENTITY;

-- 2) Las personas que se crearon para esos responsables.
--
--    En `persona`, la columna `_estado` guarda el ORIGEN, no un estado de vida:
--    'ACTIVO' son las personas del sistema (las que pueden tener usuario) y
--    'RESPONSABLE' / 'PROMOTOR' las que entraron por una venta. Solo se borran
--    estas ultimas, y solo si ya no las usa nadie.
DELETE FROM persona p
 WHERE p._estado IN ('RESPONSABLE', 'PROMOTOR')
   AND NOT EXISTS (SELECT 1 FROM usuario u      WHERE u.persona_id = p.id)
   AND NOT EXISTS (SELECT 1 FROM admistrativo a WHERE a.id_persona = p.id)
   AND NOT EXISTS (SELECT 1 FROM responsable r  WHERE r.id_persona = p.id);

-- 3) Las casetas vivas vuelven a estar libres y sin reserva.
--    Las anuladas (_estado = 'X') se quedan dadas de baja: eso es plano, no venta.
UPDATE puesto
   SET estado_puesto = 'L',
       reservado_por_id_usuario = NULL,
       reserva_expira = NULL,
       version = COALESCE(version, 0) + 1
 WHERE _estado <> 'X'
   AND (estado_puesto <> 'L'
        OR reservado_por_id_usuario IS NOT NULL
        OR reserva_expira IS NOT NULL);

-- 4) Las tablas que SI conservan filas: se les recoloca el contador justo despues
--    del ultimo id que quedo.
--
--    Hace falta porque borrar filas no mueve la secuencia: si se borraron 700
--    personas de prueba, la siguiente seguiria naciendo con el id 800 y los numeros
--    quedarian con un hueco enorme para siempre. Esto no renumera lo que ya existe
--    —seria romper cada referencia que apunte a ello—, solo evita el salto.
--
--    `is_called = false` cuando la tabla quedo vacia hace que el primero sea el 1.
DO $$
DECLARE
    t record;
    ultimo bigint;
BEGIN
    FOR t IN
        SELECT c.relname AS secuencia, tb.relname AS tabla, a.attname AS columna
          FROM pg_class c
          JOIN pg_depend d  ON d.objid = c.oid
          JOIN pg_class tb  ON tb.oid = d.refobjid
          JOIN pg_attribute a ON a.attrelid = tb.oid AND a.attnum = d.refobjsubid
         WHERE c.relkind = 'S'
           AND tb.relname NOT IN ('inscripcion_puesto','notificacion','inscripcion','responsable',
                                  'entidad','venta_boleto','solicitud_cancelacion','auditoria',
                                  'control_responsable','control_acceso_responsable')
    LOOP
        EXECUTE format('SELECT COALESCE(max(%I), 0) FROM %I', t.columna, t.tabla) INTO ultimo;
        EXECUTE format('SELECT setval(%L, GREATEST(%s, 1), %L::boolean)',
                       t.secuencia, ultimo, ultimo > 0);
    END LOOP;
END $$;

COMMIT;

-- Como quedo. Si algo de esto no es lo esperado, restaura el respaldo.
\echo ''
\echo '=== Estado tras el reinicio ==='
SELECT 'ventas'            AS que, count(*) AS filas FROM inscripcion
UNION ALL SELECT 'entidades',        count(*) FROM entidad
UNION ALL SELECT 'responsables',     count(*) FROM responsable
UNION ALL SELECT 'ventas boleteria', count(*) FROM venta_boleto
UNION ALL SELECT 'notificaciones',   count(*) FROM notificacion
UNION ALL SELECT '— se conserva —',  NULL
UNION ALL SELECT 'categorias',       count(*) FROM categoria WHERE _estado <> 'X'
UNION ALL SELECT 'casetas vivas',    count(*) FROM puesto    WHERE _estado <> 'X'
UNION ALL SELECT 'casetas libres',   count(*) FROM puesto    WHERE _estado <> 'X' AND estado_puesto = 'L'
UNION ALL SELECT 'casetas en plano', count(*) FROM puesto    WHERE mapa_x IS NOT NULL
UNION ALL SELECT 'usuarios',         count(*) FROM usuario   WHERE _estado <> 'ELIMINADO'
UNION ALL SELECT 'habilitaciones',   count(*) FROM vendedor_puesto
UNION ALL SELECT 'personas sistema', count(*) FROM persona   WHERE _estado = 'ACTIVO';
\echo ''
\echo 'La proxima venta sera la N.º 1.'
