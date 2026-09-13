<script setup>
import { ref, watch, onUnmounted } from 'vue';
import { cargando, textoCarga, esperaMs, estadoEspera } from '../ui/cargando';

/*
 * El indicador de carga, montado una sola vez en App.vue.
 *
 * Aparece con RETRASO a proposito. Una peticion que tarda 150 ms no necesita anuncio, y
 * enseñar y quitar un velo en ese tiempo produce un parpadeo que se siente peor que no poner
 * nada. Solo se muestra si la espera se nota.
 */
const RETRASO_MS = 250;
/** El guiño solo aparece si la espera se hace de verdad larga. Si no, no seria un secreto. */
const MS_KIWI = 14000;

const visible = ref(false);
let tAparecer = null;
let cronometro = null;

watch(cargando, (activo) => {
  clearTimeout(tAparecer);
  clearInterval(cronometro);
  if (!activo) { visible.value = false; return; }
  tAparecer = setTimeout(() => { visible.value = true; }, RETRASO_MS);
  // El contador vive aqui y no en el modulo para que no corra cuando no hay nada que esperar.
  cronometro = setInterval(() => { esperaMs.value += 250; }, 250);
});

onUnmounted(() => { clearTimeout(tAparecer); clearInterval(cronometro); });
</script>

<template>
  <transition name="velo">
    <div v-if="visible" class="velo-carga" role="status" aria-live="polite">
      <div class="caja">
        <div class="aro" aria-hidden="true"><span></span><span></span><span></span></div>
        <p class="texto">{{ textoCarga }}</p>

        <!-- Lo que se dice cambia con la espera: a los pocos segundos es "va lento", y pasados
             nueve ya conviene sospechar de la señal en vez de seguir esperando en silencio. -->
        <p v-if="estadoEspera === 'lento'" class="pista">
          La conexión está lenta. Seguimos intentando…
        </p>
        <p v-else-if="estadoEspera === 'sin-senal'" class="pista aviso">
          Parece que no hay señal. No cierres la aplicación: se reintenta solo.
        </p>

        <p v-if="esperaMs >= MS_KIWI" class="kiwi">🥝 Kiwi estuvo aquí</p>
      </div>
    </div>
  </transition>
</template>

<style scoped>
/*
 * Translucido y no opaco: por debajo se sigue viendo la pantalla donde estaba el usuario. Un
 * muro opaco refuerza la sensacion de "se colgo", que es justo lo que esto viene a evitar.
 * Lo que si hace es comerse los toques, para que no se dispare dos veces la misma venta.
 */
.velo-carga {
  position: fixed; inset: 0; z-index: 2500;
  display: grid; place-items: center;
  background: rgba(2, 6, 23, 0.35);
  backdrop-filter: blur(1.5px);
}
.caja {
  display: flex; flex-direction: column; align-items: center; gap: 0.7rem;
  padding: 1.4rem 1.6rem; border-radius: var(--radio);
  background: var(--panel); border: 1px solid var(--border); box-shadow: var(--sombra-md);
  max-width: min(86vw, 340px); text-align: center;
}
.texto { margin: 0; font-weight: 700; font-size: 1rem; }
.pista { margin: 0; font-size: 0.86rem; color: var(--muted); line-height: 1.45; }
.pista.aviso { color: var(--tramite); font-weight: 600; }
.kiwi { margin: 0.2rem 0 0; font-size: 0.78rem; color: var(--muted); opacity: 0.65; }

/* Tres puntos que rebotan. Sin librería y sin imagen: son tres nodos y una animación. */
.aro { display: flex; gap: 0.4rem; }
.aro span {
  width: 10px; height: 10px; border-radius: 50%; background: var(--acento);
  animation: rebote 1.1s ease-in-out infinite;
}
.aro span:nth-child(2) { animation-delay: 0.15s; }
.aro span:nth-child(3) { animation-delay: 0.3s; }
@keyframes rebote {
  0%, 80%, 100% { transform: translateY(0); opacity: 0.5; }
  40%           { transform: translateY(-7px); opacity: 1; }
}

.velo-enter-active, .velo-leave-active { transition: opacity 0.18s ease; }
.velo-enter-from, .velo-leave-to { opacity: 0; }

@media (prefers-reduced-motion: reduce) {
  .aro span { animation: none; opacity: 1; }
}
</style>
