<script setup>
import { ref, computed, watch, onBeforeUnmount } from 'vue';
import { apiFetch } from '../api';
import { ETIQUETA_ESTADO, CLASE_ESTADO } from '../mapa';
import { categorias, asegurarCategorias } from '../ui/catalogoCategorias';

/*
 * Ficha de una caseta: lo que el vendedor le enseña al cliente.
 *
 * Aparece al tocar una caseta en el mapa. Reune lo que hace falta para decidir una compra
 * —como se ve de verdad, cuanto cuesta, que mide y donde esta— y ofrece la accion.
 *
 * En movil es una hoja que sube desde abajo, que es donde llega el pulgar; en pantalla
 * grande, un panel lateral.
 */
const props = defineProps({
  puesto: { type: Object, default: null },
  esMia: { type: Boolean, default: false },
  ocupado: { type: Boolean, default: false },
  /** false cuando la caseta esta asignada a otro vendedor: se informa, no se vende. */
  vendible: { type: Boolean, default: true },
  /**
   * Los vendedores habilitados para esta caseta: [{ vendedorId, vendedor, celular }, ...].
   * Vacio = todavia no la lleva nadie. Son varios porque una caseta puede habilitarse a mas
   * de un vendedor; la vende quien la reserve primero.
   */
  asignaciones: { type: Array, default: () => [] },
  /**
   * Quien TIENE esta caseta: { vendedorId, vendedor, celular, estado } o null.
   *
   * Distinto de `asignaciones`, y conviene no mezclarlos: aquellas son quienes PUEDEN
   * venderla —pueden ser varios— y esto es quien se la llevo, que es uno. Nulo significa
   * "no la tiene nadie" (libre o bloqueada), o que el servidor todavia no lo ha dicho.
   */
  ocupante: { type: Object, default: null },
});
const emit = defineEmits(['cerrar', 'agregar', 'quitar']);


const accion = computed(() => {
  if (!props.puesto) return null;
  // Una caseta de otro vendedor no se vende desde aqui, pero SI se consulta: la ficha
  // completa es la razon de que ahora aparezca en el mapa en vez de estar escondida.
  if (!props.vendible) return null;
  if (props.puesto.estado === 'L') return 'agregar';
  if (props.esMia) return 'quitar';
  return null; // de otro vendedor, vendida o bloqueada: no se toca
});

const motivoSinAccion = computed(() => {
  if (!props.puesto || accion.value) return '';
  // Sin motivo: cuando no es vendible, lo que se muestra es el contacto del companiero,
  // que dice mucho mas que un "no puedes".
  if (!props.vendible) return '';
  // Con nombre, el motivo lo da el bloque `ocupada` de abajo, que ademas trae el telefono.
  // Repetirlo aqui diria dos veces lo mismo y la segunda, peor.
  if (quienLaTiene.value) return '';
  if (props.puesto.estado === 'T') return 'La tiene reservada otro vendedor.';
  if (props.puesto.estado === 'O') return 'Ya está vendida.';
  if (props.puesto.estado === 'X') return 'Está bloqueada por reparación.';
  return '';
});

/**
 * El vendedor que tiene esta caseta, solo cuando hay algo que decir.
 *
 * Se exige el NOMBRE, no solo el id: "la vendió alguien" no le sirve a nadie delante de un
 * cliente, y el hueco donde deberia ir un nombre parece un fallo de la aplicacion. Mientras
 * el servidor no lo confirme, la ficha se queda con el texto de siempre.
 */
const quienLaTiene = computed(() => {
  const o = props.ocupante;
  if (!o || !o.vendedor) return null;
  if (props.puesto?.estado !== 'T' && props.puesto?.estado !== 'O') return null;
  // Es el propio vendedor: ya se lo dice el chip "En mi venta" de la cabecera.
  if (props.esMia) return null;
  return o;
});

const vendida = computed(() => props.puesto?.estado === 'O');

/**
 * "vence en 8 min". Es lo unico honesto que se puede decir del tiempo de una reserva: no hay
 * ninguna columna que guarde CUANDO se tomo, y el vencimiento se renueva cada vez que el
 * vendedor vuelve a tocar una caseta de su carrito.
 */
const venceEn = computed(() => {
  if (props.puesto?.estado !== 'T' || !props.puesto?.reservaExpira) return '';
  const ms = new Date(props.puesto.reservaExpira).getTime() - ahora.value;
  if (!Number.isFinite(ms) || ms <= 0) return '';
  const min = Math.round(ms / 60000);
  if (min < 1) return 'vence en menos de un minuto';
  if (min < 60) return `vence en ${min} min`;
  return `vence en ${Math.round(min / 60)} h`;
});

/**
 * Un reloj propio, y solo mientras la ficha esta abierta.
 *
 * `reservaExpira` es una fecha fija: sin algo que cambie, el texto "vence en 8 min" se
 * quedaria congelado los ocho minutos. Se para al cerrar porque un intervalo por ficha
 * abierta y nunca detenido es como se acumulan los temporizadores huerfanos.
 */
const ahora = ref(Date.now());
let reloj = null;
watch(() => props.puesto?.id, (id) => {
  clearInterval(reloj);
  reloj = null;
  if (!id) return;
  ahora.value = Date.now();
  reloj = setInterval(() => { ahora.value = Date.now(); }, 30000);
}, { immediate: true });
onBeforeUnmount(() => clearInterval(reloj));

/*
 * ---- las opciones de precio de la categoria ----
 *
 * Una categoria puede venderse de varias formas ("PYMES" a 800, "PYMES con tarima" a 1.200).
 * La ficha enseñaba UN precio y punto, asi que el vendedor le cantaba al cliente una cifra que
 * no era la unica posible. Aqui se listan todas; cual se cobra se elige al registrar la venta.
 *
 * El catalogo vive en `ui/catalogoCategorias`, compartido con el mapa: el rotulo del pin
 * necesita lo mismo para poder avisar de que hay varios precios, y dos caches separados serian
 * dos descargas de la misma lista.
 */

watch(() => props.puesto?.id, (id) => { if (id) asegurarCategorias(); }, { immediate: true });

/** Las opciones VIVAS de la categoria de esta caseta, ordenadas como las definio administracion. */
const opciones = computed(() => {
  const c = (categorias.value || []).find((x) => x.id === props.puesto?.categoriaId);
  return (c?.opciones || []).slice().sort((a, b) => (a.orden ?? 0) - (b.orden ?? 0));
});

/**
 * ¿Hay varios precios que enseñar?
 *
 * Solo cuando la categoria tiene MAS DE UNA opcion **y** la caseta no lleva precio propio. Un
 * precio propio (V37) manda sobre las opciones, asi que listarlas ahi seria enseñar importes
 * que esa caseta no va a cobrar — justo el error que se viene a corregir, pero al reves.
 */
const hayVariosPrecios = computed(() => !props.puesto?.precioPropio && opciones.value.length > 1);

const precio = computed(() => Number(props.puesto?.precio || 0));
const bs = (n) => Number(n || 0).toLocaleString('es-BO');

/** Deja un celular listo para `tel:`: sin espacios ni guiones. */
const telefono = (celular) => (celular || '').replace(/[^\d+]/g, '');

/**
 * Cual se copio, por id: con varios vendedores en pantalla, un unico "copiado" pondria el
 * visto en todos los botones a la vez y no se sabria cual se copio.
 */
const copiado = ref(null);
async function copiarContacto(a) {
  if (!a) return;
  try {
    await navigator.clipboard.writeText(`${a.vendedor}${a.celular ? ' — ' + a.celular : ''}`);
    copiado.value = a.vendedorId;
    setTimeout(() => { if (copiado.value === a.vendedorId) copiado.value = null; }, 2000);
  } catch {
    // Sin permiso de portapapeles queda el enlace de llamada, que es lo que mas se usa.
  }
}
</script>

<template>
  <Teleport to="body">
    <Transition name="hoja">
      <div v-if="puesto" class="velo" @click.self="emit('cerrar')">
        <aside class="ficha" role="dialog" aria-label="Detalle de la caseta">
          <header>
            <div>
              <h2>{{ puesto.categoria }} {{ puesto.codigo }}</h2>
              <!-- `mia` pinta el chip de azul (ver style.css): el mismo codigo de color
                   que el pin en el plano, para que no haya que traducir nada. -->
              <span class="chip" :class="[CLASE_ESTADO[puesto.estado], { mia: esMia }]">
                {{ esMia ? 'En mi venta' : ETIQUETA_ESTADO[puesto.estado] }}
              </span>
            </div>
            <button class="btn btn-fantasma btn-icono" aria-label="Cerrar" @click="emit('cerrar')">✕</button>
          </header>

          <!-- La galería de fotos se retiró a propósito: casi ninguna caseta tenía foto, así
               que la ficha se abría con un recuadro de "Sin fotos todavía" ocupando el sitio
               de lo que sí se consulta —el precio— y costaba una petición por cada caseta que
               el vendedor tocaba. Para volver a ponerla, el endpoint sigue estando:
               GET /api/app/puestos/{id}/fotos. -->

          <!-- Varios precios: se listan TODOS en vez de enseñar uno como si fuera el definitivo.
               Cuál se cobra se decide al registrar la venta, no aquí. -->
          <div v-if="hayVariosPrecios" class="precios">
            <p class="titulo-precios">
              Esta categoría se vende de {{ opciones.length }} formas
              <span class="muted">· el precio depende de cuál se elija al registrar</span>
            </p>
            <ul>
              <li v-for="o in opciones" :key="o.id">
                <span class="nom">
                  {{ o.nombre }}
                  <span v-if="o.predeterminada" class="marca">por defecto</span>
                </span>
                <strong>{{ bs(o.precio) }} Bs</strong>
              </li>
            </ul>
          </div>

          <dl class="datos">
            <!-- Un solo precio: se enseña tal cual, que es lo que el vendedor canta. Con varios,
                 el precio vive en el bloque de arriba y aquí sobraría (diría uno de los tres). -->
            <div v-if="!hayVariosPrecios">
              <dt>Precio</dt>
              <dd class="precio">
                {{ precio > 0 ? bs(precio) + ' Bs' : 'sin precio' }}
                <span v-if="puesto.precioPropio" class="marca">precio propio</span>
              </dd>
            </div>
            <div><dt>Medida</dt><dd>{{ puesto.tamano || '—' }}</dd></div>
            <div v-if="puesto.referencia" class="ancho"><dt>Ubicación</dt><dd>{{ puesto.referencia }}</dd></div>
          </dl>

          <!-- Quien se llevo esta caseta. Va ANTES del bloque de habilitados a proposito: si
               la caseta ya esta vendida o reservada, lo que el cliente pregunta es "¿y esta?",
               y la respuesta util es quien la tiene, no quien podria haberla vendido. -->
          <div v-if="quienLaTiene" class="ocupada" :class="{ vendida }">
            <p class="quien">
              <span class="etiqueta">{{ vendida ? 'La vendió' : 'La está registrando' }}</span>
              <strong>{{ quienLaTiene.vendedor }}</strong>
            </p>
            <!-- Solo en trámite: en una vendida, el tiempo ya no significa nada. -->
            <p v-if="venceEn" class="cuando">La reserva {{ venceEn }}.</p>
            <div class="contacto">
              <a v-if="quienLaTiene.celular" class="btn btn-primario grande"
                 :href="`tel:${telefono(quienLaTiene.celular)}`">
                📞 {{ quienLaTiene.celular }}
              </a>
              <button v-if="quienLaTiene.celular" class="btn" @click="copiarContacto(quienLaTiene)">
                {{ copiado === quienLaTiene.vendedorId ? '✓ Copiado' : 'Copiar contacto' }}
              </button>
              <p v-else class="motivo">No tiene celular registrado.</p>
            </div>
          </div>

          <!-- Caseta de otro vendedor: en vez de un "no puedes", el contacto de quien si la
               lleva. Es el motivo de que estas casetas hayan vuelto al mapa: el cliente esta
               parado delante de una y el vendedor tiene que poder decirle a quien llamar.
               Pueden ser VARIOS: la caseta se habilita a quien haga falta y la vende el que
               la reserve primero, asi que se listan todos con su telefono. -->
          <!-- `!quienLaTiene`: si la caseta ya tiene dueño, arriba sale el que se la llevo.
               Listar ademas a los tres habilitados diria "la vende Ana, Luis y Rosa" justo
               debajo de "la vendió Ana", y de las dos frases la de arriba es la que importa. -->
          <div v-if="!vendible && !quienLaTiene" class="ajena">
            <template v-if="asignaciones.length">
              <p class="quien">
                {{ asignaciones.length > 1 ? 'La venden' : 'La vende' }}
                <strong>{{ asignaciones.map((a) => a.vendedor).join(', ') }}</strong>
              </p>
              <div v-for="a in asignaciones" :key="a.vendedorId" class="contacto">
                <a v-if="a.celular" class="btn btn-primario grande" :href="`tel:${telefono(a.celular)}`">
                  📞 {{ a.celular }}<template v-if="asignaciones.length > 1"> · {{ a.vendedor }}</template>
                </a>
                <p v-else class="motivo">{{ a.vendedor }} no tiene celular registrado.</p>
                <button v-if="a.celular" class="btn" @click="copiarContacto(a)">
                  {{ copiado === a.vendedorId ? '✓ Copiado' : 'Copiar contacto' }}
                </button>
              </div>
            </template>
            <p v-else class="motivo">Esta caseta todavía no tiene vendedor asignado.</p>
          </div>

          <footer>
            <p v-if="motivoSinAccion" class="motivo">{{ motivoSinAccion }}</p>
            <button v-if="accion === 'agregar'" class="btn btn-primario grande"
                    :disabled="ocupado" @click="emit('agregar', puesto)">
              Agregar a la venta
            </button>
            <button v-else-if="accion === 'quitar'" class="btn btn-peligro grande"
                    :disabled="ocupado" @click="emit('quitar', puesto)">
              Quitar de la venta
            </button>
          </footer>
        </aside>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.velo {
  position: fixed; inset: 0; z-index: 80; background: rgba(2, 6, 23, 0.45);
  display: flex; align-items: flex-end; justify-content: center;
}
/* La ficha es lo que el vendedor le PONE DELANTE al cliente: la foto, la medida y el
   precio se miran entre dos personas y a un brazo de distancia. Con el tamaño anterior
   (460px de ancho, foto en 4:3, texto de 0.95rem) había que acercarse a leerla. */
.ficha {
  background: var(--panel); width: 100%; max-width: 560px;
  border-radius: var(--radio) var(--radio) 0 0;
  box-shadow: var(--sombra-md); max-height: 92vh; overflow-y: auto;
  display: flex; flex-direction: column; gap: 1rem;
  /* El botón de acción vive al final de la hoja: sin el margen seguro cae justo debajo de
     la barra de gestos de Android y se pulsa el gesto en vez del botón. */
  padding: 1.1rem 1.2rem calc(1.3rem + var(--safe-bottom));
}
header { display: flex; align-items: flex-start; justify-content: space-between; gap: 0.6rem; }
header h2 { margin: 0 0 0.35rem; font-size: 1.35rem; }
.chip { padding: 0.15rem 0.55rem; border-radius: 999px; font-size: 0.75rem; font-weight: 700; }


.datos { display: grid; grid-template-columns: 1fr 1fr; gap: 0.7rem; margin: 0; }
.datos .ancho { grid-column: 1 / -1; }
.datos dt { font-size: 0.78rem; text-transform: uppercase; letter-spacing: 0.04em; color: var(--muted); font-weight: 700; }
.datos dd { margin: 0.15rem 0 0; font-size: 1.1rem; }
/* El precio es el dato que se dice en voz alta: se lee de lejos y sin buscarlo. */
.datos .precio { font-weight: 800; font-size: 1.45rem; font-variant-numeric: tabular-nums; }

/* Las formas de vender una categoría. Ocupa el sitio donde antes iba la galería, que es el
   primer golpe de vista de la ficha: es lo que el cliente pregunta. */
.precios {
  display: flex; flex-direction: column; gap: 0.5rem;
  padding: 0.9rem 1rem; border-radius: var(--radio-sm);
  background: var(--panel-2); border: 1px solid var(--border);
}
.titulo-precios { margin: 0; font-size: 0.95rem; line-height: 1.4; }
.precios ul { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 0.45rem; }
.precios li {
  display: flex; justify-content: space-between; align-items: baseline; gap: 0.8rem;
  padding-top: 0.45rem; border-top: 1px solid var(--border);
}
.precios li:first-child { border-top: none; padding-top: 0; }
.precios .nom { font-size: 1.05rem; }
/* Los importes se comparan entre sí, así que van alineados y con cifras de ancho fijo. */
.precios strong { font-size: 1.25rem; font-variant-numeric: tabular-nums; white-space: nowrap; }
.marca {
  margin-left: 0.4rem; padding: 0.05rem 0.45rem; border-radius: 999px;
  font-size: 0.7rem; font-weight: 700; vertical-align: middle;
  color: var(--acento); border: 1px solid color-mix(in srgb, var(--acento) 45%, transparent);
}
.muted { color: var(--muted); }

.ajena {
  display: flex; flex-direction: column; gap: 0.6rem;
  padding: 0.9rem 1rem; border-radius: var(--radio-sm);
  background: var(--panel-2); border: 1px solid var(--border);
}
.ajena .quien { margin: 0; font-size: 1.05rem; }
.ajena .contacto { display: flex; flex-direction: column; gap: 0.5rem; }
/* El botón de llamar es el que se pulsa delante del cliente: mismo tamaño que el de vender. */
.ajena .contacto .btn { min-height: 52px; font-size: 1.05rem; }

/* Quien se llevó la caseta. Toma prestado el color del estado —ámbar en trámite, rojo
   vendida— para que el bloque diga lo mismo que el pin del plano sin tener que leerlo. */
.ocupada {
  display: flex; flex-direction: column; gap: 0.6rem;
  padding: 0.9rem 1rem; border-radius: var(--radio-sm);
  border: 1px solid color-mix(in srgb, var(--tramite) 45%, transparent);
  background: color-mix(in srgb, var(--tramite) 12%, var(--panel));
}
.ocupada.vendida {
  border-color: color-mix(in srgb, var(--ocupado) 45%, transparent);
  background: color-mix(in srgb, var(--ocupado) 12%, var(--panel));
}
.ocupada .quien { margin: 0; font-size: 1.05rem; display: flex; flex-wrap: wrap; gap: 0.35rem; }
.ocupada .etiqueta { color: var(--muted); }
.ocupada .cuando { margin: 0; font-size: 0.9rem; color: var(--muted); }
.ocupada .contacto { display: flex; flex-direction: column; gap: 0.5rem; }
.ocupada .contacto .btn { min-height: 52px; font-size: 1.05rem; }

footer { display: flex; flex-direction: column; gap: 0.5rem; }
.motivo { margin: 0; font-size: 0.92rem; color: var(--muted); text-align: center; }
/* 56px de alto: se pulsa de pie, con una mano y con el cliente mirando. El mínimo táctil
   recomendado son 44 y este es EL botón de la pantalla. */
.grande { width: 100%; min-height: 56px; padding: 0.9rem; font-size: 1.1rem; font-weight: 700; }

/* En pantalla grande deja de ser una hoja y pasa a panel lateral. */
@media (min-width: 720px) {
  .velo { align-items: center; }
  .ficha { border-radius: var(--radio); max-height: 88vh; }
}

.hoja-enter-active, .hoja-leave-active { transition: opacity 0.18s ease; }
.hoja-enter-active .ficha, .hoja-leave-active .ficha { transition: transform 0.18s ease; }
.hoja-enter-from, .hoja-leave-to { opacity: 0; }
.hoja-enter-from .ficha, .hoja-leave-to .ficha { transform: translateY(1.5rem); }
@media (prefers-reduced-motion: reduce) {
  .hoja-enter-active, .hoja-leave-active,
  .hoja-enter-active .ficha, .hoja-leave-active .ficha { transition: none; }
}
</style>
