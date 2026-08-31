# PLAN.md — Plan de trabajo UniFex

> Documento vivo: cada tarea es un checkbox. Se tacha `[x]` cuando está terminada y
> verificada. Todo en español, como el resto del proyecto.

**Cómo usar:** `- [ ]` pendiente · `- [x]` hecho. Una tarea solo se marca hecha si se
probó (navegó, ejecutó, comprobó) — no por intención.

**Actualizado el 2026-08-11** con las decisiones del usuario y el flujo de venta definido.

---

## Visión del producto

Sistema de **venta y control de venta de casetas** para ferias y, en general, para
**cualquier actividad con puestos asignables**. Es una boletería de puestos: un plano
con inventario, vendedores que colocan, y control de recaudación.

**Dos clientes con la misma capacidad**: web (SPA Vue) y **APK Android** (Capacitor).
Decisión tomada: el vendedor **registra la inscripción completa desde ambos**, no solo
reserva desde el móvil.

### El flujo de venta (definido por el usuario, 2026-08-11)

1. El vendedor entra con sus credenciales.
2. Va al mapa y ve las casetas en tiempo real.
3. **Selecciona una o varias casetas** → quedan `EN TRÁMITE` y **los demás vendedores lo
   ven al instante**, para que nadie las revenda.
4. Al tocar una caseta puede ver **sus fotos reales** y su ubicación, para enseñarle al
   cliente cómo es en el lugar.
5. Carga los datos del cliente: entidad, **titular** y **un acompañante**, con foto,
   CI y contacto.
6. Adjunta o **saca foto del comprobante de pago**, o lo deja **pendiente marcado como
   urgente** si el cliente aún no pagó pero ya se compromete.
7. Al cerrar el registro la caseta pasa a **VENDIDA** y el mapa de todos se recolorea.
8. Se emite **recibo de inscripción y venta**.
9. Más adelante, con los inscritos ya cargados, se **generan sus credenciales**.

### Perfiles de usuario

| Perfil | Rol real | Qué hace |
|---|---|---|
| Administrador | `SUPER USUARIO`, `ADMINISTRADOR` | Control total. **Únicos que editan el plano.** Usuarios, reportes globales, contabilidad. |
| Vendedor | `ADMINISTRATIVO` | Vende, registra inscripciones, gestiona **sus** ventas y sus pendientes. Web + APK. |
| Control / Asesoría | `CONTROL`, `ASESORIA` | Por definir: reportes, credenciales, control de acceso. |

> **Decisión tomada:** el rol sigue siendo `ADMINISTRATIVO` en la base de datos; solo se
> **etiqueta «Vendedor» en pantalla**. No se toca la tabla `rol` ni se migran los usuarios.
> Motivo: `RESPONSABLE` ya está ocupado como valor de `persona._estado` para el contacto
> de la entidad expositora, y renombrar roles obliga a revisar cada `@PreAuthorize`.

## Reglas de trabajo

- Todo lo nuevo va a la **SPA (5173)**. El sitio Thymeleaf queda congelado.
- Backend: Java 21, perfil `dev`, rutas y comentarios en español.
- Antes de tocar casetas/mapa/reservas: leer skill `unifex-fullstack` (3 invariantes).
- Antes de borrar/renombrar: leer skill `unifex-arquitectura`.
- Toda transición de estado es un **UPDATE condicional**; toda escritura exitosa **se
  difunde por WebSocket**. Son los invariantes que impiden la doble venta.

---

## Lo que YA existe (no hay que construirlo)

Buena noticia de la auditoría: gran parte de lo que se pidió tiene infraestructura hecha.

| Necesidad | Ya existe | Dónde |
|---|---|---|
| Ver casetas en trámite en vivo | **Sí** — es el estado `T` + broadcast | `PuestoReservaService`, `/topic/puestos` |
| Evitar reventa | **Sí, garantizado** — UPDATE condicional en BD | `IPuestoDao.reservarSiLibre` |
| Foto de persona | **Sí** — campo `foto` | `Persona.foto` |
| Comprobante de pago | **Sí** — imagen + banco + nº + contado | `Inscripcion.imgComprobante`, `entidadBancaria`, `numComprobante`, `pagoContado` |
| Subida de archivos | **Sí** — con buckets por año/mes | `FileStorageService` (`COMPROBANTES`, `RESPONSABLES`) |
| Varios responsables por entidad | **Sí** — relación N a N | `Entidad.responsables` → `Responsable` |
| Recibo PDF con QR | **Sí** — generado con iText | `ReciboPdfService` |
| Ediciones (multi-evento) | **Sí, la base** | `Edicion` + scripts `V3`/`V6` |

## Lo que FALTA de verdad

| Hueco | Por qué importa | Coste |
|---|---|---|
| **Alta/edición de inscripción por API** | Sin esto la SPA no vende y el APK no existe | Alto |
| **Carrito de varias casetas** | Hoy se reserva de una en una; el flujo pide seleccionar 4 | Medio |
| **TTL de reserva vs. formulario** | La reserva vence a los **300 s** — se caerá mientras el vendedor llena datos y sube fotos | Bajo |
| **Fotos de la caseta** | `Puesto` **no tiene** campo de foto. Es el único cambio de esquema realmente nuevo | Medio |
| **Titular vs. acompañante** | `Responsable` no distingue quién es el dueño ni limita a 2 | Bajo |
| **Estado de pago pendiente/urgente** | Hay que poder vender con pago pendiente y listarlo aparte | Bajo |
| **Credenciales sin internet** | Ver aviso abajo | Medio |
| **Plano por edición** | `mapa.png` está fijo en la SPA: bloquea usar el sistema en otra actividad | Medio |

### ⚠️ Revisión rigurosa del módulo de credenciales (pedida por el usuario)

Está **más incompleto de lo que parece**, y tiene un defecto que rompe en la feria:

- `generadorCredenciales` (48 líneas) **no genera nada**: solo devuelve dos vistas
  Thymeleaf, un listado y un modal de vista previa.
- La credencial se arma **entera en el navegador**, en
  `templates/credenciales/vistaCredencialesGenerador.html`: superpone texto y un QR
  sobre la imagen de fondo `assets/CREDENCIAL_4.jpg`, con posicionamiento **arrastrando
  con el mouse**, y exporta con jsPDF.
- 🔴 **Depende de tres CDN externos**: jsPDF y qrcodejs desde `cdnjs.cloudflare.com` y
  las fuentes desde Google Fonts. **Sin internet no se genera ninguna credencial** — y
  el wifi de la feria es irregular. En un APK offline directamente no funciona.
- Las posiciones que se ajustan arrastrando **no se guardan**: hay que recolocarlas cada
  sesión.
- La imagen de fondo está **fija por edición** (`CREDENCIAL_4.jpg`), no es configurable.
- Jasper (`UtilidadesServiceImpl`) **no participa**: las credenciales no usan `.jrxml`.
- Deuda menor: llama a `obtener_inscripcion_detalle` **dos veces** (una solo para un
  `System.out.println` del tamaño), duplicando la consulta.

**Conclusión:** al migrarlo hay que **traer las librerías al bundle** (no CDN), **guardar
la plantilla y las posiciones en la base**, y hacer la imagen de fondo configurable por
edición. Es rehacerlo, no moverlo.

---

## Decisiones de diseño que hay que tomar antes de codificar el Bloque 1

Salen del flujo que definiste y cambian el modelo de datos:

1. **La reserva vence a los 5 minutos.** Llenar entidad + 2 personas + fotos + comprobante
   tarda más. Opciones: (a) subir el TTL solo mientras hay un registro abierto;
   (b) renovar la reserva con un latido desde el formulario; (c) crear la inscripción en
   estado **borrador** al seleccionar, y que la reserva viva mientras el borrador viva.
   → **Recomiendo (c)**: es la que además permite retomar una venta a medias.
2. **¿Pago pendiente ocupa la caseta?** Dijiste que al terminar el registro la caseta sale
   vendida. Entonces la caseta pasa a `O` aunque el comprobante falte, y lo pendiente se
   marca **en la inscripción**, no en la caseta. Hay que definir **qué pasa si nunca paga**:
   ¿la libera un administrador a mano? ¿hay plazo?
3. **Titular y acompañante**: se permite el dueño **y uno más**. Hay que decidir si el
   límite es duro (el sistema lo impide) o blando (avisa).

---

## PLAN DE EJECUCIÓN — en este orden

### Bloque 0 — Correcciones medidas · **HECHO** (2026-08-11)

- [x] **Plano optimizado**: de **1 268 860 B a 338 781 B (−73,3 %)**. No se usó WebP —
      no hay codificador en esta máquina— sino **PNG indexado de 256 colores**, que para un
      plano CAD es mejor: JPEG habría emborronado el texto de 8 px. Se mantuvo la resolución
      (1836×2376) a propósito, porque el vendedor amplía el plano para enseñárselo al cliente.
      Herramienta reutilizable en `frontend/herramientas/indexar-png.mjs`; original guardado
      en `frontend/imagen-fuente/` (fuera de `public/`, no se publica)
- [x] **Fuga de WebSocket corregida**: `Mapa.vue` y `Board.vue` cerraban en `onUnmounted`,
      que nunca se dispara bajo `<KeepAlive>`. Ahora la conexión la suelta `AppLayout`, que
      se desmonta exactamente al cerrar sesión
- [x] **Store compartido `stores/puestos.js`**: una sola descarga de las 532 casetas y **una
      sola conexión** para toda la app. Las cargas concurrentes comparten la misma petición
- [x] **SockJS retirado** (front + `WebSocketConfig`): el chunk de tiempo real bajó de
      **68 KB a 24,5 KB**. Se corrigió de paso el motivo documentado de autenticar en el
      frame CONNECT: no era «por SockJS» sino porque la API WebSocket del navegador no
      admite cabeceras en el handshake — sigue siendo necesario
- [x] **Editor con tiempo real**, que no tenía. Con un guardia que **impide que un broadcast
      pise la geometría que el usuario aún no ha guardado**
- [x] **9 pruebas** del store (`npm --prefix frontend test`), las primeras del frontend

**Medición final del build:**

| | Antes | Después | |
|---|---|---|---|
| Total | 1 546 056 B | **570 849 B** | **−63,1 %** |
| mapa.png | 1 268 860 B | 338 781 B | −73,3 % |
| JavaScript | 238 623 B | 193 495 B | −18,9 % |

- [ ] **Pendiente de ti**: abrir `/mapa` en dos pestañas, reservar en una y ver que la otra
      se recolorea sola; y comprobar en el Editor que mover casetas sin guardar ya no se
      pierde al llegar una venta

### Bloque 0.5 — Arrancar la gestión desde cero · **HECHO** (2026-08-11)

- [x] **Respaldo previo**: `C:\uniFex\respaldos\v2_fexpo_uap-antes-del-reset-*.dump`
      (formato `-F c`, se restaura con `pg_restore`)
- [x] **Posiciones del plano exportadas aparte**: `C:\uniFex\respaldos\plano-casetas-colocadas.csv`
      (61 casetas que estaban colocadas)
- [x] **`db/reserva/R1__reset_gestion.sql`**, idempotente y transaccional, con comprobaciones
      finales que abortan si algo queda colgando
- [x] Ejecutado. Conserva esquema, stored functions, los 5 roles, las 2 ediciones
      (FEXPO 2026 activa) y `admin1`/`admin2`. Borró 39 usuarios vendedores, 796 personas,
      376 entidades, 754 responsables, 369 inscripciones, 532 casetas y 25 categorías
- [x] Verificado: los 10 endpoints de la SPA responden 200 con la base vacía, sin NPE

**Techo actual del circuito de prueba:** se puede crear usuarios, personas, categorías,
casetas, colocarlas en el plano y reservar/liberar en tiempo real. **Todavía no se puede
registrar una inscripción desde la SPA** — eso es justo el Bloque 1.

### Bloque 0.6 — Arreglos salidos de las pruebas del usuario · **HECHO** (2026-08-11)

- [x] **El mapa no se dejaba arrastrar**: `PanZoom.onMove` actualizaba la posición pero
      **nunca llamaba a `pintar()`**, así que el desplazamiento se calculaba y no se escribía
      al DOM. El zoom sí funcionaba porque `zoomAt` sí pinta. Bug preexistente de cuando se
      optimizó el pan/zoom
- [x] **El segundo de espera al seleccionar una caseta**: no era la red — el backend contesta
      en **10-25 ms**, medido. Era el repintado: cambiar una caseta obligaba a Vue a repasar
      las 520. Corregido con `v-memo` por pin, escritura optimista **antes** de la petición
      (con reversión si el servidor rechaza) y bloqueo solo de la caseta tocada en vez del
      mapa entero. Aplicado también al Tablero
- [x] **El mapa «se sacudía» en cada clic**: el `ResizeObserver` de `PanZoom` llamaba a
      `reset()`, que devuelve el plano al encuadre inicial. El párrafo de aviso que aparecía
      al reservar cambiaba el alto de la página → el observer se disparaba → el mapa saltaba.
      Dos arreglos: al redimensionar ahora se **conserva el punto que el usuario está mirando**,
      y los avisos pasaron a **toast** (`ToastHost` vive en `App.vue`, fuera del layout, así
      que no desplaza nada)
- [x] **El fondo del plano era clicable y se llenaba de azul al arrastrar**: el Editor ya
      tenía `pointer-events/user-select: none` en la imagen, el Mapa no. Añadido, más
      `user-select: none` y `-webkit-tap-highlight-color` en el viewport de `PanZoom`
      (esto último ya de cara al APK)
- [x] **Los ~2 s al reservar eran un fallo de red IPv4/IPv6 en el servidor de desarrollo.**
      Costó tres hipótesis descartadas (render, rasterizado, hilo bloqueado) y solo se cerró
      instrumentando el navegador (`ui/medir.js`). Los números decidieron: `entrada` 5-28 ms
      y `pintado` 6-22 ms (o sea, el clic se atiende y se pinta al instante), pero **`red`
      llegaba a 2718 ms**. Causa: **Vite escuchaba solo en `::1`** mientras `localhost`
      resuelve a `::1` *y* `127.0.0.1`; el navegador probaba primero 127.0.0.1, donde no había
      nadie, esperaba el fallo y recaía en IPv6. Pasaba solo al abrir conexión nueva — de ahí
      «la primera lenta, las siguientes rápidas, y cada tanto otra vez lenta».
      Corregido con `server.host: true` y objetivos del proxy por IP explícita.
      **Lección: medir en el navegador real antes de optimizar render.** Las dos optimizaciones
      previas (`v-memo`, capa propia del plano) se quedan porque son correctas, pero no eran
      esto
- [x] **La caseta ajena parpadeaba en verde antes del rechazo**: al pulsar una caseta en
      trámite de otro vendedor se intentaba «liberar» y la escritura optimista la pintaba
      libre un instante, hasta que el servidor devolvía 409. Causa de fondo: el DTO **no decía
      quién** tenía la reserva. Añadido `reservadoPor` a `PuestoEstadoDTO` (la columna ya
      existía, sin cambio de esquema) y el `id` del usuario a la respuesta del login. Ahora el
      mapa no ofrece liberar lo que no es tuyo, y tus reservas se distinguen con un borde

### Bloque 1 — La venta de punta a punta (el corazón)

> Es lo que convierte esto en un sistema de venta. Habilita el APK.

**Decisiones tomadas (2026-08-12):** precio **fijo por categoría** · reserva sostenida por
**inscripción en borrador**.

#### 1.1 Precio por categoría y medida de caseta — **HECHO**

> Era un tapón: sin esto **toda venta se registraba en 0 Bs** y no se podía probar nada.

- [x] `Categoria.precioBase` mapeado. La columna `precio_base` existía en la base desde
      siempre y la stored function la consultaba, pero **la entidad JPA no la mapeaba** y
      `CategoriaMapaService.crear()` nunca la asignaba
- [x] `nuevaCaseta()` ahora asigna `tamano` (antes quedaba NULL, lo que además rompía el
      cálculo de respaldo). Al ampliar la cantidad, las nuevas heredan la medida de sus hermanas
- [x] **Ids de categoría cableados, eliminados**: `IF p_id_categoria = 9 THEN 0.00` en la
      función, y `if (categoria.getId() != 25)` con precios por rango de código en
      `AdminController`. Tras el reset esos ids apuntan a categorías distintas — regalaban
      o encarecían casetas sin que nadie se enterara
- [x] **Retirado también el respaldo por tipo de entidad** (50/100/200 para `3x3`). No hacía
      falta para el histórico —el costo se congela en `inscripcion_puesto.costo` al vender— y
      era peor que nada: una categoría sin precio se vendía a 50 Bs, un número plausible de
      las reglas de otra feria. Ahora devuelve 0, que se ve mal a la primera
- [x] Script `V7__precio_por_categoria.sql`, aplicado y verificado (sin precio → 0; con
      precio → el de la categoría, ignorando tipo y tamaño)
- [x] Editor: campos **Precio** y **Medida** al crear categoría, precio editable después, y
      aviso en rojo cuando una categoría está sin precio
- [x] `PuestoEstadoDTO.precio`: el precio viaja al mapa para que el vendedor se lo diga al
      cliente sin cambiar de pantalla

#### 1.2 Carrito: varias casetas para una misma venta — **HECHO**

> **Desvío consciente del diseño planteado, con el mismo resultado.** La opción elegida era
> «crear la inscripción en borrador». Al ir a implementarlo apareció un riesgo: **todas las
> consultas existentes filtran por `_estado <> 'X'`, no por `inscripcion_estado`**, así que
> una fila de `Inscripcion` en estado BORRADOR aparecería en el listado de administración y
> en los reportes salvo que se modifiquen todas las consultas *y* la stored function. Es
> justo el tipo de cambio que después descuadra totales sin avisar (ya pasó antes en este
> proyecto con los filtros de estado).
>
> El carrito **es la propia reserva**: casetas en `T` a nombre del vendedor, con vencimiento
> largo. Cumple lo prometido —sobrevive a cerrar el móvil, a recargar y a un corte de señal,
> porque la verdad está en la base— sin enseñarle un estado nuevo a ningún informe. Los datos
> del formulario a medio llenar se guardarán en el cliente, que además es lo que sirve para
> el APK sin conexión.

- [x] `IPuestoDao.reservarSiLibreOMia`: UPDATE condicional que gana si está libre **o** si ya
      es del mismo vendedor (idempotente: volver a pulsar una caseta propia no falla ni se la
      roba a nadie)
- [x] `unifex.reserva.carrito-ttl-segundos` (12 h por defecto) frente a los 5 min de la
      reserva suelta. Sigue habiendo límite: el barrido de siempre recupera lo abandonado
- [x] `agregarAlCarrito` / `quitarDelCarrito` resuelven **caseta por caseta**: si una la ganó
      otro, se rechaza esa y **las demás siguen**. Anular el lote obligaría a rehacer la selección
- [x] `GET /mi-carrito`, `POST /carrito`, `DELETE /carrito` en `PuestoApiController`, con
      difusión por WebSocket solo de lo que realmente cambió
- [x] En la SPA el carrito **se deduce** del propio mapa (casetas en `T` mías): cero peticiones
      extra y se rehace solo al recargar. Barra flotante con cantidad, total y aviso si alguna
      caseta no tiene precio
- [x] **2 pruebas de concurrencia nuevas** (6 en total, todas verdes): que el carrito no le roba
      casetas a otro vendedor, y que conserva lo que sí consiguió cuando pierde una

#### 1.3 Registro de venta por API — **HECHO** (backend)

- [x] **`RegistroVentaService`**: entidad + responsables + inscripción + casetas en **una sola
      transacción**. Si una caseta se pierde a mitad, se revierte todo y el mensaje dice qué
      caseta fue. Difunde por WebSocket **después del commit**, nunca antes
- [x] **`POST /api/app/inscripciones`**. Códigos distinguibles por la SPA: 400 datos inválidos ·
      **409 caseta ya no disponible** · 200 registrada
- [x] **La autorización bajó de la clase al método**: `InscripcionApiController` estaba anotado
      entero con `ADMINISTRA`, lo que habría dejado a los 35 vendedores sin poder vender.
      Ahora el listado global sigue siendo de administración y el registro es de cualquier
      autenticado (mismo defecto que queda pendiente de corregir en `ReportesApiController`)
- [x] **`V8__venta_titular_y_edicion.sql`**: `responsable.es_titular` (dueño vs. acompañante,
      antes indistinguibles salvo por el orden de creación) y relleno de `id_edicion`
- [x] **`Inscripcion.edicion` mapeado**: la columna existía desde V3 pero la entidad no la
      mapeaba y el registro viejo nunca la rellenaba — las ventas nacían huérfanas
- [x] **El precio se congela** en `inscripcion_puesto.costo` al vender: cambiar el precio de
      una categoría después no altera lo ya vendido
- [x] Límite duro de 2 responsables (titular + acompañante)
- [x] **3 pruebas nuevas** (`RegistroVentaTest`): venta completa con precio congelado y edición
      etiquetada · **una caseta perdida no deja entidad ni personas a medias** · rechazo de un
      tercer responsable sin tocar las casetas

**Suite completa en verde: 14 pruebas.** De paso se arreglaron dos fallos preexistentes que
nada tenían que ver con este bloque:

- `UniFexApplicationTests` arrancaba con el perfil **por defecto (producción)** y moría con
  `Could not resolve placeholder 'JWT_SECRET'`. Ahora usa `dev`, como el resto
- `PuestoAnulacionTest` exigía una caseta con ventas, que ya no existe tras el reset. Ahora
  ese caso se **salta** en vez de reventar la suite

#### 1.4 Comprobante de pago y pendientes — **HECHO** (backend)

> **Sin columna de estado de pago, a propósito.** Se deduce de lo que ya existe: una venta está
> pagada si es al contado o si tiene comprobante adjunto. Añadir un estado nuevo obligaría a
> enseñárselo a todas las consultas y a la stored function — el mismo riesgo que llevó a
> resolver el carrito por la reserva en 1.2.

- [x] `POST /api/app/inscripciones/{id}/comprobante` (multipart). **Va aparte del registro**
      porque así se vende en la feria: cerrar la venta asegura la caseta, y la foto del
      comprobante puede subirse después, cuando haya señal
- [x] Solo puede adjuntarlo **quien registró la venta** — el id sale del token, nunca del cliente
- [x] `GET /api/app/inscripciones/mis-pendientes` con **`diasSinComprobante` calculado**: la
      urgencia sale del tiempo transcurrido, no de una marca manual que nadie mantiene
- [x] Reutiliza `FileStorageService`, que ya valida mime y extensión y reparte por año/mes
- [x] **2 pruebas nuevas**: un vendedor ajeno no puede adjuntar a una venta que no es suya, y
      un `.exe` se rechaza

**Suite: 16 pruebas en verde**, 1 saltada por falta de datos históricos.

#### 1.5 Wizard de venta en la SPA — **HECHO**

- [x] **`views/Venta.vue`**, ruta `/venta`, en tres pasos (Entidad · Responsables · Confirmar),
      **pensada para móvil desde el primer píxel**: una columna, campos grandes, resumen del
      total siempre visible arriba y botones fijos abajo. Es la misma pantalla que irá al APK
- [x] **Borrador local** (`ui/borrador.js`): lo tecleado se guarda en `localStorage` según se
      escribe y se recupera al volver. Va en el cliente y no en el servidor **a propósito**:
      así funciona sin conexión, que es lo que hará falta con el wifi de la feria. La clave
      incluye el id del vendedor, para que en un dispositivo compartido no se crucen borradores
- [x] Validación **por paso**: no deja avanzar con datos incompletos, en vez de rechazarlo todo
      al final
- [x] **El 409 no pierde lo escrito**: si otro vendedor gana una caseta, se avisa, se refresca
      el mapa y el formulario se conserva
- [x] Titular y acompañante, con el límite de 2 reflejado en la interfaz
- [x] `GET /api/app/catalogos/tipos-entidad` (nuevo): sin esa lista el formulario no se puede
      rellenar, y dejarla en administración habría impedido vender al vendedor
- [x] El botón «Registrar venta» del mapa ya lleva al wizard
- [x] **`scripts/verificar-venta.mjs`**: humo del circuito completo (login → catálogo → carrito
      → venta → casetas ocupadas → reventa rechazada con 409 sin dejar nada a medias → pendientes)

#### 1.6 Recibo PDF y comprobante desde la SPA — **HECHO**

- [x] `GET /api/app/inscripciones/{id}/recibo` reutiliza `ReciboPdfService` tal cual
- [x] **Con control de propiedad, que el equivalente Thymeleaf no tenía**: el legado
      (`/ver/inscripcion/{id}/recibo.pdf`) deja a cualquier usuario con sesión descargar el
      recibo de cualquier otro vendedor, con los datos del cliente dentro. En el API solo
      puede quien registró la venta, o administración
- [x] Sección **«Falta el comprobante»** arriba de *Mis ventas*, con los días transcurridos y
      marcada en rojo a partir de tres — deja de ser un olvido y pasa a ser un problema de cobro
- [x] Subida desde el móvil (cámara o galería) con `<input type="file">` oculto
- [x] **Arreglado `apiFetch`**: forzaba `Content-Type: application/json` también con `FormData`,
      lo que habría roto la subida con un error que no apunta a nada (falta el `boundary`)
- [x] El recibo se descarga vía `fetch` + blob, no con un `<a href>`: un enlace normal no puede
      mandar la cabecera `Authorization` y caería en un 401
- [x] `verificar-venta.mjs` amplía la comprobación al recibo (que sea un PDF de verdad, no una
      página de error servida con 200) y al circuito del comprobante

**Dos bugs del generador de recibos, encontrados al probarlo de verdad** (2026-08-12):

- [x] **NPE si el vendedor no tiene ficha en `admistrativo`** — que es casi siempre. El código
      pedía el `Administrativo` con `.orElse(null)`, reconociendo que puede faltar, y acto
      seguido le llamaba `getCodigoFuncionario()`. No era efecto del reset: fallaba con
      cualquier usuario sin esa ficha. Ahora degrada al nombre de usuario
- [x] **`generarRecibo` no era transaccional**: recorre relaciones LAZY y solo funcionaba
      porque Spring trae `open-in-view` activado. Fuera de una petición web —una prueba, una
      tarea programada, un futuro envío por correo— reventaba con `LazyInitializationException`,
      y bastaba con que alguien desactivara esa opción para romperlo también en la web
- [x] **Lección de método:** las pruebas cubrían la *autorización* del recibo pero nunca
      **generaban el PDF**. Añadido un caso que lo genera y comprueba que empieza por `%PDF`;
      habría atrapado los dos

#### Resto del bloque

- [x] Resolver las **3 decisiones de diseño** (precio y reserva; el límite de acompañantes
      se implementará como límite duro de 2, como se describió)
- [ ] **Reserva de varias casetas a la vez** (carrito), respetando el UPDATE condicional
      caseta por caseta: si una se pierde, se avisa y las demás siguen
- [ ] **Inscripción borrador**: se crea al seleccionar y mantiene viva la reserva
- [ ] `POST` / `PUT /api/app/inscripciones` en una sola transacción con la ocupación;
      publicar por WebSocket **después del commit**
- [ ] **Subida de comprobante** (archivo o cámara) reusando `FileStorageService`
- [ ] **Estado de pago** en la inscripción: pagado / pendiente-urgente
- [ ] **Titular + acompañante** con foto, CI y contacto
- [ ] **Wizard de venta en la SPA**, diseñado para móvil desde el primer píxel
- [ ] **Recibo de inscripción y venta** descargable (ya existe `ReciboPdfService`)
- [ ] Vista **«Mis pendientes»**: inscripciones sin comprobante, marcadas urgentes

### Bloque 2 — Fotos de casetas y ficha real · **HECHO** (2026-08-12)

- [x] `V9__fotos_de_caseta.sql`: tabla `puesto_foto` (varias por caseta, con orden) +
      `puesto.referencia` + bucket `PUESTOS`
- [x] **Una foto se asigna a VARIAS casetas y el archivo se guarda una sola vez.** No es un
      extra: una feria tiene filas de casetas idénticas y subir la misma imagen 40 veces no lo
      hace nadie — sin el lote, la funcionalidad quedaría vacía
- [x] Borrar la foto de una caseta **no** deja sin foto a las que comparten el archivo (baja
      lógica de la fila; el archivo no se toca)
- [x] Tope de 6 fotos por caseta: más no ayudan a decidir y pesan en el móvil
- [x] **Ficha de caseta** (`CasetaDetalle.vue`): fotos, precio, medida y ubicación. Hoja
      inferior en móvil (donde llega el pulgar), panel en pantalla grande
- [x] **Cambio de interacción, deliberado:** tocar una caseta ahora abre su ficha en vez de
      reservarla al instante. Es un toque más, pero es lo que permite enseñársela al cliente
      antes de comprometerla, y evita que un roce venda la caseta equivocada en un plano con
      cientos de pines diminutos
- [x] Las fotos se piden **al abrir la ficha**, no con el mapa: traer las de ~500 casetas de
      golpe cargaría megas que casi nunca se miran
- [x] `GET /puestos/con-foto` de una sola vez para marcar en el plano cuáles se pueden
      enseñar (consultarlo pin a pin sería un N+1 en el navegador)
- [x] Editor: botón **📷 Foto** y campo de ubicación, ambos aplicados a toda la selección
- [x] **4 pruebas nuevas** (21 en total, todas verdes)

> **Tropiezo del que conviene acordarse:** la primera versión de V9 creó las columnas de
> auditoría entrecomilladas (`"_modificacion_idUsuario"`), conservando la mayúscula. Hibernate
> las parte en snake_case y busca `_modificacion_id_usuario`, así que **todo INSERT fallaba**.
> Es exactamente la trampa de nombres que documenta CLAUDE.md. El script ya renombra de forma
> condicional para seguir siendo idempotente.

### Bloque 2.5 — Cancelación con aprobación y auditoría · **HECHO** (2026-08-12)

- [x] `V10__cancelacion_y_auditoria.sql`: la cancelación de una venta pasa a ser **baja lógica**
      (`_estado = 'X'`), libera las casetas (vuelven a `L` en el mapa) y deja el motivo, la
      fecha, quién canceló y desde dónde (WEB/APK). Tabla `auditoria` (sin FK, sobrevive a la
      limpieza de negocio) para todo el ciclo: REGISTRO, COMPROBANTE, CANCELACIÓN…
- [x] `V11__solicitud_cancelacion.sql`: **flujo de aprobación** — el vendedor SOLICITA cancelar
      con un motivo (obligatorio), la solicitud queda `PENDIENTE`, administración la aprueba o
      la rechaza (con respuesta obligatoria al rechazar), y **solo con la solicitud aprobada
      el vendedor puede ejecutar la cancelación**. Una venta admite UNA solicitud pendiente a
      la vez; la segunda se rechaza
- [x] Transiciones de estado con **UPDATE condicional** en el DAO (`aprobarSiPendiente` /
      `rechazarSiPendiente`): si dos admins resuelven la misma solicitud a la vez, gana la BD,
      no el código (invariante 1)
- [x] **Asíncrono, sin recargar la página**: `NotificacionService` publica por WebSocket en el
      topic personal `/topic/notificaciones/{userId}` tras el commit — la cola del admin y el
      estado de la venta del vendedor se actualizan solos
- [x] Auditoría de cada paso: `SOLICITUD_CANCELACION`, `APROBACION_CANCELACION`,
      `RECHAZO_CANCELACION` (además de la CANCELACION ejecutada), con quién/cuándo/desde dónde
- [x] API: `POST /inscripciones/{id}/solicitar-cancelacion`, `GET .../solicitud-cancelacion`,
      `GET /solicitudes-cancelacion/{pendientes,resueltas}` (admin), `GET
      /mis-solicitudes-cancelacion`, `POST /solicitudes-cancelacion/{id}/{aprobar,rechazar}`
- [x] SPA: *Mis ventas* con botón «Solicitar cancelación» (motivo en modal), badge del estado
      (en espera / aprobada / rechazada) y botón de cancelar solo tras la aprobación;
      *Inscripciones* con pestaña «Solicitudes» (cola con venta y quién la pidió, aprobar,
      rechazar con respuesta, historial de resueltas)
- [x] `V12__duplicado_puesto_baja_logica.sql`: el trigger de BD
      `verificar_duplicado_id_puesto` (BEFORE INSERT/UPDATE en `inscripcion_puesto`)
      contaba como duplicado las filas de inscripciones canceladas — desde V10 la
      cancelación es baja lógica (`X`) y la fila del detalle queda en el histórico,
      así que **revender una caseta de una venta cancelada daba «Ya existe un registro
      con id_puesto=…»** y la venta moría con 500. La función ahora filtra contra la
      inscripción vigente; `CREATE OR REPLACE` con la misma firma, el trigger no cambia
- [x] `verificar-venta.mjs` cubre el circuito completo: solicitar → cola del admin → aprobar →
      cancelar → casetas LIBRES → auditoría con los tres eventos. Además **vacía el carrito
      al empezar**: una corrida interrumpida deja casetas en trámite a nombre del usuario y el
      TTL aún no las libera, lo que ensuciaba el paso «mi-carrito»
- [x] **12 pruebas nuevas** (32 en total, todas verdes): `CancelarInscripcionTest` (5, flujo
      V11) y `SolicitudCancelacionTest` (6)

> **Tropiezos pagados en V11:** (1) la JPQL de aprobar/rechazar usaba `s.resueltoPorIdUsuario`,
> atributo que no existe — quien resuelve se guarda en `_modificacion_id_usuario`
> (`modificacionIdUsuario`, heredado de `AuditoriaConfig`); (2) el script V11 no creaba las
> columnas de auditoría `_fecha_registro`/`_fecha_modificacion` — Hibernate las exige y el
> SELECT del DAO fallaba con «no existe la columna». Ambos corregidos; el script V11 es
> idempotente (ALTER condicional para tablas ya creadas sin esas columnas).

> **Tropiezo de V12:** se descubrió con el humo contra una venta real cancelada, no con las
> pruebas (que usan puestos propios): el trigger de duplicados ignoraba la baja lógica. Vale
> la pena recordar que **los triggers/funciones de la BD son lógica viva** aunque no estén en
> el repo — cuando un 500 no apunta a nada en Java, hay que mirar `pg_proc`.

### Bloque 3 — Roles, alcance y credenciales

- [ ] Etiquetar `ADMINISTRATIVO` como **«Vendedor»** en toda la interfaz
- [ ] **Reportes para el vendedor**: hoy `ReportesApiController` tiene el `@PreAuthorize`
      a nivel de clase y los deja fuera por completo
- [ ] **Rehacer credenciales** (ver la revisión rigurosa arriba): librerías **en el bundle**,
      plantilla y posiciones **en la base**, fondo configurable por edición
- [ ] Definir qué ven `CONTROL` y `ASESORIA`

### Bloque 4 — Diseño unificado y usabilidad

- [ ] Componentes compartidos: tabla, formulario, modal, estado vacío, error, «cargando»
- [ ] Aplicarlos vista por vista a las 10 vistas
- [ ] **Inicio con datos reales** (hoy son tarjetas estáticas), en vivo por WebSocket
- [ ] Esqueletos de carga; foco visible; `aria-label` en botones de icono

### Bloque 5 — APK para vendedores · **en curso** (2026-08-12)

> Decisión de orden: el APK va **antes** de pulir el diseño (Bloque 4), como sondeo. Lo que
> falle en un teléfono real —tamaño de los toques, cámara, señal— define qué pulir; al revés
> se pule a ciegas y luego se rehace.

- [x] **Base del API configurable** (`src/config.js`). Era el bloqueo real, y no era obvio:
      en la web todo va en relativo (Vite proxya en dev, Spring sirve en prod), pero Capacitor
      sirve la app desde `https://localhost`, así que **una ruta relativa apunta al contenedor
      de la app, no al servidor** — y no falla con un error claro, simplemente no encuentra
      nada. Con `VITE_API_BASE` se compila apuntando al servidor; sin ella, se queda en
      relativo y la web no cambia
- [x] `ws.js` deriva el WebSocket de esa misma base (antes usaba `location.host`, que en el
      APK es el propio contenedor)
- [x] Las rutas `/files/**` (fotos y comprobantes) también pasan por la base
- [x] **CORS en la cadena del API** (`SecurityConfig.corsApi()`), que no existía. Orígenes
      explícitos y no `*`: con credenciales el comodín no es válido, y un API abierto a
      cualquier origen deja que cualquier web haga peticiones en nombre del usuario.
      Incluye `X-Origen` en las cabeceras permitidas o el preflight la bloquea
- [x] Capacitor 7 instalado, `capacitor.config.json` y proyecto Android generado
- [x] **`compileSdk` fijado a 36**: Capacitor genera 35, pero en esta máquina solo están
      android-36/36.1 y **no hay `cmdline-tools`**, así que Gradle no puede descargar la 35
- [x] `android/local.properties` con `sdk.dir` (no se versiona: cada máquina crea el suyo,
      porque no hay `ANDROID_HOME` en el sistema)
- [x] **`frontend/APK.md`**: cómo compilar, instalar y probar, con las trampas de esta máquina
- [ ] Compilar el APK de depuración *(en curso)*
- [ ] **Probar en un teléfono real** — lo único que da el resultado del sondeo
- [ ] Icono y pantalla de arranque propios
- [ ] Firma para distribución (`assembleRelease` + keystore)
- [ ] Reconexión con mala señal: el wifi de la feria es irregular

> `cleartext: true` permite hablar con el backend por HTTP plano, que es lo que hace falta
> para probar en red local. **En producción hay que servir el backend por HTTPS y quitarlo.**

### Bloque 6 — Multi-actividad (que no sirva solo para FEXPO)

- [ ] **Plano por edición**: hoy `mapa.png` está fijo en la SPA. Es el bloqueo real para
      usar el sistema en otra actividad
- [ ] Categorías, precios y credenciales por edición
- [ ] Crear una actividad nueva desde la interfaz, sin tocar código

### Bloque 7 — Contabilidad *(pendiente de definir contigo)*

- [ ] Gastos, caja, ventas contra gastos, partes contables — **falta que definas qué
      necesitas ver y quién lo firma**

### Bloque 8 — Retiro de Thymeleaf y seguridad

- [ ] **Boletería y control de acceso: PENDIENTE de analizar** (decisión aplazada por el usuario)
- [ ] Retirar 35 plantillas y ~52 MB del tema comprado
- [ ] Consolidar los dos `IServiceGenerico` duplicados
- [ ] Rotar secretos; quitar el seed de `admin1`/`admin2` del arranque

---

## Hecho (histórico)

- [x] Inventario completo del proyecto — 2026-08-11
- [x] **`AppLayout.vue` global**: menú, cabecera y pie se montan una sola vez
- [x] **Carga diferida** de las 10 vistas, un chunk por vista
- [x] **`<KeepAlive>`** para Mapa, Editor y Tablero
- [x] URL con deep-links, sin recargas completas (0 coincidencias en la SPA)
- [x] Estados de carga en los botones
- [x] **Mapa**: `PanZoom` no reactivo, transform directo al DOM
- [x] **Editor**: arrastre directo al DOM, 1 re-render por gesto
- [x] `GET /api/app/puestos` con `@EntityGraph`, sin N+1
- [x] **Reserva atómica** probada con 25 hilos: gana exactamente uno
- [x] **Bloquear/desbloquear** casetas por reparación
- [x] Editor: copiar/pegar y rejilla

---

## Bugs / deuda detectados

- [x] **«Mis ventas» rompía al entrar** (2026-08-11): `MisVentasApiController` armaba la
      respuesta con `Map.of(...)`, que **lanza NPE si un valor es null** — y `edicion` es null
      cuando no se pasa `?edicion=` (el caso normal). La NPE salía por `/error`, la cadena web
      la convertía en **302 al login**, el navegador seguía esa redirección a otro origen y
      el resultado visible era «CORS Missing Allow Origin» + «NetworkError». Por eso fallaba
      al entrar y funcionaba tras elegir una edición. Arreglado con `LinkedHashMap`
- [x] **Los errores del API se disfrazaban de problema de CORS** (2026-08-11): añadido
      `Config/ManejadorErroresApi` (`@RestControllerAdvice`), que devuelve JSON 500 (o 400 si
      la petición viene mal del cliente) en vez de dejar que la excepción acabe en `/error` y
      de ahí en un 302 al login. Alcance: solo `@RestController`; Thymeleaf no cambia
- [ ] `PagoController.crearPago` tiene el **mismo patrón peligroso**: `Map.of("codigoTransaccion",
      result.get(...), "urlRedireccion", result.get(...))`. Si la pasarela no devuelve alguna de
      esas claves, NPE. No se tocó por estar en el flujo de pago, que hoy no se está probando
- [ ] Wizard `/admin`: el form envía a `/admin/guardar`, el controller mapea `/guardar` (legacy)
- [ ] `GET /administracion/gaseta` devuelve plantilla inexistente
- [ ] `PersonaController.modificar-persona` no guarda nada (cuerpo comentado)
- [ ] Login por sesión compara contra `VENDEDOR`, que no existe
- [ ] `generadorCredenciales` consulta dos veces lo mismo por un `System.out.println`
- [ ] Enlaces muertos y plantillas huérfanas (`login/login.html`, `publico/verInscripcion.html`)

---

## Decisiones

- [x] **URL:** history con deep-links — 2026-08-11
- [x] **Rol vendedor:** se queda `ADMINISTRATIVO`, etiquetado «Vendedor» — 2026-08-11
- [x] **Alcance del APK:** registro de inscripción **completo** en web y móvil — 2026-08-11
- [x] **Boletería y control de acceso:** aplazado, se analiza después — 2026-08-11
- [ ] **Las 3 decisiones de diseño del Bloque 1** (TTL, pago pendiente, límite de acompañantes)
- [ ] **Contabilidad:** qué registrar, qué reportes, quién firma
