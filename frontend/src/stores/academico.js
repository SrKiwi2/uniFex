import { ref, computed } from 'vue';
import { defineStore } from 'pinia';
import { apiFetch } from '../api.js';

/**
 * El catalogo de areas y carreras de la UAP (ACEF, ACBN, ACYT y sus carreras).
 *
 * Vive en un store y no en cada vista porque lo piden tres pantallas —Personas, Usuarios y
 * Vendedores— y es el mismo dato inmovil para todas: pedirlo una vez por vista serian tres
 * peticiones identicas cada vez que alguien navega entre ellas.
 *
 * `asegurar()` es idempotente: la primera llamada pide y las demas comparten esa promesa. Si
 * falla, la promesa se SUELTA para que el siguiente intento vuelva a pedir — memorizar el
 * fallo dejaria los desplegables vacios el resto de la sesion por un corte de red al arrancar,
 * que en un telefono es lo normal.
 */
export const useAcademicoStore = defineStore('academico', () => {
  const areas = ref([]);
  const cargado = ref(false);
  let promesa = null;

  /** Todas las carreras, planas, cada una con la sigla de su area. Para buscar por id. */
  const carreras = computed(() => areas.value.flatMap((a) => a.carreras || []));

  const nombreDeCarrera = (id) => carreras.value.find((c) => c.id === id)?.nombre || null;

  /**
   * Como se escribe un area: "ACYT", o "ACYT · <nombre largo>" cuando hay uno de verdad.
   *
   * V35 siembra el nombre IGUAL que la sigla, porque los nombres oficiales no estaban
   * confirmados. Sin esta comprobacion, cada desplegable diria "ACYT · ACYT" hasta que
   * alguien los rellene; con ella, el dia que se rellenen aparecen solos.
   */
  const etiquetaArea = (a) =>
    (a?.nombre && a.nombre !== a.sigla ? `${a.sigla} · ${a.nombre}` : a?.sigla || '');

  function asegurar() {
    if (promesa) return promesa;
    promesa = (async () => {
      try {
        const r = await apiFetch('/api/app/areas');
        if (!r.ok) throw new Error('No se pudo cargar áreas y carreras');
        areas.value = await r.json();
        cargado.value = true;
      } catch (e) {
        promesa = null;
        throw e;
      }
    })();
    return promesa;
  }

  return { areas, carreras, cargado, asegurar, nombreDeCarrera, etiquetaArea };
});
