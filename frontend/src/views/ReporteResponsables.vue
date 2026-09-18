<script setup>
import { ref, computed, onMounted } from 'vue';
import { apiFetch } from '../api';
import { toast } from '../ui/toast';
import { descargarArchivo } from '../ui/descargas';

const responsables = ref([]);
const cargando = ref(true);
const filtroEntidad = ref('');
const filtroCategoria = ref('');
const filtroTitular = ref(''); // '' | 'titular' | 'extra'
const filtroFoto = ref(''); // '' | 'si' | 'no'
const filtroCredencial = ref(''); // '' | 'si' | 'no'

const entidades = computed(() => {
  const set = new Set();
  for (const r of responsables.value) {
    if (r.entidadNombre) set.add(r.entidadNombre);
  }
  return [...set].sort();
});

const categorias = computed(() => {
  const set = new Set();
  for (const r of responsables.value) {
    if (r.categoriaNombre) set.add(r.categoriaNombre);
  }
  return [...set].sort();
});

const filtrados = computed(() => {
  return responsables.value.filter(r => {
    if (filtroEntidad.value && r.entidadNombre !== filtroEntidad.value) return false;
    if (filtroCategoria.value && r.categoriaNombre !== filtroCategoria.value) return false;
    if (filtroTitular.value === 'titular' && !r.esTitular) return false;
    if (filtroTitular.value === 'extra' && !r.esExtra) return false;
    if (filtroFoto.value === 'si' && !r.foto) return false;
    if (filtroFoto.value === 'no' && r.foto) return false;
    const cred = r.vecesImpreso && r.vecesImpreso > 0;
    if (filtroCredencial.value === 'si' && !cred) return false;
    if (filtroCredencial.value === 'no' && cred) return false;
    return true;
  });
});

const total = computed(() => filtrados.value.length);
const conFoto = computed(() => filtrados.value.filter(r => r.foto).length);
const sinFoto = computed(() => total.value - conFoto.value);
const conCredencial = computed(() => filtrados.value.filter(r => r.vecesImpreso && r.vecesImpreso > 0).length);
const sinCredencial = computed(() => total.value - conCredencial.value);

async function cargar() {
  cargando.value = true;
  try {
    const r = await apiFetch('/api/app/reportes/responsables');
    if (!r.ok) throw new Error('No se pudo cargar el reporte de responsables');
    responsables.value = await r.json();
  } catch (e) {
    toast(e.message, 'error');
  } finally {
    cargando.value = false;
  }
}

function limpiar() {
  filtroEntidad.value = '';
  filtroCategoria.value = '';
  filtroTitular.value = '';
  filtroFoto.value = '';
  filtroCredencial.value = '';
}

async function exportarExcel() {
  if (!filtrados.value.length) {
    toast('No hay datos para exportar', 'error');
    return;
  }
  try {
    await descargarArchivo('/api/app/reportes/responsables/excel', 'reporte-responsables.xlsx');
    toast('Reporte de responsables descargado en Excel', 'ok');
  } catch (e) {
    toast(e.message, 'error');
  }
}

onMounted(cargar);
</script>

<template>
  <section class="card reporte-responsables">
    <header class="reporte-head">
      <div>
        <p class="eyebrow">Acreditación</p>
        <h2>Reporte de responsables por categoría</h2>
        <p class="muted">Listado completo de la edición activa con estado de credencial y foto.</p>
      </div>
      <div class="acciones-exportar">
        <button class="btn btn-fantasma" :disabled="cargando || !filtrados.length" @click="exportarExcel">
          Exportar Excel
        </button>
      </div>
    </header>

        <div class="filtros card">
          <label class="campo"><span>Entidad</span>
            <select v-model="filtroEntidad" class="control">
              <option value="">Todas</option>
              <option v-for="e in entidades" :key="e" :value="e">{{ e }}</option>
            </select>
          </label>
          <label class="campo"><span>Categoría</span>
            <select v-model="filtroCategoria" class="control">
              <option value="">Todas</option>
              <option v-for="c in categorias" :key="c" :value="c">{{ c }}</option>
            </select>
          </label>
          <label class="campo"><span>Tipo</span>
            <select v-model="filtroTitular" class="control">
              <option value="">Todos</option>
              <option value="titular">Titular</option>
              <option value="extra">Extra</option>
            </select>
          </label>
          <label class="campo"><span>Foto</span>
            <select v-model="filtroFoto" class="control">
              <option value="">Todas</option>
              <option value="si">Con foto</option>
              <option value="no">Sin foto</option>
            </select>
          </label>
          <label class="campo"><span>Credencial impresa</span>
            <select v-model="filtroCredencial" class="control">
              <option value="">Todas</option>
              <option value="si">Impresa</option>
              <option value="no">Sin imprimir</option>
            </select>
          </label>
          <div class="botones-filtro">
            <button class="btn btn-fantasma" :disabled="cargando" @click="limpiar">Limpiar filtros</button>
          </div>
        </div>

        <div class="mini-kpis">
          <div><span>Total</span><strong>{{ total }}</strong></div>
          <div><span>Con foto</span><strong>{{ conFoto }}</strong></div>
          <div><span>Sin foto</span><strong>{{ sinFoto }}</strong></div>
          <div><span>Cred. impresa</span><strong>{{ conCredencial }}</strong></div>
          <div><span>Sin credencial</span><strong>{{ sinCredencial }}</strong></div>
        </div>

        <div v-if="cargando" class="vacio">Cargando…</div>
        <div v-else class="tabla-scroll">
          <table class="tabla">
            <thead>
              <tr>
                <th>Entidad</th>
                <th>Tipo</th>
                <th>Objeto</th>
                <th>Categoría</th>
                <th>Puesto</th>
                <th>Responsable</th>
                <th>C.I.</th>
                <th>Celular</th>
                <th class="centro">Titular</th>
                <th class="centro">Extra</th>
                <th class="centro">Foto</th>
                <th class="centro">Cred. Impresa</th>
                <th class="der">Veces</th>
                <th>Última Imp.</th>
                <th class="centro">Incompleta</th>
                <th>Sin Credencial Impresa</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="r in filtrados" :key="r.id">
                <td>{{ r.entidadNombre || '—' }}</td>
                <td>{{ r.tipoEntidadNombre || '—' }}</td>
                <td>{{ r.entidadObjeto || '—' }}</td>
                <td>{{ r.categoriaNombre || '—' }}</td>
                <td>{{ r.puestoCodigo || '—' }}</td>
                <td><strong>{{ r.nombreCompleto }}</strong></td>
                <td>{{ r.ci || '—' }}</td>
                <td>{{ r.celular || '—' }}</td>
                <td class="centro">{{ r.esTitular ? 'SI' : 'NO' }}</td>
                <td class="centro">{{ r.esExtra ? 'SI' : 'NO' }}</td>
                <td class="centro" :class="{ 'badge-ok': r.foto, 'badge-no': !r.foto }">
                  {{ r.foto ? 'SI' : 'NO' }}
                </td>
                <td class="centro" :class="{ 'badge-ok': r.vecesImpreso && r.vecesImpreso > 0, 'badge-no': !r.vecesImpreso || r.vecesImpreso === 0 }">
                  {{ r.vecesImpreso && r.vecesImpreso > 0 ? 'SI' : 'NO' }}
                </td>
                <td class="der">{{ r.vecesImpreso || 0 }}</td>
                <td>{{ r.ultimaImpresion ? new Date(r.ultimaImpresion).toLocaleString('es-BO') : '—' }}</td>
                <td class="centro">{{ r.algunaIncompleta ? 'SI' : 'NO' }}</td>
                <td class="campo-editable" contenteditable="true" @blur="r.sinCredencialImpresa = $event.target.innerText"></td>
              </tr>
              <tr v-if="filtrados.length === 0"><td colspan="16" class="vacio">Sin responsables con esos filtros.</td></tr>
            </tbody>
          </table>
        </div>
      </section>
</template>

<style scoped>
.reporte-responsables { padding: 1.2rem; }
.reporte-head { display: flex; justify-content: space-between; align-items: flex-start; gap: 1rem; margin-bottom: 1rem; flex-wrap: wrap; }
.reporte-head h2 { margin: 0; }
.acciones-exportar { display: flex; flex-wrap: wrap; gap: 0.55rem; justify-content: flex-end; }
.eyebrow { margin: 0 0 0.25rem; color: var(--muted); text-transform: uppercase; letter-spacing: 0.08em; font-size: 0.75rem; font-weight: 800; }
.muted { color: var(--muted); margin: 0.25rem 0 0; }
.filtros { padding: 1rem; display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 0.8rem; align-items: end; background: var(--panel-2); box-shadow: none; }
.botones-filtro { display: flex; flex-wrap: wrap; gap: 0.5rem; }
.mini-kpis { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 0.7rem; margin: 1rem 0; }
.mini-kpis > div { padding: 0.85rem 1rem; border: 1px solid var(--border); border-radius: var(--radio-sm); background: linear-gradient(135deg, var(--panel), var(--panel-2)); text-align: center; }
.mini-kpis span { display: block; color: var(--muted); font-size: 0.78rem; font-weight: 700; text-transform: uppercase; letter-spacing: 0.04em; }
.mini-kpis strong { display: block; margin-top: 0.25rem; font-size: 1.25rem; color: var(--acento); }
.tabla-scroll { overflow-x: auto; }
.tabla { width: 100%; border-collapse: collapse; font-size: 0.85rem; }
.tabla th, .tabla td { padding: 0.5rem 0.6rem; border: 1px solid var(--border); vertical-align: top; }
.tabla th { background: var(--panel-2); font-weight: 800; text-align: left; white-space: nowrap; position: sticky; top: 0; z-index: 1; }
.centro { text-align: center; }
.der { text-align: right; font-variant-numeric: tabular-nums; }
.badge-ok { color: var(--ok); font-weight: 700; }
.badge-no { color: var(--error); font-weight: 700; }
.campo-editable { min-width: 180px; outline: none; }
.campo-editable:focus { background: var(--panel-2); }
.vacio { padding: 2rem; text-align: center; color: var(--muted); }
@media (max-width: 980px) {
  .filtros { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .mini-kpis { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
@media (max-width: 640px) {
  .filtros, .mini-kpis { grid-template-columns: 1fr; }
}
</style>