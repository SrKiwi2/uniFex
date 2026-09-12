<script setup>
import { ref, onMounted, onUnmounted, computed, watch } from 'vue';
import { alertaVisible, alertaConfig, cerrar } from '../ui/alerta';

const segundos = ref(0);
let intervalo = null;

const iconoTipo = computed(() => {
  const mapa = { ok: '✅', error: '❌', advertencia: '⚠️', info: 'ℹ️' };
  return mapa[alertaConfig.tipo] || '🔔';
});

const colorTipo = computed(() => {
  const mapa = { ok: 'var(--ok)', error: 'var(--danger)', advertencia: 'var(--tramite)', info: 'var(--acento)' };
  return mapa[alertaConfig.tipo] || 'var(--acento)';
});

function iniciarTemporizador() {
  if (alertaConfig.ms <= 0) { segundos.value = 0; return; }
  segundos.value = Math.ceil(alertaConfig.ms / 1000);
  intervalo = setInterval(() => {
    segundos.value--;
    if (segundos.value <= 0) {
      clearInterval(intervalo);
      cerrar(false);
    }
  }, 1000);
}

function limpiar() {
  if (intervalo) { clearInterval(intervalo); intervalo = null; }
}

function confirmar() {
  limpiar();
  alertaConfig.onConfirmar?.();
  cerrar(true);
}

function cancelar() {
  limpiar();
  alertaConfig.onCerrar?.();
  cerrar(false);
}

// AlertaModal vive montado siempre en App.vue (junto a ToastHost y Bienvenida), visible o
// no: bloquear el scroll en onMounted lo dejaba bloqueado desde el arranque de la app
// entera, en TODAS las rutas, se mostrara una alerta o no. Pasaba desapercibido porque las
// rutas bajo AppLayout desplazan un contenedor interno, no el body — pero una pagina suelta
// como /feria, que sí depende del scroll del documento, quedaba sin poder hacer scroll nunca.
watch(alertaVisible, (visible) => {
  if (visible) {
    iniciarTemporizador();
    document.body.style.overflow = 'hidden';
  } else {
    limpiar();
    document.body.style.overflow = '';
  }
});

onMounted(() => {
  document.addEventListener('keydown', onTecla);
});

onUnmounted(() => {
  limpiar();
  if (alertaVisible.value) document.body.style.overflow = '';
  document.removeEventListener('keydown', onTecla);
});

function onTecla(e) {
  if (e.key === 'Enter' && alertaConfig.mostrarConfirmar) confirmar();
  if (e.key === 'Escape') cancelar();
}
</script>

<template>
  <Transition name="modal">
    <div v-if="alertaVisible" class="overlay" @click.self="cancelar" role="dialog" aria-modal="true" aria-labelledby="alerta-titulo">
      <div class="dialogo card" :style="{ borderTop: `4px solid ${colorTipo}` }">
        <header class="cabecera">
          <div class="icono-titulo">
            <span class="icono" :style="{ color: colorTipo }">{{ iconoTipo }}</span>
            <h2 id="alerta-titulo">{{ alertaConfig.titulo }}</h2>
          </div>
          <button v-if="!alertaConfig.mostrarConfirmar" class="btn btn-fantasma btn-icono" @click="cancelar" aria-label="Cerrar">✕</button>
        </header>
        <div class="cuerpo">
          <p>{{ alertaConfig.mensaje }}</p>
          <div v-if="alertaConfig.ms > 0 && alertaConfig.mostrarConfirmar" class="temporizador" :style="{ color: colorTipo }">
            Se cerrará en {{ segundos }}s…
          </div>
        </div>
        <footer v-if="alertaConfig.mostrarConfirmar" class="pie">
          <button class="btn btn-fantasma" @click="cancelar">Cancelar</button>
          <button class="btn btn-primario" :style="{ background: colorTipo, borderColor: colorTipo }" @click="confirmar">
            Confirmar
          </button>
        </footer>
      </div>
    </div>
  </Transition>
</template>

<style scoped>
.overlay {
  position: fixed; inset: 0; z-index: 2000;
  display: grid; place-items: center; padding: calc(1rem + var(--safe-top)) calc(1rem + var(--safe-right)) calc(1rem + var(--safe-bottom)) calc(1rem + var(--safe-left));
  background: rgba(2, 6, 23, 0.55); backdrop-filter: blur(3px);
}
.dialogo {
  width: 100%; max-width: 420px; max-height: 90vh; overflow: auto;
  box-shadow: var(--sombra-lg);
  animation: subir 0.2s ease;
}
@keyframes subir { from { opacity: 0; transform: translateY(12px) scale(0.98); } to { opacity: 1; transform: none; } }
.cabecera { display: flex; align-items: center; justify-content: space-between; padding: 1rem 1.2rem 0.6rem; }
.icono-titulo { display: flex; align-items: center; gap: 0.6rem; }
.icono { font-size: 1.5rem; }
.cabecera h2 { margin: 0; font-size: 1.15rem; }
.cuerpo { padding: 0.4rem 1.2rem 1rem; line-height: 1.5; }
.temporizador { margin-top: 0.6rem; font-size: 0.85rem; font-weight: 600; text-align: center; }
.pie { display: flex; justify-content: flex-end; gap: 0.6rem; padding: 0.8rem 1.2rem; border-top: 1px solid var(--border); }
.pie .btn { min-width: 100px; }
.modal-enter-active, .modal-leave-active { transition: opacity 0.15s ease; }
.modal-enter-from, .modal-leave-to { opacity: 0; }
</style>