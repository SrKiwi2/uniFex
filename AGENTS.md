# AGENTS.md

UniFex: Spring Boot 3.5.5 / **Java 21** app for the UAP institutional fair (FEXPO). PostgreSQL. All code, routes, DB columns, and comments are in **Spanish** — match that.

## Read these first

- `CLAUDE.md` — full architecture reference (auth, controllers, layers, payments). Keep it as source of truth.
- `PLAN.md` — living roadmap: what's done, decisions taken (e.g. rol `ADMINISTRATIVO` labeled «Vendedor», carrito = la propia reserva, no columna de estado de pago), and known bugs. Check it before feature work.
- Skill **`unifex-fullstack`** (`.claude/skills/`) — before touching booths/puestos, map, reservations, WebSocket, `/api/app`, Vue views, or any new endpoint. Holds the 3 invariants that prevent double-selling a booth.
- Skill **`unifex-arquitectura`** (`.claude/skills/`) — **before deleting/renaming anything**: a symbol with no Java references is often live from a Thymeleaf template, a SQL string, or same-package resolution. The compiler won't warn you.
- **`README.md` is aspirational and wrong** (wrong package, port, Java version). Ignore it.

## Verified facts that contradict intuition

- Real package: `com.usic.uniFex`. App serves on **port 7676**. Build with a JDK 21 or you get `UnsupportedClassVersionError`.
- Both Spring profiles set `hbm2ddl=none` — schema changes are hand-applied numbered SQL in `src/main/resources/db/reserva/` (`V1`…`V9`, plus `R1__reset_gestion.sql`), **no Flyway/Liquibase**. Scripts must stay idempotent (conditional renames/creates — quoted column names broke V9 once; Hibernate splits them to snake_case).
- Default profile points at the **production** DB (`virtual.uap.edu.bo:5432/v2_fexpo_uap`) and reads secrets (`DB_PASSWORD`, `PASARELA_KEY`, `API_KEY`, `JWT_SECRET`) from env vars with **no defaults** — it won't boot without them and must not be run carelessly. Always develop with the `dev` profile (`application-dev.properties` has all secrets baked in locally, so no env vars needed).
- Much business logic (pricing, availability, inscription details) lives in **PostgreSQL stored functions** called from `model/repository/FuncionesInscripcion` and `FuncionesApi` — check the DB functions when logic seems missing in Java.
- **Two identical `IServiceGenerico` exist and both are live**: 12 interfaces resolve the one in `model/IService/` by same-package (no import); 4 explicitly import the one in `model/service/`. Deleting either breaks the build — consolidate, don't delete.
- Roles are only `SUPER USUARIO`, `ADMINISTRADOR`, `ADMINISTRATIVO`, `CONTROL`, `ASESORIA` — **no `VENDEDOR`**. Authorities are `ROLE_<rol uppercased, spaces→underscores>` via `JwtUser.rolNormalizado()`; use `security/Roles` constants in `@PreAuthorize`.
- `@PreAuthorize` is silently ignored without `@EnableMethodSecurity` (it's on `SecurityConfig`). A 302 where you expected a 401 = `sendError` forwarding to `/error` (chain 2 redirects to login); 401/403 handlers write JSON by hand with `setStatus`.
- `@ValidarUsuarioAutenticado` IS enforced (`Config/AutenticacionInterceptor`).
- WebSocket is real: STOMP `/ws`, topic `/topic/puestos`, auth in the CONNECT frame (not handshake). Map pattern: optimistic local write + broadcast as source of truth, never re-`GET` the list after a write.

## Commands (Windows: use `mvnw.cmd`)

- Run dev: `mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"` → http://localhost:7676 (Thymeleaf templates auto-reload, cache off)
- Build: `mvnw.cmd clean install`
- All tests: `mvnw.cmd test` · single: `mvnw.cmd test -Dtest=PuestoReservaConcurrenciaTest`
- SPA dev: `npm --prefix frontend run dev` → http://localhost:5173 (proxies `/api`, `/ws`, `/files` to 7676)
- SPA tests (Node only, no DB): `npm --prefix frontend test`
- API smoke tests: `node .claude/skills/unifex-fullstack/scripts/verificar-api.mjs` and `verificar-venta.mjs` (full sales circuit: login → catálogo → carrito → venta → recibo → comprobante)
- **No linter/formatter configured.** No Python on this machine — helper scripts are Node.

## Structure

- `src/main/resources/templates/` — legacy Thymeleaf site (still sells booths); `static/assets/` is a huge purchased theme only it uses.
- `frontend/` — Vue 3 + Vite + Pinia SPA. Key files: `src/api.js` (Bearer token, 401→logout), `src/ws.js` (STOMP), `src/stores/auth.js` (JWT in localStorage), `src/views/Mapa.vue` (pins at normalized 0..1 coords `puesto.mapa_x/y`), `src/views/Editor.vue`, `src/components/PanZoom.vue`. `vite.config.js` sets `host: true` and proxies to explicit `127.0.0.1:7676` — deliberate IPv4/IPv6 fix, don't revert (localhost-only was a ~2 s stall on every new connection).
- Auth: chain 1 (`/api/auth/**`, `/api/app/**`) = stateless JWT; chain 2 = session formLogin with nearly `permitAll`. REST `/api` uses `X-API-KEY` header, unrelated to `/api/app`.
- Receipts: iText in-code (`ReciboPdfService`). Credentials/XLSX/DOCX: JasperReports `.jrxml` compiled at runtime from a `reportes/` dir **relative to process CWD, not in the repo** — missing on disk ⇒ report failure.
- Uploads under `app.upload-root` (absolute path, must exist), served at `/files/**`.

## Testing quirks

- 5 test classes, all `@SpringBootTest` + `@ActiveProfiles("dev")` against the **local** PostgreSQL copy (must be running): `UniFexApplicationTests` (context load), `PuestoReservaConcurrenciaTest` (threads race one booth; exactly one wins), `RegistroVentaTest` (atomic sale rollback), `PuestoFotoTest`, `PuestoAnulacionTest` (skips 1 case when no historical sales). 21 tests, currently all green. They create/clean up their own rows.
- Frontend store tests (`pruebas/*.test.mjs`) run with `npm --prefix frontend test` — Node only, no DB, no backend.
