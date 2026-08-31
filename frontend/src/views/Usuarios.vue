<script setup>
import { ref, reactive, onMounted } from 'vue';
import UiModal from '../components/UiModal.vue';
import { apiFetch } from '../api';
import { toast } from '../ui/toast';

const usuarios = ref([]);
const roles = ref([]);
const cargando = ref(true);
const filtro = ref('');

// Modal de crear/editar
const modal = reactive({ abierto: false, editando: null, username: '', password: '', rolId: null,
  personaId: null, personaTexto: '' });
const personasSugeridas = ref([]);
const guardando = ref(false);
// Ocupado: una operacion por vez; evita dobles clicks en acciones de fila y contraseña.
const ocupado = ref(false);

// Modal de contraseña
const modalPass = reactive({ abierto: false, id: null, username: '', nueva: '', confirmar: '' });

const usuariosFiltrados = () => {
  const q = filtro.value.trim().toLowerCase();
  if (!q) return usuarios.value;
  return usuarios.value.filter((u) =>
    [u.username, u.persona, u.rol].some((c) => (c || '').toLowerCase().includes(q)));
};

async function cargar() {
  cargando.value = true;
  try {
    const [ru, rr] = await Promise.all([
      apiFetch('/api/app/usuarios'),
      apiFetch('/api/app/usuarios/roles'),
    ]);
    usuarios.value = await ru.json();
    roles.value = await rr.json();
  } catch (e) {
    toast(e.message, 'error');
  } finally {
    cargando.value = false;
  }
}

// ---- buscador de persona ----
let debounce;
function buscarPersona() {
  clearTimeout(debounce);
  modal.personaId = null; // al escribir, se deselecciona hasta elegir de la lista
  debounce = setTimeout(async () => {
    const r = await apiFetch(`/api/app/usuarios/personas?q=${encodeURIComponent(modal.personaTexto)}`);
    personasSugeridas.value = await r.json();
  }, 250);
}
function elegirPersona(p) {
  modal.personaId = p.id;
  modal.personaTexto = `${p.nombre}${p.ci ? ` · ${p.ci}` : ''}`;
  personasSugeridas.value = [];
}

// ---- crear / editar ----
function abrirCrear() {
  Object.assign(modal, { abierto: true, editando: null, username: '', password: '',
    rolId: roles.value[0]?.id ?? null, personaId: null, personaTexto: '' });
  personasSugeridas.value = [];
}
function abrirEditar(u) {
  Object.assign(modal, { abierto: true, editando: u.id, username: u.username, password: '',
    rolId: u.rolId, personaId: u.personaId, personaTexto: u.persona || '' });
  personasSugeridas.value = [];
}

async function guardar() {
  if (!modal.username.trim()) return toast('El usuario es obligatorio.', 'error');
  if (!modal.rolId) return toast('Elige un rol.', 'error');
  if (!modal.editando && !modal.password) return toast('La contraseña es obligatoria.', 'error');
  if (!modal.personaId) return toast('Elige una persona de la lista.', 'error');

  guardando.value = true;
  try {
    const url = modal.editando ? `/api/app/usuarios/${modal.editando}` : '/api/app/usuarios';
    const metodo = modal.editando ? 'PATCH' : 'POST';
    const body = modal.editando
      ? { username: modal.username, personaId: modal.personaId, rolId: modal.rolId }
      : { username: modal.username, password: modal.password, personaId: modal.personaId, rolId: modal.rolId };
    const r = await apiFetch(url, { method: metodo, body: JSON.stringify(body) });
    const d = await r.json();
    if (!d.ok) return toast(d.mensaje || 'No se pudo guardar.', 'error');
    toast(d.mensaje, 'ok');
    modal.abierto = false;
    await cargar();
  } catch (e) {
    toast(e.message, 'error');
  } finally {
    guardando.value = false;
  }
}

// ---- contraseña ----
function abrirPassword(u) {
  Object.assign(modalPass, { abierto: true, id: u.id, username: u.username, nueva: '', confirmar: '' });
}
async function guardarPassword() {
  if (ocupado.value) return;
  if (!modalPass.nueva) return toast('Escribe la contraseña.', 'error');
  if (modalPass.nueva !== modalPass.confirmar) return toast('Las contraseñas no coinciden.', 'error');
  ocupado.value = true;
  try {
    const r = await apiFetch(`/api/app/usuarios/${modalPass.id}/password`, {
      method: 'PATCH', body: JSON.stringify({ password: modalPass.nueva }),
    });
    const d = await r.json();
    if (!d.ok) return toast(d.mensaje || 'Error', 'error');
    toast(d.mensaje, 'ok');
    modalPass.abierto = false;
  } finally {
    ocupado.value = false;
  }
}

// ---- estado / eliminar ----
async function alternarEstado(u) {
  if (ocupado.value) return;
  const activar = u.estado !== 'ACTIVO';
  ocupado.value = true;
  try {
    const r = await apiFetch(`/api/app/usuarios/${u.id}/estado`, {
      method: 'PATCH', body: JSON.stringify({ activo: activar }),
    });
    const d = await r.json();
    if (!d.ok) return toast(d.mensaje || 'Error', 'error');
    toast(d.mensaje, 'ok');
    await cargar();
  } finally {
    ocupado.value = false;
  }
}
async function eliminar(u) {
  if (ocupado.value) return;
  if (!confirm(`¿Eliminar al usuario "${u.username}"?`)) return;
  ocupado.value = true;
  try {
    const r = await apiFetch(`/api/app/usuarios/${u.id}`, { method: 'DELETE' });
    const d = await r.json();
    if (!d.ok) return toast(d.mensaje || 'Error', 'error');
    toast(d.mensaje, 'ok');
    await cargar();
  } finally {
    ocupado.value = false;
  }
}

onMounted(cargar);
</script>

<template>
  <div class="fila entre encabezado">
      <input v-model="filtro" class="control busca" placeholder="Buscar por usuario, persona o rol…" />
      <button class="btn btn-primario" @click="abrirCrear">＋ Nuevo usuario</button>
    </div>

    <div class="card">
      <div v-if="cargando" class="vacio">Cargando…</div>
      <div v-else-if="usuariosFiltrados().length === 0" class="vacio">No hay usuarios que mostrar.</div>
      <table v-else class="tabla">
        <thead>
          <tr><th>Usuario</th><th>Persona</th><th>Rol</th><th>Estado</th><th></th></tr>
        </thead>
        <tbody>
          <tr v-for="u in usuariosFiltrados()" :key="u.id">
            <td><strong>{{ u.username }}</strong></td>
            <td>{{ u.persona }}</td>
            <td><span class="badge badge-muted">{{ u.rol }}</span></td>
            <td>
              <span class="badge" :class="u.estado === 'ACTIVO' ? 'badge-ok' : 'badge-danger'">
                {{ u.estado === 'ACTIVO' ? 'Activo' : 'Inactivo' }}
              </span>
            </td>
            <td class="acciones">
              <button class="btn btn-sm btn-fantasma" :disabled="ocupado" title="Editar" @click="abrirEditar(u)">✏️</button>
              <button class="btn btn-sm btn-fantasma" :disabled="ocupado" title="Contraseña" @click="abrirPassword(u)">🔑</button>
              <button class="btn btn-sm btn-fantasma" :disabled="ocupado" :title="u.estado === 'ACTIVO' ? 'Desactivar' : 'Activar'" @click="alternarEstado(u)">
                {{ u.estado === 'ACTIVO' ? '⏸️' : '▶️' }}
              </button>
              <button class="btn btn-sm btn-peligro" :disabled="ocupado" title="Eliminar" @click="eliminar(u)">🗑</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- Crear / editar -->
    <UiModal v-if="modal.abierto" :titulo="modal.editando ? 'Editar usuario' : 'Nuevo usuario'" @cerrar="modal.abierto = false">
      <label class="campo">
        <span>Nombre de usuario</span>
        <input v-model="modal.username" class="control" autocomplete="off" />
      </label>
      <label v-if="!modal.editando" class="campo">
        <span>Contraseña</span>
        <input v-model="modal.password" type="password" class="control" autocomplete="new-password" />
      </label>
      <div class="campo persona-box">
        <span>Persona</span>
        <input v-model="modal.personaTexto" class="control" placeholder="Buscar por nombre o CI…"
               autocomplete="off" @input="buscarPersona" />
        <span v-if="modal.personaId" class="elegida">✓ seleccionada</span>
        <ul v-if="personasSugeridas.length" class="sugerencias card">
          <li v-for="p in personasSugeridas" :key="p.id" @click="elegirPersona(p)">
            {{ p.nombre }} <span class="muted" v-if="p.ci">· {{ p.ci }}</span>
          </li>
        </ul>
      </div>
      <label class="campo">
        <span>Rol</span>
        <select v-model="modal.rolId" class="control">
          <option v-for="r in roles" :key="r.id" :value="r.id">{{ r.nombre }}</option>
        </select>
      </label>
      <template #pie>
        <button class="btn btn-fantasma" @click="modal.abierto = false">Cancelar</button>
        <button class="btn btn-primario" :disabled="guardando" @click="guardar">
          {{ guardando ? 'Guardando…' : 'Guardar' }}
        </button>
      </template>
    </UiModal>

    <!-- Contraseña -->
    <UiModal v-if="modalPass.abierto" :titulo="`Contraseña de ${modalPass.username}`" @cerrar="modalPass.abierto = false" ancho="380px">
      <label class="campo">
        <span>Nueva contraseña</span>
        <input v-model="modalPass.nueva" type="password" class="control" autocomplete="new-password" />
      </label>
      <label class="campo">
        <span>Confirmar</span>
        <input v-model="modalPass.confirmar" type="password" class="control" autocomplete="new-password" />
      </label>
      <template #pie>
        <button class="btn btn-fantasma" @click="modalPass.abierto = false">Cancelar</button>
        <button class="btn btn-primario" :disabled="ocupado" @click="guardarPassword">
          {{ ocupado ? 'Cambiando…' : 'Cambiar' }}
        </button>
      </template>
    </UiModal>
</template>

<style scoped>
.encabezado { margin-bottom: 1rem; gap: 0.8rem; }
.busca { max-width: 360px; }
.card { overflow: visible; }

.persona-box { position: relative; }
.elegida { color: var(--ok); font-size: 0.78rem; font-weight: 600; }
.sugerencias {
  list-style: none; margin: 0.3rem 0 0; padding: 0.3rem; position: absolute; top: 100%; left: 0; right: 0;
  z-index: 20; max-height: 210px; overflow-y: auto; box-shadow: var(--sombra-md);
}
.sugerencias li { padding: 0.5rem 0.6rem; border-radius: var(--radio-sm); cursor: pointer; font-size: 0.9rem; }
.sugerencias li:hover { background: var(--panel-2); }
</style>
