<script setup>
import { ref, onMounted, onUnmounted } from 'vue';

/*
 * Pantalla de bienvenida: el logo mientras la app termina de arrancar.
 *
 * Va sobre NEGRO a proposito, el mismo negro del splash nativo de Android. Asi el paso del
 * splash del sistema a este no tiene costura: el usuario ve un solo arranque, no dos
 * pantallas distintas encadenadas.
 *
 * No bloquea nada. Por debajo, el router ya esta decidiendo si toca el login o el inicio
 * —segun haya sesion o no—, asi que esto solo cubre el hueco que de todas formas existe
 * mientras se resuelven la sesion, el plano y las casetas. Cuando se va, lo de debajo ya
 * esta puesto.
 *
 * Se puede saltar tocando: un vendedor que abre la app veinte veces en una feria no tiene
 * por que esperar la animacion entera.
 */

const emit = defineEmits(['fin']);

/** Lo que dura en pantalla antes de empezar a irse. Corto: esto no es un anuncio. */
const VISIBLE_MS = 1500;
/** Lo que tarda en desvanecerse; tiene que coincidir con la transicion del CSS. */
const SALIDA_MS = 420;

const saliendo = ref(false);
let tVisible = null;
let tSalida = null;

function terminar() {
  if (saliendo.value) return;
  saliendo.value = true;
  tSalida = setTimeout(() => emit('fin'), SALIDA_MS);
}

onMounted(() => {
  tVisible = setTimeout(terminar, VISIBLE_MS);
});

onUnmounted(() => {
  clearTimeout(tVisible);
  clearTimeout(tSalida);
});
</script>

<template>
  <div class="bienvenida" :class="{ saliendo }" @click="terminar" role="presentation">
    <div class="halo"></div>
    <div class="marca">
      <img src="/logo-fexpo.png" alt="FEXPO UAP" width="640" height="433" />
      <div class="barra"><span></span></div>
    </div>
  </div>
</template>

<style scoped>
.bienvenida {
  position: fixed; inset: 0; z-index: 3000;
  display: grid; place-items: center;
  /* El mismo negro que el splash nativo: sin costura entre los dos arranques. */
  background: #000;
  overflow: hidden;
  transition: opacity 0.42s ease;
}
.bienvenida.saliendo { opacity: 0; pointer-events: none; }

/* Un resplandor verde que se abre detras del logo. Da profundidad sin tapar nada y es lo
   que hace que el negro no se sienta una pantalla apagada. */
.halo {
  position: absolute; width: 140vmin; height: 140vmin; border-radius: 50%;
  background: radial-gradient(circle, rgba(20, 168, 58, 0.30) 0%, rgba(20, 168, 58, 0.08) 42%, transparent 68%);
  animation: abrir 1.5s cubic-bezier(0.16, 1, 0.3, 1) both;
}

.marca { position: relative; display: flex; flex-direction: column; align-items: center; gap: 1.6rem; }

/* El logo entra desde un poco mas abajo y algo mas pequeño. La curva es la misma del halo:
   arranca rapido y frena al final, que es lo que se siente "moderno" y no "lento". */
.marca img {
  width: min(72vw, 420px); height: auto; display: block;
  filter: drop-shadow(0 0 28px rgba(20, 168, 58, 0.45));
  animation: entrar 0.85s cubic-bezier(0.16, 1, 0.3, 1) both;
}

/* Barra de progreso indeterminada: no miente con un porcentaje, solo dice "sigo trabajando". */
.barra {
  width: min(48vw, 220px); height: 3px; border-radius: 999px;
  background: rgba(255, 255, 255, 0.14); overflow: hidden;
  animation: aparecer 0.5s 0.45s ease both;
}
.barra span {
  display: block; width: 40%; height: 100%; border-radius: inherit;
  background: linear-gradient(90deg, transparent, #7ED321, #14A83A, transparent);
  animation: recorrer 1.15s ease-in-out infinite;
}

@keyframes entrar {
  from { opacity: 0; transform: translateY(18px) scale(0.92); }
  to   { opacity: 1; transform: none; }
}
@keyframes abrir {
  from { opacity: 0; transform: scale(0.55); }
  to   { opacity: 1; transform: scale(1); }
}
@keyframes aparecer { from { opacity: 0; } to { opacity: 1; } }
@keyframes recorrer {
  from { transform: translateX(-110%); }
  to   { transform: translateX(360%); }
}

/* Quien pidió menos movimiento ve el logo puesto, sin nada animado. */
@media (prefers-reduced-motion: reduce) {
  .halo, .marca img, .barra, .barra span { animation: none; }
  .barra span { width: 100%; }
}
</style>
