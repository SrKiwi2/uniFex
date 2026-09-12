-- V27: Personal de apoyo para la feria (fotógrafos, azafatas, logística, etc.).
--
-- Dos tablas:
--   dependencia        Catálogo de dependencias/unidades (ej. "Unidad de Comunicación").
--   personal_apoyo     Personas asignadas a una dependencia con un rol concreto
--                      (ej. "fotógrafo", "azafata", "logística").
--
-- No tocan la tabla persona ni usuario existentes: es un módulo aislado de apoyo.

CREATE TABLE IF NOT EXISTS dependencia (
    id                       BIGSERIAL PRIMARY KEY,
    nombre                   VARCHAR(150) NOT NULL UNIQUE,
    descripcion              VARCHAR(500),
    _fecha_registro          TIMESTAMP(6),
    _registro_id_usuario     BIGINT,
    _fecha_modificacion      TIMESTAMP(6),
    _modificacion_id_usuario BIGINT,
    _estado                  VARCHAR(1) NOT NULL DEFAULT 'A'
);

COMMENT ON COLUMN dependencia._estado IS 'A = activa, X = eliminada lógicamente';

CREATE TABLE IF NOT EXISTS personal_apoyo (
    id                       BIGSERIAL PRIMARY KEY,
    id_dependencia           BIGINT NOT NULL REFERENCES dependencia(id),
    nombre                   VARCHAR(80) NOT NULL,
    paterno                  VARCHAR(80) NOT NULL,
    materno                  VARCHAR(80),
    ci                       VARCHAR(20) NOT NULL,
    correo                   VARCHAR(120),
    celular                  VARCHAR(20),
    rol                      VARCHAR(80) NOT NULL,       -- ej. "fotógrafo", "azafata", "logística"
    _fecha_registro          TIMESTAMP(6),
    _registro_id_usuario     BIGINT,
    _fecha_modificacion      TIMESTAMP(6),
    _modificacion_id_usuario BIGINT,
    _estado                  VARCHAR(1) NOT NULL DEFAULT 'A'
);

CREATE INDEX IF NOT EXISTS idx_personal_apoyo_dependencia ON personal_apoyo (id_dependencia);
CREATE INDEX IF NOT EXISTS idx_personal_apoyo_ci ON personal_apoyo (ci);

COMMENT ON COLUMN personal_apoyo.rol IS 'Rol dentro de la dependencia: fotógrafo, azafata, logística, etc.';
COMMENT ON COLUMN personal_apoyo._estado IS 'A = activo, X = eliminado lógicamente';

-- Datos de ejemplo (opcional, se pueden borrar si no se quieren)
INSERT INTO dependencia (nombre, descripcion, _fecha_registro, _estado)
VALUES
    ('Unidad de Comunicación', 'Área de comunicación y prensa de la feria', NOW(), 'A'),
    ('Unidad de Protocolo', 'Área de protocolo y atención a autoridades', NOW(), 'A'),
    ('Logística y Montaje', 'Equipo de montaje, desmontaje y logística general', NOW(), 'A')
ON CONFLICT (nombre) DO NOTHING;