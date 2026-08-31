<script setup>
import { ref, computed, onMounted } from 'vue';
import BarrasHorizontales from '../components/BarrasHorizontales.vue';
import { apiFetch } from '../api';
import { toast } from '../ui/toast';

const resumen = ref({ inscripciones: 0, puestos: 0, totalBs: 0 });
const porCategoria = ref([]);
const porEntidad = ref([]);
const cargando = ref(true);
const vista = ref('categoria'); // categoria | entidad

const bs = (n) => 'Bs ' + Number(n || 0).toLocaleString('es-BO', { minimumFractionDigits: 2 });
const num = (n) => Number(n || 0).toLocaleString('es-BO');

// Filas de la tabla según el toggle, ordenadas por total.
const filas = computed(() => {
  const data = vista.value === 'categoria' ? porCategoria.value : porEntidad.value;
  return [...data].sort((a, b) => (b.totalBs || 0) - (a.totalBs || 0));
});
const etiquetaDim = computed(() => (vista.value === 'categoria' ? 'Categoría' : 'Entidad'));

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
    const [rr, rc, re] = await Promise.all([
      apiFetch('/api/app/reportes/resumen'),
      apiFetch('/api/app/reportes/por-categoria'),
      apiFetch('/api/app/reportes/por-entidad'),
    ]);
    resumen.value = await rr.json();
    porCategoria.value = await rc.json();
    porEntidad.value = await re.json();
  } catch (e) {
    toast(e.message, 'error');
  } finally {
    cargando.value = false;
  }
}

onMounted(cargar);
</script>

<template>
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

<style scoped>
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
</style>
