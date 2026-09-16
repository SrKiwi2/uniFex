<script setup>
import { ref, computed, onMounted } from 'vue';
import BarrasHorizontales from '../components/BarrasHorizontales.vue';
import { apiFetch } from '../api';
import { toast } from '../ui/toast';
import { descargarArchivo, descargarPdf } from '../ui/descargas';

const resumen = ref({ inscripciones: 0, puestos: 0, totalBs: 0 });
const porCategoria = ref([]);
const porEntidad = ref([]);
const cargando = ref(true);
const cargandoVentas = ref(false);
const seccion = ref('resumen'); // resumen | ventas
const vista = ref('categoria'); // categoria | entidad
const ventas = ref([]);
const opciones = ref({ categorias: [], promotores: [] });
const filtros = ref({ desde: '', hasta: '', responsable: '', categorias: [], promotores: [] });

const bs = (n) => 'Bs ' + Number(n || 0).toLocaleString('es-BO', { minimumFractionDigits: 2 });
const num = (n) => Number(n || 0).toLocaleString('es-BO');

// Filas de la tabla según el toggle, ordenadas por total.
const filas = computed(() => {
  const data = vista.value === 'categoria' ? porCategoria.value : porEntidad.value;
  return [...data].sort((a, b) => (b.totalBs || 0) - (a.totalBs || 0));
});
const etiquetaDim = computed(() => (vista.value === 'categoria' ? 'Categoría' : 'Entidad'));
const totalVentas = computed(() => ventas.value.reduce((s, v) => s + Number(v.totalBs || 0), 0));
const casetasVentas = computed(() => ventas.value.reduce((s, v) => s + Number(v.cantidadCasetas || 0), 0));

// Para el gráfico: agrego el total por su dimensión (across vendedores), top 10.
const barras = computed(() => {
  const data = vista.value === 'categoria' ? porCategoria.value : porEntidad.value;
  const clave = vista.value === 'categoria' ? 'categoria' : 'entidad';
  const m = new Map();
  for (const r of data) {
    const k = r[clave] || '(sin nombre)';
    m.set(k, (m.get(k) || 0) + Number(r.totalBs || 0));
  }
  return [...m.entries()]
    .map(([label, valor]) => ({ label, valor }))
    .sort((a, b) => b.valor - a.valor)
    .slice(0, 10);
});

async function cargar() {
  cargando.value = true;
  try {
    const [rr, rc, re, rf] = await Promise.all([
      apiFetch('/api/app/reportes/resumen'),
      apiFetch('/api/app/reportes/por-categoria'),
      apiFetch('/api/app/reportes/por-entidad'),
      apiFetch('/api/app/reportes/ventas/filtros'),
    ]);
    resumen.value = await rr.json();
    porCategoria.value = await rc.json();
    porEntidad.value = await re.json();
    opciones.value = await rf.json();
    await cargarVentas();
  } catch (e) {
    toast(e.message, 'error');
  } finally {
    cargando.value = false;
  }
}

function paramsVentas() {
  const p = new URLSearchParams();
  if (filtros.value.desde) p.set('desde', filtros.value.desde);
  if (filtros.value.hasta) p.set('hasta', filtros.value.hasta);
  if (filtros.value.responsable.trim()) p.set('responsable', filtros.value.responsable.trim());
  for (const id of filtros.value.categorias) p.append('categorias', id);
  for (const id of filtros.value.promotores) p.append('promotores', id);
  const qs = p.toString();
  return qs ? `?${qs}` : '';
}

async function cargarVentas() {
  cargandoVentas.value = true;
  try {
    const r = await apiFetch(`/api/app/reportes/ventas${paramsVentas()}`);
    if (!r.ok) throw new Error('No se pudo cargar el reporte de ventas');
    ventas.value = await r.json();
  } catch (e) {
    toast(e.message, 'error');
  } finally {
    cargandoVentas.value = false;
  }
}

function limpiarVentas() {
  filtros.value = { desde: '', hasta: '', responsable: '', categorias: [], promotores: [] };
  cargarVentas();
}

function normalizarFiltroMultiple(campo) {
  if (filtros.value[campo].includes('')) filtros.value[campo] = [];
}

async function exportarVentasPdf() {
  try {
    await descargarPdf(`/api/app/reportes/ventas/pdf${paramsVentas()}`, 'reporte-ventas.pdf');
    toast('Reporte de ventas descargado', 'ok');
  } catch (e) {
    toast(e.message, 'error');
  }
}

async function exportarVentasExcel() {
  try {
    await descargarArchivo(`/api/app/reportes/ventas/excel${paramsVentas()}`, 'reporte-ventas.xlsx');
    toast('Reporte de ventas descargado en Excel', 'ok');
  } catch (e) {
    toast(e.message, 'error');
  }
}

/*
 * ---- reportes de análisis, para bajar y entregar ----
 *
 * El `id` es la ruta: `/api/app/analisis/{id}/{pdf|excel}`. Un id desconocido responde 400,
 * así que la lista de aquí y el `switch` del servidor no pueden separarse en silencio.
 *
 * Los tres primeros son los que se pidieron; los tres últimos ya alimentaban el Tablero de
 * dirección y se ofrecen aquí también, porque quien viene a "Reportes" viene a llevarse un
 * papel, no a mirar una pantalla.
 */
const REPORTES = [
  { id: 'vendido-categoria', titulo: 'Puestos vendidos por categoría',
    detalle: 'Cuántas se vendieron de cada categoría y cuánto dinero representa.' },
  { id: 'facultad', titulo: 'Venta por facultad',
    detalle: 'Agrupado por el área académica del vendedor que registró la venta.',
    aviso: 'Necesita que cada vendedor tenga su carrera asignada (Vendedores). '
         + 'Sin ella, sus ventas salen en «Sin carrera».' },
  { id: 'vendedores', titulo: 'Venta por administrativo',
    detalle: 'Ranking de vendedores: ventas, casetas, total y ticket medio.' },
  { id: 'ocupacion', titulo: 'Ocupación del plano',
    detalle: 'Lo vendido y lo que queda por vender, por categoría.' },
  { id: 'cobros', titulo: 'Cobros: pagado y pendiente',
    detalle: 'Qué se cobró de verdad y qué ventas siguen sin comprobante.' },
  { id: 'avance', titulo: 'Avance en el tiempo',
    detalle: 'Ventas por día, con el acumulado.' },
];

const bajando = ref('');
async function bajarAnalisis(id, formato) {
  if (bajando.value) return;
  bajando.value = `${id}-${formato}`;
  try {
    const ruta = `/api/app/analisis/${id}/${formato}`;
    const archivo = `${id}.${formato === 'pdf' ? 'pdf' : 'xlsx'}`;
    if (formato === 'pdf') await descargarPdf(ruta, archivo);
    else await descargarArchivo(ruta, archivo);
    toast('Reporte descargado', 'ok');
  } catch (e) {
    toast(e.message || 'No se pudo descargar el reporte', 'error');
  } finally {
    bajando.value = '';
  }
}

onMounted(cargar);
</script>

<template>
  <div class="tabs-reportes">
    <button class="tab-reporte" :class="{ activo: seccion === 'resumen' }" @click="seccion = 'resumen'">
      <span>Resumen general</span>
      <small>KPIs y ranking</small>
    </button>
    <button class="tab-reporte" :class="{ activo: seccion === 'ventas' }" @click="seccion = 'ventas'">
      <span>Ventas realizadas</span>
      <small>Filtros y PDF</small>
    </button>
    <button class="tab-reporte" :class="{ activo: seccion === 'analisis' }" @click="seccion = 'analisis'">
      <span>Reportes de análisis</span>
      <small>Por categoría, facultad y vendedor</small>
    </button>
  </div>

  <!-- Reportes que se bajan y se entregan. Cada uno sale del MISMO cálculo que alimenta el
       Tablero de dirección, no de una consulta propia: con dos consultas, el día que una
       cambie de criterio el papel diría una cifra y la pantalla otra. -->
  <template v-if="seccion === 'analisis'">
    <p class="muted nota-analisis">
      Todos miran la <strong>edición activa</strong> y descartan las ventas canceladas.
      El importe es el <strong>congelado</strong> el día de la venta, no el precio de hoy.
    </p>
    <div class="lista-analisis">
      <article v-for="r in REPORTES" :key="r.id" class="card fila-analisis">
        <div class="texto">
          <strong>{{ r.titulo }}</strong>
          <span class="muted">{{ r.detalle }}</span>
          <span v-if="r.aviso" class="aviso-dato">{{ r.aviso }}</span>
        </div>
        <div class="acciones">
          <button class="btn btn-fantasma" :disabled="!!bajando" @click="bajarAnalisis(r.id, 'pdf')">
            {{ bajando === r.id + '-pdf' ? '…' : 'PDF' }}
          </button>
          <button class="btn btn-fantasma" :disabled="!!bajando" @click="bajarAnalisis(r.id, 'excel')">
            {{ bajando === r.id + '-excel' ? '…' : 'Excel' }}
          </button>
        </div>
      </article>
    </div>
  </template>

  <template v-if="seccion === 'resumen'">
    <div class="kpis">
      <div class="card kpi"><span class="muted">Inscripciones</span><strong>{{ num(resumen.inscripciones) }}</strong></div>
      <div class="card kpi"><span class="muted">Casetas vendidas</span><strong>{{ num(resumen.puestos) }}</strong></div>
      <div class="card kpi total"><span class="muted">Total recaudado</span><strong>{{ bs(resumen.totalBs) }}</strong></div>
    </div>

    <div class="fila entre toggle-row">
      <div class="toggle">
        <button class="btn btn-sm" :class="{ 'btn-primario': vista === 'categoria' }" @click="vista = 'categoria'">Por categoría</button>
        <button class="btn btn-sm" :class="{ 'btn-primario': vista === 'entidad' }" @click="vista = 'entidad'">Por entidad</button>
      </div>
    </div>

    <div v-if="cargando" class="card vacio">Cargando…</div>
    <template v-else>
      <section class="card grafico">
        <h3>Top {{ etiquetaDim.toLowerCase() }}s por recaudación</h3>
        <BarrasHorizontales :items="barras" :formato="bs" />
      </section>

      <section class="card">
        <table class="tabla">
          <thead>
            <tr>
              <th>Vendedor</th><th>{{ etiquetaDim }}</th>
              <th class="der">Inscr.</th><th class="der">Casetas</th><th class="der">Total</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(f, i) in filas" :key="i">
              <td>{{ f.nombreCompleto || '(sin vendedor)' }}</td>
              <td>{{ vista === 'categoria' ? f.categoria : f.entidad }}</td>
              <td class="der">{{ num(f.cantidadInscripciones) }}</td>
              <td class="der">{{ num(f.cantidadPuestos) }}</td>
              <td class="der"><strong>{{ bs(f.totalBs) }}</strong></td>
            </tr>
            <tr v-if="filas.length === 0"><td colspan="5" class="vacio">Sin datos.</td></tr>
          </tbody>
        </table>
      </section>
    </template>
  </template>

  <!-- `v-else-if` y no `v-else`: mientras fue un `v-else` del resumen, cualquier sección que no
       fuera «resumen» lo pintaba — al añadir la tercera pestaña, el reporte detallado de ventas
       aparecía debajo de los reportes de análisis. Con tres pestañas, cada una tiene que decir
       explícitamente cuál es la suya. -->
  <template v-else-if="seccion === 'ventas'">
    <section class="card reporte-ventas">
      <div class="reporte-head">
        <div>
          <p class="eyebrow">Reporte detallado</p>
          <h2>Ventas realizadas</h2>
          <p class="muted">Filtra por fechas, responsables, categorías y promotores/vendedores.</p>
        </div>
        <div class="acciones-exportar">
          <button class="btn btn-primario" :disabled="cargandoVentas || !ventas.length" @click="exportarVentasPdf">
            Exportar PDF
          </button>
          <button class="btn btn-fantasma" :disabled="cargandoVentas || !ventas.length" @click="exportarVentasExcel">
            Exportar Excel
          </button>
        </div>
      </div>

      <div class="filtros card">
        <label class="campo"><span>Desde</span><input v-model="filtros.desde" class="control" type="date" /></label>
        <label class="campo"><span>Hasta</span><input v-model="filtros.hasta" class="control" type="date" /></label>
        <label class="campo buscar"><span>Responsable</span><input v-model="filtros.responsable" class="control" placeholder="Nombre, apellido o C.I." /></label>
        <label class="campo"><span>Categorías</span>
          <select v-model="filtros.categorias" class="control multi" multiple @change="normalizarFiltroMultiple('categorias')">
            <option value="">Todos</option>
            <option v-for="c in opciones.categorias" :key="c.id" :value="String(c.id)">{{ c.nombre }}</option>
          </select>
        </label>
        <label class="campo"><span>Promotores/Vendedores</span>
          <select v-model="filtros.promotores" class="control multi" multiple @change="normalizarFiltroMultiple('promotores')">
            <option value="">Todos</option>
            <option v-for="p in opciones.promotores" :key="p.id" :value="String(p.id)">{{ p.nombre }}</option>
          </select>
        </label>
        <div class="botones-filtro">
          <button class="btn btn-primario" :disabled="cargandoVentas" @click="cargarVentas">Aplicar filtros</button>
          <button class="btn btn-fantasma" :disabled="cargandoVentas" @click="limpiarVentas">Limpiar</button>
        </div>
      </div>

      <div class="mini-kpis">
        <div><span>Ventas</span><strong>{{ num(ventas.length) }}</strong></div>
        <div><span>Casetas</span><strong>{{ num(casetasVentas) }}</strong></div>
        <div><span>Total</span><strong>{{ bs(totalVentas) }}</strong></div>
      </div>

      <div v-if="cargandoVentas" class="vacio">Cargando ventas…</div>
      <div v-else class="tabla-scroll">
        <table class="tabla ventas-tabla">
          <thead>
            <tr>
              <th>Fecha</th><th>Entidad</th><th>Promotor/Vendedor</th><th>Responsables</th>
              <th>Categorías</th><th>Casetas</th><th class="der">Total</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="v in ventas" :key="v.inscripcionId">
              <td>{{ v.fechaCompra ? new Date(v.fechaCompra).toLocaleString('es-BO') : '—' }}</td>
              <td><strong>{{ v.entidad }}</strong></td>
              <td>{{ v.promotor }}</td>
              <td class="texto-largo">{{ v.responsables }}</td>
              <td>{{ v.categorias }}</td>
              <td>{{ v.casetas }}</td>
              <td class="der"><strong>{{ bs(v.totalBs) }}</strong></td>
            </tr>
            <tr v-if="ventas.length === 0"><td colspan="7" class="vacio">Sin ventas con esos filtros.</td></tr>
          </tbody>
        </table>
      </div>
    </section>
  </template>
</template>

<style scoped>
/* auto-fit: eran dos columnas fijas y la tercera pestaña caía sola en una segunda fila,
   ocupando el doble de ancho que las otras dos. */
.tabs-reportes { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(200px, 100%), 1fr)); gap: 0.75rem; margin-bottom: 1rem; padding: 0.35rem; border: 1px solid var(--border); border-radius: var(--radio); background: linear-gradient(135deg, var(--panel-2), var(--panel)); box-shadow: var(--sombra); }
.tab-reporte { border: 0; border-radius: calc(var(--radio) - 0.25rem); padding: 0.9rem 1rem; background: transparent; color: var(--texto); text-align: left; cursor: pointer; transition: 0.18s ease; }
.tab-reporte span { display: block; font-weight: 900; }
.tab-reporte small { display: block; margin-top: 0.2rem; color: var(--muted); font-weight: 700; }
.tab-reporte:hover { background: color-mix(in srgb, var(--acento) 10%, transparent); }
.tab-reporte.activo { background: linear-gradient(135deg, var(--acento), var(--acento-2)); color: #1d4ed8; box-shadow: 0 12px 28px color-mix(in srgb, var(--acento) 30%, transparent); }
.tab-reporte.activo small { color: #1d4ed8; }
.kpis { display: grid; gap: 1rem; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); margin-bottom: 1.2rem; }
.kpi { padding: 1.1rem 1.3rem; display: flex; flex-direction: column; gap: 0.3rem; }
.kpi strong { font-size: 1.7rem; }
.kpi.total strong { color: var(--acento); }
.toggle-row { margin-bottom: 1rem; }
.toggle { display: flex; gap: 0.4rem; }
.grafico { padding: 1.2rem; margin-bottom: 1.2rem; }
.grafico h3 { margin: 0 0 1rem; font-size: 1rem; }
.der { text-align: right; font-variant-numeric: tabular-nums; }
section.card { padding: 0; overflow: hidden; }
section.grafico { padding: 1.2rem; }
.reporte-ventas { padding: 1.2rem; margin-top: 1.2rem; overflow: visible; }
.reporte-head { display: flex; justify-content: space-between; align-items: flex-start; gap: 1rem; margin-bottom: 1rem; }
.reporte-head h2 { margin: 0; }
.acciones-exportar { display: flex; flex-wrap: wrap; gap: 0.55rem; justify-content: flex-end; }
.eyebrow { margin: 0 0 0.25rem; color: var(--muted); text-transform: uppercase; letter-spacing: 0.08em; font-size: 0.75rem; font-weight: 800; }
.muted { color: var(--muted); margin: 0.25rem 0 0; }
.filtros { padding: 1rem; display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 0.8rem; align-items: end; background: var(--panel-2); box-shadow: none; }
.buscar { grid-column: span 2; }
.multi { min-height: 6.6rem; }
.botones-filtro { display: flex; flex-wrap: wrap; gap: 0.5rem; }
.mini-kpis { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 0.7rem; margin: 1rem 0; }
.mini-kpis > div { padding: 0.85rem 1rem; border: 1px solid var(--border); border-radius: var(--radio-sm); background: linear-gradient(135deg, var(--panel), var(--panel-2)); }
.mini-kpis span { display: block; color: var(--muted); font-size: 0.78rem; font-weight: 700; text-transform: uppercase; letter-spacing: 0.04em; }
.mini-kpis strong { display: block; margin-top: 0.25rem; font-size: 1.25rem; color: var(--acento); }
.ventas-tabla td { vertical-align: top; }
.texto-largo { max-width: 260px; white-space: normal; }
@media (max-width: 980px) {
  .filtros { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .buscar { grid-column: span 2; }
}
@media (max-width: 640px) {
  .tabs-reportes { grid-template-columns: 1fr; }
  .reporte-head { flex-direction: column; }
  .acciones-exportar, .acciones-exportar .btn { width: 100%; }
  .filtros, .mini-kpis { grid-template-columns: 1fr; }
  .buscar { grid-column: auto; }
}

/* ---- reportes de análisis ---- */
.nota-analisis { margin: 0 0 0.9rem; line-height: 1.5; font-size: 0.9rem; }
.lista-analisis { display: flex; flex-direction: column; gap: 0.6rem; }
/* `fila-analisis` y no `fila`: en style.css hay una utilidad GLOBAL `.fila` con
   `align-items: center`, y `scoped` NO protege de ella —solo añade especificidad a las reglas
   propias—. Ese choque ya encogió una barra del tablero a 0 px de ancho sin dar ningún error. */
.fila-analisis {
  display: flex; align-items: center; justify-content: space-between; gap: 1rem;
  padding: 0.9rem 1rem; flex-wrap: wrap;
}
.fila-analisis .texto { display: flex; flex-direction: column; gap: 0.15rem; min-width: 0; flex: 1 1 260px; }
.fila-analisis .texto strong { font-size: 1rem; }
.fila-analisis .texto span { font-size: 0.85rem; line-height: 1.4; }
/* Ámbar: no es un error, es un dato que falta y que cambia lo que dice el reporte. */
.aviso-dato { color: var(--tramite); }
.fila-analisis .acciones { display: flex; gap: 0.4rem; flex: none; }
</style>
