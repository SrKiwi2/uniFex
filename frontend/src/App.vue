<script setup>
import { ref, onMounted } from 'vue';
import ToastHost from './components/ToastHost.vue';
import AlertaModal from './components/AlertaModal.vue';
import Bienvenida from './components/Bienvenida.vue';
import { iniciarTema } from './ui/tema';
import { EN_APK } from './config.js';

/*
 * ¿Se enseña la pantalla de bienvenida?
 *
 * En el APK, siempre: es el arranque de una app y encadena con el splash nativo.
 * En el navegador, solo la primera vez de cada pestaña. Un administrador recarga el editor
 * decenas de veces al armar el plano, y hacerle esperar la animacion cada vez lo convertiria
 * de saludo en estorbo. `sessionStorage` es justo lo que distingue "abri la app" de
 * "recargue": se vacia al cerrar la pestaña.
 */
const CLAVE = 'bienvenida.vista';
let yaVista = false;
try {
  yaVista = !EN_APK && sessionStorage.getItem(CLAVE) === '1';
} catch {
  // Modo privado o almacenamiento bloqueado: se enseña, que es lo inocuo.
}
const bienvenida = ref(!yaVista);

function cerrarBienvenida() {
  bienvenida.value = false;
  try {
    sessionStorage.setItem(CLAVE, '1');
  } catch {
    /* da igual: lo peor que pasa es que se vuelva a ver */
  }
}

onMounted(iniciarTema);
</script>

<template>
  <router-view />
  <ToastHost />
  <AlertaModal />
  <!-- Va el ultimo para quedar por encima sin pelear por z-index, y NO envuelve a la app:
       por debajo el router ya esta resolviendo si toca login o inicio. -->
  <Bienvenida v-if="bienvenida" @fin="cerrarBienvenida" />
</template>
