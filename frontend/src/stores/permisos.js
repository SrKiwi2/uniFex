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

  /**
   * Idempotente: la primera llamada pide, las demas comparten la misma peticion.
   *
   * Si falla, la promesa se SUELTA. Antes se quedaba memorizada para siempre, asi que un
   * unico fallo de red al arrancar —lo normal en un telefono que aun no engancho el wifi—
   * dejaba los permisos sin cargar durante toda la sesion, sin un solo reintento.
   */
  function asegurar() {
    if (promesa) return promesa;
    hidratar();
    promesa = (async () => {
      try {
        const r = await apiFetch('/api/app/permisos/mias');
        if (!r.ok) throw new Error(`El servidor respondio ${r.status}`);
        const d = await r.json();
        pantallas.value = new Set(d.pantallas || []);
        loVeTodo.value = Boolean(d.loVeTodo);
        cargado.value = true;
        guardar();
      } catch {
        // Sin red se sigue con lo que hubiera en disco, si lo habia. Lo que no se puede es
        // dar el intento por cerrado: se suelta la promesa para que el siguiente lo repita.
        promesa = null;
      }
    })();
    return promesa;
  }

  /** Vuelve a preguntar, ignorando la copia en disco. La usa el aviso de cambio de permisos. */
  function recargar() {
    promesa = null;
    cargado.value = false;
    return asegurar();
  }

  /**
   * ¿Puede ver esta pantalla?
   *
   * Mientras no se sepa, se responde que NO —salvo Inicio, para que la aplicacion no quede
   * inservible— y esto es un cambio a conciencia respecto de como estaba antes.
   *
   * Antes se respondia que SI, con el argumento de que esconder un enlace no protege nada y
   * el servidor responde 403 igual. Lo segundo sigue siendo cierto; lo primero resulto ser el
   * problema. En el APK, si la peticion de permisos fallaba —telefono sin señal al abrir, que
   * es la situacion normal— el usuario se quedaba con TODO el menu a la vista: un vendedor
   * veia Usuarios, Roles y el Editor del plano. No podia hacer nada con ellos, pero la
   * aplicacion le estaba mintiendo sobre lo que es su trabajo, y eso se reporto como un fallo
   * grave y con razon.
   *
   * El coste de fallar cerrado es un parpadeo en el PRIMER arranque de cada instalacion. Del
   * segundo en adelante la copia en disco lo evita: `hidratar()` deja `cargado` en true antes
   * de que conteste el servidor.
   */
  const puedeVer = computed(() => (clave) => {
    if (loVeTodo.value) return true;
    if (!cargado.value) return clave === 'inicio';
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

  return { pantallas, loVeTodo, cargado, asegurar, recargar, puedeVer, limpiar };
});
