<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue';

const props = defineProps({
  aspect: { type: Number, default: 2376 / 1836 }, // alto/ancho de la imagen
  min: { type: Number, default: 0.6 },
  max: { type: Number, default: 10 },
  // Enfoque inicial: centra el punto normalizado (fx,fy) al zoom fscale
  focus: { type: Object, default: () => ({ x: 0.45, y: 0.32, scale: 2.2 }) },
  // Alternativa a `focus`: zona normalizada {x0,y0,x1,y1} que debe verse ENTERA al abrir.
  // La escala se calcula con el tamaño real del visor, asi que la misma zona entra completa
  // lo mismo en un celular angosto que en un monitor: es lo que evita tener que elegir a
  // dedo un `scale` distinto por dispositivo. Si se pasa, manda sobre `focus`.
  contenido: { type: Object, default: null },
  // Si true, PanZoom NO captura el arrastre (lo deja al contenido, p.ej. selección del editor);
  // siguen activos la rueda y los botones.
  selectMode: { type: Boolean, default: false },
  // Si true, el visor ocupa TODO el alto que le quede a su contenedor flex en vez de una
  // altura fija en vh. Lo usa el mapa para llenar la pantalla del celular hasta la barra
  // inferior; sin esto sobra un hueco muerto abajo, porque un vh fijo no sabe cuanto miden
  // la cabecera, la leyenda ni la barra.
  llenar: { type: Boolean, default: false },
  // Ancho del contenido renderizado (px, ya con el zoom) a partir del cual se enciende la
  // clase `detalle` en el mundo. Sirve para que el contenido muestre rotulos solo cuando
  // se ve lo bastante grande. 0 = nunca. Ver `actualizarDetalle`.
  umbralDetalle: { type: Number, default: 0 },
  // Techo de acercamiento expresado como ancho máximo del contenido renderizado (px), en vez
  // de como factor de aumento. Un factor fijo no sirve: 10x en un celular de 390 px deja el
  // plano en 3900 px y en un monitor de 1400 px lo deja en 14000, así que el mismo número da
  // casetas de 19 px o de 70. Solo puede SUBIR el techo de `max`, nunca bajarlo. 0 = sin usar.
  maxAncho: { type: Number, default: 0 },
});

const viewport = ref(null);
const world = ref(null);
// NO es reactivo a proposito: el transform se escribe directo al DOM en pintar().
// Asi el pan/zoom (decenas de eventos por segundo) no re-renderiza el contenido —
// 500+ casetas del mapa — solo compone la capa transformada.
const t = { x: 0, y: 0, scale: 1 };

/*
 * ---- por que el zoom se veia borroso ----
 *
 * `will-change: transform` permanente le dice a Chromium "el transform de esta capa va a
 * seguir cambiando", y Chromium responde CONGELANDO la escala a la que la rasteriza: a 20
 * aumentos seguia mostrando la textura dibujada a 1x, estirada. Los pines y sus numeros son
 * DOM —deberian salir nitidos a cualquier zoom— y salian emborronados junto con el plano.
 *
 * La cura no es quitarlo: sin el, cada fotograma del arrastre repinta 500+ casetas. Se pone
 * al empezar a mover y se quita poco despues de soltar. Durante el gesto se gana la fluidez;
 * en reposo —que es cuando de verdad se mira— el navegador vuelve a rasterizar a la escala
 * actual y todo queda nitido.
 */
const MS_NITIDEZ = 220;
let capaPromovida = false;
let temporizadorNitidez = null;

function promoverCapa() {
  const el = world.value;
  if (!el) return;
  if (!capaPromovida) {
    el.style.willChange = 'transform';
    capaPromovida = true;
  }
  clearTimeout(temporizadorNitidez);
  temporizadorNitidez = setTimeout(bajarCapa, MS_NITIDEZ);
}

function bajarCapa() {
  capaPromovida = false;
  if (world.value) world.value.style.willChange = 'auto';
}

function pintar() {
  const el = world.value;
  if (!el) return;
  el.style.transform = `translate(${t.x}px, ${t.y}px) scale(${t.scale})`;
  promoverCapa();
}

// Ancho del viewport con el que se calculo el transform actual. Al redimensionar, el mundo
// (width:100%) cambia de tamaño y el transform, que esta en pixeles, dejaria de apuntar al
// mismo sitio del plano.
let anchoPrevio = 0;

/** Ancho del visor con el que se calcula todo. `anchoPrevio` lo mantiene el ResizeObserver. */
function anchoVisor() {
  return anchoPrevio || (viewport.value ? vpRect().width : 0);
}

/**
 * Publica el ancho del mundo (`width: 100%` del visor) como variable CSS heredable.
 *
 * El contenido la usa para medir cosas en fracciones de si mismo — el numero dentro de una
 * caseta, el grosor de su aro — sin unidades de container query, que exigen un WebView
 * moderno y fallaban en silencio. Es un ancho de DISEÑO, no de pantalla: no cambia con el
 * zoom, porque del zoom ya se encarga el transform.
 *
 * Va SIN UNIDAD a proposito. CSS no sabe dividir una longitud entre otra para sacar un
 * numero, y el rotulo necesita justamente eso: un factor de escala. Con un numero pelado,
 * `calc(var(--mundo) * 0.005)` da los pixeles del pin y `calc(... / 100)` da el factor.
 */
function publicarAncho(ancho) {
  if (world.value && ancho) world.value.style.setProperty('--mundo', String(Math.round(ancho)));
}

/** El techo real de zoom: el mayor entre el factor `max` y el que pide `maxAncho`. */
function escalaMaxima() {
  const w = props.maxAncho ? anchoVisor() : 0;
  return w ? Math.max(props.max, props.maxAncho / w) : props.max;
}

const clamp = (v) => Math.min(escalaMaxima(), Math.max(props.min, v));

function vpRect() { return viewport.value.getBoundingClientRect(); }

/**
 * Enciende o apaga la clase `detalle` en el mundo segun lo grande que se vea el contenido.
 *
 * Es una CLASE en el DOM y no un dato reactivo, por lo mismo que el transform: el contenido
 * son 500+ casetas y volverlas a renderizar en cada rueda del raton se nota. Ademas solo se
 * escribe cuando el valor CRUZA el umbral, no en cada fotograma; y no se llama al arrastrar,
 * porque un desplazamiento no cambia la escala.
 */
let detalle = false;
function actualizarDetalle(anchoViewport) {
  if (!props.umbralDetalle) return;
  const visible = anchoViewport * t.scale >= props.umbralDetalle;
  if (visible === detalle) return;
  detalle = visible;
  world.value?.classList.toggle('detalle', visible);
}

/** Centra el punto normalizado (nx,ny) de la imagen a la escala dada. */
function focusOn(nx, ny, scale) {
  const r = vpRect();
  const worldW = r.width;
  const worldH = r.width * props.aspect;
  t.scale = clamp(scale);
  t.x = r.width / 2 - nx * worldW * t.scale;
  t.y = r.height / 2 - ny * worldH * t.scale;
  actualizarDetalle(r.width);
  pintar();
}

function reset() {
  const z = props.contenido;
  if (z) {
    // "Contain": la escala la fija el eje mas apretado, para que la zona entre entera y
    // quede centrada. El margen deja un respiro en el borde.
    const r = vpRect();
    const mundoW = r.width, mundoH = r.width * props.aspect;
    const margen = 1.04;
    const escala = Math.min(
      r.width / ((z.x1 - z.x0) * mundoW * margen),
      r.height / ((z.y1 - z.y0) * mundoH * margen),
    );
    focusOn((z.x0 + z.x1) / 2, (z.y0 + z.y1) / 2, escala);
    return;
  }
  focusOn(props.focus.x, props.focus.y, props.focus.scale);
}

/** Zoom manteniendo fijo el punto (cx,cy) en coordenadas de pantalla. */
function zoomAt(cx, cy, factor) {
  const r = vpRect();
  const px = cx - r.left, py = cy - r.top;
  const wx = (px - t.x) / t.scale, wy = (py - t.y) / t.scale;
  const ns = clamp(t.scale * factor);
  t.scale = ns;
  t.x = px - wx * ns;
  t.y = py - wy * ns;
  actualizarDetalle(r.width);
  pintar();
}

function onWheel(e) {
  e.preventDefault();
  zoomAt(e.clientX, e.clientY, e.deltaY < 0 ? 1.15 : 1 / 1.15);
}

// ---- gestos con Pointer Events (unifica mouse y táctil) ----
const pointers = new Map();
let panning = false, last = { x: 0, y: 0 };
let pinch = null; // {dist, cx, cy}

function dist(a, b) { return Math.hypot(a.x - b.x, a.y - b.y); }
function mid(a, b) { return { x: (a.x + b.x) / 2, y: (a.y + b.y) / 2 }; }

function onDown(e) {
  if (props.selectMode) return; // dejar el arrastre al contenido
  viewport.value.setPointerCapture(e.pointerId);
  pointers.set(e.pointerId, { x: e.clientX, y: e.clientY });
  if (pointers.size === 2) {
    const [a, b] = [...pointers.values()];
    pinch = { dist: dist(a, b), ...mid(a, b) };
    panning = false;
  } else {
    panning = true; last = { x: e.clientX, y: e.clientY };
  }
}
function onMove(e) {
  if (!pointers.has(e.pointerId)) return;
  pointers.set(e.pointerId, { x: e.clientX, y: e.clientY });
  if (pointers.size === 2 && pinch) {
    const [a, b] = [...pointers.values()];
    const d = dist(a, b), m = mid(a, b);
    if (pinch.dist > 0) zoomAt(m.x, m.y, d / pinch.dist);
    pinch = { dist: d, ...m };
  } else if (panning) {
    t.x += e.clientX - last.x; t.y += e.clientY - last.y;
    last = { x: e.clientX, y: e.clientY };
    pintar(); // sin esto el arrastre calcula la posicion pero nunca la escribe al DOM
  }
}
function onUp(e) {
  pointers.delete(e.pointerId);
  if (pointers.size < 2) pinch = null;
  if (pointers.size === 0) panning = false;
}

let ro;

/**
 * Reacciona a un cambio de tamaño CONSERVANDO lo que el usuario esta mirando.
 *
 * Antes esto llamaba a reset(), que devolvia el mapa al encuadre inicial. El efecto era
 * que el plano "se sacudia" cada vez que algo cambiaba el alto de la pagina —por ejemplo
 * al aparecer un aviso sobre el mapa al reservar—, como si se recargara. Ahora se calcula
 * que punto del plano estaba centrado y se vuelve a centrar ese mismo punto.
 */
function alRedimensionar() {
  const r = vpRect();
  if (!r.width) return;
  if (!anchoPrevio) { anchoPrevio = r.width; return; }
  if (Math.abs(r.width - anchoPrevio) < 0.5) return; // solo cambio el alto: el transform sigue valido

  const mundoAncho = anchoPrevio;
  const mundoAlto = anchoPrevio * props.aspect;
  const nx = (r.width / 2 - t.x) / (mundoAncho * t.scale);
  const ny = (r.height / 2 - t.y) / (mundoAlto * t.scale);
  anchoPrevio = r.width;
  publicarAncho(anchoPrevio);
  focusOn(nx, ny, t.scale);
}

onMounted(() => {
  anchoPrevio = vpRect().width;
  publicarAncho(anchoPrevio);
  reset();
  ro = new ResizeObserver(alRedimensionar);
  ro.observe(viewport.value);
});
onBeforeUnmount(() => {
  if (ro) ro.disconnect();
  clearTimeout(temporizadorNitidez);
});

defineExpose({ focusOn, reset, zoomAt });
</script>

<template>
  <div
    class="viewport"
    :class="{ llenar }"
    ref="viewport"
    @wheel="onWheel"
    @pointerdown="onDown"
    @pointermove="onMove"
    @pointerup="onUp"
    @pointercancel="onUp"
  >
    <div class="world" ref="world">
      <slot />
    </div>
    <!-- pointerdown.stop: sin esto el toque llega primero a onDown, que captura el puntero
         en .viewport para poder arrastrar el plano. Una vez capturado ahi, Chrome redirige
         el "click" de compatibilidad al contenedor en vez de al boton, y el boton no hace
         nada — asi es como un vendedor "le da al boton y no pasa nada" y termina pellizcando
         para alejarse en su lugar. Los pines del mapa ya llevan este mismo .stop. -->
    <div class="controls">
      <button @pointerdown.stop @click="zoomAt(vpRect().left + vpRect().width / 2, vpRect().top + vpRect().height / 2, 1.3)">+</button>
      <button @pointerdown.stop @click="zoomAt(vpRect().left + vpRect().width / 2, vpRect().top + vpRect().height / 2, 1 / 1.3)">−</button>
      <button @pointerdown.stop title="Ajustar" @click="reset">⤢</button>
    </div>
  </div>
</template>

<style scoped>
.viewport {
  position: relative; width: 100%; height: 78vh; overflow: hidden;
  background: #f8fafc; border: 1px solid var(--border); border-radius: 10px;
  touch-action: none; cursor: grab;
  /* Arrastrar el plano no debe seleccionar nada: sin esto el navegador pinta el tipico
     velo azul de seleccion sobre todo lo que cruza el puntero. Y en el APK, quita el
     destello gris del tap. */
  user-select: none; -webkit-user-select: none;
  -webkit-tap-highlight-color: transparent;
}
.viewport:active { cursor: grabbing; }
/* Ocupa el hueco que quede en el contenedor flex. min-height:0 es lo que le permite
   encogerse por debajo de su contenido: sin eso un hijo alto lo empuja y vuelve a
   desbordar la pantalla. */
.viewport.llenar { height: auto; flex: 1; min-height: 0; border-radius: 0; border-left: none; border-right: none; }
/* Sin `will-change` aqui: lo pone y lo quita el JS alrededor de cada gesto (ver
   promoverCapa). Fijo en el CSS congelaba la escala de rasterizado y era lo que hacia que
   al acercarse se viera todo borroso. */
.world { position: absolute; top: 0; left: 0; width: 100%; transform-origin: 0 0; }
.controls {
  position: absolute; right: 10px; bottom: 10px; display: flex; flex-direction: column; gap: 4px;
}
.controls button {
  width: 38px; height: 38px; border-radius: 8px; border: 1px solid var(--border);
  background: #fff; font-size: 1.1rem; box-shadow: 0 1px 3px rgba(0, 0, 0, 0.15);
}

@media (max-width: 820px) {
  /* Para los visores con alto fijo (el editor). El mapa usa `llenar` y no pasa por aca. */
  .viewport { height: calc(78vh - var(--tabbar-h)); }
  /* 38px se queda corto para el dedo (mínimo recomendado ~44px táctil). */
  .controls button { width: 46px; height: 46px; font-size: 1.3rem; }
}
</style>
