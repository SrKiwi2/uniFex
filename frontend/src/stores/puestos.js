import { ref, computed } from 'vue';
import { useAuthStore } from './auth.js';
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
  /** true mientras lo que se ve sale de la copia en disco y aun no lo confirmo el servidor. */
  const desdeCache = ref(false);
  /**
   * Quien responde por cada caseta: puestoId -> { vendedorId, vendedor, celular }.
   *
   * Va aparte de la lista de casetas porque cambia con OTRO ritmo —se toca desde la pantalla
   * de Vendedores, no con cada venta— y porque meterlo en PuestoEstadoDTO cargaria el nombre
   * y el telefono del vendedor en cada mensaje de WebSocket. Mismo criterio que las fotos.
   */
  const asignaciones = ref(new Map());
  /** Momento de la ultima lista completa recibida del servidor (ms). 0 = ninguna todavia. */
  const ultimaSync = ref(0);

  let cliente = null;
  let promesaCarga = null;
  let alRechazar = null;
  let yaConecto = false;
  /** Ultima lista tal cual la mando el servidor. Es la que se guarda en disco: la de pantalla
   *  puede llevar geometria local sin guardar del Editor, que no debe persistirse. */
  let ultimaLista = [];
  /*
   * Etiquetas HTTP de las dos lecturas. Se devuelven al servidor en `If-None-Match` y, si nada
   * cambio, contesta 304 SIN cuerpo: unos cientos de bytes en vez de la lista entera. Como el
   * cliente resincroniza a menudo —al reconectar, al volver del fondo, al sondear— y casi
   * siempre no ha cambiado nada, esto es lo que hace que el mapa siga siendo usable con mala
   * cobertura.
   */
  let etagPuestos = null;
  let etagAsignaciones = null;
  let sondeo = null;
  let oyentesCiclo = false;

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

  /*
   * ---- copia en disco ----
   *
   * Arrancar el APK contra el servidor de la feria tarda segundos, y en la feria la red es
   * mala: hasta ahora eso era un mapa en blanco mientras tanto. Se guarda la ultima lista
   * buena y se pinta al instante en el arranque siguiente, marcada como no confirmada; la
   * respuesta del servidor la corrige cuando llegue.
   *
   * localStorage y no una base: son ~530 casetas (unos 250 KB), se lee sincrono —que es lo
   * que permite pintar antes del primer fotograma— y si falla no pasa nada, porque es una
   * comodidad, no la fuente de la verdad.
   */
  const CLAVE_CACHE = 'puestos.cache.v1';

  /**
   * Opciones de una lectura condicionada.
   *
   * `cache: 'no-store'` es deliberado: sin el, el navegador puede revalidar por su cuenta y
   * devolver un 200 desde SU cache, y entonces nunca se veria el 304 ni se sabria si hubo
   * cambios. Aqui la etiqueta la lleva la app, no el navegador.
   */
  const condicional = (etag) => ({
    cache: 'no-store',
    ...(etag ? { headers: { 'If-None-Match': etag } } : {}),
  });

  function guardarCache() {
    if (!ultimaLista.length) return;
    try {
      localStorage.setItem(CLAVE_CACHE, JSON.stringify({
        ts: Date.now(), lista: ultimaLista, asignaciones: [...asignaciones.value.entries()],
      }));
    } catch {
      // Cuota llena o modo privado. La copia es un lujo: nunca se aborta una carga por esto.
    }
  }

  /** Pinta la ultima lista conocida. Devuelve true si habia algo que pintar. */
  function hidratarDesdeCache() {
    if (puestos.value.length) return false;
    try {
      const guardado = JSON.parse(localStorage.getItem(CLAVE_CACHE) || 'null');
      if (!guardado?.lista?.length) return false;
      puestos.value = guardado.lista;
      ultimaLista = guardado.lista;
      // Sin las asignaciones, el arranque en frio pintaria de gris hasta las casetas propias.
      if (Array.isArray(guardado.asignaciones)) {
        asignaciones.value = new Map(guardado.asignaciones);
      }
      desdeCache.value = true;
      return true;
    } catch {
      return false;
    }
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
  /**
   * ¿Este usuario ve el plano entero? Los vendedores (ADMINISTRATIVO) ven solo lo asignado, y el
   * servidor ya se lo filtra al pedir la lista. Se lee del rol y no de la lista recibida porque
   * un vendedor sin asignaciones tambien las recibe todas, y ahi si debe comportarse como admin.
   */
  function puedeVerTodas() {
    const rol = (useAuthStore().rol || '').toUpperCase().replace(/ /g, '_');
    return rol !== 'ADMINISTRATIVO';
  }

  function aplicar(dto) {
    const i = puestos.value.findIndex((p) => p.id === dto.id);
    if (dto.activo === false) {
      if (i >= 0) puestos.value.splice(i, 1);
      return;
    }
    if (i < 0) {
      // Una caseta que no estaba en la lista solo se agrega si el usuario puede verlas todas.
      //
      // El topic /topic/puestos es unico y global -- es el invariante que mantiene coherentes a
      // todos los clientes -- asi que a un vendedor con casetas asignadas tambien le llegan las
      // ajenas. Si se agregaran, el mapa se le iria llenando de casetas que no le tocan en cuanto
      // otro vendedor las tocara, y el filtro del servidor no serviria de nada. Las suyas ya estan
      // en la lista, asi que para el lo unico que aporta el mensaje es la ACTUALIZACION de estado,
      // no el alta.
      if (!puedeVerTodas()) return;
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

  /**
   * Sustituye la lista entera respetando los cambios locales sin guardar.
   *
   * Hace falta porque ahora la lista se vuelve a pedir SOLA (al reconectar, al volver del
   * fondo, al sondear). Antes solo se recargaba a peticion del usuario, asi que reemplazarla
   * a secas era inofensivo; hoy, con el Editor abierto y casetas movidas sin guardar, seria
   * deshacerle el trabajo sin avisar. Es la misma regla que aplica `aplicar` a un mensaje
   * suelto: se acepta todo del servidor menos la geometria que el usuario esta tocando.
   */
  function fusionar(lista) {
    if (!guardias.size) {
      puestos.value = lista;
      return;
    }
    const previos = new Map(puestos.value.map((p) => [p.id, p]));
    puestos.value = lista.map((dto) => {
      const local = previos.get(dto.id);
      if (!local || ![...guardias].some((g) => g(dto.id))) return dto;
      return { ...dto, mapaX: local.mapaX, mapaY: local.mapaY, mapaEscala: local.mapaEscala };
    });
  }

  /**
   * Aplica los cambios de asignacion que llegan por WebSocket.
   *
   * Es la mitad que faltaba: hasta ahora, reasignar casetas desde Vendedores no se veia en un
   * mapa ya abierto hasta la siguiente resincronizacion. Ahora el cambio entra al instante y
   * sin pedir nada, porque el mensaje trae la informacion, no un aviso de "vuelve a pedirlo".
   * Un `vendedorId` nulo significa que la caseta se quedo sin vendedor.
   */
  function aplicarAsignaciones(cambios) {
    if (!Array.isArray(cambios) || !cambios.length) return;
    const m = new Map(asignaciones.value);
    for (const a of cambios) {
      if (a?.puestoId == null) continue;
      if (a.vendedorId == null) m.delete(a.puestoId);
      else m.set(a.puestoId, a);
    }
    asignaciones.value = m;
    // La copia en disco tiene que quedar coherente, o el proximo arranque en frio pintaria
    // con la asignacion vieja hasta que conteste el servidor.
    guardarCache();
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
        // Las dos peticiones van juntas: el mapa necesita las dos para pintar bien —sin las
        // asignaciones, las casetas propias saldrian grises— y en serie serian dos esperas.
        // Y van CONDICIONADAS: si nada cambio desde la ultima vez, el servidor contesta 304
        // sin cuerpo y esto no descarga nada.
        const [r, rAsig] = await Promise.all([
          apiFetch('/api/app/puestos', condicional(etagPuestos)),
          apiFetch('/api/app/puestos/asignaciones', condicional(etagAsignaciones)).catch(() => null),
        ]);

        if (r.status !== 304) {
          etagPuestos = r.headers.get('ETag') || etagPuestos;
          const lista = await r.json();
          ultimaLista = lista;
          fusionar(lista);
        }
        if (rAsig && rAsig.status !== 304 && rAsig.ok) {
          etagAsignaciones = rAsig.headers.get('ETag') || etagAsignaciones;
          const filas = await rAsig.json().catch(() => []);
          asignaciones.value = new Map(filas.map((a) => [a.puestoId, a]));
        }

        desdeCache.value = false;
        ultimaSync.value = Date.now();
        guardarCache();
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

  /**
   * true cuando el servidor contesto y no hay ni una caseta.
   *
   * Para un vendedor eso significa que aun no le asignaron nada, no que el mapa este roto. Se
   * distingue de "todavia cargando" y de "estoy pintando la copia en disco" para poder decirselo
   * con palabras en vez de dejarlo mirando un plano vacio.
   */
  const sinAsignaciones = computed(() =>
    !cargando.value && !desdeCache.value && !error.value && puestos.value.length === 0);

  /*
   * ---- que el mapa no se quede viejo en silencio ----
   *
   * El tiempo real puede faltar por razones que no dependen de la app: una red que bloquea
   * el upgrade a WebSocket, el WebView del APK, un proxy, o simplemente Android cortando los
   * sockets de una app en segundo plano. Hasta ahora, cuando eso pasaba el mapa se congelaba
   * y NADA lo delataba: seguia pintando casetas verdes que ya estaban vendidas.
   *
   * Tres redes, de la mas barata a la mas cara:
   *   1. cada RE-conexion vuelve a pedir la lista (los mensajes perdidos no se recuperan);
   *   2. al volver la app al frente o al recuperar la red, se resincroniza siempre;
   *   3. mientras NO haya tiempo real, se sondea cada 20 s.
   * Con tiempo real funcionando, la 3 no hace ni una peticion.
   */
  const SONDEO_MS = 20000;

  function arrancarSondeo() {
    if (sondeo) return;
    sondeo = setInterval(() => {
      if (enVivo.value) return;
      if (typeof document !== 'undefined' && document.visibilityState === 'hidden') return;
      recargar().catch(() => {});
    }, SONDEO_MS);
  }

  /**
   * Al volver al frente se resincroniza SIEMPRE, sin mirar `enVivo`.
   *
   * Android corta los sockets de una app en segundo plano, asi que al volver lo que se ve
   * puede llevar horas de retraso. En un mapa de ventas eso es una caseta ya vendida pintada
   * de verde, que es la peor manera de equivocarse aqui. Es el caso de "cerre la app, volvi
   * y seguia igual".
   */
  function alVolverAlFrente() {
    if (typeof document !== 'undefined' && document.visibilityState !== 'visible') return;
    recargar().catch(() => {});
  }

  function escucharCicloDeVida() {
    if (oyentesCiclo || typeof document === 'undefined') return;
    document.addEventListener('visibilitychange', alVolverAlFrente);
    window.addEventListener('online', alVolverAlFrente);
    oyentesCiclo = true;
  }

  function dejarDeEscuchar() {
    if (!oyentesCiclo || typeof document === 'undefined') return;
    document.removeEventListener('visibilitychange', alVolverAlFrente);
    window.removeEventListener('online', alVolverAlFrente);
    oyentesCiclo = false;
  }

  /** Vuelve a intentar la conexion ahora mismo (lo ofrece el aviso de "sin tiempo real"). */
  function reintentar() {
    recargar().catch(() => {});
    if (!cliente) conectar();
  }

  /** Abre la conexion en tiempo real. Idempotente: la segunda llamada no hace nada. */
  function conectar(onRechazo) {
    if (onRechazo) alRechazar = onRechazo;
    escucharCicloDeVida();
    arrancarSondeo();
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
      () => {
        enVivo.value = true;
        // Una RE-conexion significa que hubo un hueco sin mensajes, y lo que pasara en ese
        // hueco (una venta, una caseta nueva, un carrito vaciado) NO llega despues: el topic
        // no guarda historial. La primera conexion no recarga, porque `asegurar` ya baja la
        // lista y seria pedirla dos veces en cada arranque.
        if (yaConecto) recargar().catch(() => {});
        yaConecto = true;
      },
      // Notificaciones personales: se reparten a los oyentes registrados por las vistas.
      (notificacion) => {
        // Un cambio de asignacion cambia QUE casetas puede ver este vendedor, y eso no llega
        // por /topic/puestos: ese canal difunde cambios de ESTADO de una caseta, no de permisos.
        // Sin esta recarga, el vendedor seguiria viendo su lista vieja hasta cerrar la app.
        if (notificacion?.tipo === 'ASIGNACION_CAMBIADA') recargar().catch(() => {});
        oyentesNotificacion.forEach((fn) => fn(notificacion));
      },
      () => { enVivo.value = false; },
      aplicarAsignaciones,
    );
  }

  /** Carga los datos y abre el tiempo real. Es lo que llaman las vistas al montarse. */
  async function asegurar(onRechazo) {
    // Se pinta lo ultimo que se supo ANTES de salir a la red. En el APK esa es la diferencia
    // entre ver el mapa al instante y mirar una pantalla en blanco varios segundos, que es
    // justo lo que pasa en la feria, donde la red es mala.
    hidratarDesdeCache();
    conectar(onRechazo);
    return cargar();
  }

  /** Cierra la conexion y vacia el estado. Lo llama AppLayout al salir de la sesion. */
  function desconectar() {
    if (cliente) {
      cliente.deactivate();
      cliente = null;
    }
    if (sondeo) {
      clearInterval(sondeo);
      sondeo = null;
    }
    dejarDeEscuchar();
    enVivo.value = false;
    yaConecto = false;
    puestos.value = [];
    promesaCarga = null;
    error.value = '';
    desdeCache.value = false;
    ultimaSync.value = 0;
    asignaciones.value = new Map();
    ultimaLista = [];
    // Las etiquetas se olvidan al cerrar sesion: la respuesta del siguiente usuario puede ser
    // otra, y arrastrar la etiqueta vieja pediria un 304 sobre datos que no son los suyos.
    etagPuestos = null;
    etagAsignaciones = null;
    // La copia en disco NO se borra: es el mismo mapa para todos los vendedores y es lo que
    // hace que el proximo arranque sea instantaneo. No lleva nada privado que no vuelva a
    // llegar en la primera respuesta.
  }

  return {
    puestos, cargando, error, enVivo, desdeCache, ultimaSync, ubicadas, carritoDe, asignaciones,
    aplicar, aplicarAsignaciones, protegerLocales, cargar, recargar, conectar, asegurar, desconectar,
    registrarNotificaciones, reintentar, sinAsignaciones,
  };
});
