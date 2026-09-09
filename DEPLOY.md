# DEPLOY.md — Puesta en producción (Ubuntu)

> Runbook para levantar UniFex en un servidor Ubuntu. Complementa a `CLAUDE.md` (arquitectura)
> y `PLAN.md` (qué falta). No reemplaza a ninguno de los dos.

---

## 1. Variables de entorno

### Obligatorias — la app no arranca sin ellas (fail-fast, a propósito)

| Variable | Qué es |
|---|---|
| `DB_PASSWORD` | Contraseña de PostgreSQL (`virtual.uap.edu.bo:5432/v2_fexpo_uap`, usuario `postgres`) |
| `API_KEY` | Clave del API interno (header `X-API-KEY`, lo usa `ApiController`) |
| `JWT_SECRET` | Secreto para firmar los JWT de la SPA/APK. **Mínimo 32 caracteres** |
| `ADMIN1_PASSWORD` | Contraseña inicial de `admin1`. Solo se usa la primera vez que ese usuario se crea |
| `ADMIN2_PASSWORD` | Contraseña inicial de `admin2`, ídem |

**Por qué se agregaron `ADMIN1_PASSWORD`/`ADMIN2_PASSWORD`:** antes las contraseñas de
`admin1`/`admin2` estaban escritas directamente en `UniFexApplication.java`
(`usuario25$` / `usuario&25`), y ese archivo está commiteado en un repositorio público. Si el
primer arranque en producción encontraba la tabla de usuarios vacía, iba a crear las cuentas
administradoras con una contraseña que cualquiera puede leer en GitHub. Ahora salen de estas
dos variables y **no tienen valor por defecto**, así que hay que decidirlas antes de arrancar.

**El seed solo actúa una vez.** `UniFexApplication` busca `admin1`/`admin2` por nombre de
usuario; si ya existen, no toca la contraseña. Es decir: **la contraseña que pongas en
`ADMIN1_PASSWORD`/`ADMIN2_PASSWORD` es solo la del primer arranque.** Después de eso, cambiarla
tú mismo (por ejemplo, generando un hash BCrypt y actualizando la fila en `usuario` directamente,
que es lo que ya hace el `PasswordEncoder` de este proyecto) es seguro: en el siguiente reinicio
el seed va a ver que el usuario ya existe y no la va a pisar.

### Opcionales — la app arranca igual sin ellas

| Variable | Para qué | Si no se define |
|---|---|---|
| `PASARELA_KEY` | AccessKey de la pasarela de pago UAP | Módulo de boletería/pasarela queda inactivo |
| `PASARELA_URL` | Endpoint de la pasarela | Usa la URL real de UAP por defecto |
| `PASARELA_SUCCESS_URL` / `PASARELA_CANCEL_URL` / `PASARELA_NOTIFY_URL` | Retornos de la pasarela | Quedan vacíos |
| `UNIFEX_UPLOAD_ROOT` | Carpeta de subidas (fotos, comprobantes) | `/var/lib/unifex/uploads` |
| `UNIFEX_TMP_DIR` | Carpeta temporal de multipart | `/var/lib/unifex/tmp` |

Ver la sección 4 (pasarela) y 3 (rutas Ubuntu) para más detalle de cada una.

### Cómo ponerlas (systemd, recomendado)

Crea `/etc/unifex/unifex.env` (no lo metas en el repo ni en el `.jar`):

```ini
DB_PASSWORD=...
API_KEY=...
JWT_SECRET=...
ADMIN1_PASSWORD=...
ADMIN2_PASSWORD=...
```

Y en la unidad systemd (ver sección 5) referencíalo con `EnvironmentFile=/etc/unifex/unifex.env`.
Permisos `600`, dueño el usuario del servicio — ese archivo es tan sensible como una contraseña
en texto plano, porque lo es.

---

## 2. Migraciones de esquema

**No hay Flyway ni Liquibase** (decisión de arquitectura, ver `CLAUDE.md`). El esquema se
actualiza con los scripts numerados de `src/main/resources/db/reserva/` (`V1` a `V12`),
aplicados con `psql` en orden.

### Por qué usar el script `aplicar-migraciones.sh` en vez de `psql -f` uno por uno

Sin Flyway no hay ningún registro de qué scripts ya corrieron contra una base dada. Aplicar a
mano es fácil de hacer mal — repetir un script, saltarse uno, aplicarlos desordenados — y
varios de estos scripts **no son idempotentes** (`V7` no tiene guardas `IF NOT EXISTS`, por
ejemplo). El script agrega una tabla de control (`public._migraciones_aplicadas`) sin traer
un framework nuevo al proyecto: es la pieza mínima que le faltaba al proceso manual para ser
seguro.

**Nunca incluye `R1__reset_gestion.sql`.** Ese script *borra* casetas, entidades, inscripciones
y ventas — es una herramienta de desarrollo para "empezar de cero" **con datos**, y su propia
cabecera dice "Nunca contra producción". El script de migraciones ni lo toca.

### Servidor nuevo, base de Postgres vacía (de verdad desde cero)

Desde el 2026-09-08 esto ya no depende de ningún respaldo externo: `V0__esquema_base.sql`
tiene el esquema completo (17 tablas originales + funciones + trigger), capturado una vez de
la base real con `pg_dump --schema-only` y guardado en el repo — **sin ninguna fila de datos**.
`aplicar-migraciones.sh` lo recoge automáticamente por ser el primero en orden (`V0` antes que
`V1`). Con una base de PostgreSQL recién creada y completamente vacía:

```bash
createdb -h <host> -U postgres <tu-base-nueva>

export PGHOST=<host> PGPORT=5432 PGUSER=postgres PGDATABASE=<tu-base-nueva>
export PGPASSWORD='...'

cd src/main/resources/db/reserva
./aplicar-migraciones.sh              # aplica V0..V14 en orden, de una
```

Al terminar, arranca la aplicación normal: `UniFexApplication` crea sola los roles
`SUPER USUARIO`/`ADMINISTRADOR` y los usuarios `admin1`/`admin2` en el primer arranque (con
las contraseñas de `ADMIN1_PASSWORD`/`ADMIN2_PASSWORD`). No hace falta insertar nada a mano.

`V0` **no es idempotente** (a diferencia de `V1`...`V14`): son `CREATE TABLE`/`CREATE FUNCTION`
tal cual los emite `pg_dump`, sin guardas `IF NOT EXISTS`. Solo se puede aplicar una vez, sobre
una base realmente vacía — igual que un baseline de Flyway. Si la base ya tiene aunque sea una
de estas tablas, `V0` va a fallar; en ese caso, salta directo a la sección siguiente.

### Uso normal (base que ya tiene el esquema base, le faltan migraciones)

```bash
export PGHOST=virtual.uap.edu.bo PGPORT=5432 PGUSER=postgres PGDATABASE=v2_fexpo_uap
export PGPASSWORD='...'

cd src/main/resources/db/reserva
./aplicar-migraciones.sh              # aplica lo pendiente, en orden V0..V14
./aplicar-migraciones.sh --estado     # lista que se aplico y cuando, sin tocar nada
```

Si la base ya tenía el esquema base desde antes (como la real, `virtual.uap.edu.bo`), marca
`V0` como aplicado sin ejecutarlo antes de correr el modo normal — ver la sección siguiente.

### Si la base de producción ya tiene algunos `V*.sql` aplicados a mano de antes

Antes de correr el modo normal, marca como aplicados (sin ejecutarlos) los que ya sabes que
están, en orden:

```bash
./aplicar-migraciones.sh --marcar-aplicada V1__reserva_puesto.sql
./aplicar-migraciones.sh --marcar-aplicada V2__puesto_mapa.sql
# ... etc, solo los que confirmes que ya corrieron
```

Después de eso, el modo normal aplica únicamente lo que falte. **Si no estás seguro de qué
está aplicado, revisa el esquema a mano primero** (columnas de `V7__precio_por_categoria.sql`
en `categoria`, tabla `puesto_foto` de `V9`, etc.) — marcar algo como aplicado sin estarlo
hace que ese script nunca se corra y el esquema quede incompleto.

---

## 3. Rutas de archivos — preparado para Ubuntu

`app.upload-root` y la carpeta temporal de multipart eran rutas de Windows
(`C:/uniFex/uploads`, `C:/uniFex/tmp`) fijas en `application.properties`. Ahora tienen default
de Linux y son configurables sin tocar código:

```properties
app.upload-root=${UNIFEX_UPLOAD_ROOT:/var/lib/unifex/uploads}
spring.servlet.multipart.location=${UNIFEX_TMP_DIR:/var/lib/unifex/tmp}
```

**El perfil `dev` no cambió** — sigue con rutas `C:/uniFex/dev/...` porque la máquina de
desarrollo es Windows. Esto solo afecta al perfil por defecto (producción).

Antes del primer arranque en el servidor Ubuntu:

```bash
sudo mkdir -p /var/lib/unifex/uploads /var/lib/unifex/tmp
sudo chown -R unifex:unifex /var/lib/unifex   # o el usuario que corra el servicio
```

Si prefieres otra ruta (por ejemplo un disco separado), usa `UNIFEX_UPLOAD_ROOT`/`UNIFEX_TMP_DIR`
en vez de editar `application.properties`.

**Nota aparte, pendiente:** los reportes de JasperReports (`UtilidadesServiceImpl`) leen
`.jrxml` de una carpeta `reportes/` **relativa al directorio de trabajo del proceso**, y esos
`.jrxml` no están en el repo. Si vas a generar reportes XLSX/DOCX o credenciales por Jasper,
tienen que existir en el servidor, en `reportes/` dentro del `WorkingDirectory` que uses en el
systemd unit (sección 5). Si no los tienes, la generación de esos reportes específicos fallará
al invocarse — el resto de la app no se ve afectado. Ver también la sección 6: es posible que
esto se resuelva migrando a iText en vez de conseguir los `.jrxml`.

---

## 4. Pasarela de pago — pendiente, dejado listo para retomar

`PagoController` (`/api/pagos/crear`, `/api/pagos/notificacion`) integra con la pasarela de
pago de la UAP para la **boletería** (venta de entradas al público, `VentaBoleto`) — es un
módulo distinto de la venta de casetas. **Hoy no está conectado a la SPA ni probado contra la
pasarela real**, así que se dejó desactivado por defecto:

- Antes: `pasarela.successUrl=https://localhost:7676/venta` fijo en el archivo de producción
  — si alguna vez se llamaba de verdad, la pasarela iba a redirigir el navegador del cliente a
  *su propio* localhost, no al servidor. Es decir, estaba roto para producción aunque nadie lo
  hubiera notado porque no se usa.
- Ahora: `pasarela.url`, `pasarela.key`, `pasarela.successUrl`, `pasarela.cancelUrl` y
  `pasarela.notifyUrl` salen de variables de entorno opcionales, todas vacías por defecto. La
  app arranca igual sin definirlas; el endpoint simplemente no funciona hasta que se configuren
  (y devuelve un error claro, ya no un `NullPointerException`, ver sección 7).

**Para retomarlo cuando llegue el momento:**

```ini
PASARELA_KEY=<AccessKey real de UAP>
PASARELA_URL=https://pasarela.uap.edu.bo/v1/pago/nuevo
PASARELA_SUCCESS_URL=https://<tu-dominio>/venta
PASARELA_CANCEL_URL=https://<tu-dominio>/venta
PASARELA_NOTIFY_URL=https://<tu-dominio>/api/pagos/notificacion
```

Y falta conectar el flujo del lado de la SPA (hoy `ventaBoleteria.html` es Thymeleaf legado,
sin vista en Vue) — eso es trabajo de producto, no solo configuración. Ver PLAN.md, Bloque 8.

---

## 5. Arranque del servicio (systemd, ejemplo)

```ini
# /etc/systemd/system/unifex.service
[Unit]
Description=UniFex
After=network.target

[Service]
Type=simple
User=unifex
WorkingDirectory=/opt/unifex
EnvironmentFile=/etc/unifex/unifex.env
ExecStart=/usr/bin/java -jar /opt/unifex/uniFex-0.0.1-SNAPSHOT.jar
Restart=on-failure
RestartSec=5

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now unifex
sudo journalctl -u unifex -f   # logs
```

Requiere JDK 21 instalado en el servidor (`java -version`). El `.jar` se genera con
`./mvnw clean package` (usa el `./mvnw` de este repo, no hace falta Maven instalado aparte).

---

## 6. Reportes/credenciales — pendiente, posible cambio a iText

Hoy `UtilidadesServiceImpl` genera XLSX/DOCX (y en teoría podría credenciales) con
**JasperReports**, compilando `.jrxml` que no están en el repositorio (ver sección 3). Se
está considerando reemplazar esa vía por **iText**, que es la librería que ya se usa para los
recibos (`ReciboPdfService`) — evitaría depender de archivos externos al repo y de una
librería más. **No se tocó en esta pasada**: queda documentado como pendiente de decisión, no
como bug a resolver ahora.

---

## 7. Generador de credenciales — en revisión, con aviso en la vista

`templates/credenciales/vistaCredencialesGenerador.html` arma la credencial en el navegador
(texto + QR sobre una imagen de fondo) y depende de tres CDN externos (jsPDF, qrcodejs, Google
Fonts): **sin internet no genera nada**, y las posiciones que se ajustan a mano no se guardan
entre sesiones. Es un problema real para un evento con wifi irregular.

No se rehizo (es trabajo de producto, ver PLAN.md Bloque 3), pero la vista ahora muestra un
aviso visible de "en revisión" explicando esta limitación, para que quien la use en el evento
sepa que no debe depender de ella a ciegas. Sigue funcionando exactamente igual que antes
cuando hay internet.

---

## 8. Compilar el APK manualmente con Android Studio

La SPA se empaqueta con Capacitor sin cambiar código — es la misma app web, solo cambia a
dónde apunta. Pasos para generarlo a mano desde Android Studio (sin usar `gradlew` por línea
de comandos):

1. **Compila la SPA apuntando al servidor real** (no a `localhost`: dentro del teléfono
   `localhost` es el teléfono mismo):

   ```bash
   cd frontend
   VITE_API_BASE=https://<dominio-o-ip>:7676 npm run build
   ```

2. **Sincroniza el build con el proyecto Android:**

   ```bash
   npx cap sync android
   ```

   Repite los pasos 1 y 2 cada vez que cambie el frontend — si se te olvida, el APK muestra la
   versión anterior.

3. **Abre el proyecto en Android Studio:**

   ```bash
   npx cap open android
   ```

   O directamente desde Android Studio: `File → Open...` → selecciona la carpeta
   `frontend/android`. La primera vez, Android Studio crea `android/local.properties` con la
   ruta de tu SDK — ese archivo no está en git, es específico de cada máquina.

4. **Generar el APK desde el menú:**
   - `Build → Generate Signed Bundle / APK...`
   - Elige **APK** (no App Bundle, salvo que vayas a publicar en Play Store)
   - Para depuración: puedes cancelar el firmado y usar `Build → Build Bundle(s)/APK(s) → Build APK(s)` en su lugar — sale sin firmar, para instalar con `adb` o directo en el teléfono.
   - Para distribución: crea o selecciona un **keystore** (`Create new...` si es la primera
     vez) y guárdalo **fuera del repositorio**, en un lugar seguro — perderlo significa no
     poder volver a firmar actualizaciones de la misma app.

5. **El APK queda en** `android/app/build/outputs/apk/debug/app-debug.apk` (o `release/` si
   firmaste). Instálalo con `adb install -r <ruta.apk>` o copiándolo al teléfono.

**Antes de compilar, verifica (ya está preparado en el repo, solo para tu referencia):**

- `android/variables.gradle` tiene `compileSdkVersion = 36` fijo — necesario en máquinas sin
  `cmdline-tools` instalado (Gradle no puede bajar el SDK 35 que Capacitor pide por defecto).
- `capacitor.config.json` tiene `cleartext: true` — permite HTTP plano, útil para probar en
  red local. **Para producción real, sirve el backend por HTTPS y quita esa línea.**
- El backend ya acepta el origen del APK vía CORS (`SecurityConfig.corsApi()`). Si cambias el
  esquema o dominio del backend, hay que agregarlo ahí.

Detalle completo (por qué hace falta `VITE_API_BASE`, cómo probar que funciona, qué aporta el
APK sobre la web) en `frontend/APK.md`.

---

## 9. Checklist antes de dar por lista la puesta en producción

- [ ] `ADMIN1_PASSWORD` / `ADMIN2_PASSWORD` definidas con contraseñas propias, no las del repo
- [ ] `DB_PASSWORD`, `API_KEY`, `JWT_SECRET` definidas (JWT_SECRET ≥ 32 caracteres)
- [ ] `/var/lib/unifex/uploads` y `/var/lib/unifex/tmp` creados, con permisos del usuario del servicio
- [ ] Migraciones aplicadas con `aplicar-migraciones.sh` (o marcadas si ya estaban) y verificadas con `--estado`
- [ ] `mvnw clean package` corrido con JDK 21, `.jar` copiado al servidor
- [ ] Servicio systemd habilitado y arrancando sin errores (`journalctl -u unifex`)
- [ ] Probado en el navegador: login, mapa en tiempo real (dos pestañas), venta completa, recibo PDF
- [ ] Pendiente a propósito, no bloquea el lanzamiento: pasarela de pago (sección 4), reportes Jasper (sección 6), generador de credenciales (sección 7), APK en dispositivo real (sección 8), boletería/control de acceso (ver PLAN.md)
