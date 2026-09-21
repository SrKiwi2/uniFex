<script setup>
import { computed } from 'vue';

/**
 * Cuánto se lleva de un total: una barra por fila, con el avance en % y las cifras al lado.
 *
 * <h2>Por qué NO es una barra apilada</h2>
 * La primera versión de esta pantalla apilaba los cuatro estados de la caseta (vendida, en
 * trámite, libre, bloqueada) en una sola barra. El validador de paleta lo tumbó: rojo y ámbar
 * son hues vecinos y quedaban a ΔE 14,4 —por debajo del suelo de visión normal— y a 6,2 en
 * protanopia, es decir, dos segmentos pegados que mucha gente no distingue.
 *
 * Se podía pelear con los colores, pero el problema real era la FORMA. Lo que pregunta quien
 * mira esto es "¿cuánto llevo vendido de PYMES?", y eso es una magnitud contra un objetivo, no
 * la composición de cuatro categorías. Una sola serie no necesita paleta categórica: no hay
 * pares adyacentes que confundir, ni leyenda de colores que descifrar. El desglose de los
 * cuatro estados vive en la tabla de al lado, en números, que es donde se consulta el detalle.
 */
const props = defineProps({
  /** [{ etiqueta, valor, total, detalle }] — `detalle` es el texto pequeño bajo la barra. */
  items: { type: Array, default: () => [] },
  /** Texto para el caso vacío: "Sin datos" a secas parece un fallo de carga. */
  vacio: { type: String, default: 'Sin datos todavía.' },
  /** Cómo se escriben las cifras "valor de total" (p. ej. en Bs). Por defecto, tal cual. */
  formato: { type: Function, default: (v) => v },
});

const filas = computed(() => props.items.map((i) => {
  const total = Number(i.total) || 0;
  const valor = Number(i.valor) || 0;
  // Sobre el total, acotado: un valor mayor que el total —que no debería pasar, pero pasa
  // cuando los datos vienen de dos consultas— pintaría una barra fuera de su carril.
  const pct = total <= 0 ? 0 : Math.min(100, Math.max(0, (valor / total) * 100));
  return { ...i, valor, total, pct };
}));

const uno = (n) => Number(n || 0).toLocaleString('es-BO', { maximumFractionDigits: 1 });
</script>

<template>
  <div class="barras-meta">
    <div v-for="(f, i) in filas" :key="i" class="fila-meta">
      <div class="cab">
        <span class="etq" :title="f.etiqueta">{{ f.etiqueta }}</span>
        <!-- Etiqueta directa, siempre: es la única serie, así que el número va pegado a su
             barra en vez de obligar a leer un eje. -->
        <span class="cifra">
          <strong>{{ uno(f.pct) }}%</strong>
          <span class="muted"> · {{ formato(f.valor) }} de {{ formato(f.total) }}</span>
        </span>
      </div>
      <!-- role=progressbar: un lector de pantalla no ve el ancho del div. -->
      <div class="pista" role="progressbar" :aria-valuenow="Math.round(f.pct)"
           aria-valuemin="0" aria-valuemax="100" :aria-label="f.etiqueta">
        <div class="relleno" :style="{ width: f.pct + '%' }"></div>
      </div>
      <p v-if="f.detalle" class="detalle">{{ f.detalle }}</p>
    </div>
    <p v-if="!filas.length" class="vacio">{{ vacio }}</p>
  </div>
</template>

<style scoped>
.barras-meta { display: flex; flex-direction: column; gap: 0.85rem; }
/* `fila-meta` y no `fila`: en style.css hay una utilidad GLOBAL
   `.fila { display: flex; align-items: center }`, y `scoped` no protege de ella —solo añade
   especificidad a las reglas de este archivo, pero la global sigue casando con el elemento—.
   El `align-items: center` heredado encogía la pista a 0 px de ancho y centraba las etiquetas:
   la barra sencillamente no se veía, sin ningún error por ningún lado. El `stretch` explícito
   es el cinturón sobre el tirante. */
.fila-meta { display: flex; flex-direction: column; align-items: stretch; gap: 0.3rem; }
.cab { display: flex; justify-content: space-between; align-items: baseline; gap: 0.8rem; }
.etq {
  font-weight: 600; font-size: 0.92rem;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.cifra { white-space: nowrap; font-variant-numeric: tabular-nums; font-size: 0.9rem; }
.muted { color: var(--muted); }

/* La pista es el "resto": recesiva, para que lo que se lea sea el relleno. */
.pista {
  height: 10px; border-radius: 999px; overflow: hidden;
  background: color-mix(in srgb, var(--text) 10%, transparent);
}
/* Extremo redondeado de 4px anclado a la línea base, como manda la guía de marcas. La
   transición hace visible que el dato CAMBIÓ cuando llega una venta por WebSocket: sin ella,
   el salto es tan instantáneo que no se percibe. */
.relleno {
  height: 100%; border-radius: 0 4px 4px 0;
  background: var(--acento);
  transition: width 0.45s ease;
}
.detalle { margin: 0.1rem 0 0; font-size: 0.8rem; color: var(--muted); }
.vacio { margin: 0; color: var(--muted); font-size: 0.9rem; }

/* Sin animación para quien la ha desactivado: aquí es decorativa, no informa de nada. */
@media (prefers-reduced-motion: reduce) {
  .relleno { transition: none; }
}
</style>
