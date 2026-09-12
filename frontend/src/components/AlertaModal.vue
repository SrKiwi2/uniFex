<script setup>
import { ref, computed, watch, nextTick, onMounted, onUnmounted } from 'vue';
import { alertaVisible, alertaConfig, cerrar } from '../ui/alerta';

const segundos = ref(0);
const botonPrincipal = ref(null);
let temporizador = null;
let intervalo = null;

const COLORES = { ok: 'var(--ok)', error: 'var(--danger)', advertencia: 'var(--tramite)', info: 'var(--acento)' };
const tipo = computed(() => (COLORES[alertaConfig.tipo] ? alertaConfig.tipo : 'info'));
const colorTipo = computed(() => COLORES[tipo.value]);

function limpiar() {
  clearTimeout(temporizador);
  clearInterval(intervalo);
  temporizador = null;
  intervalo = null;
}

function iniciarTemporizador() {
  limpiar();
  if (alertaConfig.ms <= 0) { segundos.value = 0; return; }
  segundos.value = Math.ceil(alertaConfig.ms / 1000);
  temporizador = setTimeout(cancelar, alertaConfig.ms);
  intervalo = setInterval(() => { if (segundos.value > 1) segundos.value--; }, 1000);
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

// Un aviso (sin pregunta) se cierra con un clic en cualquier parte del cuadro.
function alClicDialogo() {
  if (!alertaConfig.mostrarConfirmar) confirmar();
}

// AlertaModal vive montado siempre en App.vue (junto a ToastHost y Bienvenida), visible o
// no: bloquear el scroll en onMounted lo dejaba bloqueado desde el arranque de la app
// entera, en TODAS las rutas, se mostrara una alerta o no. Pasaba desapercibido porque las
// rutas bajo AppLayout desplazan un contenedor interno, no el body — pero una pagina suelta
// como /feria, que sí depende del scroll del documento, quedaba sin poder hacer scroll nunca.
//
// Se vigila también `serie`: una alerta que reemplaza a otra abierta reinicia su tiempo.
watch(() => (alertaVisible.value ? alertaConfig.serie : 0), (activa) => {
  if (activa) {
    iniciarTemporizador();
    document.body.style.overflow = 'hidden';
    // Foco en el botón: Enter o Espacio lo cierran, y el lector de pantalla lo anuncia.
    nextTick(() => botonPrincipal.value?.focus({ preventScroll: true }));
  } else {
    limpiar();
    document.body.style.overflow = '';
  }
});

function onTecla(e) {
  if (!alertaVisible.value) return;
  if (e.key === 'Escape') cancelar();
  else if (e.key === 'Enter') { e.preventDefault(); confirmar(); }
}

onMounted(() => document.addEventListener('keydown', onTecla));
onUnmounted(() => {
  limpiar();
  if (alertaVisible.value) document.body.style.overflow = '';
  document.removeEventListener('keydown', onTecla);
});
</script>

<template>
  <Transition name="modal">
    <div v-if="alertaVisible" class="overlay" @click.self="cancelar"
         role="alertdialog" aria-modal="true" aria-labelledby="alerta-titulo" aria-describedby="alerta-mensaje">
      <div class="dialogo" :class="{ cerrable: !alertaConfig.mostrarConfirmar }"
           :style="{ '--color-tipo': colorTipo }" @click="alClicDialogo">
        <!-- :key reinicia el dibujo del icono cuando una alerta reemplaza a otra. -->
        <svg :key="alertaConfig.serie" class="icono" viewBox="0 0 52 52" aria-hidden="true">
          <circle class="aro" cx="26" cy="26" r="24" pathLength="100" />
          <template v-if="tipo === 'ok'">
            <path class="trazo" d="M15 27.5l7.5 7.5L37.5 19" pathLength="100" />
          </template>
          <template v-else-if="tipo === 'error'">
            <path class="trazo" d="M18 18l16 16" pathLength="100" />
            <path class="trazo trazo-2" d="M34 18L18 34" pathLength="100" />
          </template>
          <template v-else-if="tipo === 'advertencia'">
            <path class="trazo" d="M26 14v15" pathLength="100" />
            <circle class="punto" cx="26" cy="37" r="2" />
          </template>
          <template v-else>
            <circle class="punto" cx="26" cy="16" r="2" />
            <path class="trazo" d="M26 23v14" pathLength="100" />
          </template>
        </svg>

        <h2 id="alerta-titulo">{{ alertaConfig.titulo }}</h2>
        <p id="alerta-mensaje" class="mensaje">{{ alertaConfig.mensaje }}</p>
        <p v-if="alertaConfig.mostrarConfirmar && alertaConfig.ms > 0" class="temporizador">
          Se cerrará en {{ segundos }} s…
        </p>

        <footer class="pie">
          <template v-if="alertaConfig.mostrarConfirmar">
            <button type="button" class="btn btn-fantasma" @click.stop="cancelar">Cancelar</button>
            <button ref="botonPrincipal" type="button" class="boton-tipo" @click.stop="confirmar">Confirmar</button>
          </template>
          <button v-else ref="botonPrincipal" type="button" class="boton-tipo" @click.stop="confirmar">Ok</button>
        </footer>

        <!-- Barra que se consume: cuánto falta para que el aviso se cierre solo. -->
        <div v-if="alertaConfig.ms > 0 && !alertaConfig.mostrarConfirmar" :key="`barra-${alertaConfig.serie}`"
             class="barra-tiempo" :style="{ animationDuration: `${alertaConfig.ms}ms` }"></div>
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
  position: relative;
  width: 100%; max-width: 360px; max-height: 90vh; overflow: auto;
  padding: 1.8rem 1.5rem 1.5rem;
  text-align: center;
  background: var(--panel);
  color: var(--text);
  border-radius: 16px;
  box-shadow: var(--sombra-lg);
  animation: aparecer 0.22s ease;
}
.cerrable { cursor: pointer; }

.icono {
  display: block; width: 78px; height: 78px; margin: 0 auto 0.9rem;
  fill: none; stroke: var(--color-tipo); stroke-width: 2.4; stroke-linecap: round; stroke-linejoin: round;
}
/* Dibujo del icono: el aro primero, luego la marca (pathLength=100 iguala los largos). */
.aro, .trazo { stroke-dasharray: 100; stroke-dashoffset: 100; animation: dibujar 0.5s ease-out forwards; }
.trazo { stroke-width: 3; animation-delay: 0.3s; }
.trazo-2 { animation-delay: 0.45s; }
.punto { fill: var(--color-tipo); stroke: none; opacity: 0; animation: mostrar 0.2s ease-out 0.55s forwards; }

h2 { margin: 0 0 0.5rem; font-size: 1.3rem; font-weight: 700; color: var(--color-tipo); }
.mensaje { margin: 0; font-size: 0.92rem; line-height: 1.5; opacity: 0.85; white-space: pre-line; }
.temporizador { margin: 0.6rem 0 0; font-size: 0.8rem; color: var(--muted); }

.pie { display: flex; justify-content: center; gap: 0.6rem; margin-top: 1.3rem; }
.boton-tipo {
  min-width: 90px; padding: 0.5rem 1.6rem;
  border: 0; border-radius: 8px;
  background: var(--color-tipo); color: #fff;
  font: inherit; font-weight: 700; cursor: pointer;
}
.boton-tipo:hover { filter: brightness(0.92); }
.boton-tipo:focus-visible { outline: 3px solid color-mix(in srgb, var(--color-tipo) 45%, transparent); outline-offset: 2px; }

.barra-tiempo {
  position: absolute; left: 0; right: 0; bottom: 0; height: 4px;
  background: var(--color-tipo); transform-origin: left;
  animation: consumir linear forwards;
}

@keyframes aparecer { from { opacity: 0; transform: translateY(12px) scale(0.96); } to { opacity: 1; transform: none; } }
@keyframes dibujar { to { stroke-dashoffset: 0; } }
@keyframes mostrar { to { opacity: 1; } }
@keyframes consumir { from { transform: scaleX(1); } to { transform: scaleX(0); } }

.modal-enter-active, .modal-leave-active { transition: opacity 0.15s ease; }
.modal-enter-from, .modal-leave-to { opacity: 0; }

/* La barra de tiempo se queda: no es decoración, dice cuánto falta para que se cierre. */
@media (prefers-reduced-motion: reduce) {
  .dialogo, .aro, .trazo, .punto { animation: none; }
  .aro, .trazo { stroke-dashoffset: 0; }
  .punto { opacity: 1; }
}
</style>
