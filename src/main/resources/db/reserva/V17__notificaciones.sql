-- =============================================================================
-- V17 — Tabla de notificaciones persistentes + bandeja por usuario.
--
-- La tabla guarda TODAS las notificaciones (sistema, admin->vendedor, observaciones)
-- para que la bandeja sobreviva a recargas, cambios de dispositivo y app cerrada.
-- El WebSocket (/topic/notificaciones/{userId}) solo avisa en tiempo real;
-- la tabla es la fuente de verdad.
--
-- Es idempotente: volver a ejecutarlo no cambia nada.
--
-- Ejecutar:
--   psql -h <host> -U postgres -d <base> -f V17__notificaciones.sql
-- =============================================================================

CREATE TABLE IF NOT EXISTS notificacion (
    id                      BIGSERIAL PRIMARY KEY,
    id_usuario_destino      BIGINT NOT NULL REFERENCES usuario(id),
    tipo                    VARCHAR(50) NOT NULL,           -- SOLICITUD_CANCELACION, APROBACION_CANCELACION, RECHAZO_CANCELACION, OBSERVACION_ADMIN, VENTA_REGISTRADA, SISTEMA, ...
    asunto                  VARCHAR(200) NOT NULL,
    cuerpo                  TEXT,
    id_inscripcion          BIGINT REFERENCES inscripcion(id),
    id_puesto               BIGINT REFERENCES puesto(id),
    leida                   BOOLEAN NOT NULL DEFAULT FALSE,
    leida_en                TIMESTAMP,
    -- Para hilos de observación (admin escribe, vendedor responde, admin cierra)
    id_notificacion_padre   BIGINT REFERENCES notificacion(id),
    estado_hilo             VARCHAR(20) DEFAULT 'ABIERTA',  -- ABIERTA | RESPONDIDA | RESUELTA
    respuesta               TEXT,                           -- última respuesta del hilo
    respondida_por_id_usuario BIGINT REFERENCES usuario(id),
    respondida_en           TIMESTAMP,
    _fecha_registro         TIMESTAMP NOT NULL DEFAULT NOW(),
    _registro_id_usuario    BIGINT REFERENCES usuario(id)
);

CREATE INDEX IF NOT EXISTS ix_notificacion_usuario_fecha
    ON notificacion (id_usuario_destino, _fecha_registro DESC);

CREATE INDEX IF NOT EXISTS ix_notificacion_inscripcion
    ON notificacion (id_inscripcion);

CREATE INDEX IF NOT EXISTS ix_notificacion_puesto
    ON notificacion (id_puesto);

CREATE INDEX IF NOT EXISTS ix_notificacion_hilo
    ON notificacion (id_notificacion_padre);

COMMENT ON TABLE notificacion IS
    'Bandeja de notificaciones por usuario. El WebSocket avisa en vivo; esta tabla persiste.';

COMMENT ON COLUMN notificacion.tipo IS
    'Categoría: SOLICITUD_CANCELACION, APROBACION_CANCELACION, RECHAZO_CANCELACION, OBSERVACION_ADMIN, VENTA_REGISTRADA, SISTEMA, etc.';

COMMENT ON COLUMN notificacion.estado_hilo IS
    'Solo para tipo OBSERVACION_ADMIN: ABIERTA (admin escribió, esperando respuesta), RESPONDIDA (vendedor respondió), RESUELTA (admin cerró).';

COMMENT ON COLUMN notificacion.respuesta IS
    'Último mensaje del hilo (respuesta del vendedor o cierre del admin).';