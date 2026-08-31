---
name: unifex-fullstack
description: >-
  Construye funcionalidades de punta a punta en UniFex (feria FEXPO/UAP): casetas y puestos,
  mapa interactivo tipo butacas de cine, reserva en tiempo real sin doble venta, categorías,
  usuarios vendedores, API JWT bajo /api/app, WebSocket STOMP, la SPA Vue y el APK Capacitor.
  Úsala SIEMPRE que el usuario mencione casetas, puestos, el mapa o plano de la feria,
  reservar / liberar / bloquear / mover / registrar una caseta, categorías, vendedores,
  tiempo real, WebSocket, el editor del plano, un endpoint nuevo, una vista Vue o el APK —
  aunque no diga "UniFex" ni pida arquitectura explícitamente. Aplícala también antes de tocar
  Puesto, PuestoReservaService, IPuestoDao, PuestoEventPublisher o SecurityConfig: ahí viven
  los invariantes que impiden vender dos veces la misma caseta. Para renombrar, reordenar
  capas o borrar código muerto usa unifex-arquitectura en su lugar.
---

# UniFex de punta a punta

UniFex vende casetas de una feria. La operación crítica —dos vendedores tocando la misma
caseta en el mismo segundo— ya está resuelta, y la forma en que está resuelta es frágil ante
código bienintencionado. Esta skill existe para que cada funcionalidad nueva atraviese las
mismas capas sin romper esa garantía.

## Mapa mental

Backend Spring Boot 3.5.5 / Java 21, paquete `com.usic.uniFex`, puerto **7676**.
Frontend Vue 3 + Vite en `frontend/`, puerto **5173**, que proxya `/api` y `/ws` al backend.
Todo el código está **en español**: nombres de clase, rutas, columnas y comentarios. Escribe igual.

La SPA tiene un **sistema de diseño propio** (sin framework, para que el bundle siga chico de cara
al APK) en `frontend/src/`: `style.css` (tokens de color con tema claro/oscuro + clases `.btn`,
`.control`, `.campo`, `.tabla`, `.card`, `.badge`), `components/AppShell.vue` (layout con sidebar
filtrado por rol + topbar con toggle de tema), `components/UiModal.vue`, y los helpers
`ui/tema.js` y `ui/toast.js` (`toast(msg, 'ok'|'error'|'info')`). Una vista administrativa nueva
se envuelve en `<AppShell titulo="…">` y reutiliza esas clases; no reinventes botones ni tablas.

Conviven dos frontends. El Thymeleaf viejo (`resources/templates/`) sigue vendiendo y no se toca
salvo que te lo pidan; la SPA Vue es lo nuevo. Se distinguen por la ruta y por cómo autentican:

| | Web Thymeleaf | SPA / app móvil |
|---|---|---|
| Rutas | `/admin`, `/guardar`, `/venta`… | `/api/auth/**`, `/api/app/**` |
| Auth | sesión `HttpSession` | JWT `Authorization: Bearer` |
| Cadena de seguridad | `webSecurityFilterChain` `@Order(2)` | `apiSecurityFilterChain` `@Order(1)` |
| Usuario actual | `request.getSession().getAttribute("usuario")` | `SecurityContextHolder` → `JwtUser` |

Una caseta (`Puesto`) tiene cuatro estados, constantes en `Puesto.java`:
`LIBRE="L"` → `EN_TRAMITE="T"` (reserva con TTL de 300s) → `OCUPADO="O"`, y `BLOQUEADO="X"`
fuera del ciclo. `PuestoEstadoDTO` es el contrato único: lo devuelve la API **y** lo difunde el
WebSocket, así que si agregas un campo al mapa, agrégalo ahí y ambos lo ven.

## Los tres invariantes

Estos no son estilo, son corrección. Si dudas entre elegancia y uno de estos, gana el invariante.

### 1. Toda transición de estado de una caseta es un UPDATE condicional en el DAO

La garantía contra la doble venta **no vive en Java**. Vive en el `WHERE` de un UPDATE nativo en
`IPuestoDao`, del tipo `WHERE id = :id AND estado_puesto = 'L'`. Cuando dos vendedores reservan a
la vez, PostgreSQL serializa el acceso a la fila: el primero la ve libre y afecta 1 fila, el
segundo afecta 0 y recibe "no disponible". Ese conteo de filas —`> 0`— **es** la respuesta.

Leer el puesto, comprobar el estado en Java y luego guardar es exactamente el bug que este
diseño elimina, porque entre la lectura y la escritura cabe el otro vendedor. Por eso:

```java
// BIEN: la condición viaja con la escritura, la BD arbitra.
@Modifying(clearAutomatically = true)
@Query(value = "UPDATE puesto SET estado_puesto = 'X', version = version + 1 " +
       "WHERE id = :id AND estado_puesto = 'L'", nativeQuery = true)
int bloquearSiLibre(@Param("id") Long id);

// MAL: hay una ventana entre el check y el save. Aquí es donde se vende dos veces.
Puesto p = puestoDao.findById(id).orElseThrow();
if (p.getEstadoPuesto().equals(Puesto.LIBRE)) { p.setEstadoPuesto("X"); puestoDao.save(p); }
```

`clearAutomatically = true` no es decorativo: sin él, el contexto de persistencia conserva la
entidad con el estado viejo y una lectura posterior en la misma transacción te miente.

El servicio (`PuestoReservaService`) solo traduce filas-afectadas a `boolean` y anota
`@Transactional`. Métodos nuevos de transición van ahí, con el mismo patrón. El controlador
convierte `false` en **409 Conflict**, no en 200 con un mensaje triste, porque la SPA distingue
por código de estado.

### 2. Toda escritura exitosa se difunde por WebSocket

```java
boolean ok = reservaService.bloquear(id, usuarioId);
if (ok) publisher.publicar(id);   // ← sin esto, el mapa de los demás miente
```

`PuestoEventPublisher.publicar(id)` relee la caseta y manda su `PuestoEstadoDTO` a
`/topic/puestos`. Es el único punto de publicación; no inventes otros topics para el estado de
casetas. Una caseta que cambia sin difundirse deja a los otros vendedores viendo verde algo que
ya está vendido —que es el problema original disfrazado de otra manera.

Si la escritura ocurre dentro de una transacción más larga (como `/guardar`, el registro
completo), publica **después del commit** (`TransactionSynchronization` / `afterCommit`), o
difundirás un estado que aún puede revertirse.

Esto ya cubre el caso de las reservas que vencen solas: `PuestoReservaScheduler` barre cada 30 s
(`unifex.reserva.barrido-ms`) y difunde **cada** caseta que volvió a `LIBRE`. El cliente no tiene
que sondear para enterarse de una expiración.

### 3. El esquema de producción no se toca desde la aplicación

`application.properties` (perfil por defecto = producción) trae `hbm2ddl.auto=update`. Arrancar
la app contra la BD remota con ese perfil deja que Hibernate altere el esquema de producción.

Trabaja siempre contra la copia local con el perfil `dev`:

```bash
mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"   # hbm2ddl=none, Postgres 16 local
```

Los cambios de esquema son **scripts SQL numerados** en `src/main/resources/db/reserva/`
(`V1__reserva_puesto.sql` … `V4__categoria_forma.sql`). **No hay Flyway ni Liquibase**: los
números son una convención humana y los scripts se aplican a mano con `psql`. Escribe el
siguiente número, hazlo idempotente (`ADD COLUMN IF NOT EXISTS`) y aplícalo solo a la copia local.
Detalles y estado de cada script: `references/esquema-y-migraciones.md`.

## La receta: una rebanada vertical

Cuando te piden una capacidad nueva sobre casetas ("bloquear una caseta", "mover una caseta de
categoría", "ver quién la reservó"), atraviesa las capas en este orden. Cada paso se apoya en el
anterior, y el orden importa porque el contrato (`PuestoEstadoDTO`) tiene que existir antes de
que el frontend pueda consumirlo.

1. **Esquema** — ¿hace falta una columna? Script SQL nuevo en `db/reserva/`, aplicado a la local.
2. **Entidad** — campo en `Puesto`/`Categoria`. Si el nombre Java tiene mayúscula intermedia
   (`mapaX`), pon `@Column(name = "mapa_x")` explícito. Ver "Trampas" abajo: esto ya explotó.
3. **DAO** — el UPDATE condicional (invariante 1), devolviendo `int`.
4. **Servicio** — `@Transactional`, traduce filas a `boolean`, registra con `log.info`.
5. **DTO** — si el mapa debe ver el dato nuevo, agrégalo a `PuestoEstadoDTO` y a su `de(Puesto)`.
   Es un `record`: el orden de los campos es el orden del JSON.
6. **Controlador** — en `PuestoApiController` (o el que corresponda) bajo `/api/app/**`. Usuario
   desde el JWT vía el helper `usuarioActual()`, nunca desde la sesión. Éxito → `publicar(id)`.
   Fracaso → 409. Sin usuario → 401.
7. **Vue** — `apiFetch()` de `src/api.js` mete el `Bearer` y cierra sesión ante un 401. La vista
   ya recibe el estado nuevo por WebSocket sola, porque escucha `/topic/puestos`: **no refresques
   la lista con un GET tras escribir**, deja que llegue el broadcast. Eso mantiene a todos los
   clientes coherentes con una sola ruta de actualización.

Al terminar, verifica de verdad (sección "Verificar").

## Antes de añadir una columna o un campo al DTO

Los pasos 1 y 5 de la receta son los caros, y la tentación de usarlos es fuerte. Resístela salvo
que el pedido lo exija.

**Una columna nueva** significa un script SQL que alguien tendrá que aplicar a mano a producción en
una ventana de mantenimiento. **Un campo nuevo en `PuestoEstadoDTO`** cambia un contrato que
consumen tres cosas a la vez: `GET /api/app/puestos`, cada mensaje de `/topic/puestos` y el mapa.
Ninguno de los dos es gratis, y ninguno se revierte con un `git revert`.

Así que antes de crearlos, pregúntate si el dato **ya viaja**. Muy a menudo sí:

- ¿Cuánto le queda a una reserva? → `reservaExpira` ya está en el DTO. Cuenta atrás en el cliente.
- ¿De qué color y forma se pinta la caseta? → `color` y `forma` ya vienen de la categoría.
- ¿Está bloqueada? → el estado `X` ya existe en `estado_puesto`.

Si el usuario pidió "bloquear una caseta", entrégale bloquear una caseta. Añadir de paso una
columna `motivo_bloqueo` que nadie pidió te obliga a tocar esquema, entidad, DTO y frontend, y
convierte un cambio de riesgo bajo en uno que necesita coordinación con producción. Si crees que el
motivo hace falta, **propónlo aparte** y deja que el usuario decida.

## Autorización: declárala, no la escribas

Cuando un endpoint deba ser solo de administrador, no compares el rol a mano dentro del método:

```java
// MAL: reimplementa la autorización, y ese 403 lo tienes que construir tú.
if (!"ADMINISTRADOR".equalsIgnoreCase(ju.rol())) return ResponseEntity.status(403)...

// BIEN: Spring ya sabe hacer esto. JwtAuthFilter ya concede la autoridad ROLE_<rol>,
// y la cadena API ya tiene un accessDeniedHandler que devuelve 403 con JSON.
@PreAuthorize(Roles.EDITA_PLANO)
```

`@PreAuthorize` necesita `@EnableMethodSecurity` (ya está en `SecurityConfig`). **Sin él las
anotaciones se ignoran en silencio** y el endpoint queda abierto pareciendo cerrado.

Los nombres de rol viven en `security/Roles`, no esparcidos por los controladores. Y **verifica
contra la tabla `rol` antes de escribir uno**: los roles reales son `SUPER USUARIO`,
`ADMINISTRADOR`, `ADMINISTRATIVO`, `CONTROL` y `ASESORIA`. No existe `VENDEDOR`, pese a que un
`if` viejo de `AdminController` lo compare. Los 35 usuarios `ADMINISTRATIVO` **son** los vendedores:
venden casetas, pero no rediseñan el plano. Los detalles están en `references/seguridad-y-roles.md`.

## Qué transiciones son legales

Al añadir una transición nueva, la pregunta de diseño no es "¿cómo la escribo?" sino "¿desde qué
estados se permite?". Esa decisión va en el `WHERE` y no en un `if`, y tiene consecuencias de
negocio reales:

| Transición | Desde | Por qué |
|---|---|---|
| reservar | `L` | solo se reserva lo libre |
| liberar | `T` **del mismo usuario** | nadie suelta la reserva de otro |
| confirmar | `T` **del mismo usuario** | solo el titular cierra su venta |
| ocupar (registro directo) | `L`, o `T` propio | atajo de `/guardar` |
| bloquear | `L` | bloquear una `T` le roba la reserva a un vendedor; bloquear una `O` esconde una venta real |
| anular (baja lógica) | `L` **y sin ventas** | `inscripcion_puesto` referencia casetas: borrar una vendida destruiría historial |

Cuando dudes de si una transición debe permitirse desde `T` u `O`, piensa en qué persona pierde
algo. Si alguien pierde, la respuesta suele ser no, y el 409 se lo explica.

## Trampas ya pagadas

Cada una costó una sesión de depuración. No las repitas.

- **`securityMatcher` con `/**`.** El `MvcRequestMatcher` por defecto no capturaba
  `GET /api/app/puestos` (3 segmentos) aunque sí capturaba rutas más profundas. La cadena JWT usa
  `AntPathRequestMatcher.antMatcher(...)` dentro de un `OrRequestMatcher`. Si agregas un prefijo
  nuevo a la API, agrégalo ahí con `antMatcher`, y comprueba el caso corto sin subrutas.
- **`sendError` en la cadena API.** Dispara un forward interno a `/error` que la cadena web
  convierte en un 302 hacia el login. La API responde con `setStatus` + escribir el JSON a mano.
  Un 302 donde esperabas 401 casi siempre es esto.
- **Nombres de columna de Hibernate.** `estadoPuesto` → `estado_puesto`, pero `mapaX` → `mapax`
  (sin guion bajo antes de una letra sola). Da un 500 "no existe la columna mapax" que además
  se redirige a `/error` y llega al navegador como un 302 desconcertante. Anota `@Column` explícito.
- **No hay auditoría automática de JPA.** Pese a las anotaciones `@CreatedDate`/`@CreatedBy` en
  `AuditoriaConfig`, el auditing está **apagado**. Al crear o modificar una entidad, setea a mano
  `registro`, `modificacion`, `registroIdUsuario`, `modificacionIdUsuario` y `estado` —como hace
  `CategoriaMapaService.crear()`. Si los olvidas, quedan nulos y el listado los esconde.
- **`_estado` no significa lo mismo en cada tabla.** En `puesto` y `categoria` vale `'A'` (activo)
  o `'X'` (anulado); en `usuario` e `inscripcion` es `"ACTIVO"`; en `persona` guarda un *tipo*
  (`"RESPONSABLE"`, `"PROMOTOR"`). Lo único transversal es que `'X'` marca lo anulado. Usa las
  constantes `Puesto.REGISTRO_ACTIVO`/`REGISTRO_ANULADO`, no escribas `"ACTIVO"` en una caseta.
  Nota que hay **dos** campos de estado en `puesto`: `estado_puesto` (L/T/O/X, el ciclo de venta)
  y `_estado` (A/X, la baja lógica). La baja pone **ambos** a `'X'`, porque `fn_lista_puestos`
  filtra por `estado_puesto`, no por `_estado`.
- **Hibernate incluye la columna en el INSERT con NULL**, así que un `DEFAULT` de la BD no se
  aplica al crear desde JPA. Si una columna nueva necesita valor por defecto, ponlo también en el
  código (constante en la entidad), no solo en el `ALTER TABLE`.
- **Los broadcasts son asíncronos y engañan a los tests.** Al verificar por WebSocket, un mensaje
  de una acción anterior puede llegar *después* de la que estás midiendo. Si tomas el primer
  mensaje de una caseta tras una acción, puedes leer el estado viejo y creer que el backend falló
  cuando la BD tiene el valor correcto. Drena antes de medir y toma el **último** estado difundido
  de esa caseta. Cuando un test de broadcast falle, confirma en la BD antes de "arreglar" el código.
- **`global` en SockJS.** `sockjs-client` referencia `global`; `vite.config.js` lo define como
  `globalThis`. Si migras el bundler, esto se rompe primero.
- **Lógica de negocio en la BD.** Precios, disponibilidad y detalle de inscripciones viven en
  *stored functions* de PostgreSQL (`fn_lista_puestos()`, `obtenercostopuesto(...)`,
  `fn_get_inscripciones(...)`), invocadas con `JdbcTemplate` desde `model/repository/`. Si una
  regla "no aparece" en Java, está en la BD. Consúltala antes de reimplementarla.

## Verificar

Nada se da por hecho sin ejercitar el flujo real. Hay un script que hace el humo completo
(login → listar → reservar → reintentar y esperar 409 → liberar → 401 sin token → `/ws/info`):

```bash
node .claude/skills/unifex-fullstack/scripts/verificar-api.mjs
# opciones: --base http://localhost:7676 --usuario admin1 --clave 'usuario25$'
```

Está en Node y no en Python a propósito: **esta máquina no tiene Python instalado** (solo los
alias de la Microsoft Store, que no ejecutan nada). Node ya es dependencia del frontend. Tenlo en
cuenta antes de escribir cualquier herramienta auxiliar para este proyecto.

Levanta los dos servidores antes:

```bash
mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"   # backend :7676
npm --prefix frontend run dev                                # SPA   :5173
```

Y luego abre `http://localhost:5173/mapa` (visor) o `/editor` (diseñador del plano) en **dos
pestañas**: reserva en una y confirma que la otra se recolorea sola. Esa es la prueba de que el
invariante 2 se cumple; un test unitario no la sustituye.

Para concurrencia hay `src/test/java/com/usic/uniFex/PuestoReservaConcurrenciaTest.java` (25 hilos
sobre la misma caseta, exactamente uno gana). Si añades una transición nueva, añádele un caso:
es barato y es la única red que atrapa una regresión del invariante 1.

```bash
mvnw.cmd test -Dtest=PuestoReservaConcurrenciaTest
```

## Dónde seguir leyendo

- `references/mapa-y-editor.md` — cómo funcionan `Mapa.vue`, `Editor.vue` y `PanZoom.vue`:
  coordenadas normalizadas, modos del editor, formas y colores por categoría, pan/zoom táctil.
  Léelo antes de tocar el plano o agregar interacción al mapa.
- `references/esquema-y-migraciones.md` — tablas, los scripts `V1`–`V4`, versionado por edición,
  las stored functions y cómo aplicar un cambio a la copia local.
- `references/seguridad-y-roles.md` — las dos cadenas, el JWT, qué falta para tener roles reales
  (hoy `/api/app/**` es "cualquier autenticado") y cómo asegurar `/ws`. Léelo si el pedido habla
  de permisos, vendedores o "que solo el admin pueda…".
- `references/capacitor-apk.md` — empaquetar la SPA como APK Android. Fase 4, aún no iniciada.

## Estado y pendientes

Fases 1 (reserva atómica) y 2 (JWT + secretos fuera del repo) están **completas y verificadas**.
La 3 (SPA + mapa) está avanzada: login, tablero, visor del mapa, editor de plano, alta de
categorías y guardado de posiciones funcionan.

También está hecho el acceso: `@PreAuthorize(Roles.EDITA_PLANO)` protege la edición del plano
(`/api/app/categorias/**` y `/posiciones`), `/ws` exige JWT en el `CONNECT`, y la SPA tiene una
pantalla de inicio con **Mapa de ventas** y **Administración**, con el editor oculto a quien no
puede editar.

También está hecha la **Fase C (editor completo)**: el DTO lleva `tamanoMapa`, `mapaEscala` y
`activo`; el pin se dibuja en unidades del plano (escala con el zoom, ya no queda diminuto);
`PuestoApiController` tiene alta (`POST`), baja lógica (`DELETE`, con el `UPDATE` condicional
`anularSiLibreYSinVentas`) y guardado por lotes que **ahora sí difunde**; y `Editor.vue` trae
multiselección por caja, mover, escalar, quitar del plano, eliminar y deshacer/rehacer. Cambiar
color/forma/tamaño de una categoría redifunde todas sus casetas (`publicarVarios`).

La **gestión de categorías** también está: renombrar, ajustar la cantidad de casetas
(`PATCH /categorias/{id}/cantidad` — subir crea, bajar anula las libres de código más alto) y
eliminar la categoría entera (`DELETE /categorias/{id}`). Las dos operaciones destructivas
respetan el mismo principio que la baja de una caseta: **nunca sacrifican una caseta vendida o
reservada**. Bajar la cantidad salta esas y devuelve `noQuitadas`; eliminar la categoría da 409
sin tocar nada si `contarNoEliminablesDeCategoria > 0`. Es la regla de "¿quién pierde algo?"
aplicada a un lote.

**Gestión de usuarios (Fase E) — hecha:** `UsuarioApiController` (`/api/app/usuarios`, solo
`Roles.GESTIONA_USUARIOS`) con listar, roles, buscar personas, crear, editar, cambiar contraseña,
activar/desactivar y baja lógica, sobre `GestionUsuarioService` (valida username único, persona sin
usuario, no auto-bajarse). Estados en `usuario._estado`: `ACTIVO`/`INACTIVO`/`ELIMINADO` (aquí sí
son palabras, no `A`/`X` — `_estado` no es homogéneo, ver más abajo). `UsuarioDTO` **nunca** lleva
el hash. Vista `Usuarios.vue`. **Ojo:** `IPersonasDao.listarPersonas()` filtra `estado='ACTIVO'`,
que en la práctica son ~5 personas (las demás tienen `_estado` = `RESPONSABLE`/`PROMOTOR`), así que
el selector de persona al crear un usuario solo ofrece esas; crear vendedores en masa exige antes
registrar sus personas como `ACTIVO`. Es deuda heredada del módulo Thymeleaf, no un bug nuevo.

**"Mis ventas" (Fase F) — hecha:** `MisVentasApiController` (`/api/app/mis-ventas`) envuelve
`fn_get_inscripciones(uid)` con el uid del JWT; el aislamiento por vendedor sale del token, nunca de
un parámetro. Cuidado: esa función **solo cuenta casetas en estado `'O'`** (confirmadas), así que en
la copia local —con los puestos reseteados a `'L'`— sale vacía; no es un bug, en prod devuelve datos.
El inicio (`Inicio.vue`) es un dashboard por rol, y Mapa/Editor ahora usan `<AppShell inmersivo>`
(se retiraron `NavBar.vue` y `Administracion.vue`).

**Reportes admin (Fase G) — hechos:** `ReportesApiController` (`/api/app/reportes`, solo admin)
expone `/resumen` (KPIs), `/por-categoria` y `/por-entidad` desde las proyecciones `resumenPor*`
de `IInscripcionDao`. A diferencia de `fn_get_inscripciones`, estas queries **no** filtran por
caseta `'O'`, así que devuelven datos en dev. `Reportes.vue` + `BarrasHorizontales.vue` (barras
CSS, una serie, sin librería). Al construirlo salieron dos problemas de datos que conviene conocer:
las queries de resumen **no excluían inscripciones anuladas** (`_estado='X'`; corregido), y existen
**2 `inscripcion_puesto` con `id_puesto` NULL** (ventas huérfanas sin caseta) que descuadraban el
KPI general contra las tablas — las 3 queries ahora las ignoran igual. Lección: cuando un total
agregado no cuadra con su desglose, sospecha de filtros de estado distintos y de filas huérfanas
antes que de tu código.

Plan de migración vigente (Thymeleaf → Vue, lo administrativo primero): D. sistema de diseño
✓ · E. usuarios ✓ · F. inicio por rol + "Mis ventas" ✓ · G. reportes administrativos ✓ ·
H. resto de vistas, en sub-fases: **H1 personas ✓** · H2 responsables/inscripciones
(**H2a listado ✓** · H2b detalle · H2c registro/edición — este OCUPA casetas + pasarela, alto
riesgo) · H3 credenciales · H4 boletería+control de acceso · H5 retiro de Thymeleaf.

**Listado de inscripciones (H2a) — hecho:** `InscripcionApiController` (`/api/app/inscripciones`,
`Roles.ADMINISTRA`) reutiliza `listarParaTabla()`; `Inscripciones.vue` es la vista global (todas
las inscripciones de todos los vendedores, complementa "Mis ventas"). Al construirlo salió otro
bug de datos: `findAllConTodo()` filtraba `inscripcionEstado = 'ACTIVO'`, pero esa columna vale
`PENDIENTE`/`X` en los datos reales, así que el listado —viejo y nuevo— salía **siempre vacío**;
corregido a filtrar por la baja lógica `_estado <> 'X'`. Otra confirmación de la regla: cuando un
listado sale inexplicablemente vacío, mira los **valores reales** de la columna del `where` en la
BD antes que el código.

**Personas (H1) — hecha:** `PersonaApiController` (`/api/app/personas`, solo admin) +
`GestionPersonaService`. Gestiona solo las "personas del sistema" (estado `ACTIVO`), no los 749
responsables. Una persona creada aquí queda `ACTIVO`, con lo que **aparece en el selector del
módulo Usuarios** (así se resolvió el "solo salen 5 personas"). Reemplaza el CRUD del
`PersonaController` de Thymeleaf, que estaba roto (`modificar-persona` entero comentado). Recuerda
que `_estado` en `persona` no es homogéneo: `ACTIVO` para las del sistema, pero `RESPONSABLE` /
`PROMOTOR` marcan el origen de las demás (por eso `listarPersonas` filtra `ACTIVO`).

**Bloquear/desbloquear (F4) — hecha:** `IPuestoDao.bloquearSiLibre` (`L -> X` en
`estado_puesto`, sin tocar `_estado`) y `desbloquearSiBloqueada` (`X -> L`, con `_estado <> 'X'`
para no revivir anuladas), ambas UPDATEs condicionales con `_modificacion_id_usuario`; métodos
`bloquear`/`desbloquear` en `PuestoMapaService`; `POST /api/app/puestos/{id}/bloquear|desbloquear`
en `PuestoApiController` con `@PreAuthorize(Roles.EDITA_PLANO)` que difunden por WebSocket y
responden 409 si la transición no aplica; botón "🔒 Bloquear" en la barra del Editor
(`Editor.alternarBloqueo`: bloquea las `L` y desbloquea las `X` de la selección). Caso cubierto en
`PuestoReservaConcurrenciaTest`.

**Editor copiar/pegar y rejilla (F4) — hecha:** `Editor.vue` — "📄 Copiar" (Ctrl+C) guarda la
geometría de la selección en `portapapeles`; "📋 Pegar" (Ctrl+V) duplica como casetas nuevas
reusando `POST /api/app/puestos` (código siguiente numérico por categoría) + `/posiciones`
(desplazadas 3% por pegado, sin superponerse); toggle "⧉ Rejilla" (`aRejilla`, paso 2%) alinea
colocación, arrastre en vivo, flechas y pegado. Todo local al Editor: no se tocó el backend.

Pendientes conocidos, por si el pedido roza uno:
- Filtrar las stored functions de listado por edición activa (`fn_get_inscripciones`, etc.) y
  etiquetar `venta_boleto` por edición. "Mis ventas" en Administración depende de esto.
- Fase E: empaquetar como APK con Capacitor (`references/capacitor-apk.md`).
- En producción hay que exportar `DB_PASSWORD`, `PASARELA_KEY`, `API_KEY`, `JWT_SECRET` o la app
  no arranca. Los secretos viejos están en el historial de git y **deben rotarse**. Y falta
  aplicar los scripts `V1`–`V5` a producción, en orden, en una ventana de mantenimiento.
