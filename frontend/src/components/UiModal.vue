<script setup>
import { onMounted, onUnmounted } from 'vue';

const props = defineProps({
  titulo: { type: String, default: '' },
  ancho: { type: String, default: '480px' },
});
const emit = defineEmits(['cerrar']);

function onTecla(e) {
  if (e.key === 'Escape') emit('cerrar');
}
onMounted(() => window.addEventListener('keydown', onTecla));
onUnmounted(() => window.removeEventListener('keydown', onTecla));
</script>

<template>
  <div class="overlay" @click.self="emit('cerrar')">
    <div class="dialogo card" :style="{ maxWidth: ancho }" role="dialog" aria-modal="true">
      <header class="cabecera">
        <h2>{{ titulo }}</h2>
        <button class="btn btn-fantasma btn-icono" @click="emit('cerrar')" aria-label="Cerrar">✕</button>
      </header>
      <div class="cuerpo">
        <slot />
      </div>
      <footer v-if="$slots.pie" class="pie">
        <slot name="pie" />
      </footer>
    </div>
  </div>
</template>

<style scoped>
.overlay {
  position: fixed; inset: 0; z-index: 900; display: grid; place-items: center; padding: 1rem;
  background: rgba(2, 6, 23, 0.5); backdrop-filter: blur(2px);
}
.dialogo { width: 100%; max-height: 90vh; overflow: auto; box-shadow: var(--sombra-md); }
.cabecera { display: flex; align-items: center; justify-content: space-between; padding: 1rem 1.1rem 0.6rem; }
.cabecera h2 { margin: 0; font-size: 1.15rem; }
.cuerpo { padding: 0.4rem 1.1rem 1rem; display: flex; flex-direction: column; gap: 0.8rem; }
.pie { display: flex; justify-content: flex-end; gap: 0.5rem; padding: 0.8rem 1.1rem; border-top: 1px solid var(--border); }
</style>
