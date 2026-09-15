<script setup>
import { computed, onMounted, ref } from 'vue';
import { apiFetch } from '../api';
import { toast } from '../ui/toast';

const cargando = ref(true);
const categorias = ref([]);
const busqueda = ref('');

const visibles = computed(() => {
  const q = busqueda.value.trim().toLowerCase();
  if (!q) return categorias.value;
  return categorias.value.filter((c) =>
    `${c.nombre || ''} ${c.descripcion || ''}`.toLowerCase().includes(q));
});

function precio(c) {
  const n = Number(c.precioBase || 0);
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

onMounted(cargar);
</script>

<template>
  <div class="catalogo">
    <section class="hero card">
      <div>
        <span class="eyebrow">Catalogo FEXPO UAP</span>
        <h2>Categorias y precios</h2>
        <p>Consulta rapida de las categorias disponibles y su precio base por caseta.</p>
      </div>
      <div class="total">
        <strong>{{ categorias.length }}</strong>
        <span>categorias</span>
      </div>
    </section>

    <div class="barra">
      <input v-model="busqueda" class="control" placeholder="Buscar categoria…" />
      <button class="btn btn-fantasma" :disabled="cargando" @click="cargar">Actualizar</button>
    </div>

    <div v-if="cargando" class="vacio">Cargando catalogo…</div>
    <div v-else-if="!visibles.length" class="vacio">
      {{ busqueda.trim() ? 'No hay categorias para esa busqueda.' : 'Todavia no hay categorias activas.' }}
    </div>

    <ul v-else class="grid">
      <li v-for="c in visibles" :key="c.id" class="categoria card">
        <div class="marca" :style="{ '--color': c.color || '#64748b' }">
          <span :class="['figura', c.forma || 'cuadrado']"></span>
        </div>
        <div class="info">
          <h3>{{ c.nombre }}</h3>
          <p>{{ c.descripcion || 'Sin descripcion registrada.' }}</p>
        </div>
        <div class="precio">
          <span>Precio base</span>
          <strong>Bs {{ precio(c) }}</strong>
        </div>
      </li>
    </ul>
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
.total { display: grid; place-items: center; min-width: 105px; padding: 0.8rem; border-radius: var(--radio-sm); background: var(--panel); border: 1px solid var(--border); }
.total strong { font-size: 1.7rem; color: var(--acento); line-height: 1; }
.total span { font-size: 0.78rem; color: var(--muted); }
.barra { display: flex; gap: 0.6rem; align-items: center; flex-wrap: wrap; }
.barra .control { flex: 1; min-width: 220px; }
.vacio { padding: 2rem 1rem; text-align: center; color: var(--muted); }
.grid { list-style: none; margin: 0; padding: 0; display: grid; grid-template-columns: repeat(auto-fit, minmax(260px, 1fr)); gap: 0.8rem; }
.categoria { display: grid; grid-template-columns: auto minmax(0, 1fr); gap: 0.8rem; padding: 1rem; align-items: start; }
.marca { width: 46px; height: 46px; border-radius: 14px; display: grid; place-items: center; background: color-mix(in srgb, var(--color) 18%, transparent); border: 1px solid color-mix(in srgb, var(--color) 42%, var(--border)); }
.figura { width: 24px; height: 24px; background: var(--color); box-shadow: 0 8px 18px color-mix(in srgb, var(--color) 35%, transparent); }
.figura.circulo { border-radius: 50%; }
.figura.triangulo { width: 0; height: 0; background: transparent; border-left: 14px solid transparent; border-right: 14px solid transparent; border-bottom: 24px solid var(--color); box-shadow: none; }
.figura.cuadrado { border-radius: 7px; }
.info { min-width: 0; }
.info h3 { margin: 0; font-size: 1rem; }
.info p { margin: 0.3rem 0 0; color: var(--muted); font-size: 0.86rem; line-height: 1.35; }
.precio { grid-column: 1 / -1; display: flex; justify-content: space-between; align-items: baseline; gap: 1rem; padding-top: 0.75rem; border-top: 1px solid var(--border); }
.precio span { color: var(--muted); font-size: 0.78rem; font-weight: 700; text-transform: uppercase; letter-spacing: 0.04em; }
.precio strong { font-size: 1.25rem; color: var(--ok); font-variant-numeric: tabular-nums; }
@media (max-width: 620px) {
  .hero { align-items: flex-start; flex-direction: column; }
  .total { width: 100%; display: flex; justify-content: space-between; }
}
</style>
