<script setup>
import { toasts, cerrar } from '../ui/toast';
</script>

<template>
  <div class="host" aria-live="polite">
    <transition-group name="toast">
      <div v-for="t in toasts" :key="t.id" class="toast" :class="t.tipo" @click="cerrar(t.id)">
        {{ t.mensaje }}
      </div>
    </transition-group>
  </div>
</template>

<style scoped>
.host {
  position: fixed; right: 1rem; bottom: 1rem; z-index: 1000;
  display: flex; flex-direction: column; gap: 0.5rem; max-width: min(90vw, 380px);
}
.toast {
  padding: 0.7rem 0.9rem; border-radius: var(--radio-sm); cursor: pointer;
  background: var(--panel); border: 1px solid var(--border); box-shadow: var(--sombra-md);
  color: var(--text); font-size: 0.9rem; border-left: 4px solid var(--muted);
}
.toast.ok { border-left-color: var(--ok); }
.toast.error { border-left-color: var(--danger); }
.toast.info { border-left-color: var(--acento); }
.toast-enter-active, .toast-leave-active { transition: all 0.22s ease; }
.toast-enter-from, .toast-leave-to { opacity: 0; transform: translateY(8px); }

/* En movil los avisos van arriba a la izquierda: abajo a la derecha quedan justo donde
   esta el pulgar y el teclado, y en el formulario tapaban el campo que se acababa de
   tocar. Entran desde arriba, acorde a su nueva posicion. */
@media (max-width: 820px) {
  .host { top: calc(0.6rem + var(--safe-top)); left: 0.6rem; right: auto; bottom: auto; max-width: min(88vw, 340px); }
  .toast-enter-from, .toast-leave-to { transform: translateY(-8px); }
}
</style>
