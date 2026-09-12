<script setup>
import { ref, onMounted, onUnmounted } from 'vue';

/*
 * Pantalla de bienvenida: el logo mientras la app termina de arrancar.
 *
 * Va sobre NEGRO a propósito, el mismo negro del splash nativo de Android. Así el paso del
 * splash del sistema a este no tiene costura: el usuario ve un solo arranque, no dos
 * pantallas distintas encadenadas.
 *
 * No bloquea nada. Por debajo, el router ya está decidiendo si toca el login o el inicio
 * —según haya sesión o no—, así que esto solo cubre el hueco que de todas formas existe
 * mientras se resuelven la sesión, el plano y las casetas. Cuando se va, lo de debajo ya
 * está puesto.
 *
 * Se puede saltar tocando: un vendedor que abre la app veinte veces en una feria no tiene
 * por qué esperar la animación entera.
 */

const emit = defineEmits(['fin']);

// La build de producción sirve la SPA bajo /app/ (ver VITE_BASE en el perfil Maven
// "frontend"), así que una ruta fija "/logo-fexpo.png" apunta a la raíz del dominio, donde
// el archivo no existe, y da 404 solo en producción (en dev, base es "/", coincide por
// casualidad). BASE_URL trae el prefijo correcto en cada entorno.
const BASE = import.meta.env.BASE_URL;

/** Lo que dura en pantalla antes de empezar a irse. Optimizado: más rápido para mejor UX. */
const VISIBLE_MS = 900;
/** Lo que tarda en desvanecerse; tiene que coincidir con la transición del CSS. */
const SALIDA_MS = 300;

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
      <img :src="`${BASE}logo-fexpo.png`" alt="FEXPO UAP" width="640" height="433" />
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
  transition: opacity 0.3s ease;
}
.bienvenida.saliendo { opacity: 0; pointer-events: none; }

/* Un resplandor verde que se abre detrás del logo. Da profundidad sin tapar nada y es lo
   que hace que el negro no se sienta una pantalla apagada. */
.halo {
  position: absolute; width: 140vmin; height: 140vmin; border-radius: 50%;
  background: radial-gradient(circle, rgba(20, 168, 58, 0.30) 0%, rgba(20, 168, 58, 0.08) 42%, transparent 68%);
  animation: abrir 0.9s cubic-bezier(0.16, 1, 0.3, 1) both;
}

.marca { position: relative; display: flex; flex-direction: column; align-items: center; gap: 1.6rem; }

/* El logo entra desde un poco más abajo y algo más pequeño. La curva es la misma del halo:
   arranca rápido y frena al final, que es lo que se siente "moderno" y no "lento". */
.marca img {
  width: min(72vw, 420px); height: auto; display: block;
  filter: drop-shadow(0 0 28px rgba(20, 168, 58, 0.45));
  animation: entrar 0.6s cubic-bezier(0.16, 1, 0.3, 1) both;
}

/* Barra de progreso indeterminada: no miente con un porcentaje, solo dice "sigo trabajando". */
.barra {
  width: min(48vw, 220px); height: 3px; border-radius: 999px;
  background: rgba(255, 255, 255, 0.14); overflow: hidden;
  animation: aparecer 0.35s 0.3s ease both;
}
.barra span {
  display: block; width: 40%; height: 100%; border-radius: inherit;
  background: linear-gradient(90deg, transparent, #7ED321, #14A83A, transparent);
  animation: recorrer 0.85s ease-in-out infinite;
}

@keyframes entrar {
  from { opacity: 0; transform: translateY(14px) scale(0.94); }
  to   { opacity: 1; transform: none; }
}
@keyframes abrir {
  from { opacity: 0; transform: scale(0.6); }
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
