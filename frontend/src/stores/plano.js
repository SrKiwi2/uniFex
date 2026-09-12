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

/*
 * El plano tambien se recuerda entre arranques, y no solo por velocidad.
 *
 * Sin esto, cada arranque empieza con el plano de RESPALDO —otra imagen y, sobre todo, otra
 * proporcion— y salta al de verdad cuando contesta el servidor. En una red lenta eso son
 * segundos de plano equivocado y, cuando llega el bueno, un reencuadre a la vista. Guardar la
 * ficha (que son cuatro campos, no la imagen) hace que el segundo arranque abra ya con el
 * plano correcto; la imagen en si la cachea el navegador, que para eso el backend la sirve
 * con un año de caducidad (las rutas llevan UUID y nunca cambian de contenido).
 */
const CLAVE_CACHE = 'plano.cache.v1';

function leerCache() {
  try {
    const guardado = JSON.parse(localStorage.getItem(CLAVE_CACHE) || 'null');
    return guardado && guardado.url && guardado.ancho ? guardado : null;
  } catch {
    return null;
  }
}

function guardarCache(p) {
  try {
    localStorage.setItem(CLAVE_CACHE, JSON.stringify(p));
  } catch {
    // Cuota llena o modo privado: se pierde el arranque rapido, nada mas.
  }
}

export const usePlanoStore = defineStore('plano', () => {
  const plano = ref(leerCache() || { ...RESPALDO });
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

  /** Invalida el cache local (útil al detectar versión nueva o forzar refresco). */
  function invalidarCache() {
    try {
      localStorage.removeItem(CLAVE_CACHE);
    } catch {}
    plano.value = { ...RESPALDO };
    promesa = null;
  }

  /** Idempotente: la primera llamada baja, las demas comparten la misma peticion. */
  function asegurar() {
    if (promesa) return promesa;
    promesa = (async () => {
      try {
        const r = await apiFetch('/api/app/plano');
        if (r.ok) {
          const datos = await r.json();
          // Si el servidor dice que hay plano propio pero el cache local dice que no,
          // o viceversa, o cambió la versión, forzamos la actualización.
          if (datos.propio !== plano.value.propio || datos.version !== plano.value.version) {
            plano.value = datos;
            guardarCache(datos);
          }
        } else {
          console.warn('[Plano] API respondió error:', r.status);
          // Si el servidor responde error y tenemos el plano de respaldo en cache,
          // invalidamos para que no se quede "pegado" el respaldo en futuros intentos.
          if (!plano.value.propio) invalidarCache();
        }
      } catch (e) {
        console.warn('[Plano] Error al obtener plano del servidor:', e.message);
        // Sin red se sigue con el plano de respaldo: es preferible un mapa viejo a
        // ninguno, sobre todo en la feria, donde el wifi se cae.
      }
    })();
    return promesa;
  }

  /** Tras subir uno nuevo: refresca sin recargar la pagina. */
  function aplicar(nuevo) {
    if (!nuevo) return;
    plano.value = nuevo;
    guardarCache(nuevo);
  }

  return { plano, src, aspecto, asegurar, aplicar, invalidarCache };
});
