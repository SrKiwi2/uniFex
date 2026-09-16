<script setup>
import { computed, ref } from 'vue';

/**
 * Una serie en el tiempo: línea con área bajo ella y lectura al pasar por encima.
 *
 * SVG a mano y sin librería, como el resto de la aplicación: el paquete también se empaqueta
 * como APK y una librería de gráficos pesa más que todo lo que hay aquí dentro.
 *
 * <b>Una sola serie, así que no hay leyenda</b> —el título dice qué es— ni paleta categórica
 * que validar. Se usa el acento de la aplicación, que pasa el contraste mínimo contra el panel
 * en los dos temas; es el control que gobierna la legibilidad de una marca suelta.
 *
 * El eje Y arranca SIEMPRE en cero. Recortarlo para "que se vea mejor la pendiente" convierte
 * una subida del 3 % en una montaña, y esto lo mira quien decide.
 */
const props = defineProps({
  /** [{ x: 'etiqueta', y: número }] en orden cronológico. */
  puntos: { type: Array, default: () => [] },
  /** Cómo se lee un valor en el globo (p. ej. "1.200 Bs"). */
  formato: { type: Function, default: (v) => String(v) },
  alto: { type: Number, default: 180 },
});

// Coordenadas internas fijas: el SVG escala con viewBox, así que el dibujo no depende del
// ancho real y no hay que medir el contenedor ni reaccionar a que cambie.
const ANCHO = 600;
const MARGEN = { arriba: 10, derecha: 8, abajo: 22, izquierda: 8 };

const datos = computed(() => props.puntos.filter((p) => p && Number.isFinite(Number(p.y))));
const maximo = computed(() => Math.max(1, ...datos.value.map((p) => Number(p.y))));

const geo = computed(() => {
  const n = datos.value.length;
  const alto = props.alto;
  const util = { w: ANCHO - MARGEN.izquierda - MARGEN.derecha, h: alto - MARGEN.arriba - MARGEN.abajo };
  // Con UN punto no hay recta que trazar: se coloca en el centro para que el globo lo
  // encuentre, en vez de dejarlo pegado al borde izquierdo donde parece un error de dibujo.
  const x = (i) => (n <= 1 ? MARGEN.izquierda + util.w / 2
    : MARGEN.izquierda + (i / (n - 1)) * util.w);
  const y = (v) => MARGEN.arriba + util.h - (Number(v) / maximo.value) * util.h;
  return { x, y, util, alto };
});

const linea = computed(() => datos.value
  .map((p, i) => `${i === 0 ? 'M' : 'L'} ${geo.value.x(i).toFixed(1)} ${geo.value.y(p.y).toFixed(1)}`)
  .join(' '));

/** El área se cierra contra la línea base, no contra el borde inferior del SVG. */
const area = computed(() => {
  if (!datos.value.length) return '';
  const base = geo.value.alto - MARGEN.abajo;
  const primera = geo.value.x(0).toFixed(1);
  const ultima = geo.value.x(datos.value.length - 1).toFixed(1);
  return `${linea.value} L ${ultima} ${base} L ${primera} ${base} Z`;
});

// ---- lectura al pasar por encima ----
const activo = ref(-1);

/**
 * El punto más cercano al cursor, no el que esté justo debajo.
 *
 * Con treinta días en 600 px, exigir acertar sobre la marca deja el globo inalcanzable. Se
 * captura el ratón en TODO el rectángulo y se busca el índice más próximo.
 */
function alMover(e) {
  if (!datos.value.length) return;
  const caja = e.currentTarget.getBoundingClientRect();
  const px = ((e.clientX - caja.left) / caja.width) * ANCHO;
  let mejor = 0;
  let dist = Infinity;
  for (let i = 0; i < datos.value.length; i++) {
    const d = Math.abs(geo.value.x(i) - px);
    if (d < dist) { dist = d; mejor = i; }
  }
  activo.value = mejor;
}

const punto = computed(() => (activo.value >= 0 ? datos.value[activo.value] : null));
/** El globo se voltea al pasar la mitad: pegado al borde derecho se saldría del panel. */
const globoDerecha = computed(() => activo.value >= 0
  && geo.value.x(activo.value) > ANCHO * 0.6);
</script>

<template>
  <figure class="linea-tiempo">
    <svg v-if="datos.length" :viewBox="`0 0 ${ANCHO} ${alto}`" :style="{ height: alto + 'px' }"
         preserveAspectRatio="none" role="img"
         :aria-label="`Serie de ${datos.length} puntos, máximo ${formato(maximo)}`"
         @mousemove="alMover" @mouseleave="activo = -1">
      <!-- Línea base recesiva: orienta sin competir con el dato. -->
      <line :x1="0" :x2="ANCHO" :y1="alto - 22" :y2="alto - 22" class="eje" />

      <path :d="area" class="area" />
      <path :d="linea" class="curva" />

      <!-- Solo se marca el ÚLTIMO punto: un círculo en cada día convierte la curva en un
           collar y esconde justo la forma que se venía a ver. -->
      <circle v-if="datos.length > 1" :cx="geo.x(datos.length - 1)" :cy="geo.y(datos[datos.length - 1].y)"
              r="4.5" class="ultimo" />

      <template v-if="punto">
        <line :x1="geo.x(activo)" :x2="geo.x(activo)" :y1="10" :y2="alto - 22" class="guia" />
        <circle :cx="geo.x(activo)" :cy="geo.y(punto.y)" r="5" class="activo" />
      </template>
    </svg>
    <p v-else class="vacio">Todavía no hay ventas que dibujar.</p>

    <!-- El globo va en HTML y no en SVG: así hereda la tipografía y los tokens de color, y no
         hay que recortar el texto a mano dentro del dibujo. -->
    <div v-if="punto" class="globo" :class="{ derecha: globoDerecha }"
         :style="{ left: (geo.x(activo) / ANCHO * 100) + '%' }">
      <strong>{{ formato(punto.y) }}</strong>
      <span>{{ punto.x }}</span>
    </div>
  </figure>
</template>

<style scoped>
.linea-tiempo { position: relative; margin: 0; }
svg { width: 100%; display: block; overflow: visible; }

.eje { stroke: color-mix(in srgb, var(--text) 18%, transparent); stroke-width: 1; }
/* 2px, como manda la guía de marcas: más fino se pierde en pantallas normales y más grueso
   compite con el texto. */
.curva { fill: none; stroke: var(--acento); stroke-width: 2; stroke-linejoin: round; stroke-linecap: round; }
.area { fill: color-mix(in srgb, var(--acento) 16%, transparent); stroke: none; }
.ultimo { fill: var(--acento); }
/* Anillo del color del panel: separa la marca del área cuando se superponen. */
.activo { fill: var(--acento); stroke: var(--panel); stroke-width: 2; }
.guia { stroke: color-mix(in srgb, var(--text) 28%, transparent); stroke-width: 1; stroke-dasharray: 3 3; }

.globo {
  position: absolute; top: 0; transform: translateX(-50%);
  background: var(--panel); border: 1px solid var(--border); border-radius: var(--radio-sm);
  box-shadow: var(--sombra-md); padding: 0.35rem 0.6rem;
  display: flex; flex-direction: column; gap: 0.1rem;
  font-size: 0.82rem; white-space: nowrap; pointer-events: none;
}
.globo.derecha { transform: translateX(-100%); }
.globo span { color: var(--muted); }
.vacio { margin: 0; padding: 1.5rem 0; text-align: center; color: var(--muted); font-size: 0.9rem; }
</style>
