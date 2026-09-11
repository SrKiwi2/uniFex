import { createApp } from 'vue';
import { createPinia } from 'pinia';
import App from './App.vue';
import router from './router';
import { EN_APK } from './config.js';
import './style.css';

/*
 * Marca el documento cuando corre dentro del APK.
 *
 * Sirve para una sola cosa, pero importante: poner un SUELO a los margenes seguros. Si el
 * WebView no reporta los insets —pasa segun version y fabricante—, `env()` devuelve 0 y la
 * cabecera queda debajo de los iconos de bateria y señal, sin poder tocarse. En el navegador
 * ese suelo sobraria (no hay barra de estado), de ahi que se marque solo el APK.
 */
if (EN_APK) document.documentElement.classList.add('apk');

createApp(App).use(createPinia()).use(router).mount('#app');
