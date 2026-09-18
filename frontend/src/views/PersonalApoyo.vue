<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue';
import UiModal from '../components/UiModal.vue';
import AutocompleteSelect from '../components/AutocompleteSelect.vue';
import { apiFetch } from '../api';
import { url as urlApi } from '../config.js';
import { toast } from '../ui/toast';
import { useAuthStore } from '../stores/auth';

const auth = useAuthStore();
/** Solo para pintar; el recorte real lo hace el servidor (ver mi-dependencia). */
const esSuper = computed(() => (auth.rol || '').toUpperCase().replace(/ /g, '_') === 'SUPER_USUARIO');

/** Lo que el servidor dice de mi: si no soy super, mi dependencia (si mi CI tiene ficha). */
const miDependencia = ref({ id: null, nombre: '' });

/** Aviso bloqueante cuando un no-super entra sin dependencia asignada. */
const avisoSinDependencia = ref(false);

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

/** Opciones de rol + el actual si es personalizado (sin mutar la lista base). */
const rolesParaSelect = computed(() => {
  const actual = (modalPersonal.rol || '').trim();
  if (!actual) return ROLES_PREDEFINIDOS;
  if (ROLES_PREDEFINIDOS.some((r) => r.value === actual)) return ROLES_PREDEFINIDOS;
  return [{ value: actual, label: actual }, ...ROLES_PREDEFINIDOS];
});

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

// Modal personal (sin correo ni celular: ya no se piden)
const modalPersonal = reactive({
  abierto: false,
  editando: null,
  idDependencia: '',
  nombre: '',
  paterno: '',
  materno: '',
  ci: '',
  rol: '',
  descripcionTarea: '',
  foto: ''   // URL actual (/files/...) o '' si no tiene
});
/** Archivo elegido en el modal, aun sin subir. Se sube al guardar. */
const archivoFoto = ref(null);
const vistaPreviaFoto = ref('');

function elegirFoto(e) {
  const f = e.target.files?.[0] || null;
  if (vistaPreviaFoto.value.startsWith('blob:')) URL.revokeObjectURL(vistaPreviaFoto.value);
  archivoFoto.value = f;
  vistaPreviaFoto.value = f ? URL.createObjectURL(f) : '';
}

function limpiarFotoPendiente() {
  if (vistaPreviaFoto.value.startsWith('blob:')) URL.revokeObjectURL(vistaPreviaFoto.value);
  archivoFoto.value = null;
  vistaPreviaFoto.value = '';
}

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
    [p.nombreCompleto, p.ci, p.rol, p.dependenciaNombre, p.descripcionTarea].some((c) => (c || '').toLowerCase().includes(q)));
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
    // Sin super solo pido mi dependencia; el servidor ignora cualquier otro id.
    const qs = (!esSuper.value && miDependencia.value.id) ? `?dependencia=${miDependencia.value.id}` : '';
    const r = await apiFetch(`/api/app/personal-apoyo${qs}`);
    personal.value = await r.json();
  } catch (e) {
    toast(e.message, 'error');
  } finally {
    cargando.value = false;
  }
}

async function cargarTodo() {
  try {
    const r = await apiFetch('/api/app/personal-apoyo/mi-dependencia');
    const d = await r.json();
    if (!d.esSuper && d.idDependencia) {
      miDependencia.value = { id: d.idDependencia, nombre: d.dependenciaNombre || '' };
    }
  } catch (e) {
    toast(e.message, 'error');
  }
  // Las dependencias solo las ve el super (el endpoint da 403 al resto).
  if (esSuper.value) await cargarDependencias();
  await cargarPersonal();
  // Sin dependencia no hay nada que operar: se avisa una vez al entrar.
  if (!esSuper.value && !miDependencia.value.id) avisoSinDependencia.value = true;
}

function cambiarModo(nuevo) {
  if (modo.value === nuevo) return;
  // La pestaña de dependencias no existe para quien no es super (ni se pinta).
  if (nuevo === 'dependencias' && !esSuper.value) return;
  modo.value = nuevo;
  filtro.value = '';
}

function abrirCrearPersonal() {
  limpiarFotoPendiente();
  Object.assign(modalPersonal, {
    abierto: true, editando: null,
    // Sin super la dependencia va fija: es la suya (el servidor la vuelve a forzar).
    idDependencia: (!esSuper.value && miDependencia.value.id) ? String(miDependencia.value.id) : '',
    nombre: '', paterno: '', materno: '',
    ci: '', rol: '', descripcionTarea: '', foto: ''
  });
}

function abrirEditarPersonal(p) {
  limpiarFotoPendiente();
  Object.assign(modalPersonal, {
    abierto: true, editando: p.id,
    idDependencia: String(p.idDependencia || ''),
    nombre: p.nombre || '', paterno: p.paterno || '', materno: p.materno || '',
    ci: p.ci || '', rol: p.rol || '', descripcionTarea: p.descripcionTarea || '', foto: p.foto || ''
  });
}

function abrirCrearDependencia() {
  // Defensa en profundidad: la pestaña ya no se pinta sin super, pero si algun
  // navegador con JS viejo muestra el boton, el modal tampoco se abre (y el servidor da 403).
  if (!esSuper.value) return;
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
      ci: modalPersonal.ci, rol: modalPersonal.rol,
      descripcionTarea: modalPersonal.descripcionTarea || null
    };
    const r = await apiFetch(url, { method: metodo, body: JSON.stringify(body) });
    const d = await r.json();
    if (!d.ok) return toast(d.mensaje || 'No se pudo guardar.', 'error');
    // La foto va aparte (multipart): primero la ficha, luego la imagen.
    if (archivoFoto.value && d.personal?.id) {
      console.log('[APOYO-FOTO] subiendo', archivoFoto.value.name,
        archivoFoto.value.size, 'bytes a ficha', d.personal.id);
      const forma = new FormData();
      forma.append('foto', archivoFoto.value);
      const rf = await apiFetch(`/api/app/personal-apoyo/${d.personal.id}/foto`,
        { method: 'POST', body: forma });
      console.log('[APOYO-FOTO] respuesta foto: HTTP', rf.status);
      const df = await rf.json().catch(() => ({}));
      console.log('[APOYO-FOTO] cuerpo foto:', df);
      if (!df.ok) return toast(`Se guardó la ficha, pero la foto no: ${df.mensaje || 'error'}.`, 'error');
    } else if (!archivoFoto.value) {
      console.log('[APOYO-FOTO] sin archivo elegido, no se sube nada');
    }
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

async function quitarFoto() {
  if (!modalPersonal.editando || ocupado.value) return;
  ocupado.value = true;
  try {
    const r = await apiFetch(`/api/app/personal-apoyo/${modalPersonal.editando}/foto`,
      { method: 'DELETE' });
    const d = await r.json().catch(() => ({}));
    if (!d.ok) return toast(d.mensaje || 'No se pudo quitar.', 'error');
    modalPersonal.foto = '';
    limpiarFotoPendiente();
    toast('Foto quitada.', 'ok');
    await cargarPersonal();
  } catch (e) {
    toast(e.message, 'error');
  } finally {
    ocupado.value = false;
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
        v-if="esSuper"
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
      ? (!esSuper && miDependencia.nombre
        ? `Personal de tu dependencia (${miDependencia.nombre}). No afecta a la tabla de personas del sistema.`
        : 'Gestión de personal de apoyo: fotógrafos, azafatas, logística, etc. No afecta a la tabla de personas del sistema.')
      : 'Catálogo de dependencias/unidades a las que se asigna el personal de apoyo.' }}
  </p>
  <p v-if="modo === 'personal' && !esSuper && !miDependencia.id" class="muted nota">
    Tu usuario no tiene ficha de apoyo registrada (se enlaza por C.I.): no hay personal que mostrar.
    Pide al super usuario que te registre en tu dependencia.
  </p>

  <div class="card">
    <div v-if="cargando" class="vacio">Cargando…</div>
    <div v-else-if="modo === 'personal' && personalFiltrado.length === 0" class="vacio">No hay personal que mostrar.</div>
    <div v-else-if="modo === 'dependencias' && dependenciasFiltradas.length === 0" class="vacio">No hay dependencias que mostrar.</div>

    <!-- Tabla Personal (sin correo ni celular) -->
    <table v-else-if="modo === 'personal'" class="tabla">
      <thead>
        <tr><th></th><th>Nombre</th><th>C.I.</th><th>Dependencia</th><th>Rol</th><th>Tarea</th><th></th></tr>
      </thead>
      <tbody>
        <tr v-for="p in personalFiltrado" :key="p.id">
          <td><img v-if="p.foto" :src="urlApi(p.foto)" :alt="p.nombreCompleto" class="mini" />
            <span v-else class="mini sinfoto">—</span></td>
          <td><strong>{{ p.nombreCompleto }}</strong></td>
          <td>{{ p.ci }}</td>
          <td>{{ p.dependenciaNombre || '—' }}</td>
          <td><span class="badge">{{ p.rol }}</span></td>
          <td>{{ p.descripcionTarea || '—' }}</td>
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
        v-if="esSuper"
        v-model="modalPersonal.idDependencia"
        :options="dependencias.map(d => ({ value: d.id, label: d.nombre }))"
        label="Dependencia *"
        placeholder="Seleccione dependencia…"
        required
        searchKey="label"
        valueKey="value"
        labelKey="label"
      />
      <p v-else class="campo"><span>Dependencia</span><strong>{{ miDependencia.nombre || '—' }}</strong></p>
      <AutocompleteSelect
        v-model="modalPersonal.rol"
        :options="rolesParaSelect"
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
      <label class="campo campo-ancho"><span>Descripción de la tarea ({{ (modalPersonal.descripcionTarea || '').length }}/200)</span><input v-model="modalPersonal.descripcionTarea" class="control" maxlength="200" placeholder="Brevemente: ¿qué va a realizar?" /></label>
      <div class="campo campo-ancho">
        <span>Foto (va en el círculo de la credencial)</span>
        <div class="foto-fila">
          <img v-if="vistaPreviaFoto" :src="vistaPreviaFoto" alt="Nueva foto" class="foto-prev" />
          <img v-else-if="modalPersonal.foto" :src="urlApi(modalPersonal.foto)" alt="Foto actual" class="foto-prev" />
          <div v-else class="foto-prev sinfoto">Sin foto</div>
          <div class="foto-acciones">
            <label class="btn btn-sm btn-fantasma">Elegir…
              <input type="file" accept="image/*" class="oculto" @change="elegirFoto" />
            </label>
            <button v-if="modalPersonal.editando && (modalPersonal.foto || archivoFoto)"
                    class="btn btn-sm btn-peligro" :disabled="ocupado" @click="quitarFoto">Quitar</button>
          </div>
        </div>
      </div>
    </div>
    <template #pie>
      <button class="btn btn-fantasma" @click="modalPersonal.abierto = false">Cancelar</button>
      <button class="btn btn-primario" :disabled="guardando" @click="guardarPersonal">
        {{ guardando ? 'Guardando…' : 'Guardar' }}
      </button>
    </template>
  </UiModal>

  <!-- Aviso cuando el usuario no tiene dependencia asignada -->
  <UiModal v-if="avisoSinDependencia" titulo="⚠️ Sin dependencia asignada"
           @cerrar="avisoSinDependencia = false" ancho="480px">
    <p>No tienes ninguna dependencia asignada.</p>
    <p class="muted">Comunícate con soporte técnico para que te asignen a tu dependencia
      y puedas ver y registrar tu personal de apoyo.</p>
    <template #pie>
      <button class="btn btn-primario" @click="avisoSinDependencia = false">Entendido</button>
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
.campo-ancho { grid-column: 1 / -1; }
.foto-fila { display: flex; align-items: center; gap: 0.8rem; }
.foto-prev { width: 72px; height: 72px; border-radius: 50%; object-fit: cover; background: var(--border); }
.foto-prev.sinfoto { display: flex; align-items: center; justify-content: center; font-size: 0.7rem; color: var(--muted); }
.foto-acciones { display: flex; gap: 0.4rem; }
.mini { width: 32px; height: 32px; border-radius: 50%; object-fit: cover; }
.mini.sinfoto { display: inline-flex; align-items: center; justify-content: center; background: var(--border); color: var(--muted); }
.oculto { display: none; }
@media (max-width: 600px) { .grid2 { grid-template-columns: 1fr; } }
</style>