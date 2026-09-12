<script setup>
import { ref, reactive, computed, watch, onMounted, onUnmounted } from 'vue';
import UiModal from '../components/UiModal.vue';
import { apiFetch } from '../api';
import { aviso } from '../ui/alerta';
import { vFiltro } from '../ui/filtroEntrada.js';
import { escucharTopic } from '../ws.js';

const interesados = ref([]);
const categorias = ref([]);
const cargando = ref(true);
const errorCarga = ref('');
const enVivo = ref(false);

// Filtros y orden se aplican aquí, en el cliente: son pocas filas y así cada cambio es
// instantáneo, sin peticiones (ver InteresadoStandApiController).
const filtros = reactive({ texto: '', categoriaId: null, desde: '', hasta: '' });
const orden = reactive({ campo: 'fecha', asc: false });
const CAMPOS_ORDEN = { fecha: 'Fecha de registro', persona: 'Persona', empresa: 'Emprendimiento', categoria: 'Categoría' };

// `silencioso`: resincronización tras reconectar el WebSocket, sin tapar la tabla con "Cargando…".
async function cargar({ silencioso = false } = {}) {
  if (!silencioso) cargando.value = true;
  errorCarga.value = '';
  try {
    const [ri, rc] = await Promise.all([
      apiFetch('/api/app/interesados-stand'),
      apiFetch('/api/app/interesados-stand/categorias'),
    ]);
    const [di, dc] = await Promise.all([ri.json().catch(() => null), rc.json().catch(() => null)]);
    if (!ri.ok || !Array.isArray(di)) throw new Error(di?.mensaje || `No se pudo cargar la lista (error ${ri.status}).`);
    interesados.value = di;
    categorias.value = Array.isArray(dc) ? dc : [];
  } catch (e) {
    errorCarga.value = e.message;
  } finally {
    cargando.value = false;
  }
}

// Búsqueda sin importar mayúsculas ni tildes: "maria" encuentra a "María".
function normalizar(s) {
  return (s || '').normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();
}

function formatearFecha(iso) {
  if (!iso) return '—';
  return new Date(iso).toLocaleString('es-BO', {
    day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit',
  });
}

// Todos los filtros MENOS la categoría: así los contadores de cada chip dicen cuántos hay de
// esa categoría con la búsqueda y las fechas actuales.
const base = computed(() => {
  const q = normalizar(filtros.texto.trim());
  return interesados.value.filter((i) => {
    const dia = (i.fechaRegistro || '').slice(0, 10); // "2026-09-12": se compara como texto
    if (filtros.desde && dia < filtros.desde) return false;
    if (filtros.hasta && dia > filtros.hasta) return false;
    if (!q) return true;
    return [i.nombreCompleto, i.empresa, i.rubro, i.celular].some((c) => normalizar(c).includes(q));
  });
});

const colator = new Intl.Collator('es', { sensitivity: 'base', numeric: true });
const CLAVES = {
  fecha: (i) => i.fechaRegistro || '',
  persona: (i) => i.nombreCompleto || '',
  empresa: (i) => i.empresa || '',
  categoria: (i) => i.categoriaNombre || '',
};

const filtrados = computed(() => {
  const lista = filtros.categoriaId === null
    ? [...base.value]
    : base.value.filter((i) => i.categoriaId === filtros.categoriaId);
  const clave = CLAVES[orden.campo];
  return lista.sort((a, b) => {
    const r = orden.campo === 'fecha'
      ? clave(a).localeCompare(clave(b))
      : colator.compare(clave(a), clave(b));
    return orden.asc ? r : -r;
  });
});

// Chips de categoría con su contador. Incluye también las categorías que ya no existen pero
// que algún interesado eligió en su momento, para que ese registro no quede imposible de filtrar.
const chips = computed(() => {
  const n = new Map();
  base.value.forEach((i) => n.set(i.categoriaId, (n.get(i.categoriaId) || 0) + 1));
  const lista = categorias.value.map((c) => ({ id: c.id, nombre: c.nombre, color: c.color, n: n.get(c.id) || 0 }));
  interesados.value.forEach((i) => {
    if (i.categoriaId != null && !lista.some((c) => c.id === i.categoriaId)) {
      lista.push({ id: i.categoriaId, nombre: i.categoriaNombre || 'Sin nombre', color: i.categoriaColor, n: n.get(i.categoriaId) || 0 });
    }
  });
  return lista;
});

const hayFiltros = computed(() =>
  Boolean(filtros.texto.trim() || filtros.desde || filtros.hasta || filtros.categoriaId !== null));

function limpiarFiltros() {
  Object.assign(filtros, { texto: '', categoriaId: null, desde: '', hasta: '' });
}

// Al cambiar de campo, cada uno arranca en su sentido natural: los textos de la A a la Z y la
// fecha con lo más reciente primero (lo que se quiere ver al entrar).
watch(() => orden.campo, (campo) => { orden.asc = campo !== 'fecha'; });

function ordenarPor(campo) {
  if (orden.campo === campo) orden.asc = !orden.asc;
  else orden.campo = campo;
}

function flecha(campo) {
  return orden.campo === campo ? (orden.asc ? '▲' : '▼') : '';
}

const etiquetaSentido = computed(() => {
  if (orden.campo === 'fecha') return orden.asc ? '↑ Más antiguos primero' : '↓ Más recientes primero';
  return orden.asc ? 'A → Z' : 'Z → A';
});

// ---- Edición en modal ----

const edicion = reactive({ abierto: false, id: null, nombreCompleto: '', celular: '', empresa: '',
  rubro: '', categoriaId: null, categoriaNombre: '', fechaRegistro: '' });
const guardando = ref(false);

const categoriaDelRegistroExiste = computed(() => categorias.value.some((c) => c.id === edicion.categoriaId));

function abrirEditar(i) {
  Object.assign(edicion, {
    abierto: true, id: i.id, nombreCompleto: i.nombreCompleto || '', celular: i.celular || '',
    empresa: i.empresa || '', rubro: i.rubro || '', categoriaId: i.categoriaId,
    categoriaNombre: i.categoriaNombre || '', fechaRegistro: i.fechaRegistro,
  });
}

function cerrarEdicion() {
  if (!guardando.value) edicion.abierto = false;
}

// Éxito: se cierra el modal, la fila se actualiza en el sitio y sale el aviso de 2 s.
// Fallo: el aviso dice el motivo y el modal queda abierto con lo escrito, para corregir.
async function guardar() {
  if (guardando.value) return;
  guardando.value = true;
  try {
    const r = await apiFetch(`/api/app/interesados-stand/${edicion.id}`, {
      method: 'PATCH',
      body: JSON.stringify({
        nombreCompleto: edicion.nombreCompleto, celular: edicion.celular, empresa: edicion.empresa,
        rubro: edicion.rubro, categoriaId: edicion.categoriaId,
      }),
    });
    const d = await r.json().catch(() => null);
    if (!d?.ok) {
      aviso(d?.mensaje || `No se pudo guardar el cambio (error ${r.status}).`, 'error', 2000, 'No se pudo guardar');
      return;
    }
    aplicarEnVivo(d.interesado, { resaltar: false });
    edicion.abierto = false;
    aviso('Los cambios del registro se guardaron correctamente.', 'ok', 2000, '¡Cambios guardados!');
  } catch (e) {
    aviso(e.message || 'No se pudo guardar el cambio.', 'error', 2000, 'No se pudo guardar');
  } finally {
    guardando.value = false;
  }
}

// ---- En vivo ----
// Cada registro nuevo del formulario "Quiero exponer" (y cada edición hecha desde otro panel)
// llega por /topic/interesados, solo para administración (ver WebSocketConfig).

// Filas recién llegadas, resaltadas unos segundos para que se note qué apareció.
const recienLlegados = ref(new Set());

function aplicarEnVivo(i, { resaltar = true } = {}) {
  if (!i?.id) return;
  const idx = interesados.value.findIndex((x) => x.id === i.id);
  if (idx >= 0) {
    interesados.value.splice(idx, 1, i);
    return;
  }
  interesados.value.unshift(i);
  if (!resaltar) return;
  recienLlegados.value = new Set(recienLlegados.value).add(i.id);
  setTimeout(() => {
    const s = new Set(recienLlegados.value);
    s.delete(i.id);
    recienLlegados.value = s;
  }, 6000);
}

let dejarDeEscuchar = null;
let primeraConexion = true;
onMounted(() => {
  cargar();
  dejarDeEscuchar = escucharTopic('/topic/interesados', {
    onMensaje: (i) => aplicarEnVivo(i),
    onConectado: () => {
      enVivo.value = true;
      // La primera conexión coincide con la carga inicial; en las siguientes (tras un corte)
      // se vuelve a pedir la lista: lo que llegó mientras tanto no se redifunde.
      if (!primeraConexion) cargar({ silencioso: true });
      primeraConexion = false;
    },
    onCerrado: () => { enVivo.value = false; },
  });
});
onUnmounted(() => dejarDeEscuchar?.());
</script>

<template>
  <div class="cabecera-vista">
    <p class="muted nota">
      Personas que llenaron el formulario "Quiero exponer" de la vista pública de la feria.
      Los registros nuevos aparecen solos.
    </p>
    <span class="en-vivo" :class="{ activo: enVivo }" :title="enVivo ? 'Recibiendo registros en tiempo real' : 'Reconectando…'">
      <span class="en-vivo-punto" aria-hidden="true"></span>{{ enVivo ? 'En vivo' : 'Reconectando…' }}
    </span>
  </div>

  <div class="card panel-filtros">
    <div class="filtros-fila">
      <label class="campo campo-busca"><span>Buscar</span>
        <input v-model="filtros.texto" type="search" class="control"
          placeholder="Persona, emprendimiento, rubro o celular…" />
      </label>
      <label class="campo"><span>Desde</span>
        <input v-model="filtros.desde" type="date" class="control" :max="filtros.hasta || undefined" />
      </label>
      <label class="campo"><span>Hasta</span>
        <input v-model="filtros.hasta" type="date" class="control" :min="filtros.desde || undefined" />
      </label>
      <label class="campo"><span>Ordenar por</span>
        <select v-model="orden.campo" class="control">
          <option v-for="(texto, campo) in CAMPOS_ORDEN" :key="campo" :value="campo">{{ texto }}</option>
        </select>
      </label>
      <button class="btn boton-sentido" type="button" @click="orden.asc = !orden.asc">{{ etiquetaSentido }}</button>
    </div>

    <div class="chips" role="group" aria-label="Filtrar por categoría">
      <button type="button" class="chip" :class="{ activo: filtros.categoriaId === null }"
        :aria-pressed="filtros.categoriaId === null" @click="filtros.categoriaId = null">
        Todas <span class="chip-n">{{ base.length }}</span>
      </button>
      <button v-for="c in chips" :key="c.id" type="button" class="chip"
        :class="{ activo: filtros.categoriaId === c.id, 'sin-datos': !c.n }"
        :style="{ '--color-cat': c.color || 'var(--muted)' }"
        :aria-pressed="filtros.categoriaId === c.id" @click="filtros.categoriaId = c.id">
        <span class="punto" aria-hidden="true"></span>{{ c.nombre }} <span class="chip-n">{{ c.n }}</span>
      </button>
    </div>

    <div class="resumen">
      <span class="muted">Mostrando <strong>{{ filtrados.length }}</strong> de {{ interesados.length }}</span>
      <button v-if="hayFiltros" type="button" class="btn btn-sm btn-fantasma" @click="limpiarFiltros">✕ Limpiar filtros</button>
    </div>
  </div>

  <div class="card">
    <div v-if="cargando" class="vacio">Cargando…</div>
    <div v-else-if="errorCarga" class="vacio">
      <p>{{ errorCarga }}</p>
      <button class="btn btn-sm" @click="cargar">Reintentar</button>
    </div>
    <div v-else-if="interesados.length === 0" class="vacio">
      Todavía nadie llenó el formulario "Quiero exponer".
    </div>
    <div v-else-if="filtrados.length === 0" class="vacio">
      Ningún registro coincide con los filtros.
      <button class="btn btn-sm btn-fantasma" @click="limpiarFiltros">Limpiar filtros</button>
    </div>
    <div v-else class="tabla-scroll">
      <table class="tabla">
        <thead>
          <tr>
            <th><button type="button" class="th-orden" @click="ordenarPor('fecha')">Fecha {{ flecha('fecha') }}</button></th>
            <th><button type="button" class="th-orden" @click="ordenarPor('persona')">Persona {{ flecha('persona') }}</button></th>
            <th>Celular</th>
            <th><button type="button" class="th-orden" @click="ordenarPor('empresa')">Emprendimiento {{ flecha('empresa') }}</button></th>
            <th>Rubro</th>
            <th><button type="button" class="th-orden" @click="ordenarPor('categoria')">Categoría {{ flecha('categoria') }}</button></th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="i in filtrados" :key="i.id" :class="{ 'fila-nueva': recienLlegados.has(i.id) }">
            <td class="celda-fecha">{{ formatearFecha(i.fechaRegistro) }}</td>
            <td>
              <strong>{{ i.nombreCompleto }}</strong>
              <span v-if="recienLlegados.has(i.id)" class="badge badge-ok etiqueta-nuevo">Nuevo</span>
            </td>
            <td><a :href="`tel:${i.celular}`">{{ i.celular }}</a></td>
            <td>{{ i.empresa }}</td>
            <td>{{ i.rubro }}</td>
            <td>
              <span class="categoria" :style="{ '--color-cat': i.categoriaColor || 'var(--muted)' }">
                <span class="punto" aria-hidden="true"></span>{{ i.categoriaNombre || '—' }}
              </span>
            </td>
            <td class="acciones">
              <button class="btn btn-sm btn-fantasma" title="Editar" @click="abrirEditar(i)">✏️</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>

  <UiModal v-if="edicion.abierto" titulo="Editar interesado" ancho="560px" @cerrar="cerrarEdicion">
    <p class="muted registrado">Se registró el {{ formatearFecha(edicion.fechaRegistro) }}</p>
    <div class="grid2">
      <label class="campo campo-ancho"><span>Nombre completo *</span>
        <input v-model="edicion.nombreCompleto" v-filtro="'nombre'" autocapitalize="characters" class="control" maxlength="200" autocomplete="off" />
      </label>
      <label class="campo"><span>Número de celular *</span>
        <input v-model="edicion.celular" v-filtro="'telefono'" type="tel" inputmode="tel" class="control" maxlength="16" autocomplete="off" />
      </label>
      <label class="campo"><span>Categoría de stand *</span>
        <select v-model="edicion.categoriaId" class="control">
          <option v-if="!categoriaDelRegistroExiste" :value="edicion.categoriaId" disabled>
            {{ edicion.categoriaNombre || 'Sin categoría' }} (ya no existe)
          </option>
          <option v-for="c in categorias" :key="c.id" :value="c.id">{{ c.nombre }}</option>
        </select>
      </label>
      <label class="campo campo-ancho"><span>Empresa o emprendimiento *</span>
        <input v-model="edicion.empresa" v-filtro="'letras'" autocapitalize="characters" class="control" maxlength="200" autocomplete="off" />
      </label>
      <label class="campo campo-ancho"><span>Rubro *</span>
        <input v-model="edicion.rubro" v-filtro="'letras'" autocapitalize="characters" class="control" maxlength="200" autocomplete="off" />
      </label>
    </div>
    <template #pie>
      <button class="btn btn-fantasma" :disabled="guardando" @click="cerrarEdicion">Cancelar</button>
      <button class="btn btn-primario" :disabled="guardando" @click="guardar">
        {{ guardando ? 'Guardando…' : 'Guardar cambios' }}
      </button>
    </template>
  </UiModal>
</template>

<style scoped>
.cabecera-vista { display: flex; align-items: flex-start; justify-content: space-between; gap: 0.8rem; margin-bottom: 0.8rem; }
.nota { margin: 0; font-size: 0.85rem; }

.en-vivo { display: inline-flex; align-items: center; gap: 0.4rem; font-size: 0.8rem; font-weight: 600; color: var(--muted); white-space: nowrap; }
.en-vivo-punto { width: 0.55rem; height: 0.55rem; border-radius: 50%; background: var(--muted); }
.en-vivo.activo { color: var(--ok); }
.en-vivo.activo .en-vivo-punto { background: var(--ok); animation: latir 1.6s ease-in-out infinite; }
@keyframes latir { 0%, 100% { opacity: 1; } 50% { opacity: 0.35; } }

/* Fila recién llegada por WebSocket: se ilumina y se apaga sola. */
.fila-nueva td { animation: resaltar 6s ease-out; }
@keyframes resaltar { 0%, 40% { background: color-mix(in srgb, var(--ok) 16%, transparent); } 100% { background: transparent; } }
.etiqueta-nuevo { margin-left: 0.4rem; font-size: 0.7rem; }
@media (prefers-reduced-motion: reduce) {
  .en-vivo.activo .en-vivo-punto, .fila-nueva td { animation: none; }
}

.panel-filtros { margin-bottom: 0.8rem; padding: 0.9rem 1rem; display: flex; flex-direction: column; gap: 0.8rem; }
.filtros-fila { display: flex; flex-wrap: wrap; align-items: flex-end; gap: 0.7rem; }
.campo-busca { flex: 1 1 260px; }
.boton-sentido { white-space: nowrap; }

.chips { display: flex; flex-wrap: wrap; gap: 0.4rem; }
.chip {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  padding: 0.3rem 0.7rem;
  border: 1px solid var(--border);
  border-radius: 999px;
  background: transparent;
  color: inherit;
  font: inherit;
  font-size: 0.85rem;
  cursor: pointer;
}
.chip:hover { border-color: var(--acento); }
.chip.activo { background: var(--acento-suave); border-color: var(--acento); font-weight: 600; }
.chip.sin-datos { opacity: 0.55; }
.chip-n { font-size: 0.75rem; color: var(--muted); font-weight: 600; }
.punto { width: 0.6rem; height: 0.6rem; border-radius: 50%; background: var(--color-cat); flex-shrink: 0; }

.resumen { display: flex; align-items: center; justify-content: space-between; gap: 0.6rem; font-size: 0.85rem; min-height: 1.9rem; }

.tabla-scroll { overflow-x: auto; }
.th-orden { background: none; border: 0; padding: 0; font: inherit; color: inherit; cursor: pointer; text-transform: inherit; letter-spacing: inherit; }
.th-orden:hover { color: var(--acento); }
.celda-fecha { white-space: nowrap; font-size: 0.85rem; }
.categoria { display: inline-flex; align-items: center; gap: 0.4rem; white-space: nowrap; }

.registrado { margin: 0; font-size: 0.82rem; }
.grid2 { display: grid; grid-template-columns: 1fr 1fr; gap: 0.8rem; }
.campo-ancho { grid-column: 1 / -1; }
@media (max-width: 520px) { .grid2 { grid-template-columns: 1fr; } }
</style>
