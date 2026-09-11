-- =============================================================================
-- R2 — Deja TODAS las casetas libres y borra las ventas de prueba.
--
-- NO es una migracion de esquema (esas son las V*). Es una operacion de datos para
-- el entorno de DESARROLLO, pensada para volver a empezar una tanda de pruebas sin
-- perder el trabajo hecho.
--
-- CONSERVA:
--   * el plano entero: categorias, casetas, posiciones, tamaños, fotos de caseta
--   * usuarios, roles, personas del sistema
--   * las asignaciones de vendedor (vendedor_categoria, vendedor_puesto)
--   * las ediciones y los catalogos
--
-- BORRA:
--   * inscripciones (ventas) y su detalle de casetas
--   * solicitudes de cancelacion
--   * entidades expositoras y sus responsables, con las personas creadas para ellos
--   * notificaciones colgadas de una venta o una caseta
--   * y deja cada caseta viva en 'L' (libre), sin reserva
--
-- Se diferencia de R1 en que R1 ademas TRUNCA puesto y categoria, o sea que tira el
-- plano. Este no: por eso existe.
--
-- ⚠ DESTRUCTIVO E IRREVERSIBLE. Nunca contra produccion.
--
-- Ejecutar:
--   psql -h localhost -U postgres -d fexpouaplocal -f R2__liberar_casetas.sql
--
-- Es idempotente: volver a ejecutarlo sobre una base ya limpia no falla ni cambia nada.
-- =============================================================================

BEGIN;

-- 1) Avisos colgados de una venta o de una caseta. Van primero: apuntan a inscripcion.
DELETE FROM notificacion WHERE id_inscripcion IS NOT NULL OR id_puesto IS NOT NULL;

-- 2) El detalle de la venta (que caseta se vendio en que inscripcion).
DELETE FROM inscripcion_puesto;

-- 3) Solicitudes de cancelacion. No tienen clave foranea declarada hacia inscripcion,
--    asi que hay que borrarlas a mano o quedarian apuntando a ventas inexistentes.
DELETE FROM solicitud_cancelacion;

-- 4) Las ventas.
DELETE FROM inscripcion;

-- 5) Los responsables y las personas que se crearon para ellos. Se identifican por su
--    _estado: en la tabla persona esa columna guarda el ORIGEN, no un estado de vida
--    ('RESPONSABLE' o 'PROMOTOR' frente a 'ACTIVO' para las personas del sistema).
--    Solo se borran las que ya no las use nadie, para no llevarse por delante a alguien
--    que ademas tenga usuario.
DELETE FROM responsable;

DELETE FROM persona p
 WHERE p._estado IN ('RESPONSABLE', 'PROMOTOR')
   AND NOT EXISTS (SELECT 1 FROM usuario u WHERE u.persona_id = p.id)
   AND NOT EXISTS (SELECT 1 FROM responsable r WHERE r.id_persona = p.id);

-- 6) Las entidades expositoras.
DELETE FROM entidad;

-- 7) Todas las casetas vivas quedan libres y sin reserva.
--    Las anuladas (_estado = 'X') se dejan como estan: siguen dadas de baja.
UPDATE puesto
   SET estado_puesto = 'L',
       reservado_por_id_usuario = NULL,
       reserva_expira = NULL,
       version = COALESCE(version, 0) + 1
 WHERE _estado <> 'X'
   AND (estado_puesto <> 'L'
        OR reservado_por_id_usuario IS NOT NULL
        OR reserva_expira IS NOT NULL);

COMMIT;
