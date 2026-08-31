# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

UniFex is a Spring Boot 3.5.5 / Java 21 web app for running an institutional fair (FEXPO at UAP). It manages exhibitor entities (`Entidad`), their responsibles (`Responsable`/`Persona`), registrations (`Inscripcion`), fair booths (`Puesto`), ticket sales (`VentaBoleto` / "boletería"), badge credentials, on-site access control, and payments through the UAP gateway. Data is PostgreSQL.

**Two frontends coexist.** The original server-rendered Thymeleaf site (`resources/templates/`) still sells booths, and a newer **Vue 3 + Vite SPA** lives in `frontend/`. They are being migrated gradually; see "Frontend" below.

**The README is aspirational and does not match the code.** Ignore it for architecture. Specifically: the real package is `com.usic.uniFex` (not `com.unifex`); the project targets **Java 21** (pom `java.version`/`release` = 21; build with a JDK 21 or the class files won't run — a version mismatch here gives `UnsupportedClassVersionError`); the app serves on **port 7676** (not 8080); and much business logic lives in PostgreSQL stored functions, not Java. The whole codebase (class names, routes, DB columns, comments) is in **Spanish** — match that when adding code.

**There IS a WebSocket** (`Config/WebSocketConfig`, STOMP endpoint `/ws`, topic `/topic/puestos`). Every booth state change is broadcast through `PuestoEventPublisher` so the live map recolors in every client. Older notes claiming otherwise are wrong.

**`PLAN.md` is the living work plan** (in Spanish, phase-by-phase checkboxes). Check it for what is in flight and what is deliberately frozen — notably: everything new goes to the SPA, and the Thymeleaf site is frozen until it is retired. `AGENTS.md` is a condensed version of this file for other agents; if you change a fact here, change it there too.

## Skills

Two project skills encode the hard-won invariants. Read them before working:

- **`.claude/skills/unifex-fullstack/`** — building features end-to-end (booths, map, reservations, real-time, API, Vue). Contains the three invariants that prevent double-selling a booth.
- **`.claude/skills/unifex-arquitectura/`** — refactoring, renaming, deleting dead code. **Read before deleting anything**: in this repo a symbol with no Java references is often live from a Thymeleaf template, a SQL string, a same-package resolution, or a `.jrxml` that isn't even in the repo. The compiler will not warn you.

**No Python on this machine** (only the empty Microsoft Store aliases). Write helper scripts in Node.

## Commands

Use the Maven wrapper. On this Windows machine use `mvnw.cmd`; the POSIX `./mvnw` also works via the Bash tool.

- Build: `mvnw.cmd clean install`
- **Run (dev, always use this)**: `mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"` → http://localhost:7676
- Run packaged: `java -jar target/uniFex-0.0.1-SNAPSHOT.jar`
- All tests: `mvnw.cmd test`
- Single test: `mvnw.cmd test -Dtest=PuestoReservaConcurrenciaTest` (or `-Dtest=ClassName#method`)
- SPA dev server: `npm --prefix frontend run dev` → http://localhost:5173 (proxies `/api`, `/ws` and `/files` to 7676, so there is no CORS in dev)
- SPA production build: `npm --prefix frontend run build`
- API smoke test: `node .claude/skills/unifex-fullstack/scripts/verificar-api.mjs`

Three tests: `UniFexApplicationTests` (empty context load), `PuestoReservaConcurrenciaTest` (25 threads race for one booth; exactly one wins) and `PuestoAnulacionTest` (logical deletion / blocking rules). They need the dev database up. There is no linter/formatter configured.

## Configuration

`src/main/resources/application.properties` is the production profile. Secrets are **not committed**: `DB_PASSWORD`, `PASARELA_KEY`, `API_KEY` and `JWT_SECRET` are read from environment variables with **no defaults**, so production won't boot without them. (The old committed secrets are still in git history and should be rotated.)

It points at the **production** database (`virtual.uap.edu.bo:5432/v2_fexpo_uap`) — never run the default profile while developing. Both profiles now set `hbm2ddl.auto=none`, so Hibernate never alters the schema on its own.

`src/main/resources/application-dev.properties` (profile `dev`) targets a local PostgreSQL 16 copy on `localhost`, carries local secret values, and uses upload dirs under `C:/uniFex/dev`.

Schema changes are hand-applied numbered SQL scripts in `src/main/resources/db/reserva/` (`V1`…`V6`) — **there is no Flyway or Liquibase**. Apply them yourself, in order, and add the next `V<n>__nombre.sql` for new schema work.

`app.upload-root=C:/uniFex/uploads` and multipart temp dir `C:/uniFex/tmp` are absolute Windows paths that must exist.

Reservation tuning lives here too: `unifex.reserva.ttl-segundos` (default 300) and `unifex.reserva.barrido-ms` (default 30000); JWT: `unifex.jwt.secret` / `unifex.jwt.expiration-ms` (24 h).

`UniFexApplication` has `@EnableScheduling` (required by the reservation sweeper) and an `ApplicationRunner` that **seeds roles and the `admin1`/`admin2` users with hardcoded passwords on every boot** — including production, where it will recreate them if missing.

## Architecture

Everything is under `com.usic.uniFex`. Layering is a classic controller → service-interface → service-impl → DAO stack, plus a parallel hand-written stored-function layer.

- **`controller/`** — `@Controller` (Thymeleaf) and `@RestController` classes grouped by feature subpackage (`admin`, `administracion`, `api`, `auth`, `credencialesController`, `edicion`, `inscripcion`, `login`, `pasarela`, `persona`, `puesto`, `responsables`, `usuario`, `venta`). Note `LoginController` is empty — the actual login handler `POST /iniciar-sesion` lives in `admin/AdminController`, which is the largest controller and also holds the main registration flow (`/guardar`).
- **`model/entity/`** — JPA entities. Most extend `Config/AuditoriaConfig` (`@MappedSuperclass`) which adds underscore-prefixed audit columns (`_fecha_registro`, `_registro_idUsuario`, `_modificacion_idUsuario`, `_estado`). Despite `@CreatedDate`/`@CreatedBy` annotations, JPA auditing is **not** enabled — controllers set `registro`/`modificacion`/`estado` fields manually on every save. `Edicion` is the exception: its table has no audit columns, so it does not extend `AuditoriaConfig`.
- **`model/dao/`** — Spring Data `I*Dao extends JpaRepository<Entity, Long>`, with `@Query` (JPQL) and `@EntityGraph` for eager loads. `IPuestoDao` is special: it holds the conditional-`UPDATE` state machine (see below) as native queries.
- **`model/IService/`** — service interfaces; most extend the generic `IServiceGenerico<T, K>` (`findAll`/`findById`/`save`/`deleteById`). **There are two identical `IServiceGenerico`, and both are live**: 12 interfaces extend the one in `model/IService/` by same-package resolution (no `import`, so grepping for imports finds nothing and it looks dead), and 4 explicitly import the one in `model/service/` (`ICargoService`, `ICategoriaVentaService`, `IOficinaService`, `IVentaBoletoService`). Deleting either without fixing its users breaks the build. Consolidate, don't delete.
- **`model/IServiceImp/`** — `*ServiceImpl` implementations (thin wrappers over DAOs).
- **`model/repository/`** — `FuncionesInscripcion` and `FuncionesApi`: hand-written `JdbcTemplate` calls to **PostgreSQL stored functions** in the `public.` schema (e.g. `fn_lista_puestos()`, `obtenercostopuesto(...)`, `fn_get_inscripciones(...)`). Pricing, booth availability, and inscription detail logic live in the database, not in Java — check the DB functions when this logic seems missing.
- **`model/service/`** — the real business layer for everything built after the Thymeleaf era (see "Booth domain" below), plus cross-cutting services: `FileStorageService` (uploads, `Bucket` enum → `year/month` subfolders) and `ReciboPdfService` (PDF receipts built programmatically with **iText**, incl. QR codes).
- **`model/dto/`** — DTOs and read-only view projections (`*View`, `*DTO`).
- **`Config/`** — `SecurityConfig`, `WebConfig`, `WebSocketConfig`, `AutenticacionInterceptor`, `AuditoriaConfig`, `Encriptar`, `TomcatUploadConfig`.
- **`security/`** — `JwtService`, `JwtAuthFilter`, `JwtUser`, `Roles`.
- **`anotacion/`** — the `@ValidarUsuarioAutenticado` annotation (see below).

## Booth domain (`Puesto`) — where the money is

This is the part of the system that must not be gotten wrong, because a bug here sells the same booth twice.

**Two independent state columns, and they are not interchangeable:**

- `estado_puesto` — the sales state machine: `L` libre, `T` en trámite (temporarily reserved), `O` ocupado (sold), `X` bloqueado (out of service / annulled). Constants on `Puesto`.
- `_estado` — the audit column: `'A'` alive, `'X'` logically deleted. **`_estado` does not mean the same thing in every table**: in `usuario` and `inscripcion` it holds `"ACTIVO"`, in `persona` it holds a *type* (`"RESPONSABLE"`, `"PROMOTOR"`, `"ACTIVO"`). The only transversal rule is that `'X'` marks something annulled. Never copy `"ACTIVO"` into `puesto`.

Annulling a booth sets **both** (`_estado='X'` and `estado_puesto='X'`) because the legacy Thymeleaf site filters on `estado_puesto` via `fn_lista_puestos`, not on `_estado`. Blocking for repair sets *only* `estado_puesto='X'`, so the booth stays alive and can be unblocked — the `_estado <> 'X'` guard on `desbloquearSiBloqueada` is what stops an annulled booth from returning to sale.

**Every transition is a conditional `UPDATE` that returns an affected-row count**, never a read-then-write. `reservarSiLibre` is `... WHERE id = ? AND estado_puesto = 'L'`: PostgreSQL serializes the row, the first caller gets 1 row, the loser gets 0 and is told "no disponible". This — not Java locking — is the anti-double-sale guarantee. `Puesto.version` is a JPA `@Version` on top of it. Rows are never physically deleted: `inscripcion_puesto` references them, so annulled booths keep their historical sales.

The services in `model/service/`:

- **`PuestoReservaService`** — `reservar` (L→T), `liberar` (T→L, owner only), `confirmar` (T→O, owner only), `ocupar` (L→O or own T→O, used by the one-shot `/guardar` flow; joins the caller's transaction so a later failure rolls the occupation back), `liberarVencidas`.
- **`PuestoReservaScheduler`** — `@Scheduled` sweeper that expires stale `T` reservations and publishes each freed booth.
- **`PuestoEventPublisher`** — the single publication point for `/topic/puestos`. Reads inside a read-only transaction to resolve the lazy `categoria`, and swallows broadcast failures so a WebSocket problem never fails the sale.
- **`PuestoMapaService`** / **`CategoriaMapaService`** — the plan editor: batch positions, resize, create/annul booths, and category CRUD (colour, shape, size, quantity adjustment). A category can only be removed once `contarNoEliminablesDeCategoria` returns 0.
- **`GestionUsuarioService`** / **`GestionPersonaService`** — the API-side CRUD for users and system people, with the validations the old Thymeleaf controllers never had (`PersonaController.modificar-persona` was entirely commented out). User `_estado` is `ACTIVO` / `INACTIVO` / `ELIMINADO`; logical deletion only.

**`Edicion`** (fair edition: FEXPO 2025, 2026…) tags inscriptions and ticket sales (`inscripcion.id_edicion`, `venta_boleto.id_edicion`); listings filter by the `activa` edition — see `V6__edicion_filtros.sql` and `fn_get_inscripciones`. When adding a listing, filter by edition or it will show every year at once.

## Authentication (read before touching anything security-related)

`SecurityConfig` defines **two filter chains**, and which one handles a request decides how it authenticates:

**Chain 1 — `@Order(1)`, `securityMatcher("/api/auth/**", "/api/app/**")`** — the SPA / mobile API. Stateless, CSRF off, **JWT**. `POST /api/auth/login` returns a token (`security/JwtService`, HS256, 24 h). `security/JwtAuthFilter` reads `Authorization: Bearer`, validates it, and puts a `JwtUser` principal plus a `ROLE_<rol>` authority in the `SecurityContext`. Controllers get the user from `SecurityContextHolder`, never from the session.

Two things here are load-bearing and look wrong if you don't know why:
- The matchers are `AntPathRequestMatcher.antMatcher(...)` inside an `OrRequestMatcher`. The default `MvcRequestMatcher` with `/**` did **not** match `GET /api/app/puestos` (3 segments) though it matched deeper paths.
- The 401/403 handlers use `setStatus` + write the JSON body by hand. `sendError` forwards to `/error`, which chain 2 turns into a **302 to the login page**. A 302 where you expected a 401 is almost always this.

Chain 1 is `.anyRequest().authenticated()` plus `@PreAuthorize` on the endpoints that need a role. `@EnableMethodSecurity` is on `SecurityConfig` — **without it `@PreAuthorize` is silently ignored** and the endpoint stays open.

`/ws` authenticates in the STOMP `CONNECT` frame (`WebSocketConfig`'s `ChannelInterceptor`), not in the HTTP handshake, because SockJS can't always sign the handshake. Clients pass the token via `connectHeaders`.

**Chain 2 — `@Order(2)`** — the Thymeleaf site. Session-based, `formLogin(loginPage("/"))`, CSRF off, and a `permitAll()` list covering nearly every route, so Spring Security enforces almost nothing on web pages.

- `POST /iniciar-sesion` (in `AdminController`) looks up the user, verifies the password with the `BCryptPasswordEncoder` bean, and stores `usuario` / `persona` / `nombre_rol` in the `HttpSession`. Protected controllers read `request.getSession().getAttribute("usuario")` and will NPE if it's absent.
- **`@ValidarUsuarioAutenticado` IS enforced** — `Config/AutenticacionInterceptor`, registered in `WebConfig`, reads it and redirects to `/` when there is no session user. Seven controllers rely on it. (It used to be an inert marker; that is no longer true.)
- The REST API under `/api` (`ApiController`) authenticates by comparing the `X-API-KEY` header to the `api.key` property manually. It is unrelated to `/api/app`.

### Roles (check the `rol` table, not your intuition)

The only roles that exist are **`SUPER USUARIO`**, **`ADMINISTRADOR`**, **`ADMINISTRATIVO`**, **`CONTROL`** and **`ASESORIA`**. There is **no `VENDEDOR` role** — the `else if ("VENDEDOR".equals(rol))` branch in `AdminController` is dead code.

The 35 `ADMINISTRATIVO` users are the sellers ("vendedores"): they sell booths but must not redesign the plan. `admin1` is `SUPER USUARIO`.

Authorities arrive as `ROLE_<rol uppercased, spaces → underscores>`, so `SUPER USUARIO` → `ROLE_SUPER_USUARIO`. That normalization lives in exactly one place, `JwtUser.rolNormalizado()`. Authorization expressions live in `security/Roles` (`EDITA_PLANO`, `GESTIONA_USUARIOS`, `ADMINISTRA` — same roles today, declared separately so they can diverge later). Use `@PreAuthorize(Roles.EDITA_PLANO)` rather than spelling role names into each controller.

### `/api/app` surface

`puestos` (list, reservar/liberar/confirmar, and the `EDITA_PLANO`-gated posiciones/create/delete/bloquear/desbloquear), `categorias`, `inscripciones`, `personas`, `usuarios`, `mis-ventas`, `ediciones`, `reportes` (whole controller gated on `GESTIONA_USUARIOS`: resumen, por-categoria, por-entidad).

## Files, reports, and payments

- **Uploads** live under `app.upload-root` and are served back at `/files/**` via a resource handler in `WebConfig`.
- **Receipts** → iText, in-code, in `ReciboPdfService`.
- **Other reports** (credentials, XLSX, DOCX) → **JasperReports** in `IServiceImp/UtilidadesServiceImpl`, which compiles `.jrxml` at runtime read from a `reportes/` directory resolved **relative to the process working directory**. These templates are **not in the repo** and must exist on disk where the app runs, or report generation fails.
- **Payments** → UAP gateway (`pasarela.*` properties) via `pasarela/PagoController` and `PagoPasarelaService`.

## Frontend

**Legacy (Thymeleaf), frozen.** Templates in `resources/templates/`, grouped by feature, sharing fragments in `layout/` (`head`, `topbar`, `sidebar`, `footer`, `script`) and `fragments/alerts.html`. `static/assets/` is a large purchased Bootstrap admin theme (Sneat-style, ~52 MB, 58 vendor libs) — most of it is unused boilerplate, but only this site consumes it, so purge it when Thymeleaf is retired, not before. `spring.thymeleaf.cache=false` is set, so template edits show up without a restart. Per `PLAN.md`, **add no new features here.**

**Current (Vue SPA).** `frontend/` — Vue 3 + Vite + Pinia + vue-router, no UI framework. Key files:

- `src/api.js` — `apiFetch()` adds the `Bearer` token and logs out on 401.
- `src/ws.js` — STOMP over SockJS, subscribes to `/topic/puestos`.
- `src/stores/auth.js` — JWT in `localStorage`, exposes `autenticado` / `puedeEditarPlano`.
- `src/router.js` — `/login` standalone; everything else nests under `components/AppLayout.vue`, which renders the menu/header/footer **once** with `<router-view/>` inside. All 10 views are lazy `import()`ed. Route `meta`: `requiereAuth`, `editaPlano` (hides admin tools — the backend still returns 403, this is only convenience), `inmersivo` (full-bleed, for Mapa and Editor), `titulo`.
- `src/views/Mapa.vue` — the live sales map: booth pins over `public/mapa.png`, positioned by **normalized 0..1 coordinates** (`puesto.mapa_x` / `mapa_y`, with `mapa_escala` as a per-booth multiplier over the category size), recolored by state via WebSocket, click to reserve/release.
- `src/views/Editor.vue` — the layout designer: place, move, resize, and create categories on the plan.
- Other views: `Inicio`, `MisVentas`, `Board` (tablero), `Usuarios`, `Personas`, `Reportes`, `Inscripciones`, `Login`.
- `src/components/PanZoom.vue` — pointer-events pan/zoom (mouse + touch), shared by Mapa and Editor. `ToastHost.vue` / `UiModal.vue` / `BarrasHorizontales.vue` are the shared UI primitives; `src/ui/toast.js` and `src/ui/tema.js` back them.

The map's update pattern is: **optimistic local write + broadcast as source of truth**. Never re-`GET` the whole list after a write. Mapa, Editor and Board are wrapped in `<KeepAlive>`, so they keep state across navigation — a view that assumes it remounts on every visit will be wrong.

Eventually the SPA will be packaged as an Android APK with Capacitor (not started).
