-- =============================================================================
-- V11 — Solicitudes de cancelacion con aprobacion de administracion.
--
-- La cancelacion de una venta deja de ser un acto inmediato del vendedor:
-- el vendedor que registro la venta SOLICITA cancelarla con un motivo, la
-- solicitud queda PENDIENTE, administracion la aprueba o la rechaza (con
-- respuesta), y solo despues de la aprobacion el vendedor puede ejecutar la
-- cancelacion. Cada paso deja huella en `auditoria` (V10) y notifica por
-- WebSocket al interesado, sin recargar la pagina.
--
-- Reglas que impone el codigo (no la BD):
--  - Una venta admite UNA solicitud PENDIENTE a la vez (la segunda se rechaza).
--  - Solo el vendedor que registro la venta puede solicitar (o administracion).
--  - Solo administracion aprueba o rechaza.
--  - Cancelar una venta ajena exige una solicitud APROBADA vigente.
--
-- Sin FK a inscripcion a proposito, igual que auditoria: el historico de
-- solicitudes debe sobrevivir aunque el registro de negocio se limpie.
--
-- Idempotente.
-- =============================================================================

CREATE TABLE IF NOT EXISTS solicitud_cancelacion (
    id                      bigserial   PRIMARY KEY,
    id_inscripcion          bigint      NOT NULL,
    motivo                  text        NOT NULL,
    respuesta               text,
    estado_solicitud        varchar(20) NOT NULL DEFAULT 'PENDIENTE',
    fecha_solicitud         timestamp   NOT NULL DEFAULT now(),
    fecha_resolucion        timestamp,
    _registro_id_usuario    bigint,
    _modificacion_id_usuario bigint,
    _fecha_registro         timestamp NOT NULL DEFAULT now(),
    _fecha_modificacion     timestamp NOT NULL DEFAULT now(),
    _estado                 varchar(10) NOT NULL DEFAULT 'ACTIVO'
);

CREATE INDEX IF NOT EXISTS ix_solicitud_cancelacion_inscripcion
    ON solicitud_cancelacion (id_inscripcion);
CREATE INDEX IF NOT EXISTS ix_solicitud_cancelacion_estado
    ON solicitud_cancelacion (_estado);

-- Idempotencia: si la tabla ya existia de una corrida anterior sin estas
-- columnas de auditoria, se completan aqui.
ALTER TABLE solicitud_cancelacion ADD COLUMN IF NOT EXISTS _fecha_registro timestamp NOT NULL DEFAULT now();
ALTER TABLE solicitud_cancelacion ADD COLUMN IF NOT EXISTS _fecha_modificacion timestamp NOT NULL DEFAULT now();

-- ===== Comprobaciones finales =====
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables
                    WHERE table_name = 'solicitud_cancelacion') THEN
        RAISE EXCEPTION 'No se creo la tabla solicitud_cancelacion';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_name = 'solicitud_cancelacion' AND column_name = 'estado_solicitud') THEN
        RAISE EXCEPTION 'No se creo solicitud_cancelacion.estado_solicitud';
    END IF;
END $$;
