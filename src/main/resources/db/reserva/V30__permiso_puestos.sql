-- =============================================================================
-- V30 - Pantalla Puestos en el menu de la SPA.
--
-- Permite ver casetas activas y cambiar su numero sin entrar al editor del plano.
-- El endpoint sigue protegido con Roles.EDITA_PLANO; este permiso solo muestra el
-- enlace en el menu. SUPER USUARIO ya ve todo por codigo.
-- =============================================================================

INSERT INTO rol_pantalla (id_rol, pantalla, _registro_id_usuario)
SELECT r.id, 'puestos', NULL
  FROM rol r
 WHERE upper(btrim(r.nombre)) IN ('ADMINISTRADOR')
   AND (r."_estado" IS NULL OR r."_estado" <> 'ELIMINADO')
   AND NOT EXISTS (
       SELECT 1
         FROM rol_pantalla rp
        WHERE rp.id_rol = r.id
          AND rp.pantalla = 'puestos'
   );
