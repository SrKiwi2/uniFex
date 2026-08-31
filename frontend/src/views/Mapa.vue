<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue';
import { useRouter } from 'vue-router';
import PanZoom from '../components/PanZoom.vue';
import CasetaDetalle from '../components/CasetaDetalle.vue';
import { apiFetch } from '../api';
import { useAuthStore } from '../stores/auth';
import { usePuestosStore } from '../stores/puestos';
import { toast } from '../ui/toast';
import { iniciarMedicion, marcarPintado, marcarRed, purgarMediciones } from '../ui/medir';
import { CLASE_ESTADO, ETIQUETA_ESTADO, estiloPin } from '../mapa';

// Las casetas y la conexion en tiempo real son compartidas con el Tablero y el Editor:
// una sola descarga y un solo WebSocket para toda la app (ver stores/puestos.js).
const tienda = usePuestosStore();
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

// Ojo: Pinia DESENVUELVE los computed al leerlos del store, asi que `tienda.ubicadas` es
// ya un array, no un ref. Hay que envolverlo en un computed propio: leerlo suelto una vez
// aqui daria un array plano, sin `.value` y sin reactividad (se quedaria congelado).
const ubicados = computed(() => tienda.ubicadas);
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

/** ¿La reserva en tramite de esta caseta es de quien esta mirando? */
const esMia = (p) => p.estado === 'T' && p.reservadoPor != null && p.reservadoPor === auth.id;

/**
 * Texto de la caseta al pasar por encima. Lleva el precio a proposito: el vendedor
 * lo necesita en la mano para decirselo al cliente sin cambiar de pantalla.
 */
function rotulo(p) {
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

onMounted(() => {
  tienda.asegurar(sesionCaducada);
  cargarCualesTienenFoto();
  // Delata las mediciones cuyo broadcast nunca llego: sin esto, un mensaje perdido
  // se confunde con uno lento (la linea simplemente no aparece).
  if (import.meta.env.DEV) {
    temporizadorMedicion = setInterval(purgarMediciones, 3000);
  }
});
onUnmounted(() => clearInterval(temporizadorMedicion));
</script>

<template>
  <div class="barra">
      <div class="legend">
        <span v-for="(txt, cod) in ETIQUETA_ESTADO" :key="cod" class="chip" :class="CLASE_ESTADO[cod]">
          {{ txt }}
        </span>
      </div>
      <div class="info">
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
          <button class="btn btn-fantasma btn-sm" :disabled="vaciando" @click="vaciarCarrito">
            {{ vaciando ? 'Liberando…' : 'Vaciar' }}
          </button>
          <router-link to="/venta" class="btn btn-primario btn-sm">
            Registrar venta
          </router-link>
        </div>
      </div>
    </Transition>

    <CasetaDetalle
      :puesto="casetaEnFicha"
      :es-mia="casetaEnFicha ? esMia(casetaEnFicha) : false"
      :ocupado="casetaEnFicha ? enPeticion.has(casetaEnFicha.id) : false"
      @cerrar="seleccionada = null"
      @agregar="(p) => click(p)"
      @quitar="(p) => click(p)"
    />

    <PanZoom :focus="{ x: 0.44, y: 0.34, scale: 2.4 }">
      <div class="plano">
        <!-- width/height intrinsecos: el navegador reserva la proporcion antes de
             descargar, asi los pines no bailan mientras carga el plano. -->
        <img
          src="/mapa.png"
          alt="Plano FEXPO UAP"
          width="1836"
          height="2376"
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
          v-memo="[p.estado, p.reservadoPor === auth.id, conFoto.has(p.id), p.mapaX, p.mapaY, p.mapaEscala, p.tamanoMapa, p.color, p.forma]"
          class="pin"
          :class="[CLASE_ESTADO[p.estado], `forma-${p.forma || 'cuadrado'}`,
                   { mia: esMia(p), 'con-foto': conFoto.has(p.id) }]"
          :style="estiloPin(p)"
          :title="rotulo(p)"
          @pointerdown.stop
          @click="abrirFicha(p)"
        ></button>
      </div>
    </PanZoom>
</template>

<style scoped>
.barra {
  display: flex; justify-content: space-between; align-items: center;
  padding: 0.6rem 1rem; gap: 1rem; flex-wrap: wrap;
}
.legend { display: flex; gap: 0.4rem; flex-wrap: wrap; }
.chip { padding: 0.2rem 0.55rem; border-radius: 999px; font-size: 0.78rem; font-weight: 600; }
.info { font-size: 0.9rem; }
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
  width: 100%; display: block;
  pointer-events: none;
  user-select: none; -webkit-user-select: none;
  will-change: transform;
  transform: translateZ(0);
}

/* El pin se mide en % del ancho del plano, asi que crece con el zoom como una caseta real.
   aspect-ratio deriva el alto del ancho ya resuelto en px: sale cuadrado sobre una imagen
   que no lo es. */
.pin {
  position: absolute; aspect-ratio: 1;
  transform: translate(-50%, -50%);
  border: 1px solid rgba(255, 255, 255, 0.85);
  padding: 0; cursor: pointer; box-shadow: 0 1px 3px rgba(0, 0, 0, 0.35);
}
.pin:hover { z-index: 5; filter: brightness(1.12); }
.pin.ocupado, .pin.bloqueado { cursor: default; }

/* Una caseta en trámite ajena no se puede tocar; la propia sí. Se distinguen con un
   borde marcado en vez de otro color, para no romper la leyenda de estados. */
.pin.tramite { cursor: default; }
.pin.tramite.mia { cursor: pointer; border: 2px solid var(--text); box-shadow: 0 0 0 1px #fff; }

/* Las casetas con foto se distinguen con un punto blanco en la esquina. No lleva icono ni
   texto a propósito: al zoom normal un pin mide pocos píxeles y cualquier glifo sería una
   mancha ilegible. */
.pin.con-foto::after {
  content: ""; position: absolute; top: 8%; right: 8%;
  width: 26%; height: 26%; border-radius: 50%;
  background: #fff; box-shadow: 0 0 0 1px rgba(2, 6, 23, 0.35);
}

/* ---- carrito ---- */
.carrito {
  position: fixed; left: 50%; transform: translateX(-50%); bottom: 1rem; z-index: 40;
  display: flex; align-items: center; gap: 1rem; flex-wrap: wrap;
  padding: 0.6rem 0.9rem; border-radius: 999px;
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
.carrito .botones { display: flex; gap: 0.4rem; }

.subir-enter-active, .subir-leave-active { transition: opacity 0.15s ease, transform 0.15s ease; }
.subir-enter-from, .subir-leave-to { opacity: 0; transform: translateX(-50%) translateY(0.5rem); }
@media (prefers-reduced-motion: reduce) {
  .subir-enter-active, .subir-leave-active { transition: none; }
}

.forma-cuadrado { border-radius: 2px; }
.forma-circulo { border-radius: 50%; }
.forma-triangulo { clip-path: polygon(50% 0%, 100% 100%, 0% 100%); border: none; }
</style>
