-- =============================================================================
-- V28 - Pantalla Catalogo en el menu de la SPA.
--
-- El catalogo es solo lectura: categorias y precio base por caseta. Lo necesita
-- quien vende, administra o consulta la feria, por eso se concede a todos los
-- roles operativos. SUPER USUARIO ya ve todo por codigo.
--
-- Es idempotente: si el permiso ya existe, no duplica filas.
-- =============================================================================

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
