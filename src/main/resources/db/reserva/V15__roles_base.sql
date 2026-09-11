-- =============================================================================
-- V15 — Catalogo de roles completo y con nombres unicos.
--
-- Hasta aqui el arranque solo sembraba dos roles (SUPER USUARIO y ADMINISTRADOR),
-- asi que en una base recien creada era imposible dar de alta a un vendedor
-- (ADMINISTRATIVO), a un control de puerta (CONTROL) o a asesoria (ASESORIA):
-- el selector del modulo de usuarios solo ofrecia esos dos.
--
-- Este script:
--   1) normaliza los nombres ya existentes (MAYUSCULAS, sin espacios sobrantes),
--   2) fusiona los duplicados que esa normalizacion deje a la vista,
--   3) inserta los cinco roles del sistema que falten, con su descripcion,
--   4) impide que vuelvan a duplicarse con un indice unico.
--
-- Es idempotente: volver a ejecutarlo no cambia nada.
--
-- Ejecutar:
--   psql -h <host> -U postgres -d <base> -f V15__roles_base.sql
-- =============================================================================

BEGIN;

-- 1) Nombres canonicos: "  administrador " y "ADMINISTRADOR" son el mismo rol.
--    Se hace ANTES de deduplicar, porque es justo lo que destapa los duplicados.
UPDATE rol
   SET nombre = upper(btrim(regexp_replace(nombre, '\s{2,}', ' ', 'g')))
 WHERE nombre IS NOT NULL
   AND nombre <> upper(btrim(regexp_replace(nombre, '\s{2,}', ' ', 'g')));

-- 2) Duplicados: los usuarios se reapuntan a la fila de menor id y las demas se borran.
--    Sin esto el indice unico del paso 4 fallaria y el script quedaria a medias.
WITH canonico AS (
    SELECT nombre, min(id) AS id_bueno
      FROM rol
     WHERE nombre IS NOT NULL
     GROUP BY nombre
    HAVING count(*) > 1
)
UPDATE usuario u
   SET rol_id = c.id_bueno
  FROM rol r
  JOIN canonico c ON c.nombre = r.nombre
 WHERE u.rol_id = r.id
   AND u.rol_id <> c.id_bueno;

DELETE FROM rol r
 WHERE EXISTS (
        SELECT 1 FROM rol otro
         WHERE otro.nombre = r.nombre
           AND otro.id < r.id)
   AND NOT EXISTS (SELECT 1 FROM usuario u WHERE u.rol_id = r.id);

-- 3) Los cinco roles del sistema. Las descripciones son las mismas que declara
--    security/RolesSistema.java; si cambian ahi, cambian aqui.
INSERT INTO rol (nombre, descripcion, _estado, _fecha_registro, _fecha_modificacion)
SELECT v.nombre, v.descripcion, 'ACTIVO', now(), now()
  FROM (VALUES
        ('SUPER USUARIO',  'Control total: gestiona usuarios, roles, el plano de la feria y toda la administracion.'),
        ('ADMINISTRADOR',  'Administracion de la feria: usuarios, plano, inscripciones y reportes globales.'),
        ('ADMINISTRATIVO', 'Vendedor: reserva, vende y consulta sus propias ventas. No redisena el plano.'),
        ('CONTROL',        'Control de acceso en puerta: verifica credenciales y registra el ingreso de responsables.'),
        ('ASESORIA',       'Consulta: ve listados y reportes de la feria, sin modificar nada.')
       ) AS v(nombre, descripcion)
 WHERE NOT EXISTS (SELECT 1 FROM rol r WHERE r.nombre = v.nombre);

-- Descripcion para los que ya existian sin ella (los dos que sembraba el arranque).
UPDATE rol r
   SET descripcion = v.descripcion,
       _fecha_modificacion = now()
  FROM (VALUES
        ('SUPER USUARIO',  'Control total: gestiona usuarios, roles, el plano de la feria y toda la administracion.'),
        ('ADMINISTRADOR',  'Administracion de la feria: usuarios, plano, inscripciones y reportes globales.'),
        ('ADMINISTRATIVO', 'Vendedor: reserva, vende y consulta sus propias ventas. No redisena el plano.'),
        ('CONTROL',        'Control de acceso en puerta: verifica credenciales y registra el ingreso de responsables.'),
        ('ASESORIA',       'Consulta: ve listados y reportes de la feria, sin modificar nada.')
       ) AS v(nombre, descripcion)
 WHERE r.nombre = v.nombre
   AND (r.descripcion IS NULL OR btrim(r.descripcion) = '');

-- Un rol sin _estado no se distingue de uno dado de baja: se marcan ACTIVO.
UPDATE rol SET _estado = 'ACTIVO' WHERE _estado IS NULL OR btrim(_estado) = '';

-- 4) Un nombre, un rol. El modulo de roles valida lo mismo en Java, pero la garantia
--    real vive aqui: dos altas simultaneas con el mismo nombre chocan contra el indice.
CREATE UNIQUE INDEX IF NOT EXISTS ux_rol_nombre ON rol (nombre);

COMMIT;
