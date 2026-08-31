# El mapa interactivo y el editor de plano

Dos vistas Vue comparten el mismo plano y el mismo componente de navegación:

- **`views/Mapa.vue`** — el visor de venta. Muestra las casetas coloreadas por estado y permite
  reservar/liberar tocándolas. Es lo que usa el vendedor.
- **`views/Editor.vue`** — el diseñador del plano. Permite crear categorías y **colocar y mover**
  las casetas sobre la imagen. Es una herramienta de administración, no de venta.
- **`components/PanZoom.vue`** — pan y zoom con Pointer Events (mouse + táctil), usado por ambas.

## Coordenadas normalizadas

`puesto.mapa_x` y `puesto.mapa_y` son `double` en **0..1**, fracciones del ancho y alto de la
imagen del plano (`frontend/public/mapa.png`). Nunca píxeles.

Esa decisión es la que hace que el mapa sobreviva a cambios de tamaño, zoom y pantallas de
celular: el pin se posiciona con `left: mapaX * 100%` sobre un contenedor `position: relative`
cuyo hijo es la imagen a `width: 100%`. Si algún día se reemplaza el plano por una imagen de otra
resolución, los puntos siguen donde deben mientras la proporción no cambie.

Al convertir un evento de puntero a coordenada normalizada, usa el rectángulo **de la imagen**,
no el del viewport, porque bajo zoom son distintos:

```js
function norm(e) {
  const r = plano.value.getBoundingClientRect();   // plano = ref al <img>
  return { x: (e.clientX - r.left) / r.width, y: (e.clientY - r.top) / r.height };
}
```

Una caseta con `mapaX == null` existe pero **no está en el plano**: `Mapa.vue` la omite y muestra
el conteo "N sin ubicar (Editor)". Es el estado normal de una categoría recién creada.

## Cómo se actualiza el mapa en vivo

`Mapa.vue` sigue este ciclo:

1. `onMounted` → `cargar()` hace `GET /api/app/puestos` y llena `puestos`.
2. `crearClientePuestos(aplicar)` (de `src/ws.js`) abre STOMP sobre SockJS contra `/ws` y se
   suscribe a `/topic/puestos`. Cada `PuestoEstadoDTO` que llega pasa por `aplicar(dto)`, que
   reemplaza el elemento con el mismo `id`.
3. `onUnmounted` → `ws.deactivate()`. Olvidarlo deja sockets colgando entre navegaciones.

Al hacer clic, la vista **también** aplica el estado de forma optimista, para que el vendedor vea
la respuesta al instante sin esperar el ida y vuelta del broadcast. El broadcast llega igual
milisegundos después y confirma (o corrige) lo mismo. Ese es el patrón: escritura optimista local
+ broadcast como fuente de verdad. Lo que **no** se hace es volver a pedir la lista completa con
un GET; eso vuelve la UI lenta y crea una segunda ruta de actualización que puede discrepar.

`aplicar()` solo reemplaza casetas ya presentes en la lista. Una caseta **nueva** difundida por
WebSocket no aparecerá sola: si agregas creación de casetas en caliente, o la insertas en el
array cuando no la encuentras, o recargas.

Colores por estado, en `Mapa.vue`: `L` libre, `T` en trámite, `O` ocupado, `X` bloqueado
(mapa `clase = { L:'libre', T:'tramite', O:'ocupado', X:'bloqueado' }` → clases CSS).

## PanZoom

Props relevantes:

- `aspect` — alto/ancho de la imagen (`2376/1836` para el plano actual). Si cambias `mapa.png`,
  actualiza esto o el enfoque inicial saldrá desviado.
- `focus` — `{ x, y, scale }` normalizado: dónde centrar y a qué zoom al abrir. `Mapa.vue` usa
  `{ x: 0.44, y: 0.34, scale: 2.4 }` porque las casetas ocupan una franja del plano y arrancar
  en "ver todo" las deja ilegibles.
- `selectMode` — cuando es `true`, PanZoom **no** captura el arrastre y lo deja pasar al
  contenido. Así el editor puede dibujar una selección por caja mientras la rueda y los botones
  de zoom siguen funcionando.

Los pines llevan `@pointerdown.stop`: eso impide que PanZoom interprete el toque como inicio de
arrastre, de modo que **tocar un pin reserva** y **arrastrar el fondo mueve el plano**. Si agregas
elementos interactivos encima del plano, replica ese `.stop` o el gesto se los comerá.

Los gestos usan Pointer Events (no `mousedown`/`touchstart` por separado), lo que da mouse, dedo
y lápiz con un solo camino de código. `zoomAt(cx, cy, factor)` conserva fijo el punto bajo el
cursor/dedo, que es lo que hace que el zoom se sienta natural.

**Pendiente conocido:** los pines mantienen 12px CSS a cualquier escala, así que con mucho zoom
quedan diminutos respecto al plano. Arreglarlo implica escalar el pin con `1/t.scale` o exponer
la escala del PanZoom a los hijos.

## El editor

Modos (`modo`): `mapa` (solo navegar), `colocar`, `mover`.

- **Colocar**: un toque coloca una caseta; un arrastre coloca una **línea de N** casetas
  (`porLinea`, por defecto 8) repartidas entre el punto inicial y el final. Las casetas se toman
  de `porColocar`, que son las de la categoría activa sin posición, ordenadas por código numérico.
- **Mover**: arrastrar un pin existente cambia su posición.
- Los cambios se acumulan en un `Set` `dirty` y se guardan en bloque con
  `POST /api/app/puestos/posiciones` (cuerpo: lista de `{id, x, y}`).

La imagen de fondo está bloqueada (`pointer-events: none; user-select: none`) para que arrastrar
sobre ella no dispare el arrastre nativo de imagen del navegador.

**Categorías**: el formulario "Nueva categoría" hace `POST /api/app/categorias`
(`{nombre, cantidad, color, forma}`), que crea la categoría **y sus N casetas** libres con código
`1..N` (ver `CategoriaMapaService.crear()`). Editar nombre/color/forma es
`PATCH /api/app/categorias/{id}`. Las formas son `cuadrado`, `circulo`, `triangulo`; el triángulo
se dibuja con `clip-path` de CSS, no con un SVG.

El color y la forma viajan en `PuestoEstadoDTO` (campos `color`, `forma`, heredados de la
categoría), así que el visor no necesita pedir las categorías por separado.

## De dónde salió el plano

El original es `src/main/java/com/usic/uniFex/uploads/FEXPO UAP V1 2025.pdf` (plano rotado del
predio). Se rasterizó a `frontend/public/mapa.png` con Node (`pdf-to-img` + `sharp`), y en un
momento se extrajeron 443 etiquetas de número con `pdfjs-dist/legacy` a
`frontend/public/puntos-extraidos.json`.

**Ese JSON ya no lo usa el editor.** Se abandonó la colocación automática porque los números de
caseta se repiten por zona (MYPES 1-128, AGRO 1-95…), así que una etiqueta "14" no identifica una
caseta sin saber a qué categoría pertenece —y eso solo lo sabe una persona mirando el plano. El
editor manual sustituyó ese enfoque. El archivo sigue ahí por si sirve de referencia; no construyas
sobre él sin hablarlo.

Quedan sembradas 18 casetas colocadas automáticamente (EMPRESAS GRANDES `E-1..11`, PUNTOS BANCOS
`B-1..7`), las únicas cuyas etiquetas eran inequívocas.
