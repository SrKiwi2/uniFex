-- =============================================================================
-- V28 - Pantalla Catalogo en el menu de la SPA.
--
-- El catalogo es solo lectura: categorias y precio base por caseta. Lo necesita
-- quien vende, administra o consulta la feria, por eso se concede a todos los
-- roles operativos. SUPER USUARIO ya ve todo por codigo.
--
-- Es idempotente: si el permiso ya existe, no duplica filas.
-- =============================================================================

-- La tabla no existia en ningun script anterior: en produccion se creo a mano y nunca se
-- versiono, asi que una base nueva caia aqui con "no existe la relación «rol_pantalla»".
-- Se crea aqui (antes del primer INSERT que la usa) con IF NOT EXISTS: en produccion no
-- cambia nada, en una base nueva permite seguir.
CREATE TABLE IF NOT EXISTS rol_pantalla (
    id                       BIGSERIAL PRIMARY KEY,
    id_rol                   BIGINT NOT NULL REFERENCES rol(id) ON DELETE CASCADE,
    pantalla                 VARCHAR(40) NOT NULL,
    _fecha_registro          TIMESTAMP NOT NULL DEFAULT now(),
    _registro_id_usuario     BIGINT,
    UNIQUE (id_rol, pantalla)
);

COMMENT ON TABLE rol_pantalla IS 'Pantallas visibles para cada rol. Controla el menu y el acceso a las rutas de la SPA, NO la autorizacion del servidor.';

INSERT INTO rol_pantalla (id_rol, pantalla, _registro_id_usuario)
SELECT r.id, 'catalogo', NULL
  FROM rol r
 WHERE upper(btrim(r.nombre)) IN ('ADMINISTRADOR', 'ADMINISTRATIVO', 'CONTROL', 'ASESORIA')
   AND (r."_estado" IS NULL OR r."_estado" <> 'ELIMINADO')
   AND NOT EXISTS (
       SELECT 1
         FROM rol_pantalla rp
        WHERE rp.id_rol = r.id
          AND rp.pantalla = 'catalogo'
   );
