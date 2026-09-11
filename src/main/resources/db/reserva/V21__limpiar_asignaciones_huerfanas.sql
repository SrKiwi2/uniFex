-- =============================================================================
-- V21 — Suelta las casetas que quedaron atrapadas por vendedores dados de baja.
--
-- Los usuarios se borran de forma LOGICA (_estado = 'ELIMINADO'); la fila sigue ahi
-- porque hay inscripciones y auditoria que la referencian. Pero sus asignaciones de
-- vendedor tambien se quedaban, y desde V20 una caseta asignada es exclusiva: el
-- resultado es que las casetas de un vendedor eliminado quedaban bloqueadas para
-- siempre, sin aparecerle a nadie y sin que nadie pudiera tomarlas. Y como el
-- vendedor ya no sale en el listado, no habia forma de llegar a ellas por pantalla.
--
-- Las asignaciones son configuracion, no historia: quien vendio que queda en la
-- inscripcion, no aqui. Asi que se borran.
--
-- A partir de ahora las limpia la propia baja del usuario (GestionUsuarioService);
-- este script es para las que ya estaban.
--
-- Es idempotente.
-- =============================================================================

BEGIN;

DELETE FROM vendedor_puesto vp
 WHERE EXISTS (SELECT 1 FROM usuario u
                WHERE u.id = vp.id_usuario AND u._estado = 'ELIMINADO');

DELETE FROM vendedor_categoria vc
 WHERE EXISTS (SELECT 1 FROM usuario u
                WHERE u.id = vc.id_usuario AND u._estado = 'ELIMINADO');

COMMIT;
