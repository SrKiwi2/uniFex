import { ref, computed } from 'vue';
import { defineStore } from 'pinia';
import { apiFetch } from '../api.js';
import { crearClientePuestos } from '../ws.js';
import { cerrarMedicion } from '../ui/medir.js';

/**
 * Estado compartido de las casetas: UNA descarga y UNA conexion WebSocket para toda la app.
 *
 * Antes cada vista (Mapa, Tablero, Editor) se bajaba su propia copia de las ~530 casetas y
 * abria su propio cliente STOMP. Como las tres viven dentro de <KeepAlive>, `onUnmounted`
 * nunca se disparaba al navegar, asi que las conexiones quedaban abiertas para siempre y
 * cada mensaje del servidor llegaba duplicado. Centralizarlo aqui arregla las dos cosas y,
 * de paso, le da tiempo real al Editor, que no lo tenia.
 *
 * Ciclo de vida: `asegurar()` es idempotente y lo llaman las vistas al montarse;
 * `desconectar()` lo llama AppLayout al desmontarse, que es exactamente cuando se cierra
 * la sesion (el /login vive fuera del layout).
 */
export const usePuestosStore = defineStore('puestos', () => {
  const puestos = ref([]);
  const cargando = ref(false);
  const error = ref('');
  /** true cuando el WebSocket esta conectado: si es false, el mapa puede estar desfasado. */
  const enVivo = ref(false);

  let cliente = null;
  let promesaCarga = null;
  let alRechazar = null;

  /**
   * Casetas con cambios locales sin guardar. El Editor las registra para que un broadcast
   * no le pise la geometria que el usuario acaba de mover y todavia no ha guardado.
   * Cada guardia es una funcion (id) => boolean.
   */
  const guardias = new Set();

  /**
   * Oyentes de notificaciones personales (V11: solicitudes de cancelacion). Las vistas
   * registran aqui su handler al montarse y lo retiran al desmontarse; cuando llega un
   * mensaje al topic personal, se llama a todos. Asi MisVentas y Inscripciones se
   * refrescan solas cuando el otro lado resuelve, sin recargar la pagina.
   */
  const oyentesNotificacion = new Set();
  function registrarNotificaciones(fn) {
    oyentesNotificacion.add(fn);
    return () => oyentesNotificacion.delete(fn);
  }

  const ubicadas = computed(() => puestos.value.filter((p) => p.mapaX != null && p.mapaY != null));

  /**
   * El carrito del vendedor: las casetas que tiene en tramite ahora mismo.
   *
   * No se guarda en ningun sitio ni se pide aparte — **se deduce** de la lista que ya
   * tenemos. Eso significa que sobrevive a recargar la pagina, a cerrar el movil y a un
   * corte de señal, porque la verdad vive en la base de datos (estado 'T' + el id del
   * vendedor), no en memoria del navegador.
   *
   * `idUsuario` se pasa como argumento en vez de leer el store de sesion aqui, para que
   * este store no dependa del de autenticacion.
   */
  function carritoDe(idUsuario) {
    return puestos.value.filter((p) => p.estado === 'T' && p.reservadoPor === idUsuario);
  }

  /**
   * Aplica un PuestoEstadoDTO recibido por WebSocket.
   * Cubre alta, cambio y baja (`activo: false`), que es lo que necesita el editor.
   */
  function aplicar(dto) {
    const i = puestos.value.findIndex((p) => p.id === dto.id);
    if (dto.activo === false) {
      if (i >= 0) puestos.value.splice(i, 1);
      return;
    }
    if (i < 0) {
      puestos.value.push(dto);
      return;
    }
    const local = puestos.value[i];
    const protegida = [...guardias].some((g) => g(dto.id));
    if (protegida) {
      // Se acepta el estado de venta (que es la verdad del servidor) pero se conserva la
      // geometria local: si no, mover una caseta y recibir un broadcast la devolveria de golpe
      // a su sitio anterior y el usuario perderia el arrastre.
      puestos.value[i] = {
        ...dto,
        mapaX: local.mapaX,
        mapaY: local.mapaY,
        mapaEscala: local.mapaEscala,
      };
    } else {
      puestos.value[i] = dto;
    }
  }

  /** Registra un guardia de cambios sin guardar. Devuelve la funcion para quitarlo. */
  function protegerLocales(fn) {
    guardias.add(fn);
    return () => guardias.delete(fn);
  }

  /**
   * Descarga la lista. Las llamadas concurrentes comparten la misma peticion: si el Mapa y
   * el Tablero se montan a la vez, se hace UN solo GET.
   */
  function cargar(forzar = false) {
    if (promesaCarga && !forzar) return promesaCarga;
    cargando.value = true;
    error.value = '';
    promesaCarga = (async () => {
      try {
        const r = await apiFetch('/api/app/puestos');
        puestos.value = await r.json();
      } catch (e) {
        error.value = e.message;
        promesaCarga = null; // permitir reintento tras un fallo
        throw e;
      } finally {
        cargando.value = false;
      }
    })();
    return promesaCarga;
  }

  /** Vuelve a pedir la lista al servidor (altas o bajas en lote hechas desde el Editor). */
  function recargar() {
    return cargar(true);
  }

  /** Abre la conexion en tiempo real. Idempotente: la segunda llamada no hace nada. */
  function conectar(onRechazo) {
    if (onRechazo) alRechazar = onRechazo;
    if (cliente) return;
    cliente = crearClientePuestos(
      // La medicion se cierra AQUI y no dentro de aplicar(), porque aplicar() lo usa
      // tambien la escritura optimista: si estuviera dentro, cada medicion se cerraria
      // a los 0 ms contra su propio pintado local en vez de contra el aviso del servidor.
      (dto) => { cerrarMedicion(dto.id); aplicar(dto); },
      (motivo) => {
        enVivo.value = false;
        cliente = null;
        if (alRechazar) alRechazar(motivo);
      },
      () => { enVivo.value = true; },
      // Notificaciones personales: se reparten a los oyentes registrados por las vistas.
      (notificacion) => oyentesNotificacion.forEach((fn) => fn(notificacion)),
    );
  }

  /** Carga los datos y abre el tiempo real. Es lo que llaman las vistas al montarse. */
  async function asegurar(onRechazo) {
    conectar(onRechazo);
    return cargar();
  }

  /** Cierra la conexion y vacia el estado. Lo llama AppLayout al salir de la sesion. */
  function desconectar() {
    if (cliente) {
      cliente.deactivate();
      cliente = null;
    }
    enVivo.value = false;
    puestos.value = [];
    promesaCarga = null;
    error.value = '';
  }

  return {
    puestos, cargando, error, enVivo, ubicadas, carritoDe,
    aplicar, protegerLocales, cargar, recargar, conectar, asegurar, desconectar,
    registrarNotificaciones,
  };
});
