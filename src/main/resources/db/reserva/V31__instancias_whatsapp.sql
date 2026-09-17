-- Requiere el esquema base. Aplicar antes de iniciar la nueva version.
-- La configuracion anterior se importa una sola vez al arrancar, si estaba habilitada.
BEGIN;
CREATE TABLE IF NOT EXISTS instancia_whatsapp (
    id BIGSERIAL PRIMARY KEY,
    nombre VARCHAR(120) NOT NULL,
    url_api VARCHAR(500) NOT NULL,
    clave_api VARCHAR(500) NOT NULL,
    instancia VARCHAR(200) NOT NULL,
    activa BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_instancia_whatsapp_activa
    ON instancia_whatsapp (activa) WHERE activa;
COMMIT;
