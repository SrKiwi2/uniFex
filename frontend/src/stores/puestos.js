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
  /** true mientras lo que se ve sale de la copia en disco y aun no lo confirmo el servidor. */
  const desdeCache = ref(false);
  /**
   * Quien responde por cada caseta: puestoId -> [{ vendedorId, vendedor, celular }, ...].
   *
   * Es una LISTA por caseta, no un vendedor suelto: la misma caseta puede estar habilitada a
   * varios, y la vende quien la reserva primero. Una caseta que no lleva nadie no tiene entrada.
   *
   * Va aparte de la lista de casetas porque cambia con OTRO ritmo —se toca desde la pantalla
   * de Vendedores, no con cada venta— y porque meterlo en PuestoEstadoDTO cargaria el nombre
   * y el telefono del vendedor en cada mensaje de WebSocket. Mismo criterio que las fotos.
   */
  const asignaciones = ref(new Map());
  /**
   * Quien TIENE cada caseta no libre: puestoId -> { vendedorId, vendedor, celular, estado }.
   *
   * No confundir con `asignaciones`, que es quien PUEDE venderla y pueden ser varios. Esto es
   * uno solo: el que la reservo o el que ya la vendio.
   *
   * Se mantiene por dos vias distintas, y la diferencia importa:
   *
   * - **En tramite**: la difusion ya trae `reservadoPor`, asi que se resuelve AQUI, contra el
   *   `directorio`, sin pedir nada. Reservar es lo que mas pasa en la feria; salir al servidor
   *   por cada reserva ajena seria una peticion por cada toque de cada vendedor.
   * - **Vendida**: al vender, el servidor pone `reservado_por_id_usuario` a NULL, asi que el
   *   mensaje NO dice quien fue. Eso obliga a volver a pedir la lista, pero vender es raro
   *   comparado con reservar, y la peticion va agrupada (ver `pedirOcupacion`).
   */
  const ocupacion = ref(new Map());
  /**
   * vendedorId -> { vendedor, celular }. Es lo que permite ponerle nombre a un `reservadoPor`
   * recien llegado sin salir a la red. Se llena con las asignaciones y con la ocupacion, que
   * entre las dos nombran a todo el que puede aparecer en el plano.
   */
  const directorio = ref(new Map());
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
  let etagOcupacion = null;
  /** Temporizador de la relectura agrupada de ocupacion (ver `pedirOcupacion`). */
  let pendienteOcupacion = null;
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
        // Se guarda el DIRECTORIO (quien es quien) y NO la ocupacion (quien tiene que caseta).
        // Un nombre y un telefono no caducan, asi que en frio una caseta en tramite se resuelve
        // al instante con el `reservadoPor` que ya trae la lista. Quien VENDIO cada caseta si
        // caduca, y pintarlo desde una copia vieja seria afirmarle al vendedor que una caseta
        // la vendio alguien que quiza ya la cancelo. Eso se espera a que lo confirme el servidor.
        directorio: [...directorio.value.entries()],
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
      //
      // La copia guardada por una version anterior tiene UN vendedor por caseta, no una lista.
      // En un telefono ya instalado esa copia sobrevive a la actualizacion, asi que se normaliza
      // al leerla: sin esto, la primera pantalla tras actualizar reventaba al preguntar si la
      // caseta era suya, y el mapa se quedaba en blanco hasta que contestara el servidor.
      if (Array.isArray(guardado.asignaciones)) {
        asignaciones.value = new Map(guardado.asignaciones.map(
          ([id, v]) => [id, Array.isArray(v) ? v : [v]],
        ));
      }
      // Una copia guardada por una version anterior no lo trae: sin el, las casetas en tramite
      // salen sin nombre hasta que conteste el servidor, que es un segundo.
      if (Array.isArray(guardado.directorio)) directorio.value = new Map(guardado.directorio);
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
    // Se comparan NUMEROS. El id llega por JSON y desde localStorage, asi que uno de los dos
    // lados puede ser texto; con `===` estricto el carrito salia vacio aunque la caseta
    // estuviera bien reservada a su nombre.
    if (idUsuario == null) return [];
    const mio = Number(idUsuario);
    return puestos.value.filter((p) => p.estado === 'T' && p.reservadoPor != null
      && Number(p.reservadoPor) === mio);
  }

  /**
   * Aplica un PuestoEstadoDTO recibido por WebSocket.
   * Cubre alta, cambio y baja (`activo: false`), que es lo que necesita el editor.
   */
  /**
   * Cola de lo que llega del servidor, para aplicarlo AGRUPADO: un solo repintado por
   * fotograma en vez de uno por mensaje.
   *
   * Meter un lote al carrito son N casetas y el servidor difunde N mensajes, cada uno en su
   * propio turno del bucle de eventos. Sin agrupar, cada mensaje dispara su pasada de render:
   * Vue vuelve a evaluar el `v-memo` de las ~400 casetas del plano N veces seguidas. Medido con
   * la CPU frenada 6x (un telefono modesto), un lote de 20 tardaba 309 ms en terminar de
   * marcarse en la pantalla del otro vendedor, creciendo en linea recta con el tamaño del lote
   * —~15 ms por caseta—, aunque el aviso hubiera llegado a los 16 ms. La espera no era la red:
   * era repintar el plano veinte veces.
   *
   * Se guarda por id y gana el ULTIMO mensaje de cada caseta: cada uno trae el estado completo
   * de esa caseta, asi que el mas nuevo no necesita a los anteriores.
   *
   * Medido en el mismo equipo, con la CPU frenada 6x: un lote de 20 pasa de ~243 ms a ~72 ms
   * y deja de crecer con el tamaño del lote; una caseta suelta se queda como estaba (~24 ms).
   * El guion que lo mide es `medir-tiempo-real.mjs`, en los scripts de la habilidad.
   */
  let enCola = null;
  let vaciadoPedido = false;

  /** Abre la ventana de agrupado hasta el proximo fotograma. Sin `requestAnimationFrame` —en
   *  las pruebas con Node no existe— no hay bucle de pintado, asi que no hay nada que agrupar. */
  function abrirVentana() {
    const raf = globalThis.requestAnimationFrame;
    if (typeof raf !== 'function') return;
    vaciadoPedido = true;
    raf(() => { vaciadoPedido = false; vaciarCola(); });
  }

  function vaciarCola() {
    if (!enCola) return;
    const lote = enCola;
    enCola = null;
    for (const dto of lote.values()) aplicar(dto);
  }

  /**
   * Un mensaje del servidor.
   *
   * El PRIMERO se aplica en el acto y los que lleguen detras, hasta el siguiente fotograma, se
   * agrupan. Agrupar tambien el primero costaba un fotograma de espera a la caseta suelta —que
   * es el caso mas frecuente, el vendedor que toca una— sin ganar nada: no hay ninguna ráfaga
   * con la que juntarla. Asi, una caseta sola se pinta igual de rapido que antes y un lote de
   * veinte cuesta dos pasadas de render en vez de veinte.
   */
  function aplicarDelServidor(dto) {
    if (!dto || dto.id == null) return;
    if (!vaciadoPedido) {
      aplicar(dto);
      abrirVentana();
      return;
    }
    if (!enCola) enCola = new Map();
    enCola.set(dto.id, dto);
  }

  function aplicar(dto) {
    // Un cambio local (reservar, soltar) va DESPUES de lo que el servidor ya mando, o un
    // mensaje encolado de hace un instante pisaria el cambio optimista y el pin parpadearia
    // de vuelta a libre durante un fotograma.
    if (enCola) vaciarCola();
    const i = puestos.value.findIndex((p) => p.id === dto.id);
    if (dto.activo === false) {
      if (i >= 0) puestos.value.splice(i, 1);
      if (ocupacion.value.has(dto.id)) {
        const m = new Map(ocupacion.value);
        m.delete(dto.id);
        ocupacion.value = m;
      }
      return;
    }
    sincronizarOcupacion(dto);
    if (i < 0) {
      // Una caseta que no estaba en la lista se agrega, sea quien sea el que mira.
      //
      // Aqui hubo una guarda que la descartaba cuando el rol era ADMINISTRATIVO, partiendo de
      // que "el servidor ya se lo filtra al pedir la lista". Eso dejo de ser cierto: el mapa
      // ensena TODAS las casetas y pinta en gris las que no le tocan, justamente para que el
      // vendedor pueda decirle a un cliente quien lleva cada una. Con la guarda puesta, una
      // caseta creada desde el Editor NO le aparecia al vendedor hasta que recargara -- el
      // mismo sintoma que se reporto desde el APK y que se suponia arreglado.
      //
      // Lo que impide que venda una caseta ajena no es esconderla, es la comprobacion del
      // servidor en cada escritura (PuestoApiController.vetoPorAsignacion).
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
        mapaRotacion: local.mapaRotacion,
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
      return {
        ...dto,
        mapaX: local.mapaX,
        mapaY: local.mapaY,
        mapaEscala: local.mapaEscala,
        mapaRotacion: local.mapaRotacion,
      };
    });
  }

  /**
   * Aplica los cambios de asignacion que llegan por WebSocket.
   *
   * Es la mitad que faltaba: hasta ahora, reasignar casetas desde Vendedores no se veia en un
   * mapa ya abierto hasta la siguiente resincronizacion. Ahora el cambio entra al instante y
   * sin pedir nada, porque el mensaje trae la informacion, no un aviso de "vuelve a pedirlo".
   * El servidor manda la lista COMPLETA de habilitados de cada caseta que toco, no el cambio
   * suelto, asi que aqui se REEMPLAZA la entrada entera en vez de sumar o restar de a uno. Si se
   * fuera sumando, dos mensajes cruzados dejarian la lista a medias. Una fila con `vendedorId`
   * nulo es la forma de decir "esta caseta ya no la lleva nadie".
   */
  function aplicarAsignaciones(cambios) {
    if (!Array.isArray(cambios) || !cambios.length) return;
    const m = new Map(asignaciones.value);
    // Primero se vacian las casetas mencionadas: lo que llegue despues las vuelve a llenar, y
    // las que solo traian la fila nula se quedan sin entrada, que es justo lo que significa.
    for (const a of cambios) if (a?.puestoId != null) m.delete(a.puestoId);
    for (const a of cambios) {
      if (a?.puestoId == null || a.vendedorId == null) continue;
      if (!m.has(a.puestoId)) m.set(a.puestoId, []);
      m.get(a.puestoId).push(a);
    }
    asignaciones.value = m;
    // La copia en disco tiene que quedar coherente, o el proximo arranque en frio pintaria
    // con la asignacion vieja hasta que conteste el servidor.
    guardarCache();
  }

  /**
   * Reemplaza la ocupacion con la lista completa del servidor.
   *
   * Se REEMPLAZA entera y no se fusiona: el servidor manda todas las casetas no libres, asi
   * que lo que no venga en la lista es precisamente lo que ya no tiene dueño. Fusionando,
   * una caseta liberada mientras la peticion viajaba se quedaria marcada como reservada.
   */
  function aplicarOcupacion(filas) {
    if (!Array.isArray(filas)) return;
    const m = new Map();
    for (const o of filas) {
      if (o?.puestoId == null || o.vendedorId == null) continue;
      m.set(o.puestoId, {
        vendedorId: o.vendedorId,
        vendedor: o.vendedor ?? null,
        celular: o.celular ?? null,
        estado: o.estado ?? null,
        desde: o.desde ?? null,
      });
      anotarEnDirectorio(o.vendedorId, o.vendedor, o.celular);
    }
    ocupacion.value = m;
    guardarCache();
  }

  /** Lee quien tiene cada caseta. Condicionada: si nada cambio, el servidor contesta 304. */
  async function cargarOcupacion() {
    const r = await apiFetch('/api/app/puestos/ocupacion', condicional(etagOcupacion));
    if (r.status === 304 || !r.ok) return;
    etagOcupacion = r.headers.get('ETag') || etagOcupacion;
    aplicarOcupacion(await r.json().catch(() => []));
  }

  /** Mete a alguien en el directorio si trae nombre. Nunca lo borra: un nombre no caduca. */
  function anotarEnDirectorio(vendedorId, vendedor, celular) {
    if (vendedorId == null || !vendedor) return;
    const previo = directorio.value.get(vendedorId);
    if (previo && previo.vendedor === vendedor && previo.celular === celular) return;
    directorio.value = new Map(directorio.value).set(vendedorId, { vendedor, celular: celular ?? null });
  }

  /**
   * Vuelve a leer quien tiene cada caseta, AGRUPANDO las peticiones.
   *
   * Registrar una venta de doce casetas difunde doce mensajes, y cada uno descubre que no sabe
   * quien la vendio. Sin agrupar serian doce lecturas de la lista entera para enterarse de lo
   * mismo. Con la espera, son una.
   */
  function pedirOcupacion() {
    if (pendienteOcupacion) return;
    pendienteOcupacion = setTimeout(() => {
      pendienteOcupacion = null;
      cargarOcupacion().catch(() => {
        // Sin esto el mapa sigue siendo correcto: solo se queda sin el nombre de quien la
        // vendio, que es informativo. La siguiente resincronizacion lo vuelve a intentar.
      });
    }, 700);
  }

  /**
   * Ajusta la ocupacion de UNA caseta a partir de su estado.
   *
   * Se llama desde `aplicar`, asi que cubre por igual lo que llega del servidor y el pintado
   * optimista del propio vendedor: al reservar, su nombre aparece en el acto porque ya esta
   * en el directorio.
   */
  function sincronizarOcupacion(dto) {
    const previa = ocupacion.value.get(dto.id);

    if (dto.estado === 'T' && dto.reservadoPor != null) {
      const quien = directorio.value.get(dto.reservadoPor);
      // Ya estaba anotada a nombre del mismo: no se toca el Map, que dispararia un render.
      if (previa && previa.estado === 'T' && previa.vendedorId === dto.reservadoPor) return;
      ocupacion.value = new Map(ocupacion.value).set(dto.id, {
        vendedorId: dto.reservadoPor,
        vendedor: quien?.vendedor ?? null,
        celular: quien?.celular ?? null,
        estado: 'T',
        desde: null,
      });
      // Alguien que el directorio no conoce: administracion vendiendo, o un vendedor sin
      // ninguna caseta habilitada todavia. Se pide la lista para ponerle nombre.
      if (!quien) pedirOcupacion();
      return;
    }

    if (dto.estado === 'O') {
      // El mensaje no dice quien la vendio (al ocupar se borra `reservado_por_id_usuario`),
      // asi que si no lo sabiamos ya, hay que preguntarlo. Si la anotacion previa era la
      // reserva de esa misma persona, se conserva su nombre mientras llega la confirmacion:
      // casi siempre vende quien la tenia reservada, y asi no parpadea a "sin nombre".
      if (previa?.estado === 'O') return;
      ocupacion.value = new Map(ocupacion.value).set(dto.id, {
        vendedorId: previa?.vendedorId ?? null,
        vendedor: previa?.vendedor ?? null,
        celular: previa?.celular ?? null,
        estado: 'O',
        desde: null,
      });
      pedirOcupacion();
      return;
    }

    // Libre o bloqueada: no la tiene nadie.
    if (previa) {
      const m = new Map(ocupacion.value);
      m.delete(dto.id);
      ocupacion.value = m;
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
        // Las dos peticiones van juntas: el mapa necesita las dos para pintar bien —sin las
        // asignaciones, las casetas propias saldrian grises— y en serie serian dos esperas.
        // Y van CONDICIONADAS: si nada cambio desde la ultima vez, el servidor contesta 304
        // sin cuerpo y esto no descarga nada.
        const [r, rAsig, rOcup] = await Promise.all([
          apiFetch('/api/app/puestos', condicional(etagPuestos)),
          apiFetch('/api/app/puestos/asignaciones', condicional(etagAsignaciones)).catch(() => null),
          apiFetch('/api/app/puestos/ocupacion', condicional(etagOcupacion)).catch(() => null),
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
          // Una fila por pareja (caseta, vendedor): se agrupan por caseta.
          const m = new Map();
          for (const a of filas) {
            if (a?.puestoId == null) continue;
            if (!m.has(a.puestoId)) m.set(a.puestoId, []);
            m.get(a.puestoId).push(a);
            anotarEnDirectorio(a.vendedorId, a.vendedor, a.celular);
          }
          asignaciones.value = m;
        }
        if (rOcup && rOcup.status !== 304 && rOcup.ok) {
          etagOcupacion = rOcup.headers.get('ETag') || etagOcupacion;
          aplicarOcupacion(await rOcup.json().catch(() => []));
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
    // En el navegador esto no existe y la llamada se evapora; en Node devuelve un objeto con
    // unref(), y sin el este intervalo mantiene vivo el proceso: `npm test` se quedaba colgado
    // para siempre despues de pasar las once pruebas, que parece un fallo de la prueba y no lo es.
    sondeo?.unref?.();
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
      // La medicion se cierra con la LLEGADA del mensaje, no con su pintado: mide el camino
      // servidor -> cliente, que es lo que ese numero significa.
      (dto) => { cerrarMedicion(dto.id); aplicarDelServidor(dto); },
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
    ocupacion.value = new Map();
    if (pendienteOcupacion) { clearTimeout(pendienteOcupacion); pendienteOcupacion = null; }
    // Un lote a medio aplicar no puede sobrevivir al cierre de sesion: se pintaria encima de
    // la lista del siguiente usuario.
    enCola = null;
    ultimaLista = [];
    // Las etiquetas se olvidan al cerrar sesion: la respuesta del siguiente usuario puede ser
    // otra, y arrastrar la etiqueta vieja pediria un 304 sobre datos que no son los suyos.
    etagPuestos = null;
    etagAsignaciones = null;
    etagOcupacion = null;
    // La copia en disco NO se borra: es el mismo mapa para todos los vendedores y es lo que
    // hace que el proximo arranque sea instantaneo. No lleva nada privado que no vuelva a
    // llegar en la primera respuesta.
  }

  return {
    puestos, cargando, error, enVivo, desdeCache, ultimaSync, ubicadas, carritoDe, asignaciones,
    ocupacion, directorio,
    aplicar, aplicarDelServidor, aplicarAsignaciones, protegerLocales, cargar, recargar, conectar, asegurar, desconectar,
    registrarNotificaciones, reintentar, sinAsignaciones,
  };
});
