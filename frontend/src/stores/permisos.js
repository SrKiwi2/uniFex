import { ref, computed } from 'vue';
import { defineStore } from 'pinia';
import { apiFetch } from '../api.js';

/**
 * Que pantallas ve este usuario.
 *
 * <h2>Que decide esto y que no</h2>
 * Decide el MENU y a que rutas deja entrar la aplicacion. **No autoriza nada**: cada endpoint
 * del servidor sigue protegido por su cuenta. Esconder "Editor del plano" quita el enlace; lo
 * que de verdad impide mover una caseta es que el servidor exige el rol. Si algun dia alguien
 * confia en esto para proteger algo, se equivoca de capa.
 *
 * Se pide UNA vez tras entrar y se guarda en disco para que el arranque siguiente pinte el
 * menu correcto antes de que conteste el servidor: sin eso, cada arranque enseña el menu de
 * todos y luego lo recorta a la vista, que se ve como un parpadeo.
 */
const CLAVE_CACHE = 'permisos.cache.v1';

export const usePermisosStore = defineStore('permisos', () => {
  const pantallas = ref(new Set());
  const loVeTodo = ref(false);
  const cargado = ref(false);
  let promesa = null;

  function hidratar() {
    try {
      const g = JSON.parse(localStorage.getItem(CLAVE_CACHE) || 'null');
      if (!g) return;
      pantallas.value = new Set(g.pantallas || []);
      loVeTodo.value = Boolean(g.loVeTodo);
      cargado.value = true;
    } catch {
      /* sin copia: se espera a la respuesta del servidor */
    }
  }

  function guardar() {
    try {
      localStorage.setItem(CLAVE_CACHE, JSON.stringify({
        pantallas: [...pantallas.value], loVeTodo: loVeTodo.value,
      }));
    } catch {
      /* modo privado o cuota llena: solo se pierde el arranque sin parpadeo */
    }
  }

  /** Idempotente: la primera llamada pide, las demas comparten la misma peticion. */
  function asegurar() {
    if (promesa) return promesa;
    hidratar();
    promesa = (async () => {
      try {
        const r = await apiFetch('/api/app/permisos/mias');
        if (!r.ok) return;
        const d = await r.json();
        pantallas.value = new Set(d.pantallas || []);
        loVeTodo.value = Boolean(d.loVeTodo);
        cargado.value = true;
        guardar();
      } catch {
        // Sin red se sigue con lo que hubiera en disco. Es preferible un menu de hace un rato
        // a uno vacio: lo que protege de verdad esta en el servidor, no aqui.
      }
    })();
    return promesa;
  }

  /**
   * ¿Puede ver esta pantalla?
   *
   * Mientras no se sepa (primer arranque, sin copia en disco) se responde que SI. Es
   * deliberado: decir que no dejaria al usuario mirando un menu vacio durante la primera
   * peticion, y entrar a una ruta que no le toca no le da acceso a nada — el servidor
   * responde 403 igual.
   */
  const puedeVer = computed(() => (clave) => {
    if (loVeTodo.value) return true;
    if (!cargado.value) return true;
    return pantallas.value.has(clave);
  });

  function limpiar() {
    pantallas.value = new Set();
    loVeTodo.value = false;
    cargado.value = false;
    promesa = null;
    try {
      localStorage.removeItem(CLAVE_CACHE);
    } catch {
      /* da igual */
    }
  }

  return { pantallas, loVeTodo, cargado, asegurar, puedeVer, limpiar };
});
