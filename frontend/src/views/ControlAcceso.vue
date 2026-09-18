<script setup>
import { ref, computed, onMounted, onUnmounted, watch } from 'vue';
import { apiFetch } from '../api.js';
import { alerta } from '../ui/alerta.js';
import { useAccesosStore } from '../stores/accesos.js';
import BarrasHorizontales from '../components/BarrasHorizontales.vue';
import LineaTiempo from '../components/LineaTiempo.vue';
import BarraProgreso from '../components/BarraProgreso.vue';
import UiModal from '../components/UiModal.vue';

const accesos = useAccesosStore();

const desde = ref('');
const hasta = ref('');
const categoriaId = ref('');
const sentidoFiltro = ref('');
const limite = ref(50);
const offset = ref(0);

const categorias = ref([]);

const KPIs = computed(() => ({
  entradas: accesos.resumenCategoria.reduce((s, c) => s + (Number(c.entradas) || 0), 0),
  salidas: accesos.resumenCategoria.reduce((s, c) => s + (Number(c.salidas) || 0), 0),
  dentro: accesos.dentroDetalle.length,
}));

const opcionesSentido = [
  { valor: '', texto: 'Todos' },
  { valor: 'E', texto: 'Entrada' },
  { valor: 'S', texto: 'Salida' },
];

async function cargarCategorias() {
  try {
    const r = await apiFetch('/api/app/categorias');
    if (r.ok) categorias.value = await r.json();
  } catch { /* silencioso */ }
}

async function cargarTodo() {
  const filtros = {
    categoriaId: categoriaId.value || null,
    desde: desde.value || null,
    hasta: hasta.value || null,
    sentido: sentidoFiltro.value || null,
    limite: limite.value,
    offset: offset.value,
  };
  await accesos.cargar(filtros);
}

async function buscar() {
  offset.value = 0;
  await cargarTodo();
}

async function pagina(direccion) {
  const nuevo = offset.value + direccion * limite.value;
  if (nuevo < 0 || nuevo >= accesos.movimientos.length && !accesos.movimientos.length) return;
  // Para paginacion, recargar con nuevo offset
  const filtros = {
    categoriaId: categoriaId.value || null,
    desde: desde.value || null,
    hasta: hasta.value || null,
    sentido: sentidoFiltro.value || null,
    limite: limite.value,
    offset: nuevo,
  };
  await accesos.cargar(filtros);
  if (accesos.movimientos.length > 0) offset.value = nuevo;
}

function limpiarFiltros() {
  desde.value = '';
  hasta.value = '';
  categoriaId.value = '';
  sentidoFiltro.value = '';
  offset.value = 0;
  cargarTodo();
}

function nombreCompleto(m) {
  return [m.nombre, m.paterno, m.materno].filter(Boolean).join(' ');
}

function formatearFecha(ts) {
  if (!ts) return '—';
  const d = new Date(ts);
  return d.toLocaleString('es-BO', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' });
}

function badgeSentido(s) {
  return s === 'E' ? 'badge-entrada' : 'badge-salida';
}

function textoSentido(s) {
  return s === 'E' ? 'Entrada' : 'Salida';
}

onMounted(async () => {
  await cargarCategorias();
  accesos.conectar();
  await cargarTodo();
});

onUnmounted(() => {
  accesos.desconectar();
});

watch([desde, hasta], () => {
  if (desde.value || hasta.value) {
    offset.value = 0;
  }
});
</script>

<template>
  <div class="control-acceso">
    <!-- Indicador tiempo real -->
    <div v-if="accesos.enVivo" class="en-vivo-indicador" title="Conectado en tiempo real">
      <span class="punto"></span> EN VIVO
    </div>
    <div v-else class="en-vivo-indicador desconectado" title="Sin conexión en tiempo real (usando sondeo)">
      <span class="punto"></span> SIN TIEMPO REAL
    </div>

    <!-- Cabecera con KPIs -->
    <section class="kpis card">
      <div class="kpi-grid">
        <div class="kpi-item">
          <span class="kpi-label">Entradas</span>
          <span class="kpi-valor entrada">{{ KPIs.entradas }}</span>
        </div>
        <div class="kpi-item">
          <span class="kpi-label">Salidas</span>
          <span class="kpi-valor salida">{{ KPIs.salidas }}</span>
        </div>
        <div class="kpi-item">
          <span class="kpi-label">Dentro ahora</span>
          <span class="kpi-valor dentro">{{ KPIs.dentro }}</span>
        </div>
        <div class="kpi-item">
          <span class="kpi-label">Total movimientos</span>
          <span class="kpi-valor total">{{ accesos.movimientos.length }}</span>
        </div>
      </div>
    </section>

    <!-- Filtros -->
    <section class="filtros card">
      <form class="filtros-form" @submit.prevent="buscar">
        <div class="fila-filtros">
          <div class="campo">
            <label>Desde</label>
            <input type="datetime-local" v-model="desde" class="control" />
          </div>
          <div class="campo">
            <label>Hasta</label>
            <input type="datetime-local" v-model="hasta" class="control" />
          </div>
          <div class="campo">
            <label>Categoría</label>
            <select v-model="categoriaId" class="control">
              <option value="">Todas</option>
              <option v-for="c in categorias" :key="c.id" :value="c.id">{{ c.nombre }}</option>
            </select>
          </div>
          <div class="campo">
            <label>Sentido</label>
            <select v-model="sentidoFiltro" class="control">
              <option v-for="o in opcionesSentido" :key="o.valor" :value="o.valor">{{ o.texto }}</option>
            </select>
          </div>
        </div>
        <div class="acciones-filtros">
          <button type="submit" class="btn btn-primario" :disabled="accesos.cargando">
            {{ accesos.cargando ? 'Buscando…' : 'Buscar' }}
          </button>
          <button type="button" class="btn btn-fantasma" @click="limpiarFiltros">Limpiar</button>
        </div>
      </form>
    </section>

    <!-- Gráficos -->
    <section class="graficos">
      <div class="grafico-card card">
        <h3>Movimientos por categoría</h3>
        <BarrasHorizontales
          v-if="accesos.resumenCategoria.length"
          :items="accesos.resumenCategoria.map(c => ({
            label: c.categoria || 'Sin categoría',
            valor: (Number(c.entradas) || 0) + (Number(c.salidas) || 0),
            entradas: Number(c.entradas) || 0,
            salidas: Number(c.salidas) || 0,
          }))"
          :formato="(v) => v.toLocaleString('es-BO')"
        />
        <p v-else class="vacio">Sin datos para el período seleccionado.</p>
      </div>

      <div class="grafico-card card">
        <h3>Actividad por usuario (quien escaneó)</h3>
        <BarrasHorizontales
          v-if="accesos.resumenUsuario.length"
          :items="accesos.resumenUsuario.map(u => ({
            label: u.username || 'Desconocido',
            valor: (Number(u.entradas) || 0) + (Number(u.salidas) || 0),
          }))"
          :formato="(v) => v.toLocaleString('es-BO')"
        />
        <p v-else class="vacio">Sin datos para el período seleccionado.</p>
      </div>
    </section>

    <!-- Personas dentro ahora -->
    <section class="dentro-ahora card" v-if="accesos.dentroDetalle.length">
      <div class="dentro-header">
        <h3>🟢 Dentro ahora ({{ accesos.dentroDetalle.length }})</h3>
      </div>
      <div class="dentro-lista">
        <div v-for="p in accesos.dentroDetalle" :key="p.responsable_id" class="dentro-item">
          <div class="dentro-info">
            <strong>{{ [p.nombre, p.paterno, p.materno].filter(Boolean).join(' ') }}</strong>
            <span class="dentro-meta">{{ p.entidad }} · {{ p.categoria || '—' }} · Caseta(s): {{ p.casetas || '—' }}</span>
          </div>
          <span class="dentro-hora">Entró: {{ formatearFecha(p.ultima_entrada) }}</span>
        </div>
      </div>
    </section>

    <!-- Tabla de movimientos -->
    <section class="movimientos card">
      <div class="movimientos-header">
        <h3>Historial de movimientos</h3>
        <span class="movimientos-total">{{ accesos.movimientos.length }} registros</span>
      </div>

      <div v-if="accesos.cargando && !accesos.movimientos.length" class="cargando-tabla">
        <div class="spinner"></div> Cargando…
      </div>

      <div v-else-if="!accesos.movimientos.length" class="vacio-tabla">
        No hay movimientos para los filtros seleccionados.
      </div>

      <div v-else class="tabla-wrapper">
        <table class="tabla-movimientos">
          <thead>
            <tr>
              <th>Fecha / Hora</th>
              <th>Sentido</th>
              <th>Responsable</th>
              <th>C.I.</th>
              <th>Entidad</th>
              <th>Categoría</th>
              <th>Casetas</th>
              <th>Registrado por</th>
              <th>Origen</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="m in accesos.movimientos" :key="m.id">
              <td class="fecha">{{ formatearFecha(m.cuando) }}</td>
              <td><span class="badge" :class="badgeSentido(m.sentido)">{{ textoSentido(m.sentido) }}</span></td>
              <td>{{ nombreCompleto(m) }}</td>
              <td class="ci">{{ m.ci || '—' }}</td>
              <td>{{ m.entidad }}</td>
              <td>{{ m.categoria || '—' }}</td>
              <td class="casetas">{{ m.casetas || '—' }}</td>
              <td>{{ m.registrado_por || '—' }}</td>
              <td><span class="origen" :class="m.origen === 'APK' ? 'origen-apk' : 'origen-web'">{{ m.origen || '—' }}</span></td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- Paginación -->
      <div v-if="accesos.movimientos.length >= limite" class="paginacion">
        <button class="btn btn-fantasma btn-sm" @click="pagina(-1)" :disabled="offset === 0">Anterior</button>
        <span class="paginacion-info">
          Mostrando {{ offset + 1 }}–{{ Math.min(offset + limite, accesos.movimientos.length + (offset > 0 ? offset : 0)) }} de {{ accesos.movimientos.length }}
        </span>
        <button class="btn btn-fantasma btn-sm" @click="pagina(1)" :disabled="accesos.movimientos.length < limite">Siguiente</button>
      </div>
    </section>
  </div>
</template>

<style scoped>
.control-acceso { display: flex; flex-direction: column; gap: 1.2rem; }

/* Indicador tiempo real */
.en-vivo-indicador {
  display: inline-flex; align-items: center; gap: 0.4rem;
  padding: 0.3rem 0.8rem; border-radius: 999px; font-size: 0.7rem;
  font-weight: 700; text-transform: uppercase; letter-spacing: 0.04em;
  width: fit-content; margin-bottom: 0.5rem;
}
.en-vivo-indicador .punto {
  width: 8px; height: 8px; border-radius: 50%; background: var(--ok);
  box-shadow: 0 0 6px var(--ok); animation: pulso 1.5s ease-in-out infinite;
}
.en-vivo-indicador.desconectado .punto {
  background: var(--tramite); box-shadow: 0 0 6px var(--tramite);
  animation: none;
}
@keyframes pulso { 0%, 100% { opacity: 1; } 50% { opacity: 0.4; } }

/* KPIs */
.kpis { padding: 1rem 1.2rem; }
.kpi-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(160px, 1fr)); gap: 1rem; }
.kpi-item { display: flex; flex-direction: column; gap: 0.2rem; padding: 0.8rem; background: var(--panel-2); border-radius: var(--radio-sm); }
.kpi-label { font-size: 0.75rem; text-transform: uppercase; letter-spacing: 0.06em; color: var(--muted); font-weight: 700; }
.kpi-valor { font-size: 1.8rem; font-weight: 800; font-variant-numeric: tabular-nums; }
.kpi-valor.entrada { color: var(--ok); }
.kpi-valor.salida { color: var(--tramite); }
.kpi-valor.dentro { color: var(--acento); }
.kpi-valor.total { color: var(--texto); }

/* Filtros */
.filtros { padding: 1rem 1.2rem; }
.filtros-form { display: flex; flex-direction: column; gap: 1rem; }
.fila-filtros { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 1rem; align-items: end; }
.campo { display: flex; flex-direction: column; gap: 0.3rem; }
.campo label { font-size: 0.75rem; text-transform: uppercase; letter-spacing: 0.04em; color: var(--muted); font-weight: 700; }
.campo .control { padding: 0.5rem 0.7rem; border: 1px solid var(--border); border-radius: var(--radio-sm); background: var(--panel); color: var(--texto); font: inherit; }
.acciones-filtros { display: flex; gap: 0.6rem; flex-wrap: wrap; }

/* Gráficos */
.graficos { display: grid; grid-template-columns: repeat(auto-fit, minmax(380px, 1fr)); gap: 1.2rem; }
.grafico-card { padding: 1rem 1.2rem; }
.grafico-card h3 { margin: 0 0 1rem; font-size: 1rem; font-weight: 700; }
.vacio { margin: 1rem 0 0; text-align: center; color: var(--muted); font-size: 0.9rem; }

/* Dentro ahora */
.dentro-ahora { padding: 1rem 1.2rem; border-top: 4px solid var(--ok); }
.dentro-header { margin: 0 0 0.8rem; }
.dentro-header h3 { margin: 0; font-size: 1rem; }
.dentro-lista { display: flex; flex-direction: column; gap: 0.6rem; }
.dentro-item { display: flex; justify-content: space-between; align-items: center; padding: 0.7rem; background: var(--panel-2); border-radius: var(--radio-sm); gap: 1rem; flex-wrap: wrap; }
.dentro-info { display: flex; flex-direction: column; gap: 0.2rem; min-width: 0; }
.dentro-meta { font-size: 0.8rem; color: var(--muted); }
.dentro-hora { font-size: 0.8rem; color: var(--muted); font-variant-numeric: tabular-nums; white-space: nowrap; }

/* Tabla */
.movimientos { padding: 1rem 1.2rem; }
.movimientos-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 1rem; }
.movimientos-header h3 { margin: 0; font-size: 1rem; }
.movimientos-total { font-size: 0.85rem; color: var(--muted); }
.tabla-wrapper { overflow-x: auto; }
.tabla-movimientos { width: 100%; border-collapse: collapse; font-size: 0.85rem; }
.tabla-movimientos th, .tabla-movimientos td { padding: 0.6rem 0.8rem; text-align: left; border-bottom: 1px solid var(--border); white-space: nowrap; }
.tabla-movimientos th { font-weight: 700; font-size: 0.7rem; text-transform: uppercase; letter-spacing: 0.06em; color: var(--muted); background: var(--panel-2); position: sticky; top: 0; }
.tabla-movimientos tr:hover td { background: var(--panel-2); }
.fecha { font-variant-numeric: tabular-nums; font-family: ui-monospace, monospace; }
.ci { font-variant-numeric: tabular-nums; font-family: ui-monospace, monospace; }
.casetas { font-weight: 700; color: var(--acento); }
.badge { display: inline-block; padding: 0.15rem 0.5rem; border-radius: 999px; font-size: 0.7rem; font-weight: 700; text-transform: uppercase; }
.badge-entrada { background: color-mix(in srgb, var(--ok) 18%, transparent); color: var(--ok); }
.badge-salida { background: color-mix(in srgb, var(--tramite) 18%, transparent); color: var(--tramite); }
.origen { font-size: 0.75rem; text-transform: uppercase; letter-spacing: 0.04em; font-weight: 600; }
.origen-apk { color: var(--acento); }
.origen-web { color: var(--muted); }

/* Paginación */
.paginacion { display: flex; align-items: center; justify-content: center; gap: 1rem; margin-top: 1rem; padding-top: 1rem; border-top: 1px solid var(--border); }
.paginacion-info { font-size: 0.85rem; color: var(--muted); }

/* Estados */
.cargando-tabla { display: flex; align-items: center; justify-content: center; gap: 0.6rem; padding: 2rem; color: var(--muted); }
.spinner { width: 18px; height: 18px; border: 2px solid var(--border); border-top-color: var(--acento); border-radius: 50%; animation: girar 0.8s linear infinite; }
@keyframes girar { to { transform: rotate(360deg); } }
.vacio-tabla { text-align: center; padding: 2rem; color: var(--muted); }
</style>