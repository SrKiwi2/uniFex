<script setup>
import { ref, computed, onMounted } from 'vue';
import { apiFetch } from '../api';
import { url as urlApi } from '../config.js';
import { toast } from '../ui/toast';
import { descargarPdf } from '../ui/descargas';
import { alerta } from '../ui/alerta';

const cargando = ref(true);
const busqueda = ref('');
const generando = ref(false);
const credenciales = ref([]);
const seleccion = ref(new Set());
const rotas = ref(new Set());

async function cargar() {
  cargando.value = true;
  try {
    const r = await apiFetch('/api/app/impresion-masiva');
    if (!r.ok) throw new Error('No se pudo cargar la lista de expositores.');
    credenciales.value = await r.json();
  } catch (e) {
    toast(e.message, 'error');
  } finally {
    cargando.value = false;
  }
}

const filtradas = computed(() => {
  const q = busqueda.value.trim().toLowerCase();
  if (!q) return credenciales.value;
  return credenciales.value.filter(c =>
    [c.nombre, c.ci, c.entidad, c.casetas].some(v => (v || '').toLowerCase().includes(q)));
});

const marcadas = computed(() => credenciales.value.filter(c => seleccion.value.has(c.responsableId)));

function alternar(c) {
  const s = new Set(seleccion.value);
  s.has(c.responsableId) ? s.delete(c.responsableId) : s.add(c.responsableId);
  seleccion.value = s;
}

function marcarTodas(marcar) {
  const s = new Set(seleccion.value);
  for (const c of filtradas.value) {
    marcar ? s.add(c.responsableId) : s.delete(c.responsableId);
  }
  seleccion.value = s;
}

/**
 * Lanza la impresion masiva con la plantilla EXPOSITOR (10x13 cm).
 * `ids` vacio = todas las credenciales cargadas.
 * El servidor devuelve un solo PDF duplex: 2 paginas por lote de 2 (frentes + reversos).
 */
async function imprimir(ids, etiqueta) {
  if (generando.value) return;
  generando.value = true;
  try {
    const nombre = `credenciales-duplex-a4-${etiqueta}.pdf`;
    await descargarPdf('/api/app/impresion-masiva/pdf', nombre, {
      method: 'POST',
      body: JSON.stringify({ responsables: ids }),
    });
    toast('Credenciales de expositor generadas', 'ok');
  } catch (e) {
    alerta(e.message, 'error', 0); // 0 = no se cierra solo
  } finally {
    generando.value = false;
  }
}

function imprimirMarcadas() {
  imprimir([...seleccion.value], `marcadas-${seleccion.value.size}`);
}

function imprimirTodas() {
  imprimir([], `todas-${credenciales.value.length}`);
}

onMounted(cargar);
</script>

<template>
  <div class="impresion-masiva">
    <header class="fila entre encabezado">
      <h1>🖨️ Impresión Masiva — Expositores</h1>
      <div class="acciones">
        <input v-model="busqueda" class="control busca" placeholder="Buscar por nombre, C.I., entidad, caseta…" />
        <button class="btn" :disabled="generando || !marcadas.length" @click="imprimirMarcadas">
          Imprimir {{ marcadas.length || '' }} marcada{{ marcadas.length === 1 ? '' : 's' }}
        </button>
        <button class="btn btn-primario" :disabled="generando || !credenciales.length" @click="imprimirTodas">
          {{ generando ? 'Generando…' : `Imprimir todas (${credenciales.length})` }}
        </button>
      </div>
    </header>

    <p class="muted nota">
      Imprime credenciales de expositores con la plantilla <strong>EXPOSITOR</strong> (10×13 cm, foto izq, QR der).
      No exige comprobante ni foto: imprime TODOS los expositores de la edición activa.
      Se descarga <strong>un solo PDF duplex</strong>: cada lote de 4 ocupa 2 páginas seguidas en hoja A4 de
      <strong>21 × 29,7 cm</strong> — la primera con los 4 frentes en cuadrícula 2×2 y la segunda con sus 4 reversos fijos,
      ya espejados para que caigan detrás de su frente. Las credenciales llenan la hoja hasta el borde a lo alto.
      Imprime a <strong>tamaño real (100 %)</strong>, a <strong>doble cara volteando por el borde largo</strong>,
      y corta por las guías. Para imprimir de a pocos, elegí rangos de páginas (cada lote son 2 páginas seguidas).
    </p>

    <div v-if="cargando" class="vacio">Cargando…</div>
    <div v-else-if="filtradas.length === 0" class="vacio">No hay expositores para mostrar.</div>

    <table v-else class="tabla">
      <thead>
        <tr><th></th><th></th><th>Nombre</th><th>C.I.</th><th>Entidad</th><th>Casetas</th><th>Zona</th></tr>
      </thead>
      <tbody>
        <tr v-for="c in filtradas" :key="c.responsableId">
          <td><input type="checkbox" :checked="seleccion.has(c.responsableId)" @change="alternar(c)" /></td>
          <td>
            <img v-if="c.fotoUrl && !rotas.has(c.responsableId)" :src="urlApi(c.fotoUrl)" :alt="c.nombre"
                 class="mini" @error="rotas = new Set(rotas).add(c.responsableId)" />
            <span v-else class="mini sinfoto">—</span>
          </td>
          <td><strong>{{ c.nombre }}</strong></td>
          <td>{{ c.ci }}</td>
          <td>{{ c.entidad }}</td>
          <td>{{ c.casetas || '—' }}</td>
          <td>{{ c.categoria || '—' }}</td>
        </tr>
      </tbody>
    </table>

    <div v-if="generando" class="overlay">
      <div class="spinner"></div>
      <p>Generando PDF…</p>
    </div>
  </div>
</template>

<style scoped>
.impresion-masiva { display: flex; flex-direction: column; gap: 1rem; }
.encabezado { margin-bottom: 0.6rem; gap: 0.8rem; flex-wrap: wrap; }
.busca { max-width: 360px; flex: 1; min-width: 200px; }
.nota { margin: 0 0 1rem; font-size: 0.85rem; }
.tabla { width: 100%; border-collapse: collapse; }
.tabla th, .tabla td { padding: 0.5rem 0.8rem; border-bottom: 1px solid var(--border); text-align: left; }
.tabla th { background: var(--panel-2); font-weight: 600; }
.mini { width: 32px; height: 32px; border-radius: 50%; object-fit: cover; }
.mini.sinfoto { display: inline-flex; align-items: center; justify-content: center; background: var(--border); color: var(--muted); }
.overlay { position: fixed; inset: 0; background: rgba(0,0,0,0.4); display: flex; flex-direction: column; align-items: center; justify-content: center; z-index: 1000; }
.spinner { width: 40px; height: 40px; border: 3px solid var(--border); border-top-color: var(--acento); border-radius: 50%; animation: spin 1s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
</style>
