<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue';

const props = defineProps({
  aspect: { type: Number, default: 2376 / 1836 }, // alto/ancho de la imagen
  min: { type: Number, default: 0.6 },
  max: { type: Number, default: 10 },
  // Enfoque inicial: centra el punto normalizado (fx,fy) al zoom fscale
  focus: { type: Object, default: () => ({ x: 0.45, y: 0.32, scale: 2.2 }) },
  // Si true, PanZoom NO captura el arrastre (lo deja al contenido, p.ej. selección del editor);
  // siguen activos la rueda y los botones.
  selectMode: { type: Boolean, default: false },
});

const viewport = ref(null);
const world = ref(null);
// NO es reactivo a proposito: el transform se escribe directo al DOM en pintar().
// Asi el pan/zoom (decenas de eventos por segundo) no re-renderiza el contenido —
// 500+ casetas del mapa — solo compone la capa transformada.
const t = { x: 0, y: 0, scale: 1 };

function pintar() {
  const el = world.value;
  if (el) el.style.transform = `translate(${t.x}px, ${t.y}px) scale(${t.scale})`;
}

const clamp = (v) => Math.min(props.max, Math.max(props.min, v));

function vpRect() { return viewport.value.getBoundingClientRect(); }

/** Centra el punto normalizado (nx,ny) de la imagen a la escala dada. */
function focusOn(nx, ny, scale) {
  const r = vpRect();
  const worldW = r.width;
  const worldH = r.width * props.aspect;
  t.scale = clamp(scale);
  t.x = r.width / 2 - nx * worldW * t.scale;
  t.y = r.height / 2 - ny * worldH * t.scale;
  pintar();
}

function reset() { focusOn(props.focus.x, props.focus.y, props.focus.scale); }

/** Zoom manteniendo fijo el punto (cx,cy) en coordenadas de pantalla. */
function zoomAt(cx, cy, factor) {
  const r = vpRect();
  const px = cx - r.left, py = cy - r.top;
  const wx = (px - t.x) / t.scale, wy = (py - t.y) / t.scale;
  const ns = clamp(t.scale * factor);
  t.scale = ns;
  t.x = px - wx * ns;
  t.y = py - wy * ns;
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
// Ancho del viewport con el que se calculo el transform actual. Al redimensionar, el
// mundo (width:100%) cambia de tamaño y el transform, que esta en pixeles, dejaria de
// apuntar al mismo sitio del plano.
let anchoPrevio = 0;

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
  focusOn(nx, ny, t.scale);
}

onMounted(() => {
  reset();
  anchoPrevio = vpRect().width;
  ro = new ResizeObserver(alRedimensionar);
  ro.observe(viewport.value);
});
onBeforeUnmount(() => { if (ro) ro.disconnect(); });

defineExpose({ focusOn, reset, zoomAt });
</script>

<template>
  <div
    class="viewport"
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
    <div class="controls">
      <button @click="zoomAt(vpRect().left + vpRect().width / 2, vpRect().top + vpRect().height / 2, 1.3)">+</button>
      <button @click="zoomAt(vpRect().left + vpRect().width / 2, vpRect().top + vpRect().height / 2, 1 / 1.3)">−</button>
      <button title="Ajustar" @click="reset">⤢</button>
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
.world { position: absolute; top: 0; left: 0; width: 100%; transform-origin: 0 0; will-change: transform; }
.controls {
  position: absolute; right: 10px; bottom: 10px; display: flex; flex-direction: column; gap: 4px;
}
.controls button {
  width: 38px; height: 38px; border-radius: 8px; border: 1px solid var(--border);
  background: #fff; font-size: 1.1rem; box-shadow: 0 1px 3px rgba(0, 0, 0, 0.15);
}
</style>
