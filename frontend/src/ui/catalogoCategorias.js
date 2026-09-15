import { ref } from 'vue';
import { apiFetch } from '../api';

/**
 * Las categorias con sus OPCIONES de precio, pedidas una sola vez para toda la sesion.
 *
 * Una categoria puede venderse de varias formas ("PYMES" a 800, "PYMES con tarima" a 1.200), y
 * eso hay que poder decirlo en dos sitios a la vez: el rotulo del pin en el mapa y la ficha de
 * la caseta. Cada uno pidiendo lo suyo serian dos descargas de lo mismo, y la ficha ademas la
 * abre el vendedor decenas de veces por hora delante de un cliente.
 *
 * Son ~14 categorias y no cambian a media feria, asi que se cachean en el modulo. Si alguien
 * las edita desde el modulo de Categorias, lo que cambia en el acto es el PRECIO de cada caseta
 * —eso viaja por `/topic/puestos` y no depende de esto—; la lista de opciones se refresca al
 * recargar la aplicacion, que para un catalogo que se toca una vez al mes es suficiente.
 */
let cache = null;
let enVuelo = null;

/** Reactivo para que las vistas repinten cuando por fin llega. */
export const categorias = ref(null);

export async function asegurarCategorias() {
  if (cache) { categorias.value = cache; return cache; }
  if (!enVuelo) {
    enVuelo = (async () => {
      try {
        const r = await apiFetch('/api/app/categorias');
        cache = r.ok ? await r.json() : [];
      } catch {
        // Sin el catalogo, quien llama sigue teniendo el precio vigente que ya trae la caseta:
        // se pierde el matiz de "hay varios precios", no el dato principal.
        cache = [];
      } finally {
        enVuelo = null;
      }
      categorias.value = cache;
      return cache;
    })();
  }
  return enVuelo;
}

/** Las opciones de precio de una categoria. Vacio si no se sabe todavia o no tiene. */
export function opcionesDe(categoriaId) {
  if (!cache || categoriaId == null) return [];
  return cache.find((c) => c.id === categoriaId)?.opciones || [];
}

/**
 * ¿Esta caseta tiene VARIOS precios posibles?
 *
 * Solo cuando su categoria tiene mas de una opcion **y** la caseta no lleva precio propio: un
 * precio propio (V37) manda sobre las opciones, asi que anunciar varios ahi seria ofrecer
 * importes que a esa caseta no se le van a aplicar.
 */
export function tieneVariosPrecios(puesto) {
  if (!puesto || puesto.precioPropio) return false;
  return opcionesDe(puesto.categoriaId).length > 1;
}

/** Solo para las pruebas: olvida lo cacheado. */
export function olvidarCatalogo() {
  cache = null;
  enVuelo = null;
  categorias.value = null;
}
