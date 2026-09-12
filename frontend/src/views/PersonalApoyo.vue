<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue';
import UiModal from '../components/UiModal.vue';
import AutocompleteSelect from '../components/AutocompleteSelect.vue';
import { apiFetch } from '../api';
import { toast } from '../ui/toast';

// Roles predefinidos para el select de rol
const ROLES_PREDEFINIDOS = [
  { value: 'fotógrafo', label: '📸 Fotógrafo' },
  { value: 'azafata', label: '👩‍✈️ Azafata' },
  { value: 'logística', label: '📦 Logística' },
  { value: 'seguridad', label: '🛡️ Seguridad' },
  { value: 'limpieza', label: '🧹 Limpieza' },
  { value: 'montaje', label: '🔧 Montaje' },
  { value: 'coordinador', label: '📋 Coordinador' },
  { value: 'soporte técnico', label: '💻 Soporte técnico' },
  { value: 'atención al público', label: '🤝 Atención al público' },
  { value: 'otro', label: '➕ Otro' },
];

const modo = ref('personal'); // 'personal' | 'dependencias'
const cargando = ref(true);
const filtro = ref('');
const guardando = ref(false);
const guardandoDep = ref(false);
const ocupado = ref(false);

// Dependencias para el selector
const dependencias = ref([]);

// Personal
const personal = ref([]);

// Modal personal
const modalPersonal = reactive({
  abierto: false,
  editando: null,
  idDependencia: '',
  nombre: '',
  paterno: '',
  materno: '',
  ci: '',
  correo: '',
  celular: '',
  rol: ''
});

// Modal dependencia
const modalDependencia = reactive({
  abierto: false,
  editando: null,
  nombre: '',
  descripcion: ''
});

const personalFiltrado = computed(() => {
  const q = filtro.value.trim().toLowerCase();
  if (!q) return personal.value;
  return personal.value.filter((p) =>
    [p.nombreCompleto, p.ci, p.rol, p.dependenciaNombre].some((c) => (c || '').toLowerCase().includes(q)));
});

const dependenciasFiltradas = computed(() => {
  const q = filtro.value.trim().toLowerCase();
  if (!q) return dependencias.value;
  return dependencias.value.filter((d) =>
    [d.nombre, d.descripcion].some((c) => (c || '').toLowerCase().includes(q)));
});

async function cargarDependencias() {
  try {
    const r = await apiFetch('/api/app/personal-apoyo/dependencias');
    dependencias.value = await r.json();
  } catch (e) {
    toast(e.message, 'error');
  }
}

async function cargarPersonal() {
  cargando.value = true;
  try {
    const r = await apiFetch('/api/app/personal-apoyo');
    personal.value = await r.json();
  } catch (e) {
    toast(e.message, 'error');
  } finally {
    cargando.value = false;
  }
}

async function cargarTodo() {
  await Promise.all([cargarDependencias(), cargarPersonal()]);
}

function cambiarModo(nuevo) {
  if (modo.value === nuevo) return;
  modo.value = nuevo;
  filtro.value = '';
}

function abrirCrearPersonal() {
  Object.assign(modalPersonal, {
    abierto: true, editando: null,
    idDependencia: '', nombre: '', paterno: '', materno: '',
    ci: '', correo: '', celular: '', rol: ''
  });
}

function abrirEditarPersonal(p) {
  // Si el rol no está en la lista predefinida, lo agregamos para que se vea seleccionado
  const rolExistente = ROLES_PREDEFINIDOS.find(r => r.value === p.rol);
  if (!rolExistente && p.rol) {
    ROLES_PREDEFINIDOS.unshift({ value: p.rol, label: p.rol });
  }
  Object.assign(modalPersonal, {
    abierto: true, editando: p.id,
    idDependencia: String(p.idDependencia || ''),
    nombre: p.nombre || '', paterno: p.paterno || '', materno: p.materno || '',
    ci: p.ci || '', correo: p.correo || '', celular: p.celular || '', rol: p.rol || ''
  });
}

function abrirCrearDependencia() {
  Object.assign(modalDependencia, { abierto: true, editando: null, nombre: '', descripcion: '' });
}

function abrirEditarDependencia(d) {
  Object.assign(modalDependencia, { abierto: true, editando: d.id, nombre: d.nombre || '', descripcion: d.descripcion || '' });
}

async function guardarPersonal() {
  if (!modalPersonal.idDependencia) return toast('La dependencia es obligatoria.', 'error');
  if (!modalPersonal.nombre.trim()) return toast('El nombre es obligatorio.', 'error');
  if (!modalPersonal.paterno.trim()) return toast('El apellido paterno es obligatorio.', 'error');
  if (!modalPersonal.ci.trim()) return toast('El C.I. es obligatorio.', 'error');
  if (!modalPersonal.rol.trim()) return toast('El rol es obligatorio (ej. fotógrafo, azafata, logística).', 'error');

  guardando.value = true;
  try {
    const url = modalPersonal.editando ? `/api/app/personal-apoyo/${modalPersonal.editando}` : '/api/app/personal-apoyo';
    const metodo = modalPersonal.editando ? 'PATCH' : 'POST';
    const body = {
      idDependencia: Number(modalPersonal.idDependencia),
      nombre: modalPersonal.nombre, paterno: modalPersonal.paterno, materno: modalPersonal.materno,
      ci: modalPersonal.ci, correo: modalPersonal.correo, celular: modalPersonal.celular, rol: modalPersonal.rol
    };
    const r = await apiFetch(url, { method: metodo, body: JSON.stringify(body) });
    const d = await r.json();
    if (!d.ok) return toast(d.mensaje || 'No se pudo guardar.', 'error');
    toast(d.mensaje, 'ok');
    modalPersonal.abierto = false;
    await cargarPersonal();
  } catch (e) {
    toast(e.message, 'error');
  } finally {
    guardando.value = false;
  }
}

async function guardarDependencia() {
  if (!modalDependencia.nombre.trim()) return toast('El nombre de la dependencia es obligatorio.', 'error');

  guardandoDep.value = true;
  try {
    const url = modalDependencia.editando ? `/api/app/personal-apoyo/dependencias/${modalDependencia.editando}` : '/api/app/personal-apoyo/dependencias';
    const metodo = modalDependencia.editando ? 'PATCH' : 'POST';
    const body = { nombre: modalDependencia.nombre, descripcion: modalDependencia.descripcion };
    const r = await apiFetch(url, { method: metodo, body: JSON.stringify(body) });
    const d = await r.json();
    if (!d.ok) return toast(d.mensaje || 'No se pudo guardar.', 'error');
    toast(d.mensaje, 'ok');
    modalDependencia.abierto = false;
    await cargarDependencias();
    await cargarPersonal(); // refrescar nombres de dependencia
  } catch (e) {
    toast(e.message, 'error');
  } finally {
    guardandoDep.value = false;
  }
}

async function eliminarPersonal(p) {
  if (ocupado.value) return;
  if (!confirm(`¿Eliminar a "${p.nombreCompleto}"?`)) return;
  ocupado.value = true;
  try {
    const r = await apiFetch(`/api/app/personal-apoyo/${p.id}`, { method: 'DELETE' });
    const d = await r.json();
    if (!d.ok) return toast(d.mensaje || 'Error', 'error');
    toast(d.mensaje, 'ok');
    await cargarPersonal();
  } finally {
    ocupado.value = false;
  }
}

async function eliminarDependencia(d) {
  if (ocupado.value) return;
  if (!confirm(`¿Eliminar la dependencia "${d.nombre}"? Se perderán sus asignaciones.`)) return;
  ocupado.value = true;
  try {
    const r = await apiFetch(`/api/app/personal-apoyo/dependencias/${d.id}`, { method: 'DELETE' });
    const res = await r.json();
    if (!res.ok) return toast(res.mensaje || 'Error', 'error');
    toast(res.mensaje, 'ok');
    await cargarDependencias();
    await cargarPersonal();
  } finally {
    ocupado.value = false;
  }
}

onMounted(cargarTodo);
</script>

<template>
  <div class="fila entre encabezado">
    <div class="tabs" role="tablist">
      <button
        role="tab"
        :aria-selected="modo === 'personal'"
        :class="{ activo: modo === 'personal' }"
        @click="cambiarModo('personal')"
        class="btn btn-fantasma"
      >
        👥 Personal de apoyo
      </button>
      <button
        role="tab"
        :aria-selected="modo === 'dependencias'"
        :class="{ activo: modo === 'dependencias' }"
        @click="cambiarModo('dependencias')"
        class="btn btn-fantasma"
      >
        🏢 Dependencias
      </button>
    </div>
    <div class="acciones">
      <input v-model="filtro" class="control busca" :placeholder="modo === 'personal' ? 'Buscar por nombre, C.I., rol, dependencia…' : 'Buscar por nombre o descripción…'" />
      <button class="btn btn-primario" :disabled="guardando || guardandoDep" @click="modo === 'personal' ? abrirCrearPersonal() : abrirCrearDependencia()">
        {{ modo === 'personal' ? '＋ Nuevo personal' : '＋ Nueva dependencia' }}
      </button>
    </div>
  </div>

  <p class="muted nota">
    {{ modo === 'personal'
      ? 'Gestión de personal de apoyo: fotógrafos, azafatas, logística, etc. No afecta a la tabla de personas del sistema.'
      : 'Catálogo de dependencias/unidades a las que se asigna el personal de apoyo.' }}
  </p>

  <div class="card">
    <div v-if="cargando" class="vacio">Cargando…</div>
    <div v-else-if="modo === 'personal' && personalFiltrado.length === 0" class="vacio">No hay personal que mostrar.</div>
    <div v-else-if="modo === 'dependencias' && dependenciasFiltradas.length === 0" class="vacio">No hay dependencias que mostrar.</div>

    <!-- Tabla Personal -->
    <table v-else-if="modo === 'personal'" class="tabla">
      <thead>
        <tr><th>Nombre</th><th>C.I.</th><th>Dependencia</th><th>Rol</th><th>Correo</th><th>Celular</th><th></th></tr>
      </thead>
      <tbody>
        <tr v-for="p in personalFiltrado" :key="p.id">
          <td><strong>{{ p.nombreCompleto }}</strong></td>
          <td>{{ p.ci }}</td>
          <td>{{ p.dependenciaNombre || '—' }}</td>
          <td><span class="badge">{{ p.rol }}</span></td>
          <td>{{ p.correo || '—' }}</td>
          <td>{{ p.celular || '—' }}</td>
          <td class="acciones">
            <button class="btn btn-sm btn-fantasma" :disabled="ocupado" title="Editar" @click="abrirEditarPersonal(p)">✏️</button>
            <button class="btn btn-sm btn-peligro" :disabled="ocupado" title="Eliminar" @click="eliminarPersonal(p)">🗑</button>
          </td>
        </tr>
      </tbody>
    </table>

    <!-- Tabla Dependencias -->
    <table v-else class="tabla">
      <thead>
        <tr><th>Nombre</th><th>Descripción</th><th></th></tr>
      </thead>
      <tbody>
        <tr v-for="d in dependenciasFiltradas" :key="d.id">
          <td><strong>{{ d.nombre }}</strong></td>
          <td>{{ d.descripcion || '—' }}</td>
          <td class="acciones">
            <button class="btn btn-sm btn-fantasma" :disabled="ocupado" title="Editar" @click="abrirEditarDependencia(d)">✏️</button>
            <button class="btn btn-sm btn-peligro" :disabled="ocupado" title="Eliminar" @click="eliminarDependencia(d)">🗑</button>
          </td>
        </tr>
      </tbody>
    </table>
  </div>

  <!-- Modal Personal -->
  <UiModal v-if="modalPersonal.abierto" :titulo="modalPersonal.editando ? 'Editar personal' : 'Nuevo personal de apoyo'"
           @cerrar="modalPersonal.abierto = false" ancho="600px">
    <div class="grid2">
      <AutocompleteSelect
        v-model="modalPersonal.idDependencia"
        :options="dependencias.map(d => ({ value: d.id, label: d.nombre }))"
        label="Dependencia *"
        placeholder="Seleccione dependencia…"
        required
        searchKey="label"
        valueKey="value"
        labelKey="label"
      />
      <AutocompleteSelect
        v-model="modalPersonal.rol"
        :options="ROLES_PREDEFINIDOS"
        label="Rol *"
        placeholder="Seleccione rol…"
        required
        searchKey="label"
        valueKey="value"
        labelKey="label"
      />
      <label class="campo"><span>Nombre *</span><input v-model="modalPersonal.nombre" class="control" required /></label>
      <label class="campo"><span>C.I. *</span><input v-model="modalPersonal.ci" class="control" required /></label>
      <label class="campo"><span>Apellido paterno *</span><input v-model="modalPersonal.paterno" class="control" required /></label>
      <label class="campo"><span>Apellido materno</span><input v-model="modalPersonal.materno" class="control" /></label>
      <label class="campo"><span>Correo</span><input v-model="modalPersonal.correo" type="email" class="control" /></label>
      <label class="campo"><span>Celular</span><input v-model="modalPersonal.celular" class="control" /></label>
    </div>
    <template #pie>
      <button class="btn btn-fantasma" @click="modalPersonal.abierto = false">Cancelar</button>
      <button class="btn btn-primario" :disabled="guardando" @click="guardarPersonal">
        {{ guardando ? 'Guardando…' : 'Guardar' }}
      </button>
    </template>
  </UiModal>

  <!-- Modal Dependencia -->
  <UiModal v-if="modalDependencia.abierto" :titulo="modalDependencia.editando ? 'Editar dependencia' : 'Nueva dependencia'"
           @cerrar="modalDependencia.abierto = false" ancho="540px">
    <div class="grid2">
      <label class="campo"><span>Nombre *</span><input v-model="modalDependencia.nombre" class="control" required /></label>
      <label class="campo"><span>Descripción</span><input v-model="modalDependencia.descripcion" class="control" /></label>
    </div>
    <template #pie>
      <button class="btn btn-fantasma" @click="modalDependencia.abierto = false">Cancelar</button>
      <button class="btn btn-primario" :disabled="guardandoDep" @click="guardarDependencia">
        {{ guardandoDep ? 'Guardando…' : 'Guardar' }}
      </button>
    </template>
  </UiModal>
</template>

<style scoped>
.encabezado { margin-bottom: 0.6rem; gap: 0.8rem; flex-wrap: wrap; }
.tabs { display: flex; gap: 0.4rem; }
.tabs .btn { padding: 0.5rem 1rem; font-weight: 600; }
.tabs .btn.activo { background: var(--acento-suave); color: var(--acento); }
.busca { max-width: 360px; flex: 1; min-width: 200px; }
.nota { margin: 0 0 1rem; font-size: 0.85rem; }
.acciones { display: flex; gap: 0.4rem; }
.grid2 { display: grid; grid-template-columns: 1fr 1fr; gap: 0.8rem; }
@media (max-width: 600px) { .grid2 { grid-template-columns: 1fr; } }
</style>