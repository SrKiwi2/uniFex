CREATE TABLE IF NOT EXISTS configuracion_sistema (
    clave VARCHAR(80) PRIMARY KEY,
    activo BOOLEAN NOT NULL DEFAULT FALSE,
    mensaje TEXT,
    actualizado_en TIMESTAMP NOT NULL DEFAULT now(),
    actualizado_por BIGINT
);

INSERT INTO configuracion_sistema (clave, activo, mensaje)
VALUES ('MODO_MANTENIMIENTO', FALSE, 'Sistema no disponible de 9:30 pm a 6 am por verificacion y conciliacion.')
ON CONFLICT (clave) DO NOTHING;
