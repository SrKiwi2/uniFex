<script setup>
import { computed, onMounted, ref } from 'vue';
import { apiFetch } from '../api';
import { toast } from '../ui/toast';

const cargando = ref(false);
const guardando = ref(false);
const puestos = ref([]);
const categorias = ref([]);
const editados = ref({});
const filtro = ref({ texto: '', categoria: '', estado: '' });

const estados = {
  L: 'Libre',
  T: 'En trámite',
  O: 'Ocupado',
  X: 'Bloqueado',
};

const visibles = computed(() => {
  const q = filtro.value.texto.trim().toLowerCase();
  return puestos.value.filter((p) => {
    if (filtro.value.categoria && String(p.categoriaId) !== filtro.value.categoria) return false;
    if (filtro.value.estado && p.estado !== filtro.value.estado) return false;
    if (!q) return true;
    return [p.codigo, p.categoria, p.tamano, p.referencia]
      .some((v) => String(v || '').toLowerCase().includes(q));
  });
});

const cambios = computed(() => puestos.value
  .filter((p) => codigoEditado(p) !== codigoOriginal(p))
  .map((p) => ({ id: p.id, codigo: codigoEditado(p) })));

function codigoOriginal(p) {
  return String(p.codigo || '').trim();
}

function codigoEditado(p) {
  return String(editados.value[p.id] ?? p.codigo ?? '').trim();
}

function badgeEstado(estado) {
  if (estado === 'L') return 'badge-ok';
  if (estado === 'T') return 'badge-info';
  if (estado === 'O') return 'badge-danger';
  return 'badge-muted';
}

async function cargar() {
  cargando.value = true;
  try {
    const [rp, rc] = await Promise.all([
      apiFetch('/api/app/puestos'),
      apiFetch('/api/app/catalogo'),
    ]);
    if (!rp.ok) throw new Error('No se pudieron cargar los puestos');
    puestos.value = (await rp.json()).filter((p) => p.activo !== false)
      .sort((a, b) => String(a.categoria || '').localeCompare(String(b.categoria || ''), 'es')
        || Number(a.codigo) - Number(b.codigo)
        || String(a.codigo || '').localeCompare(String(b.codigo || ''), 'es'));
    categorias.value = rc.ok ? await rc.json() : [];
    editados.value = Object.fromEntries(puestos.value.map((p) => [p.id, p.codigo || '']));
  } catch (e) {
    toast(e.message, 'error');
  } finally {
    cargando.value = false;
  }
}

function descartar() {
  editados.value = Object.fromEntries(puestos.value.map((p) => [p.id, p.codigo || '']));
}

async function guardar() {
  if (guardando.value || !cambios.value.length) return;
  const vacios = cambios.value.filter((c) => !String(c.codigo || '').trim());
  if (vacios.length) { toast('No puede quedar un puesto sin número', 'error'); return; }
  if (!confirm(`Se cambiará el número de ${cambios.value.length} puesto(s). ¿Continuar?`)) return;

  guardando.value = true;
  try {
    const r = await apiFetch('/api/app/puestos/codigos', {
      method: 'PATCH',
      body: JSON.stringify(cambios.value),
    });
    const d = await r.json().catch(() => ({}));
    if (!r.ok || !d.ok) { toast(d.mensaje || 'No se pudo guardar la numeración', 'error'); return; }
    toast(d.mensaje || 'Numeración guardada', 'ok');
    await cargar();
  } catch (e) {
    toast(e.message, 'error');
  } finally {
    guardando.value = false;
  }
}

onMounted(cargar);
</script>

<template>
  <div class="puestos-vista">
    <section class="hero card">
      <div>
        <p class="eyebrow">Numeración de casetas</p>
        <h2>Puestos</h2>
        <p class="muted">Consulta los puestos activos y cambia su número sin entrar al editor del plano.</p>
      </div>
      <div class="resumen">
        <div><span>Puestos</span><strong>{{ visibles.length }}</strong></div>
        <div><span>Cambios</span><strong>{{ cambios.length }}</strong></div>
      </div>
    </section>

    <section class="card filtros">
      <input v-model="filtro.texto" class="control buscar" placeholder="Buscar por número, categoría o referencia…" />
      <select v-model="filtro.categoria" class="control">
        <option value="">Todas las categorías</option>
        <option v-for="c in categorias" :key="c.id" :value="String(c.id)">{{ c.nombre }}</option>
      </select>
      <select v-model="filtro.estado" class="control">
        <option value="">Todos los estados</option>
        <option v-for="(nombre, clave) in estados" :key="clave" :value="clave">{{ nombre }}</option>
      </select>
      <button class="btn btn-fantasma" :disabled="cargando" @click="cargar">Actualizar</button>
      <button class="btn btn-fantasma" :disabled="!cambios.length || guardando" @click="descartar">Descartar</button>
      <button class="btn btn-primario" :disabled="!cambios.length || guardando" @click="guardar">
        {{ guardando ? 'Guardando…' : 'Guardar cambios' }}
      </button>
    </section>

    <div v-if="cargando" class="vacio">Cargando puestos…</div>
    <section v-else class="card tabla-card">
      <table class="tabla">
        <thead>
          <tr>
            <th>Categoría</th>
            <th>Número actual</th>
            <th>Nuevo número</th>
            <th>Estado</th>
            <th>Tamaño</th>
            <th>Referencia</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="p in visibles" :key="p.id" :class="{ cambiado: codigoEditado(p) !== codigoOriginal(p) }">
            <td><strong>{{ p.categoria || 'Sin categoría' }}</strong></td>
            <td><span class="codigo">{{ p.codigo || '—' }}</span></td>
            <td>
              <input v-model="editados[p.id]" class="control control-codigo" maxlength="30" />
            </td>
            <td><span class="badge" :class="badgeEstado(p.estado)">{{ estados[p.estado] || p.estado }}</span></td>
            <td>{{ p.tamano || '—' }}</td>
            <td class="referencia">{{ p.referencia || '—' }}</td>
          </tr>
          <tr v-if="!visibles.length"><td colspan="6" class="vacio">Sin puestos con esos filtros.</td></tr>
        </tbody>
      </table>
    </section>
  </div>
</template>

<style scoped>
.puestos-vista { display: flex; flex-direction: column; gap: 1rem; }
.hero { display: flex; justify-content: space-between; gap: 1rem; padding: 1.2rem; background: linear-gradient(135deg, color-mix(in srgb, var(--acento) 14%, var(--panel)), var(--panel)); }
.eyebrow { margin: 0; color: var(--acento); text-transform: uppercase; letter-spacing: 0.08em; font-size: 0.72rem; font-weight: 900; }
.hero h2 { margin: 0.15rem 0 0; font-size: 1.45rem; }
.muted { color: var(--muted); }
.hero p { margin: 0.35rem 0 0; }
.resumen { display: flex; gap: 0.7rem; }
.resumen > div { min-width: 110px; padding: 0.85rem 1rem; border: 1px solid var(--border); border-radius: var(--radio-sm); background: var(--panel); text-align: center; }
.resumen span { display: block; color: var(--muted); font-size: 0.74rem; font-weight: 800; text-transform: uppercase; }
.resumen strong { display: block; margin-top: 0.25rem; color: var(--acento); font-size: 1.45rem; }
.filtros { padding: 1rem; display: grid; grid-template-columns: minmax(220px, 1fr) 210px 180px auto auto auto; gap: 0.65rem; align-items: center; }
.tabla-card { padding: 0; overflow: auto; }
.tabla { width: 100%; border-collapse: collapse; font-size: 0.88rem; }
.tabla th, .tabla td { padding: 0.75rem 1rem; border-bottom: 1px solid var(--border); text-align: left; vertical-align: middle; }
.tabla th { background: var(--panel); color: var(--muted); font-size: 0.74rem; text-transform: uppercase; letter-spacing: 0.05em; }
.tabla tr:hover td { background: var(--panel-2); }
.tabla tr.cambiado td { background: color-mix(in srgb, var(--acento) 9%, transparent); }
.codigo { display: inline-flex; min-width: 3rem; justify-content: center; padding: 0.25rem 0.55rem; border-radius: 999px; background: var(--panel-2); border: 1px solid var(--border); font-weight: 900; }
.control-codigo { max-width: 140px; font-weight: 900; text-align: center; }
.referencia { max-width: 340px; white-space: normal; color: var(--muted); }
.badge { padding: 0.18rem 0.55rem; border-radius: 999px; font-size: 0.7rem; font-weight: 800; }
.badge-info { background: var(--acento-suave); color: var(--acento); }
.badge-ok { background: var(--ok-suave); color: var(--ok); }
.badge-muted { background: var(--muted-suave); color: var(--muted); }
.badge-danger { background: var(--danger-suave); color: var(--danger); }
.vacio { padding: 2rem 1rem; text-align: center; color: var(--muted); }
@media (max-width: 980px) {
  .hero { flex-direction: column; }
  .resumen { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .filtros { grid-template-columns: 1fr 1fr; }
  .buscar { grid-column: 1 / -1; }
}
@media (max-width: 620px) {
  .resumen, .filtros { grid-template-columns: 1fr; }
}
</style>
