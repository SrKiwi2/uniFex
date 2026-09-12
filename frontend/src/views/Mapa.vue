<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue';
import { useRouter } from 'vue-router';
import PanZoom from '../components/PanZoom.vue';
import CasetaDetalle from '../components/CasetaDetalle.vue';
import { apiFetch } from '../api';
import { useAuthStore } from '../stores/auth';
import { usePuestosStore } from '../stores/puestos';
import { usePlanoStore } from '../stores/plano';
import { toast } from '../ui/toast';
import { alerta } from '../ui/alerta';
import { iniciarMedicion, marcarPintado, marcarRed, purgarMediciones } from '../ui/medir';
import { CLASE_ESTADO, ETIQUETA_ESTADO, LEYENDA, anchoParaLeer, anchoParaTocar, estiloPin, numerosVisibles } from '../mapa';

// Las casetas y la conexion en tiempo real son compartidas con el Tablero y el Editor:
// una sola descarga y un solo WebSocket para toda la app (ver stores/puestos.js).
const tienda = usePuestosStore();
// El plano ya no viaja en el bundle: lo sirve el backend por edicion, para poder cambiarlo
// sin recompilar el APK (ver stores/plano.js y V13).
const planoTienda = usePlanoStore();
// Ids con una peticion en vuelo: evita disparar dos veces sobre LA MISMA caseta
// (el backend igualmente la protege) sin congelar el resto del mapa.
const enPeticion = ref(new Set());
/** Caseta cuya ficha esta abierta, o null. */
const seleccionada = ref(null);
/** Ids de casetas que tienen alguna foto, para distinguirlas en el plano. */
const conFoto = ref(new Set());
let temporizadorMedicion = null;
const auth = useAuthStore();
const router = useRouter();

// Zona con dibujo dentro del mapa.png de respaldo, medida sobre los pixeles de esa imagen:
// la pagina tiene bastante margen en blanco alrededor y encuadrar sobre el plano entero
// deja al vendedor mirando papel vacio.
const ZONA_RESPALDO = { x0: 0.08, y0: 0.17, x1: 0.87, y1: 0.86 };
// De un plano subido no sabemos donde tiene el dibujo, asi que se encuadra entero: medir
// margenes de una imagen que no hemos visto seria adivinar.
const ZONA_COMPLETA = { x0: 0, y0: 0, x1: 1, y1: 1 };
const zonaEncuadre = computed(() =>
  (planoTienda.plano.propio ? ZONA_COMPLETA : ZONA_RESPALDO));

// En el celular se abre con esa zona entera a la vista (PanZoom calcula el zoom segun el
// tamaño real de la pantalla). En escritorio se conserva el encuadre de siempre, mas cerca.
const esMovil = window.innerWidth <= 820;
const focoInicial = { x: 0.44, y: 0.34, scale: 2.4 };

// Ojo: Pinia DESENVUELVE los computed al leerlos del store, asi que `tienda.ubicadas` es
// ya un array, no un ref. Hay que envolverlo en un computed propio: leerlo suelto una vez
// aqui daria un array plano, sin `.value` y sin reactividad (se quedaria congelado).
const ubicados = computed(() => tienda.ubicadas);

/*
 * Cuánto hay que poder acercarse, y desde cuándo se lee el número. Los dos salen del tamaño
 * REAL de las casetas de esta feria, no de una constante: el administrador dibuja las casetas
 * contra el plano (en FEXPO valen 0.005 del ancho) y ningún factor de aumento fijo puede
 * servir a la vez para eso y para un plano con casetas del doble.
 */
/**
 * Aviso de que lo que se ve puede estar desfasado. null = el mapa es de fiar.
 *
 * Este aviso es la mitad de la cura del problema de "el mapa no se actualiza". La otra mitad
 * (resincronizar al volver al frente y sondear sin tiempo real) vive en el store; esto es lo
 * que evita que un fallo de conexion vuelva a ser INVISIBLE. Un vendedor mirando casetas
 * verdes que ya estan vendidas necesita saberlo antes de prometerle una al cliente.
 */
const avisoSync = computed(() => {
  if (tienda.desdeCache) return 'Copia guardada, aún sin confirmar';
  if (!tienda.enVivo) return 'Sin tiempo real: el mapa puede estar desfasado';
  return null;
});

const topeZoom = computed(() => anchoParaTocar(ubicados.value));
const umbralNumeros = computed(() => anchoParaLeer(ubicados.value));
const sinUbicar = computed(() => tienda.puestos.length - tienda.ubicadas.length);

// El carrito se deduce del propio mapa (casetas en tramite mias), no se pide aparte:
// por eso sobrevive a recargar la pagina o a cerrar el movil.
const carrito = computed(() => tienda.carritoDe(auth.id));
const totalCarrito = computed(() =>
  carrito.value.reduce((s, p) => s + Number(p.precio || 0), 0));
const sinPrecio = computed(() => carrito.value.some((p) => !(Number(p.precio) > 0)));
const vaciando = ref(false);

/** El WebSocket rechazo el token: sin tiempo real el mapa miente, asi que salimos. */
function sesionCaducada() {
  auth.logout();
  router.push('/login');
}

/**
 * Quien responde por una caseta, o null si no la tiene nadie.
 * Es lo que convierte "esa no te toca" en "esa la lleva fulano, este es su telefono".
 */
const asignacionDe = (p) => tienda.asignaciones.get(p.id) || null;

/**
 * ¿Puede quien mira vender esta caseta?
 *
 * Antes esta pregunta no existia en el mapa porque el servidor le mandaba al vendedor SOLO
 * sus casetas. Ahora llegan todas —las ajenas en gris— asi que hay que distinguirlas aqui.
 * Administracion vende cualquiera; un vendedor, solo las suyas. El servidor comprueba lo
 * mismo en cada escritura: esto es la parte que se ve, no la que protege.
 */
const puedoVender = (p) => {
  if (!auth.esVendedor) return true;
  const a = asignacionDe(p);
  return a != null && a.vendedorId === auth.id;
};

/**
 * Soy vendedor, hay casetas en el plano, y ninguna es mia.
 *
 * Es distinto de `tienda.sinAsignaciones`, que significa "no hay NINGUNA caseta creada" —el
 * plano todavia no se armo—. Este otro caso quedo sin cubrir cuando el servidor dejo de
 * filtrar el listado: desde entonces el vendedor sin casetas recibe las 119 igual que todos,
 * asi que `puestos.length === 0` nunca se cumple y el aviso no podia salir nunca. Veia el
 * mapa entero en gris sin una sola palabra que lo explicara.
 */
const sinHabilitadas = computed(() =>
  auth.esVendedor
  && !tienda.cargando
  && tienda.puestos.length > 0
  && ![...tienda.asignaciones.values()].some((a) => a.vendedorId === auth.id));

/** ¿La reserva en tramite de esta caseta es de quien esta mirando? */
const esMia = (p) => p.estado === 'T' && p.reservadoPor != null && p.reservadoPor === auth.id;

/**
 * Texto de la caseta al pasar por encima. Lleva el precio a proposito: el vendedor
 * lo necesita en la mano para decirselo al cliente sin cambiar de pantalla.
 */
function rotulo(p) {
  if (!puedoVender(p)) {
    const a = asignacionDe(p);
    return `${p.categoria} ${p.codigo} · ${a ? `la vende ${a.vendedor}` : 'sin vendedor asignado'}`;
  }
  const duenio = esMia(p) ? ' (tuya)' : p.estado === 'T' ? ' (de otro vendedor)' : '';
  const precio = p.precio > 0 ? ` · ${p.precio} Bs` : ' · sin precio';
  return `${p.categoria} ${p.codigo} · ${p.tamano || ''} · ${ETIQUETA_ESTADO[p.estado]}${duenio}${precio}`;
}

/**
 * Tocar una caseta abre su ficha, no la reserva directamente.
 *
 * Es un toque mas que antes, y es deliberado: el vendedor necesita ENSEÑARLE la caseta al
 * cliente (fotos, precio, medida, ubicacion) antes de comprometerla, y en un plano con
 * cientos de pines diminutos reservar al primer toque hace que un roce venda la caseta
 * equivocada. La accion vive dentro de la ficha, en un boton grande.
 */
function abrirFicha(p) {
  seleccionada.value = p;
}

/** Mantiene la ficha sincronizada si llega un cambio por WebSocket mientras esta abierta. */
const casetaEnFicha = computed(() => {
  if (!seleccionada.value) return null;
  return tienda.puestos.find((p) => p.id === seleccionada.value.id) || seleccionada.value;
});

async function click(p, evento) {
  // Cinturon ademas del tirante: la ficha ya no ofrece el boton para una caseta ajena, pero
  // si alguna vista lo llamara igual, aqui se corta antes de salir a la red.
  if (!puedoVender(p)) return;

  // Solo se ignora un segundo clic sobre LA MISMA caseta. Bloquear el mapa entero
  // mientras viaja una peticion se siente como que la pagina se cuelga, aunque el
  // servidor conteste en 10 ms.
  if (enPeticion.value.has(p.id)) return;

  // Una caseta en tramite de OTRO vendedor no se toca. Antes se intentaba liberarla:
  // el pin se pintaba verde un instante (optimista), el servidor respondia 409 y volvia
  // a ambar. Ese parpadeo hacia creer que la reserva ajena se habia soltado.
  if (p.estado === 'T' && !esMia(p)) {
    toast(`${p.categoria} ${p.codigo}: la tiene reservada otro vendedor`, 'info');
    return;
  }
  // Tocar una caseta la mete o la saca del carrito. Se usa el endpoint de carrito y no
  // /reservar porque este da un vencimiento largo (12 h): el vendedor tiene que poder
  // elegir varias, hablar con el cliente y volver, sin que se le liberen por el camino.
  const acc = p.estado === 'L' ? 'agregar' : esMia(p) ? 'quitar' : null;
  if (!acc) return;

  // Escritura optimista de verdad: se pinta antes de salir a la red. El servidor sigue
  // siendo el arbitro — si rechaza, se revierte.
  const estadoPrevio = p.estado;
  const reservadoPrevio = p.reservadoPor ?? null;
  iniciarMedicion(p.id, acc, evento);
  tienda.aplicar(acc === 'agregar'
    ? { ...p, estado: 'T', reservadoPor: auth.id }
    : { ...p, estado: 'L', reservadoPor: null });
  enPeticion.value = new Set(enPeticion.value).add(p.id);

  // Doble rAF tras nextTick: el primero entra en el fotograma que Vue acaba de encolar,
  // el segundo se ejecuta cuando ese fotograma ya se presento en pantalla. Es la unica
  // forma de medir el pintado real y no solo el trabajo de Vue.
  nextTick(() => requestAnimationFrame(() => requestAnimationFrame(() => marcarPintado(p.id))));

  try {
    const r = await apiFetch('/api/app/puestos/carrito', {
      method: acc === 'agregar' ? 'POST' : 'DELETE',
      body: JSON.stringify({ ids: [p.id] }),
    });
    marcarRed(p.id);
    const d = await r.json().catch(() => ({}));
    const logrado = r.ok && (d.logradas ?? []).includes(p.id);
    // La ficha se cierra al conseguir la accion: es lo que el vendedor venia a hacer.
    // Si fallo se queda abierta, para que vea el motivo sin volver a buscar la caseta.
    if (logrado && seleccionada.value?.id === p.id) seleccionada.value = null;
    if (!logrado) {
      // La perdio otro vendedor. Se deshace el pintado; el broadcast llegara igualmente
      // y dejara el estado real.
      tienda.aplicar({ ...p, estado: estadoPrevio, reservadoPor: reservadoPrevio });
      toast(`${p.categoria} ${p.codigo}: ${d.mensaje ?? 'No disponible'}`, 'error');
    }
  } catch (e) {
    tienda.aplicar({ ...p, estado: estadoPrevio, reservadoPor: reservadoPrevio });
    toast(e.message, 'error');
  } finally {
    const s = new Set(enPeticion.value);
    s.delete(p.id);
    enPeticion.value = s;
  }
}

/** Suelta el carrito entero (una sola peticion en lote). */
async function vaciarCarrito() {
  const ids = carrito.value.map((p) => p.id);
  if (!ids.length || vaciando.value) return;
  vaciando.value = true;
  try {
    const r = await apiFetch('/api/app/puestos/carrito', {
      method: 'DELETE', body: JSON.stringify({ ids }),
    });
    const d = await r.json().catch(() => ({}));
    for (const id of d.logradas ?? []) {
      const p = tienda.puestos.find((x) => x.id === id);
      if (p) tienda.aplicar({ ...p, estado: 'L', reservadoPor: null });
    }
    toast(`${(d.logradas ?? []).length} caseta(s) liberadas`, 'ok');
  } catch (e) {
    toast(e.message, 'error');
  } finally {
    vaciando.value = false;
  }
}

// No se desconecta al desmontar: la conexion la comparte toda la app y la cierra
// AppLayout al salir de la sesion. Ademas esta vista vive en <KeepAlive>, donde
// onUnmounted no se dispara al navegar — ahi estaba la fuga de conexiones.
/**
 * Ids de casetas con foto. Se piden UNA vez y no por caseta: con ~500 pines en pantalla,
 * consultarlo pin a pin seria un N+1 en el navegador.
 */
async function cargarCualesTienenFoto() {
  try {
    const r = await apiFetch('/api/app/puestos/con-foto');
    if (r.ok) conFoto.value = new Set(await r.json());
  } catch {
    /* el mapa funciona igual sin el distintivo */
  }
}

// El cambio de asignaciones llega por el topic personal. El store ya recarga la lista solo;
// aqui solo se muestra el mensaje, que es lo que le dice al vendedor QUE cambio y de que
// categoria. Va como modal y no como toast porque cambia lo que puede vender.
let dejarDeOirAsignaciones = null;


onMounted(() => {
  dejarDeOirAsignaciones = tienda.registrarNotificaciones((n) => {
    if (n?.tipo === 'ASIGNACION_CAMBIADA' && n.cuerpo) alerta(n.cuerpo, 'info', 8000);
  });
  tienda.asegurar(sesionCaducada);
  planoTienda.asegurar();
  cargarCualesTienenFoto();
  // Delata las mediciones cuyo broadcast nunca llego: sin esto, un mensaje perdido
  // se confunde con uno lento (la linea simplemente no aparece).
  if (import.meta.env.DEV) {
    temporizadorMedicion = setInterval(purgarMediciones, 3000);
  }
});
onUnmounted(() => {
  clearInterval(temporizadorMedicion);
  if (dejarDeOirAsignaciones) dejarDeOirAsignaciones();
});
</script>

<template>
  <div class="barra">
      <div class="legend">
        <span v-for="l in LEYENDA" :key="l.clase" class="chip" :class="l.clase">{{ l.txt }}</span>
        <!-- Solo tiene sentido para un vendedor: administracion vende todas. -->
        <span v-if="auth.esVendedor" class="chip chip-ajena">De otro vendedor</span>
      </div>
      <!-- Solo aparece cuando algo va mal, y ofrece la accion en el mismo sitio: pulsar
           vuelve a pedir la lista y reintenta la conexion. -->
      <button v-if="avisoSync" class="aviso-sync" @click="tienda.reintentar()"
              :title="avisoSync + '. Tocá para actualizar ahora.'">
        ⚠ {{ avisoSync }} · Actualizar
      </button>


      <!-- El número de la caseta es como se la nombra al cliente ("la 14"), así que el
           rótulo lo puede encender también el vendedor, no solo quien edita el plano. -->
      <button class="btn btn-fantasma btn-sm numeros" :class="{ on: numerosVisibles }"
              :aria-pressed="numerosVisibles" @click="numerosVisibles = !numerosVisibles"
              title="Muestra el número de cada caseta sobre el plano (hay que acercarse para leerlo)">
        🔢 Nº
      </button>
      <!-- El conteo de ubicadas/sin ubicar habla del Editor del plano: al vendedor no le
           dice nada y le mete ruido en la unica pantalla que usa todo el dia. -->
      <div v-if="auth.puedeEditarPlano" class="info">
        {{ ubicados.length }} ubicadas
        <span v-if="sinUbicar > 0" class="muted">· {{ sinUbicar }} sin ubicar (Editor)</span>
      </div>
    </div>
    <!-- Carrito flotante: va en `position: fixed` a proposito. Si empujara el contenido,
         al aparecer y desaparecer cambiaria el alto de la pagina y el ResizeObserver del
         PanZoom recentraria el plano — el mismo "temblor" que hubo con los avisos. -->
    <Transition name="subir">
      <div v-if="carrito.length" class="carrito" role="status">
        <div class="resumen">
          <strong>{{ carrito.length }}</strong> caseta{{ carrito.length === 1 ? '' : 's' }}
          <span class="sep">·</span>
          <strong class="total">{{ totalCarrito.toLocaleString('es-BO') }} Bs</strong>
          <span v-if="sinPrecio" class="alerta" title="Hay casetas cuya categoría no tiene precio">
            ⚠ sin precio
          </span>
        </div>
        <div class="botones">
          <button class="btn btn-fantasma" :disabled="vaciando" @click="vaciarCarrito">
            {{ vaciando ? 'Liberando…' : 'Vaciar' }}
          </button>
          <router-link to="/venta" class="btn btn-primario">
            Registrar venta
          </router-link>
        </div>
      </div>
    </Transition>

    <CasetaDetalle
      :puesto="casetaEnFicha"
      :es-mia="casetaEnFicha ? esMia(casetaEnFicha) : false"
      :vendible="casetaEnFicha ? puedoVender(casetaEnFicha) : true"
      :asignacion="casetaEnFicha ? asignacionDe(casetaEnFicha) : null"
      :ocupado="casetaEnFicha ? enPeticion.has(casetaEnFicha.id) : false"
      @cerrar="seleccionada = null"
      @agregar="(p) => click(p)"
      @quitar="(p) => click(p)"
    />

    <!-- No hay NINGUNA caseta creada: el plano todavia no se armo. Ocupa la pantalla porque
         no hay nada que enseñar debajo. -->
    <div v-if="tienda.sinAsignaciones" class="sin-asignaciones">
      <span class="icono">🗺️</span>
      <h2>Todavía no hay casetas en el plano</h2>
      <p>
        Administración aún no armó el mapa de la feria.
        En cuanto lo haga, aparecerá aquí solo: no hace falta que cierres la aplicación.
      </p>
    </div>

    <!-- Hay casetas, pero ninguna es suya. NO se le oculta el mapa: en gris le sigue sirviendo
         para decirle a un cliente quien vende cada caseta (la ficha da el contacto). Lo que
         hacia falta era la frase que explicara por que no puede tocar ninguna. -->
    <div v-else-if="sinHabilitadas" class="aviso-habilitadas" role="status">
      <strong>Todavía no tienes casetas habilitadas.</strong>
      Administración aún no te asignó ninguna; aparecerán solas en cuanto lo haga.
      Mientras tanto puedes tocar cualquier caseta para ver quién la vende.
    </div>

    <!-- key: cambiar de plano cambia la proporcion y el encuadre, y `reset()` solo corre al
         montar. Remontar es lo que hace que el plano nuevo se vea bien encuadrado sin recargar. -->
    <PanZoom v-if="!tienda.sinAsignaciones" :key="planoTienda.src" :focus="focoInicial" :contenido="esMovil ? zonaEncuadre : null"
             :aspect="planoTienda.aspecto" :umbral-detalle="umbralNumeros" :max-ancho="topeZoom" llenar>
      <div class="plano" :class="{ 'con-numeros': numerosVisibles }" :style="{ aspectRatio: planoTienda.aspecto ? 1 / planoTienda.aspecto : 'auto' }">
        <!-- width/height intrinsecos: el navegador reserva la proporcion antes de
             descargar, asi los pines no bailan mientras carga el plano. Salen del propio
             plano (V13), no de un numero fijo, porque cada edicion puede traer el suyo.
             aspect-ratio en el contenedor garantiza que el alto reserve el espacio exacto
             antes de que cargue la imagen, evitando saltos de layout en el APK. -->
        <img
          :src="planoTienda.src"
          :key="planoTienda.src"
          alt="Plano de la feria"
          :width="planoTienda.plano.ancho"
          :height="planoTienda.plano.alto"
          decoding="async"
          draggable="false"
        />
        <!-- v-memo es lo que hace que reservar se sienta instantaneo: sin el, cambiar UNA
             caseta obliga a Vue a repasar y repintar las 520 (cada una recalcula su estilo
             y su titulo), y eso es el "segundo de espera" — no la red, que tarda 10 ms.
             Con la lista de dependencias, solo se vuelve a pintar el pin que cambio. -->
        <button
          v-for="p in ubicados"
          :key="p.id"
          v-memo="[p.estado, p.reservadoPor === auth.id, conFoto.has(p.id), p.mapaX, p.mapaY, p.mapaEscala, p.mapaRotacion, p.tamanoMapa, p.color, p.forma, p.codigo, puedoVender(p)]"
          class="pin"
          :class="[CLASE_ESTADO[p.estado], `forma-${p.forma || 'cuadrado'}`,
                   { mia: esMia(p), 'con-foto': conFoto.has(p.id), ajena: !puedoVender(p) }]"
          :style="{ ...estiloPin(p), '--categoria': p.color || 'transparent' }"
          :title="rotulo(p)"
          @pointerdown.stop
          @click="abrirFicha(p)"
        ><span class="num-caseta">{{ p.codigo }}</span></button>
      </div>
    </PanZoom>
</template>

<style scoped>

.sin-asignaciones {
  flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center;
  gap: 0.6rem; padding: 2.5rem 1.5rem; text-align: center; color: var(--muted);
}
.sin-asignaciones .icono { font-size: 2.6rem; }
.sin-asignaciones h2 { margin: 0; font-size: 1.15rem; color: var(--text); }
.sin-asignaciones p { margin: 0; max-width: 42ch; line-height: 1.5; }

.barra {
  display: flex; justify-content: space-between; align-items: center;
  padding: 0.6rem 1rem; gap: 1rem; flex-wrap: wrap;
}
.legend { display: flex; gap: 0.4rem; flex-wrap: wrap; }
.chip { padding: 0.2rem 0.55rem; border-radius: 999px; font-size: 0.78rem; font-weight: 600; }
.chip-ajena { background: var(--bloqueado); color: #fff; }

/* Franja, no pantalla completa: el mapa en gris sigue siendo util para consultar. */
.aviso-habilitadas {
  margin: 0 0 0.6rem; padding: 0.7rem 0.9rem; border-radius: var(--radio-sm);
  border: 1px solid color-mix(in srgb, var(--tramite) 45%, transparent);
  background: color-mix(in srgb, var(--tramite) 12%, var(--panel));
  color: var(--text); font-size: 0.9rem; line-height: 1.45;
}
.aviso-habilitadas strong { color: var(--tramite); }
.info { font-size: 0.9rem; }
.numeros.on { border-color: var(--acento); color: var(--acento); font-weight: 700; }

/* Ambar y no rojo: el mapa sigue siendo utilizable, solo que puede ir atrasado. El rojo esta
   reservado a las casetas vendidas y repetirlo aqui compite con la lectura del plano. */
.aviso-sync {
  border: 1px solid color-mix(in srgb, var(--tramite) 45%, transparent);
  background: color-mix(in srgb, var(--tramite) 14%, transparent);
  color: var(--tramite);
  border-radius: 999px; padding: 0.25rem 0.7rem;
  font: inherit; font-size: 0.8rem; font-weight: 700; cursor: pointer;
}
.muted { color: var(--muted); }
/* Los avisos van por toast (ToastHost, montado en App.vue). Antes eran un parrafo aqui
   encima del plano: al aparecer y desaparecer cambiaba el alto de la pagina, el
   ResizeObserver del PanZoom lo detectaba y recentraba el mapa — el "temblor" en cada clic. */
.plano { position: relative; width: 100%; }

/* Dos cosas, y las dos importan:
   1. `pointer-events/user-select: none` — el fondo no se puede clicar ni seleccionar.
      Sin esto, al arrastrar el navegador iniciaba su seleccion de imagen y se veia todo
      cubierto de azul. Solo las casetas deben ser interactivas.
   2. `will-change/translateZ` — pone el plano en SU PROPIA capa de composicion.
      Antes la imagen y los pines compartian la capa de .world (que lleva will-change),
      asi que cambiar de color UNA caseta obligaba al navegador a re-rasterizar el plano
      completo: a zoom 2.4 son decenas de megapixeles, y ahi estaban los ~2 segundos de
      espera al reservar. Aislada, la imagen se rasteriza una vez y los pines se repintan
      solos. */
.plano img {
  /* height:auto es obligatorio aca. El <img> lleva width/height como ATRIBUTOS HTML (para
     reservar el hueco antes de cargar); el navegador los aplica como si fueran CSS de baja
     prioridad. Como width:100% sí los pisa pero nada pisaba height, el alto se quedaba fijo
     en el height="2376" del atributo — literal, en px — sin importar en cuanto se hubiera
     encogido el ancho. En una pantalla angosta eso estira el plano varias veces su alto real
     (en una ancha, cerca de los 1836px naturales, el numero fijo coincidia casi por
     casualidad y el bug no se notaba: de ahi que "en la web se viera bien"). */
  width: 100%; height: auto; display: block;
  pointer-events: none;
  user-select: none; -webkit-user-select: none;
  /* `translateZ(0)` le da al plano su propia capa: cambiar de color UNA caseta no obliga a
     re-rasterizar la imagen entera. Lo que SI se quito es `will-change: transform`, que
     ademas congelaba la escala de rasterizado y dejaba la imagen borrosa al acercarse. */
  transform: translateZ(0);
}

/* El pin se mide en % del ancho del plano, asi que crece con el zoom como una caseta real.
   aspect-ratio deriva el alto del ancho ya resuelto en px: sale cuadrado sobre una imagen
   que no lo es. */
/* El separador entre casetas vecinas va en box-shadow, NO en border. Con
   `box-sizing: border-box` el borde se come el relleno: a zoom normal un pin mide pocos
   pixeles, asi que 1px de borde blanco (y 2px mas de borde en la propia) dejaban solo una
   mota de color en el centro — se veia un cuadro con marco en vez de una caseta de color.
   El box-shadow se dibuja FUERA de la caja: separa igual y no roba superficie. */
/* ---- REGLA DE ORO DE ESTE BLOQUE ----
   Dentro del mundo que transforma PanZoom, un `px` NO es un pixel: el zoom es un
   `transform: scale()`, asi que toda medida absoluta se multiplica por el aumento. Con las
   casetas a 0.005 del ancho del plano, una caseta mide ~2 px de maquetacion, y de ahi salian
   los tres defectos que se veian en el APK:
     · `border-radius: 2px` sobre una caja de 2 px = circulo perfecto. Por eso las casetas
       cuadradas se veian redondas — y en el monitor no, porque alli la caseta mide 6 px.
     · `box-shadow: 0 0 0 1px` = aro blanco de 22 px al acercarse. Ese era el halo.
     · `0 1px 3px` de sombra difusa = 66 px de desenfoque. Ese era el velo gris.
   Todo lo que se dibuje aqui va en fracciones de `--pin` (el ancho de la caseta, que publica
   estiloPin) o en porcentaje. Nada en px absolutos. */
/* La caja mide SIEMPRE 100 px y se reduce con transform. No se dimensiona con `width` en
   porcentaje porque con casetas de 0.005 del ancho eso da 1.95 px de maquetacion, y el
   navegador lo redondea a pixel entero de forma distinta segun donde caiga cada caseta: unas
   salian de 1 px y otras de 2. Ese era el "unas mas anchas y otras mas pequeñas".

   `transform-origin: 0 0` + `scale() translate(-50%, -50%)` en ese orden: el translate se
   aplica primero sobre la caja sin escalar (la centra sobre su punto del plano) y el scale
   despues, asi que el centro cae exacto en las coordenadas guardadas y el lado final mide
   justo `--pin`. Dentro de la caja ya se puede medir en px normales: 100 px es la unidad. */
.pin {
  position: absolute; width: 100px; height: 100px;
  transform-origin: 0 0;
  /* El `rotate` va ENTRE el scale y el translate, y ese orden no es casual: el translate
     centra la caja de 100 px sobre su punto del plano ANTES de girarla, asi que el giro sale
     sobre el centro de la caseta y no la desplaza. Ponerlo despues la haria orbitar. */
  transform: scale(calc(var(--pin, 20) / 100)) rotate(var(--giro, 0deg)) translate(-50%, -50%);
  border: none;
  padding: 0; cursor: pointer;
  /* Separador con la caseta vecina y aro de categoria, en px de la caja de 100. */
  --borde: 6px;
  --aro: 12px;
  /* Sin sombra difusa: al acercarse se convertia en un velo gris enorme, y el separador
     blanco ya despega la caseta del plano. */
  box-shadow: 0 0 0 var(--borde) rgba(255, 255, 255, 0.75);
}

/* El relleno dice el ESTADO (libre, vendida, reservada) porque es lo que decide si se puede
   vender, y eso no se negocia. El color de la categoria va en un aro alrededor: aparece solo
   con el plano acercado, igual que el numero, porque a zoom de feria entera el pin mide 2 px
   y un aro de ese grosor tenirla el punto entero y arruinaria la lectura del estado.
   El grosor va en fraccion de caseta, asi que crece con el zoom sin desbordarse. */
.world.detalle .pin {
  box-shadow:
    0 0 0 var(--aro) var(--categoria, transparent),
    0 0 0 calc(var(--aro) + var(--borde)) rgba(255, 255, 255, 0.75);
}
/* Una caseta que no le toca a este vendedor se pinta gris, pase lo que pase con su estado:
   lo que necesita saber de un vistazo es que ahi no vende el. El estado real y el contacto
   del companiero que si la lleva salen en la ficha, al tocarla — que es justo lo que antes
   no podia hacer, porque esas casetas ni le aparecian en el mapa. */
.pin.ajena { background: var(--bloqueado); cursor: pointer; }

.pin:hover { z-index: 5; filter: brightness(1.12); }
.pin.ocupado, .pin.bloqueado { cursor: default; }

/* Una caseta en trámite ajena no se puede tocar; la propia sí. Ya no hace falta marcarla
   con un borde: desde que la propia es azul y la ajena naranja, el color solo basta. */
.pin.tramite { cursor: default; }
.pin.tramite.mia { cursor: pointer; }

/* Las casetas con foto se distinguen con un punto blanco en la esquina. No lleva icono ni
   texto a propósito: al zoom normal un pin mide pocos píxeles y cualquier glifo sería una
   mancha ilegible. */
.pin.con-foto::after {
  content: ""; position: absolute; top: 8%; right: 8%;
  width: 26%; height: 26%; border-radius: 50%;
  background: #fff; box-shadow: 0 0 0 var(--borde) rgba(2, 6, 23, 0.35);
}

/* ---- carrito ---- */
.carrito {
  /* bottom suma el alto de la barra de navegación inferior (0 fuera de móvil), para que
     esta no la tape. */
  position: fixed; left: 50%; transform: translateX(-50%); bottom: calc(var(--tabbar-h) + 1rem); z-index: 40;
  display: flex; align-items: center; gap: 1rem; flex-wrap: wrap;
  padding: 0.7rem 1rem; border-radius: var(--radio);
  background: var(--panel); border: 1px solid var(--border); box-shadow: var(--sombra-md);
  max-width: calc(100vw - 2rem);
}
.carrito .resumen { display: flex; align-items: center; gap: 0.4rem; font-size: 0.92rem; }
.carrito .total { font-variant-numeric: tabular-nums; }
.carrito .sep { color: var(--muted); }
.carrito .alerta {
  font-size: 0.75rem; font-weight: 700; color: var(--tramite);
  background: color-mix(in srgb, var(--tramite) 15%, transparent);
  border-radius: 999px; padding: 0.1rem 0.5rem;
}
/* Son los dos botones con los que se cierra una venta, y estaban en tamaño `btn-sm`, el
   mismo que un icono secundario. Se pulsan de pie y con una mano: 48px de alto. */
.carrito .botones { display: flex; gap: 0.5rem; }
.carrito .botones .btn { min-height: 48px; padding-left: 1.1rem; padding-right: 1.1rem; font-size: 1rem; }
@media (max-width: 560px) {
  .carrito { left: 1rem; right: 1rem; transform: none; }
  .carrito .botones { flex: 1; }
  .carrito .botones .btn { flex: 1; }
  /* Sin esto entraría volando desde la izquierda: la transición de arriba desplaza medio
     ancho para compensar el centrado, y aquí ya no hay centrado que compensar. */
  .subir-enter-from, .subir-leave-to { transform: translateY(0.5rem); }
}

.subir-enter-active, .subir-leave-active { transition: opacity 0.15s ease, transform 0.15s ease; }
.subir-enter-from, .subir-leave-to { opacity: 0; transform: translateX(-50%) translateY(0.5rem); }
@media (prefers-reduced-motion: reduce) {
  .subir-enter-active, .subir-leave-active { transition: none; }
}

/* En porcentaje, no en px: el 2px de antes era mas que el radio de la propia caseta y la
   dejaba redonda. Un 10% es la esquina apenas matada que se buscaba. */
.forma-cuadrado { border-radius: 10%; }
.forma-circulo { border-radius: 50%; }
.forma-triangulo { clip-path: polygon(50% 0%, 100% 100%, 0% 100%); border: none; }

/* ---- número de la caseta ----
   El rótulo NO se mide con el font-size del pin, y esa es toda la historia: con casetas de
   0.005 del ancho del plano el pin mide ~2 px de maquetación, su fuente saldría a ~1 px, y el
   WebView de Android impone un tamaño MÍNIMO de fuente (8 px por defecto). El número salía
   cuatro veces más grande que su caseta y `overflow: hidden` lo dejaba en un borrón: por eso
   "no se veían las numeraciones" al acercarse.

   En su lugar, una caja FIJA de 100 px con el texto a 52 px —tamaños normales, que ningún
   mínimo toca— reducida con `transform: scale()` al tamaño real de la caseta. Un transform no
   está sujeto a mínimos de fuente y además se compone con el zoom del mundo, así que el
   navegador rasteriza el texto directamente a la escala final: nítido a cualquier zoom.

   Está siempre en el DOM y lo encienden DOS condiciones, las dos en CSS a propósito — si
   fueran reactivas, encenderlas obligaría a repintar los 500+ pines:
     · `.con-numeros`   — el botón "🔢 Nº" de la barra.
     · `.world.detalle` — PanZoom avisa de que el plano se ve lo bastante grande. Sin esto,
       al ver la feria entera el número mediría 2 px y sería una mancha. */
.num-caseta {
  display: none;
  /* Ya no se escala solo: el pin entero es una caja de 100 px que se reduce con transform,
     asi que aqui basta con ocuparla entera y usar un tamaño de fuente normal. */
  position: absolute; inset: 0;
  align-items: center; justify-content: center;
  /* Se deshace el giro de la caseta: lo que se gira es la FIGURA, no su numero. Una fila
     puesta a 90 grados con los numeros de lado no la leeria nadie. */
  transform: rotate(calc(-1 * var(--giro, 0deg)));
  transform-origin: 50% 50%;
  font-size: 52px; line-height: 1; font-weight: 700;
  font-variant-numeric: tabular-nums;
  color: #fff;
  /* El color de la categoría lo elige el administrador y puede salir claro: la sombra es lo
     que mantiene el número legible encima de cualquiera, sin un fondo que taparía la caseta. */
  text-shadow: 0 2px 4px rgba(2, 6, 23, 0.92), 0 0 4px rgba(2, 6, 23, 0.85);
  pointer-events: none;
  overflow: hidden;
}
.world.detalle .con-numeros .num-caseta { display: flex; }
/* En un triángulo el centro geométrico cae en la punta, donde no hay superficie donde
   apoyar el número: se baja al tercio ancho. */
.forma-triangulo .num-caseta { align-items: flex-end; padding-bottom: 8px; font-size: 40px; }
</style>
