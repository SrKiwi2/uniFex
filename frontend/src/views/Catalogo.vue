<script setup>
import { computed, onMounted, ref } from 'vue';
import { apiFetch } from '../api';
import { descargarPdf } from '../ui/descargas';
import { toast } from '../ui/toast';

const cargando = ref(true);
const categorias = ref([]);
const busqueda = ref('');
const seccion = ref('tarjetas'); // tarjetas | tabla

const visibles = computed(() => {
  const q = busqueda.value.trim().toLowerCase();
  if (!q) return categorias.value;
  return categorias.value.filter((c) =>
    `${c.nombre || ''} ${c.descripcion || ''} ${(c.opciones || []).map((o) => o.nombre).join(' ')}`
      .toLowerCase().includes(q));
});

const totalSubcategorias = computed(() =>
  categorias.value.reduce((s, c) => s + Math.max(1, (c.opciones || []).length), 0));

function precio(c) {
  const n = Number(c?.precio ?? c?.precioBase ?? c ?? 0);
  return new Intl.NumberFormat('es-BO', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(Number.isFinite(n) ? n : 0);
}

async function cargar() {
  cargando.value = true;
  try {
    const r = await apiFetch('/api/app/catalogo');
    if (!r.ok) throw new Error('No se pudo cargar el catalogo');
    categorias.value = await r.json();
  } catch (e) {
    toast(e.message, 'error');
  } finally {
    cargando.value = false;
  }
}

async function exportarPdf() {
  try {
    await descargarPdf('/api/app/catalogo/pdf', 'catalogo-precios.pdf');
    toast('Catálogo exportado en PDF', 'ok');
  } catch (e) {
    toast(e.message, 'error');
  }
}

onMounted(cargar);
</script>

<template>
  <div class="catalogo">
    <section class="hero card">
      <div>
        <span class="eyebrow">Catalogo FEXPO UAP</span>
        <h2>Categorias, subcategorias y precios</h2>
        <p>Consulta rapida de las categorias disponibles y sus opciones de precio por caseta.</p>
      </div>
      <div class="totales-hero">
        <div class="total">
          <strong>{{ categorias.length }}</strong>
          <span>categorias</span>
        </div>
        <div class="total">
          <strong>{{ totalSubcategorias }}</strong>
          <span>subcategorias</span>
        </div>
      </div>
    </section>

    <div class="tabs-catalogo">
      <button class="tab-catalogo" :class="{ activo: seccion === 'tarjetas' }" @click="seccion = 'tarjetas'">
        <span>Tarjetas</span>
        <small>Vista visual</small>
      </button>
      <button class="tab-catalogo" :class="{ activo: seccion === 'tabla' }" @click="seccion = 'tabla'">
        <span>Tabla de precios</span>
        <small>Comparar opciones</small>
      </button>
    </div>

    <div class="barra">
      <input v-model="busqueda" class="control" placeholder="Buscar categoria o subcategoria…" />
      <button class="btn btn-fantasma" :disabled="cargando" @click="cargar">Actualizar</button>
      <button class="btn btn-primario" :disabled="cargando || !categorias.length" @click="exportarPdf">
        Exportar PDF
      </button>
    </div>

    <div v-if="cargando" class="vacio">Cargando catalogo…</div>
    <div v-else-if="!visibles.length" class="vacio">
      {{ busqueda.trim() ? 'No hay categorias para esa busqueda.' : 'Todavia no hay categorias activas.' }}
    </div>

    <ul v-else-if="seccion === 'tarjetas'" class="grid">
      <li v-for="c in visibles" :key="c.id" class="categoria card">
        <div class="marca" :style="{ '--color': c.color || '#64748b' }">
          <span :class="['figura', c.forma || 'cuadrado']"></span>
        </div>
        <div class="info">
          <h3>{{ c.nombre }}</h3>
          <p>{{ c.descripcion || 'Sin descripcion registrada.' }}</p>
          <div class="opciones">
            <div v-for="o in (c.opciones?.length ? c.opciones : [{ id: 'base', nombre: c.nombre, precio: c.precioBase, predeterminada: true }])" :key="o.id" class="opcion">
              <span>{{ o.nombre }}</span>
              <div class="opcion-detalle">
                <strong>Bs {{ precio(o) }}</strong>
                <em v-if="o.predeterminada">Base</em>
              </div>
            </div>
          </div>
        </div>
        <div class="precio">
          <span>Precio base</span>
          <strong>Bs {{ precio(c) }}</strong>
        </div>
      </li>
    </ul>

    <section v-else class="card tabla-card">
      <table class="tabla">
        <thead>
          <tr>
            <th>Categoria</th><th>Subcategoria</th><th>Tipo</th><th class="der">Precio</th><th>Descripcion</th>
          </tr>
        </thead>
        <tbody>
          <template v-for="c in visibles" :key="c.id">
            <tr v-for="o in (c.opciones?.length ? c.opciones : [{ id: 'base', nombre: c.nombre, precio: c.precioBase, predeterminada: true }])" :key="`${c.id}-${o.id}`">
              <td><strong>{{ c.nombre }}</strong></td>
              <td>{{ o.nombre }}</td>
              <td><span class="badge" :class="o.predeterminada ? 'ok' : ''">{{ o.predeterminada ? 'Base' : 'Opcion' }}</span></td>
              <td class="der"><strong>Bs {{ precio(o) }}</strong></td>
              <td class="desc-tabla">{{ c.descripcion || 'Sin descripcion registrada.' }}</td>
            </tr>
          </template>
        </tbody>
      </table>
    </section>
  </div>
</template>

<style scoped>
.catalogo { display: flex; flex-direction: column; gap: 1rem; }
.hero {
  display: flex; justify-content: space-between; align-items: center; gap: 1rem;
  padding: 1.2rem; overflow: hidden;
  background: linear-gradient(135deg, color-mix(in srgb, var(--acento) 16%, var(--panel)), var(--panel));
}
.eyebrow { color: var(--acento); text-transform: uppercase; letter-spacing: 0.08em; font-size: 0.72rem; font-weight: 800; }
.hero h2 { margin: 0.15rem 0 0; font-size: 1.45rem; }
.hero p { margin: 0.35rem 0 0; color: var(--muted); }
.totales-hero { display: flex; gap: 0.7rem; }
.total { display: grid; place-items: center; min-width: 105px; padding: 0.8rem; border-radius: var(--radio-sm); background: var(--panel); border: 1px solid var(--border); }
.total strong { font-size: 1.7rem; color: var(--acento); line-height: 1; }
.total span { font-size: 0.78rem; color: var(--muted); }
.tabs-catalogo { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0.75rem; padding: 0.35rem; border: 1px solid var(--border); border-radius: var(--radio); background: linear-gradient(135deg, var(--panel-2), var(--panel)); box-shadow: var(--sombra); }
.tab-catalogo { border: 0; border-radius: calc(var(--radio) - 0.25rem); padding: 0.9rem 1rem; background: transparent; color: var(--texto); text-align: left; cursor: pointer; transition: 0.18s ease; }
.tab-catalogo span { display: block; font-weight: 900; }
.tab-catalogo small { display: block; margin-top: 0.2rem; color: var(--muted); font-weight: 700; }
.tab-catalogo:hover { background: color-mix(in srgb, var(--acento) 10%, transparent); }
.tab-catalogo.activo { background: linear-gradient(135deg, var(--acento), var(--acento-2)); color: #1d4ed8; box-shadow: 0 12px 28px color-mix(in srgb, var(--acento) 30%, transparent); }
.tab-catalogo.activo small { color: #1d4ed8; }
.barra { display: flex; gap: 0.6rem; align-items: center; flex-wrap: wrap; }
.barra .control { flex: 1; min-width: 220px; }
.vacio { padding: 2rem 1rem; text-align: center; color: var(--muted); }
.grid { list-style: none; margin: 0; padding: 0; display: grid; grid-template-columns: repeat(auto-fit, minmax(260px, 1fr)); gap: 0.8rem; }
.categoria { display: grid; grid-template-columns: auto minmax(0, 1fr); gap: 0.9rem; padding: 1rem; align-items: start; overflow: hidden; }
.marca { width: 46px; height: 46px; border-radius: 14px; display: grid; place-items: center; background: color-mix(in srgb, var(--color) 18%, transparent); border: 1px solid color-mix(in srgb, var(--color) 42%, var(--border)); }
.figura { width: 24px; height: 24px; background: var(--color); box-shadow: 0 8px 18px color-mix(in srgb, var(--color) 35%, transparent); }
.figura.circulo { border-radius: 50%; }
.figura.triangulo { width: 0; height: 0; background: transparent; border-left: 14px solid transparent; border-right: 14px solid transparent; border-bottom: 24px solid var(--color); box-shadow: none; }
.figura.cuadrado { border-radius: 7px; }
.info { min-width: 0; }
.info h3 { margin: 0; font-size: 1rem; }
.info p { margin: 0.3rem 0 0; color: var(--muted); font-size: 0.86rem; line-height: 1.35; }
.opciones { display: grid; gap: 0.55rem; margin-top: 0.85rem; }
.opcion { display: grid; grid-template-columns: minmax(0, 1fr); gap: 0.45rem; padding: 0.7rem 0.75rem; border: 1px solid var(--border); border-radius: var(--radio-sm); background: linear-gradient(135deg, var(--panel-2), var(--panel)); }
.opcion span { min-width: 0; font-weight: 850; line-height: 1.25; overflow-wrap: anywhere; }
.opcion-detalle { display: flex; justify-content: space-between; align-items: center; gap: 0.65rem; }
.opcion strong { flex: 0 0 auto; color: var(--ok); font-size: 1.05rem; font-variant-numeric: tabular-nums; white-space: nowrap; }
.opcion em { flex: 0 0 auto; padding: 0.15rem 0.45rem; border-radius: 999px; background: color-mix(in srgb, var(--acento) 14%, transparent); color: var(--acento); font-size: 0.68rem; font-style: normal; font-weight: 900; text-transform: uppercase; }
.precio { grid-column: 1 / -1; display: flex; justify-content: space-between; align-items: baseline; gap: 1rem; padding-top: 0.75rem; border-top: 1px solid var(--border); }
.precio span { color: var(--muted); font-size: 0.78rem; font-weight: 700; text-transform: uppercase; letter-spacing: 0.04em; }
.precio strong { font-size: 1.25rem; color: var(--ok); font-variant-numeric: tabular-nums; }
.tabla-card { padding: 0; overflow: auto; }
.der { text-align: right; font-variant-numeric: tabular-nums; }
.desc-tabla { color: var(--muted); max-width: 360px; white-space: normal; }
@media (max-width: 620px) {
  .hero { align-items: flex-start; flex-direction: column; }
  .totales-hero, .total { width: 100%; }
  .total { display: flex; justify-content: space-between; }
  .tabs-catalogo { grid-template-columns: 1fr; }
}
</style>
