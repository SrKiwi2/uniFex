-- =============================================================================
-- V18 — Asignación de casetas y categorías a vendedores (ADMINISTRATIVO).
--
-- Permite al admin definir qué casetas ve y vende cada vendedor.
-- - Cada vendedor (usuario con rol ADMINISTRATIVO) puede tener asignadas
--   una o varias categorías.
-- - Dentro de esas categorías, se le asignan casetas específicas (por ID).
-- - El mapa muestra TODAS las casetas: las que no le tocan salen en gris y su
--   ficha da el nombre y el telefono del vendedor que si las lleva, para poder
--   derivar al cliente. Lo que la asignacion decide es quien puede VENDER, y eso
--   se comprueba en cada escritura, no ocultando filas (ver PuestoApiController).
--
-- Es idempotente: volver a ejecutarlo no cambia nada.
--
-- Ejecutar:
--   psql -h <host> -U postgres -d <base> -f V18__vendedor_asignaciones.sql
-- =============================================================================

-- 1) Tabla intermedia: qué categorías tiene asignado un vendedor
CREATE TABLE IF NOT EXISTS vendedor_categoria (
    id                    BIGSERIAL PRIMARY KEY,
    id_usuario            BIGINT NOT NULL REFERENCES usuario(id),
    id_categoria          BIGINT NOT NULL REFERENCES categoria(id),
    _fecha_registro       TIMESTAMP NOT NULL DEFAULT NOW(),
    _registro_id_usuario  BIGINT REFERENCES usuario(id),
    UNIQUE (id_usuario, id_categoria)
);

CREATE INDEX IF NOT EXISTS ix_vendedor_categoria_usuario ON vendedor_categoria(id_usuario);
CREATE INDEX IF NOT EXISTS ix_vendedor_categoria_categoria ON vendedor_categoria(id_categoria);

COMMENT ON TABLE vendedor_categoria IS 'Categorías asignadas a cada vendedor (ADMINISTRATIVO).';

-- 2) Tabla intermedia: qué casetas específicas tiene asignado un vendedor
-- (permite asignar casetas sueltas, no necesariamente toda la categoría)
CREATE TABLE IF NOT EXISTS vendedor_puesto (
    id                    BIGSERIAL PRIMARY KEY,
    id_usuario            BIGINT NOT NULL REFERENCES usuario(id),
    id_puesto             BIGINT NOT NULL REFERENCES puesto(id),
    _fecha_registro       TIMESTAMP NOT NULL DEFAULT NOW(),
    _registro_id_usuario  BIGINT REFERENCES usuario(id),
    UNIQUE (id_usuario, id_puesto)
);

CREATE INDEX IF NOT EXISTS ix_vendedor_puesto_usuario ON vendedor_puesto(id_usuario);
CREATE INDEX IF NOT EXISTS ix_vendedor_puesto_puesto ON vendedor_puesto(id_puesto);

COMMENT ON TABLE vendedor_puesto IS 'Casetas asignadas individualmente a cada vendedor.';

-- 3) Vista helper: casetas visibles para un vendedor (categorías + individuales)
-- Se usa en el backend para filtrar GET /api/app/puestos
CREATE OR REPLACE VIEW v_puestos_por_vendedor AS
SELECT DISTINCT
    p.*,
    vc.id_usuario AS vendedor_id
FROM puesto p
INNER JOIN categoria c ON c.id = p.id_categoria AND c._estado <> 'X'
LEFT JOIN vendedor_categoria vc ON vc.id_categoria = c.id
LEFT JOIN vendedor_puesto vp ON vp.id_puesto = p.id AND vp.id_usuario = vc.id_usuario
WHERE p._estado <> 'X'
  AND p.estado_puesto <> 'X'
  AND (vc.id_usuario IS NOT NULL OR vp.id_puesto IS NOT NULL);

COMMENT ON VIEW v_puestos_por_vendedor IS 'Casetas visibles para cada vendedor según sus asignaciones.';