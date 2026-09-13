<script setup>
import { ref, onMounted } from 'vue';
import ToastHost from './components/ToastHost.vue';
import AlertaModal from './components/AlertaModal.vue';
import Bienvenida from './components/Bienvenida.vue';
import CargandoOverlay from './components/CargandoOverlay.vue';
import { iniciarTema } from './ui/tema';
import { EN_APK } from './config';

/*
 * ¿Se enseña la pantalla de bienvenida?
 *
 * En el APK y en la web, SIEMPRE: es el arranque de la app y encadena con el splash nativo
 * en Android, y en web funciona como preloader visual. Se muestra en cada carga/recarga
 * para dar una experiencia consistente y profesional.
 */
const bienvenida = ref(true);

function cerrarBienvenida() {
  bienvenida.value = false;
}

onMounted(() => {
  iniciarTema();
  if (EN_APK) document.documentElement.classList.add('apk');
  /*
   * Se retira el arranque estatico del index.html, que ya cumplio: tapar el hueco desde el
   * primer fotograma hasta que existio este componente. Se quita DESPUES de montar, no antes,
   * para que no haya ni un fotograma de pantalla en blanco entre los dos.
   */
  document.getElementById('arranque')?.remove();
});
</script>

<template>
  <router-view />
  <ToastHost />
  <AlertaModal />
  <!-- Encima de la aplicacion pero por debajo de la bienvenida y de las alertas: si algo
       falla mientras se carga, el mensaje de error tiene que quedar por delante. -->
  <CargandoOverlay />
  <!-- Va el ultimo para quedar por encima sin pelear por z-index, y NO envuelve a la app:
       por debajo el router ya esta resolviendo si toca login o inicio. -->
  <Bienvenida v-if="bienvenida" @fin="cerrarBienvenida" />
</template>
