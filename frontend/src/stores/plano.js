import { ref, computed } from 'vue';
import { defineStore } from 'pinia';
import { apiFetch } from '../api.js';
import { url as urlServidor } from '../config.js';

/**
 * El plano de la feria: la imagen de fondo del mapa y del editor.
 *
 * Antes era `/mapa.png`, un archivo empaquetado por Vite. Eso ataba el plano al APK:
 * cambiarlo obligaba a recompilar e instalar la app en cada telefono. Ahora lo sirve el
 * backend por edicion (V13), asi que reemplazarlo es subir una imagen.
 *
 * Se comparte entre Mapa y Editor por la misma razon que los puestos: las dos vistas viven
 * en <KeepAlive> y pedirlo por separado seria bajarlo dos veces.
 */
const RESPALDO = { url: '/mapa.png', ancho: 1836, alto: 2376, version: 0, propio: false };

export const usePlanoStore = defineStore('plano', () => {
  const plano = ref({ ...RESPALDO });
  let promesa = null;

  /**
   * URL lista para el <img>. Pasa por `urlServidor` porque el backend devuelve la ruta como
   * /files/..., que en el APK apuntaria al contenedor de Capacitor y no cargaria. El plano
   * de respaldo se queda relativo: ese si viaja dentro de la app.
   */
  const src = computed(() =>
    plano.value.propio ? urlServidor(plano.value.url) : plano.value.url);

  /** Alto/ancho: es lo que PanZoom necesita para encuadrar sin deformar. */
  const aspecto = computed(() => (plano.value.alto || 1) / (plano.value.ancho || 1));

  /** Idempotente: la primera llamada baja, las demas comparten la misma peticion. */
  function asegurar() {
    if (promesa) return promesa;
    promesa = (async () => {
      try {
        const r = await apiFetch('/api/app/plano');
        if (r.ok) plano.value = await r.json();
      } catch {
        // Sin red se sigue con el plano de respaldo: es preferible un mapa viejo a
        // ninguno, sobre todo en la feria, donde el wifi se cae.
      }
    })();
    return promesa;
  }

  /** Tras subir uno nuevo: refresca sin recargar la pagina. */
  function aplicar(nuevo) {
    if (nuevo) plano.value = nuevo;
  }

  return { plano, src, aspecto, asegurar, aplicar };
});
