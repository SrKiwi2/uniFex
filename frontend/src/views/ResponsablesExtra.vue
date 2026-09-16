<script setup>
/**
 * Control de los responsables EXTRA: los que pasan de los dos por caseta y pagan 15 Bs.
 *
 * <h2>Por qué una pantalla propia</h2>
 * La pregunta que contesta —«¿quién se agregó de más y pagó?»— es de toda la feria, no de una
 * venta. Con cien ventas, responderla abriendo una por una no lo hace nadie, y por eso estos
 * cobros se perdían de vista. Agrupada por entidad se lee como lo que es: una lista de cobros
 * a cuadrar.
 *
 * Lo primero que se ve son los que están <strong>sin comprobante</strong>: es lo único de esta
 * pantalla sobre lo que hay que actuar.
 */
import { ref, computed, onMounted } from 'vue';
import { apiFetch } from '../api';
import { toast } from '../ui/toast';
import { url as urlApi } from '../config';

const extras = ref([]);
const cargando = ref(true);
const filtro = ref('');
/** Qué comprobante está abierto. Uno a la vez: dos imágenes grandes no caben en el móvil. */
const viendo = ref(null);

const urlArchivo = (r) => (r ? urlApi(r) : '');
const esPdf = (r) => /\.pdf($|\?)/i.test(r || '');
const bs = (n) => Number(n || 0).toLocaleString('es-BO');

const cuando = (t) => {
  if (!t) return '';
  try { return new Date(t).toLocaleString('es-BO', { dateStyle: 'short', timeStyle: 'short' }); }
  catch { return ''; }
};

async function cargar() {
  cargando.value = true;
  try {
    const r = await apiFetch('/api/app/inscripciones/responsables-extra');
    if (!r.ok) { toast('No se pudo cargar el listado', 'error'); return; }
    extras.value = await r.json();
  } catch (e) {
    toast(e.message, 'error');
  } finally {
    cargando.value = false;
  }
}
onMounted(cargar);

const filtrados = computed(() => {
  const q = filtro.value.trim().toLowerCase();
  if (!q) return extras.value;
  return extras.value.filter((e) =>
    `${e.entidad} ${e.nombre} ${e.ci} ${e.agregadoPor || ''}`.toLowerCase().includes(q));
});

/** Agrupados por entidad, que es como se pregunta: "los extras de tal empresa". */
const porEntidad = computed(() => {
  const m = new Map();
  for (const e of filtrados.value) {
    const clave = e.inscripcionId;
    if (!m.has(clave)) {
      m.set(clave, { inscripcionId: clave, entidad: e.entidad, personas: [] });
    }
    m.get(clave).personas.push(e);
  }
  return [...m.values()];
});

const sinComprobante = computed(() => extras.value.filter((e) => !e.comprobanteUrl));
const totalCobrado = computed(() =>
  extras.value.reduce((s, e) => s + Number(e.monto || 0), 0));
</script>

<template>
  <p class="muted nota">
    Personas agregadas <strong>por encima</strong> de los dos responsables que da cada caseta.
    Cada una tiene un costo y su comprobante de pago.
  </p>

  <div class="tarjetas-resumen">
    <div class="card resumen"><strong>{{ extras.length }}</strong><span>responsables extra</span></div>
    <div class="card resumen"><strong>{{ bs(totalCobrado) }} Bs</strong><span>cobrado en total</span></div>
    <div class="card resumen" :class="{ ojo: sinComprobante.length }">
      <strong>{{ sinComprobante.length }}</strong><span>sin comprobante</span>
    </div>
  </div>

  <!-- Lo que hay que reclamar, arriba y con nombre y apellido. -->
  <section v-if="sinComprobante.length" class="card faltan">
    <header><h2>Falta el comprobante</h2><span class="cuenta">{{ sinComprobante.length }}</span></header>
    <ul>
      <li v-for="e in sinComprobante" :key="e.responsableId">
        <strong>{{ e.nombre }}</strong>
        <span class="muted">{{ e.entidad }}</span>
        <span class="muted chico">lo agregó {{ e.agregadoPor || '—' }} · {{ cuando(e.cuando) }}</span>
      </li>
    </ul>
  </section>

  <input v-model="filtro" class="control busca"
         placeholder="Buscar por entidad, nombre, C.I. o quién lo agregó…" />

  <div v-if="cargando" class="vacio">Cargando…</div>
  <div v-else-if="!porEntidad.length" class="vacio card">
    No hay responsables extra registrados en esta edición.
  </div>

  <ul v-else class="lista">
    <li v-for="g in porEntidad" :key="g.inscripcionId" class="card grupo">
      <header class="cab-entidad">
        <strong>{{ g.entidad }}</strong>
        <span class="badge badge-muted">
          {{ g.personas.length }} extra{{ g.personas.length === 1 ? '' : 's' }}
        </span>
      </header>

      <ul class="personas">
        <li v-for="e in g.personas" :key="e.responsableId" class="persona">
          <!-- La foto es la de su credencial: reconocer a la persona es parte de auditar. -->
          <img v-if="e.fotoUrl" class="retrato" :src="urlArchivo(e.fotoUrl)" :alt="e.nombre" />
          <span v-else class="retrato sin-retrato" aria-hidden="true">👤</span>

          <div class="datos">
            <strong>{{ e.nombre }}</strong>
            <span class="muted chico">
              C.I. {{ e.ci || '—' }}<template v-if="e.celular"> · {{ e.celular }}</template>
            </span>
            <span class="muted chico">
              Lo agregó {{ e.agregadoPor || '—' }} · {{ cuando(e.cuando) }}
            </span>
          </div>

          <div class="cobro">
            <strong class="monto">{{ bs(e.monto) }} Bs</strong>
            <span v-if="!e.comprobanteUrl" class="badge badge-danger">sin comprobante</span>
            <button v-else class="btn btn-sm"
                    @click="viendo = viendo === e.responsableId ? null : e.responsableId">
              {{ viendo === e.responsableId ? 'Ocultar' : '👁 Ver comprobante' }}
            </button>
            <a v-if="e.comprobanteUrl" class="btn btn-fantasma btn-sm"
               :href="urlArchivo(e.comprobanteUrl)" target="_blank" rel="noopener">Abrir aparte</a>
          </div>

          <div v-if="viendo === e.responsableId && e.comprobanteUrl" class="visor">
            <img v-if="!esPdf(e.comprobanteUrl)" :src="urlArchivo(e.comprobanteUrl)"
                 alt="Comprobante del cobro" />
            <p v-else class="muted">
              El comprobante es un PDF. Tócalo en «Abrir aparte» para verlo.
            </p>
          </div>
        </li>
      </ul>
    </li>
  </ul>
</template>

<style scoped>
.nota { margin: 0 0 1rem; line-height: 1.55; }
.chico { font-size: 0.82rem; }

.tarjetas-resumen { display: grid; grid-template-columns: repeat(3, 1fr); gap: 0.75rem; margin-bottom: 1rem; }
.resumen { padding: 0.8rem; display: flex; flex-direction: column; align-items: center; gap: 0.15rem; text-align: center; }
.resumen strong { font-size: 1.6rem; line-height: 1; }
.resumen span { font-size: 0.82rem; opacity: 0.75; }
/* Sin comprobante es lo único sobre lo que hay que actuar: salta a la vista. */
.resumen.ojo strong { color: var(--danger); }

.faltan { padding: 0; margin-bottom: 1rem; border-left: 3px solid var(--danger); }
.faltan header { display: flex; align-items: center; gap: 0.6rem; padding: 0.8rem 1rem; border-bottom: 1px solid var(--border); }
.faltan h2 { margin: 0; font-size: 1rem; }
.faltan .cuenta { background: var(--danger); color: #fff; border-radius: 999px; padding: 0.05rem 0.5rem; font-size: 0.78rem; font-weight: 700; }
.faltan ul { list-style: none; margin: 0; padding: 0; }
.faltan li { display: flex; gap: 0.6rem; flex-wrap: wrap; align-items: baseline; padding: 0.6rem 1rem; border-bottom: 1px solid var(--border); }
.faltan li:last-child { border-bottom: none; }

.busca { width: 100%; margin-bottom: 1rem; }

.lista { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 0.8rem; }
.grupo { padding: 0.85rem; display: flex; flex-direction: column; gap: 0.6rem; }
.cab-entidad { display: flex; align-items: center; gap: 0.6rem; flex-wrap: wrap; }

.personas { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 0.5rem; }
.persona {
  display: flex; align-items: center; gap: 0.7rem; flex-wrap: wrap;
  padding: 0.6rem 0.7rem; border-radius: var(--radio-sm); background: var(--panel-2);
}
.retrato {
  width: 46px; height: 46px; flex: none; border-radius: 50%; object-fit: cover;
  background: var(--panel); border: 1px solid var(--border);
}
.sin-retrato { display: grid; place-items: center; font-size: 1.3rem; }
.datos { display: flex; flex-direction: column; min-width: 0; flex: 1; }
.cobro { display: flex; align-items: center; gap: 0.4rem; flex-wrap: wrap; }
.monto { white-space: nowrap; font-variant-numeric: tabular-nums; }

.visor { flex-basis: 100%; margin-top: 0.3rem; }
.visor img {
  width: 100%; max-height: 60vh; object-fit: contain;
  border-radius: var(--radio-sm); border: 1px solid var(--border); background: var(--panel);
}

@media (max-width: 560px) {
  .tarjetas-resumen { grid-template-columns: 1fr; }
  .resumen { flex-direction: row; justify-content: flex-start; gap: 0.5rem; }
  .resumen strong { font-size: 1.25rem; }
  .cobro { flex-basis: 100%; }
}
</style>
