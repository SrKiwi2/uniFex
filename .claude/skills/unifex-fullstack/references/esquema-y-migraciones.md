# Esquema, migraciones y lógica en la base de datos

## Regla que no se negocia

La BD de producción (`v2_fexpo_uap` en `172.16.21.12`, PostgreSQL **12.22**) se alcanza solo
desde la LAN de la UAP; desde fuera se usa `virtual.uap.edu.bo:5432`. El perfil por defecto trae
`spring.jpa.hibernate.ddl-auto=update`, así que **arrancar la app contra producción altera el
esquema de producción**. No lo hagas.

Desarrolla contra la **copia local** (PostgreSQL 16, servicio `postgresql-x64-16`, base
`v2_fexpo_uap`) con el perfil `dev`:

```bash
mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
```

`application-dev.properties` fija `hbm2ddl=none`, credenciales locales, directorios de subida
bajo `C:/uniFex/dev` y valores locales para los secretos (así dev y los tests corren sin exportar
variables de entorno).

Las herramientas de Postgres están en `C:\Program Files\PostgreSQL\16\bin` y **no están en el
PATH**. Tras restaurar un volcado hay que recrear a mano el índice trigram
`idx_persona_fullname_trgm` (problema de `search_path`).

## Migraciones

**No hay Flyway ni Liquibase.** Los scripts viven en `src/main/resources/db/reserva/` y se
aplican a mano:

| Script | Qué hizo |
|---|---|
| `V1__reserva_puesto.sql` | `reservado_por_id_usuario`, `reserva_expira`, `version` en `puesto` |
| `V2__puesto_mapa.sql` | `mapa_x`, `mapa_y` (double, normalizados 0..1) |
| `V3__edicion.sql` | tabla `edicion` + `inscripcion.id_edicion` |
| `V4__categoria_forma.sql` | `categoria.forma` |

Para agregar uno: siguiente número, nombre descriptivo, **idempotente**
(`ADD COLUMN IF NOT EXISTS`, `CREATE TABLE IF NOT EXISTS`), y aplícalo con `psql` a la copia local:

```bash
"C:/Program Files/PostgreSQL/16/bin/psql" -U postgres -d v2_fexpo_uap \
  -f src/main/resources/db/reserva/V5__mi_cambio.sql
```

Como el número es una convención humana y no un registro de versiones, **anota en el propio
script qué asume del estado previo**. Ningún script se ha aplicado todavía a producción; el día
que se despliegue habrá que aplicarlos en orden, y esa ventana es el momento de mayor riesgo del
proyecto.

## Versionado por edición

`V3` introdujo `edicion` con FEXPO 2025 (`activa=false`, histórico) y FEXPO 2026 (`activa=true`).
`inscripcion.id_edicion` se rellenó con 1 para las 369 filas existentes y tiene `DEFAULT 2`, de
modo que las inserciones nuevas de JPA se etiquetan como 2026 **sin cambiar una línea de Java**.
Es un truco deliberado: el valor por defecto de la columna hace el trabajo.

Queda pendiente: filtrar las stored functions de listado por edición activa, y etiquetar
`venta_boleto` por edición.

## La lógica que no está en Java

Precios, disponibilidad de casetas y detalle de inscripciones son **stored functions** de
PostgreSQL en el esquema `public`, invocadas con `JdbcTemplate` desde `model/repository/`
(`FuncionesInscripcion`, `FuncionesApi`):

- `fn_lista_puestos()`
- `obtenercostopuesto(...)`
- `fn_get_inscripciones(...)`

Cuando una regla de negocio parece no existir en el código, búscala aquí antes de reimplementarla
en Java —duplicarla crearía dos fuentes de verdad que se desincronizan en silencio. Para leer una:

```sql
SELECT prosrc FROM pg_proc WHERE proname = 'fn_get_inscripciones';
```

## La tabla `puesto`

Columnas relevantes para el ciclo de venta:

- `estado_puesto` — `L` libre, `T` en trámite, `O` ocupado, `X` bloqueado.
- `reservado_por_id_usuario` — titular de la reserva temporal; `NULL` fuera de `T`.
- `reserva_expira` — `timestamp`; el barrido `PuestoReservaScheduler` libera lo vencido.
- `version` — se incrementa en cada UPDATE (`@Version` en la entidad).
- `mapa_x`, `mapa_y` — posición normalizada; `NULL` = no colocada en el plano.
- Columnas de auditoría con guion bajo inicial (`_fecha_registro`, `_registro_id_usuario`,
  `_modificacion_id_usuario`, `_estado`), heredadas de `AuditoriaConfig`. Requieren comillas
  dobles en SQL nativo: `"_fecha_modificacion" = now()`.

En la copia local las casetas se **reiniciaron**: todo lo vendido o reservado volvió a `L`, se
limpiaron titular y expiración, y se conservaron 12 en `X`. El historial en las tablas de
inscripción quedó intacto.

## Nombres de columna: la trampa de Hibernate

La estrategia de nombres convierte `estadoPuesto` → `estado_puesto`, pero `mapaX` → `mapax`
(no inserta guion bajo antes de una letra suelta). Eso produjo errores 500 *"no existe la columna
mapax"* que, al redirigirse a `/error`, llegaban al navegador como un 302 incomprensible.

Por eso `Puesto` lleva `@Column(name = "mapa_x")` y `@Column(name = "mapa_y")` explícitos.
Cualquier campo nuevo con una mayúscula tras una letra sola necesita lo mismo.
