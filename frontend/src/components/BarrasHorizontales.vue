<script setup>
import { computed } from 'vue';

/**
 * Barras horizontales de una sola serie (magnitud). Sin librería: divs con ancho en %.
 * Una serie => un solo color (el acento) y el valor como etiqueta directa; no necesita
 * leyenda ni paleta categórica (evita de raíz el problema de daltonismo).
 */
const props = defineProps({
  items: { type: Array, default: () => [] }, // [{ label, valor }]
  formato: { type: Function, default: (v) => v },
});

const max = computed(() => Math.max(1, ...props.items.map((i) => i.valor || 0)));
const ancho = (v) => `${Math.max(2, ((v || 0) / max.value) * 100)}%`;
</script>

<template>
  <div class="barras">
    <div v-for="(i, idx) in items" :key="idx" class="fila-barra">
      <span class="etq" :title="i.label">{{ i.label }}</span>
      <div class="pista">
        <div class="barra" :style="{ width: ancho(i.valor) }"></div>
      </div>
      <span class="val">{{ formato(i.valor) }}</span>
    </div>
    <p v-if="items.length === 0" class="vacio">Sin datos.</p>
  </div>
</template>

<style scoped>
.barras { display: flex; flex-direction: column; gap: 8px; }
.fila-barra { display: grid; grid-template-columns: minmax(90px, 180px) 1fr auto; align-items: center; gap: 0.7rem; }
.etq { font-size: 0.85rem; color: var(--muted); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.pista { background: var(--panel-2); border-radius: 4px; height: 16px; overflow: hidden; }
.barra {
  height: 100%; background: var(--acento); border-radius: 4px; min-width: 4px;
  transition: width 0.4s ease;
}
/* El valor va en tinta de texto, nunca dentro de la barra en color: mantiene el contraste. */
.val { font-size: 0.85rem; font-weight: 700; font-variant-numeric: tabular-nums; }
</style>
