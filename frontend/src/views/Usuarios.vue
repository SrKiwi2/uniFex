<script setup>
import { ref, reactive, computed, onMounted } from 'vue';
import UiModal from '../components/UiModal.vue';
import { apiFetch } from '../api';
import { toast } from '../ui/toast';

const PASSWORD_MIN = 8; // igual que GestionUsuarioService.PASSWORD_MIN

const usuarios = ref([]);
const roles = ref([]);
const cargando = ref(true);
const filtro = ref('');
const guardando = ref(false);
// Una operacion de fila por vez; evita dobles clicks en acciones de fila y contraseña.
const ocupado = ref(false);

// Alta y edicion. `modoPersona` decide de donde sale la persona: 'buscar' la elige de las que
// ya existen, 'nueva' la crea en el mismo guardado. Es lo que evita tener que ir al modulo de
// Personas, crearla, volver aqui y buscarla para dar de alta a un vendedor.
const modal = reactive({
  abierto: false, editando: null, username: '', password: '', verPassword: false,
  rolId: null, modoPersona: 'buscar', personaId: null, personaTexto: '',
  nueva: { nombre: '', paterno: '', materno: '', ci: '', correo: '', celular: '' },
  ciExistente: null,
});
const personasSugeridas = ref([]);
const buscandoPersonas = ref(false);

const modalPass = reactive({ abierto: false, id: null, username: '', nueva: '', confirmar: '', ver: false });

const usuariosFiltrados = computed(() => {
  const q = filtro.value.trim().toLowerCase();
  if (!q) return usuarios.value;
  return usuarios.value.filter((u) =>
    [u.username, u.persona, u.rol].some((c) => (c || '').toLowerCase().includes(q)));
});

const rolElegido = computed(() => roles.value.find((r) => r.id === modal.rolId) || null);

async function cargar() {
  cargando.value = true;
  try {
    const [ru, rr] = await Promise.all([
      apiFetch('/api/app/usuarios'),
      apiFetch('/api/app/roles'),
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
  buscandoPersonas.value = true;
  debounce = setTimeout(async () => {
    try {
      const r = await apiFetch(`/api/app/usuarios/personas?q=${encodeURIComponent(modal.personaTexto)}`);
      personasSugeridas.value = await r.json();
    } catch (e) {
      toast(e.message, 'error');
    } finally {
      buscandoPersonas.value = false;
    }
  }, 250);
}
function elegirPersona(p) {
  if (p.tieneUsuario) return; // ya tiene login: una persona, un usuario
  modal.personaId = p.id;
  modal.personaTexto = `${p.nombre}${p.ci ? ` · ${p.ci}` : ''}`;
  personasSugeridas.value = [];
}

/**
 * El C.I. ya registrado se avisa ANTES de guardar. No es un lujo: los responsables de entidad
 * viven en la misma tabla con otro `_estado`, así que no salen en el buscador de arriba pero sí
 * ocupan el C.I., y el alta fallaría con un "ya existe" sin decir quién es.
 */
async function comprobarCi() {
  const ci = modal.nueva.ci.trim();
  modal.ciExistente = null;
  if (!ci) return;
  try {
    const r = await apiFetch(`/api/app/usuarios/personas/por-ci?ci=${encodeURIComponent(ci)}`);
    const d = await r.json();
    if (d.existe) modal.ciExistente = d.persona;
  } catch { /* sin conexion: el guardado lo volvera a comprobar en el servidor */ }
}
function usarPersonaDelCi() {
  const p = modal.ciExistente;
  if (!p) return;
  modal.modoPersona = 'buscar';
  modal.ciExistente = null;
  if (p.tieneUsuario) {
    toast(`${p.nombre} ya tiene un usuario.`, 'error');
    return;
  }
  modal.personaId = p.id;
  modal.personaTexto = `${p.nombre}${p.ci ? ` · ${p.ci}` : ''}`;
  personasSugeridas.value = [];
}

// ---- contraseña ----
// Sin ambiguos (O/0, l/1): estas claves se dictan por teléfono al vendedor.
const ALFABETO = 'ABCDEFGHJKMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789';
function generarPassword() {
  const azar = new Uint32Array(12);
  crypto.getRandomValues(azar);
  return Array.from(azar, (n) => ALFABETO[n % ALFABETO.length]).join('');
}
function generarEnAlta() {
  modal.password = generarPassword();
  modal.verPassword = true;
}
function generarEnCambio() {
  modalPass.nueva = generarPassword();
  modalPass.confirmar = modalPass.nueva;
  modalPass.ver = true;
}
async function copiar(texto) {
  try {
    await navigator.clipboard.writeText(texto);
    toast('Contraseña copiada.', 'ok');
  } catch {
    toast('No se pudo copiar; selecciónala a mano.', 'error');
  }
}

// ---- crear / editar ----
function abrirCrear() {
  Object.assign(modal, {
    abierto: true, editando: null, username: '', password: '', verPassword: false,
    rolId: roles.value.find((r) => r.nombre === 'ADMINISTRATIVO')?.id ?? roles.value[0]?.id ?? null,
    modoPersona: 'buscar', personaId: null, personaTexto: '',
    nueva: { nombre: '', paterno: '', materno: '', ci: '', correo: '', celular: '' },
    ciExistente: null,
  });
  personasSugeridas.value = [];
}
function abrirEditar(u) {
  Object.assign(modal, {
    abierto: true, editando: u.id, username: u.username, password: '', verPassword: false,
    rolId: u.rolId, modoPersona: 'buscar', personaId: u.personaId, personaTexto: u.persona || '',
    nueva: { nombre: '', paterno: '', materno: '', ci: '', correo: '', celular: '' },
    ciExistente: null,
  });
  personasSugeridas.value = [];
}

function validarAlta() {
  if (!modal.username.trim()) return 'El usuario es obligatorio.';
  if (!modal.rolId) return 'Elige un rol.';
  if (!modal.editando && modal.password.length < PASSWORD_MIN) {
    return `La contraseña debe tener al menos ${PASSWORD_MIN} caracteres.`;
  }
  if (modal.editando || modal.modoPersona === 'buscar') {
    if (!modal.personaId) return 'Elige una persona de la lista.';
    return null;
  }
  if (!modal.nueva.nombre.trim()) return 'El nombre de la persona es obligatorio.';
  if (!modal.nueva.paterno.trim() && !modal.nueva.materno.trim()) return 'La persona necesita al menos un apellido.';
  if (!modal.nueva.ci.trim()) return 'El C.I. de la persona es obligatorio.';
  return null;
}

async function guardar() {
  const problema = validarAlta();
  if (problema) return toast(problema, 'error');

  guardando.value = true;
  try {
    const url = modal.editando ? `/api/app/usuarios/${modal.editando}` : '/api/app/usuarios';
    const metodo = modal.editando ? 'PATCH' : 'POST';
    const creaPersona = !modal.editando && modal.modoPersona === 'nueva';
    const body = modal.editando
      ? { username: modal.username, personaId: modal.personaId, rolId: modal.rolId }
      : {
          username: modal.username,
          password: modal.password,
          rolId: modal.rolId,
          personaId: creaPersona ? null : modal.personaId,
          persona: creaPersona ? { ...modal.nueva } : null,
        };
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

// ---- contraseña de un usuario existente ----
function abrirPassword(u) {
  Object.assign(modalPass, { abierto: true, id: u.id, username: u.username, nueva: '', confirmar: '', ver: false });
}
async function guardarPassword() {
  if (ocupado.value) return;
  if (modalPass.nueva.length < PASSWORD_MIN) {
    return toast(`La contraseña debe tener al menos ${PASSWORD_MIN} caracteres.`, 'error');
  }
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
    <div v-else-if="usuariosFiltrados.length === 0" class="vacio">No hay usuarios que mostrar.</div>
    <div v-else class="tabla-scroll">
      <table class="tabla">
        <thead>
          <tr><th>Usuario</th><th>Persona</th><th>Rol</th><th>Estado</th><th></th></tr>
        </thead>
        <tbody>
          <tr v-for="u in usuariosFiltrados" :key="u.id">
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
              <button class="btn btn-sm btn-fantasma" :disabled="ocupado"
                      :title="u.estado === 'ACTIVO' ? 'Desactivar' : 'Activar'" @click="alternarEstado(u)">
                {{ u.estado === 'ACTIVO' ? '⏸️' : '▶️' }}
              </button>
              <button class="btn btn-sm btn-peligro" :disabled="ocupado" title="Eliminar" @click="eliminar(u)">🗑</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>

  <!-- Crear / editar -->
  <UiModal v-if="modal.abierto" :titulo="modal.editando ? 'Editar usuario' : 'Nuevo usuario'"
           @cerrar="modal.abierto = false" ancho="600px">
    <div class="grid2">
      <label class="campo">
        <span>Nombre de usuario *</span>
        <input v-model="modal.username" class="control" autocomplete="off" placeholder="jperez" />
      </label>
      <label class="campo">
        <span>Rol *</span>
        <select v-model="modal.rolId" class="control">
          <option v-for="r in roles" :key="r.id" :value="r.id">{{ r.nombre }}</option>
        </select>
      </label>
    </div>
    <p v-if="rolElegido?.descripcion" class="muted aviso">{{ rolElegido.descripcion }}</p>

    <div v-if="!modal.editando" class="campo">
      <span>Contraseña *</span>
      <div class="fila">
        <input v-model="modal.password" :type="modal.verPassword ? 'text' : 'password'"
               class="control" autocomplete="new-password" />
        <button class="btn btn-sm btn-fantasma" type="button"
                :title="modal.verPassword ? 'Ocultar' : 'Mostrar'"
                @click="modal.verPassword = !modal.verPassword">{{ modal.verPassword ? '🙈' : '👁' }}</button>
        <button class="btn btn-sm btn-fantasma" type="button" title="Generar" @click="generarEnAlta">🎲</button>
        <button class="btn btn-sm btn-fantasma" type="button" title="Copiar"
                :disabled="!modal.password" @click="copiar(modal.password)">📋</button>
      </div>
      <span class="muted pista">Mínimo {{ PASSWORD_MIN }} caracteres. Genérala y cópiala para dictarla.</span>
    </div>

    <!-- Persona: elegir una que ya existe, o crearla aquí mismo -->
    <div class="campo">
      <span>Persona *</span>
      <div v-if="!modal.editando" class="pestanas">
        <button type="button" class="btn btn-sm" :class="modal.modoPersona === 'buscar' ? 'btn-primario' : 'btn-fantasma'"
                @click="modal.modoPersona = 'buscar'">Buscar existente</button>
        <button type="button" class="btn btn-sm" :class="modal.modoPersona === 'nueva' ? 'btn-primario' : 'btn-fantasma'"
                @click="modal.modoPersona = 'nueva'">Crear nueva</button>
      </div>

      <div v-if="modal.editando || modal.modoPersona === 'buscar'" class="persona-box">
        <input v-model="modal.personaTexto" class="control" placeholder="Buscar por nombre o C.I.…"
               autocomplete="off" @input="buscarPersona" @focus="buscarPersona" />
        <span v-if="modal.personaId" class="elegida">✓ seleccionada</span>
        <ul v-if="personasSugeridas.length" class="sugerencias card">
          <li v-for="p in personasSugeridas" :key="p.id"
              :class="{ ocupada: p.tieneUsuario }" @click="elegirPersona(p)">
            <span>{{ p.nombre }}</span>
            <span v-if="p.ci" class="muted"> · {{ p.ci }}</span>
            <span v-if="p.tieneUsuario" class="badge badge-muted sello">ya tiene usuario</span>
            <span v-else-if="p.origen && p.origen !== 'ACTIVO'" class="badge badge-muted sello">{{ p.origen }}</span>
          </li>
        </ul>
        <p v-else-if="!buscandoPersonas && modal.personaTexto && !modal.personaId" class="muted pista">
          Nadie con ese nombre o C.I. Usa <strong>Crear nueva</strong> para registrarla aquí mismo.
        </p>
      </div>

      <div v-else class="nueva-persona">
        <div class="grid2">
          <label class="campo"><span>Nombre *</span><input v-model="modal.nueva.nombre" class="control" /></label>
          <label class="campo">
            <span>C.I. *</span>
            <input v-model="modal.nueva.ci" class="control" @blur="comprobarCi" />
          </label>
          <label class="campo"><span>Apellido paterno</span><input v-model="modal.nueva.paterno" class="control" /></label>
          <label class="campo"><span>Apellido materno</span><input v-model="modal.nueva.materno" class="control" /></label>
          <label class="campo"><span>Correo</span><input v-model="modal.nueva.correo" type="email" class="control" /></label>
          <label class="campo"><span>Celular</span><input v-model="modal.nueva.celular" class="control" /></label>
        </div>
        <p v-if="modal.ciExistente" class="aviso-ci">
          Ese C.I. ya es de <strong>{{ modal.ciExistente.nombre }}</strong>.
          <button type="button" class="btn btn-sm btn-fantasma" @click="usarPersonaDelCi">Usar esa persona</button>
        </p>
      </div>
    </div>

    <template #pie>
      <button class="btn btn-fantasma" @click="modal.abierto = false">Cancelar</button>
      <button class="btn btn-primario" :disabled="guardando" @click="guardar">
        {{ guardando ? 'Guardando…' : 'Guardar' }}
      </button>
    </template>
  </UiModal>

  <!-- Contraseña -->
  <UiModal v-if="modalPass.abierto" :titulo="`Contraseña de ${modalPass.username}`"
           @cerrar="modalPass.abierto = false" ancho="420px">
    <div class="campo">
      <span>Nueva contraseña</span>
      <div class="fila">
        <input v-model="modalPass.nueva" :type="modalPass.ver ? 'text' : 'password'"
               class="control" autocomplete="new-password" />
        <button class="btn btn-sm btn-fantasma" type="button" title="Mostrar"
                @click="modalPass.ver = !modalPass.ver">{{ modalPass.ver ? '🙈' : '👁' }}</button>
        <button class="btn btn-sm btn-fantasma" type="button" title="Generar" @click="generarEnCambio">🎲</button>
        <button class="btn btn-sm btn-fantasma" type="button" title="Copiar"
                :disabled="!modalPass.nueva" @click="copiar(modalPass.nueva)">📋</button>
      </div>
    </div>
    <label class="campo">
      <span>Confirmar</span>
      <input v-model="modalPass.confirmar" :type="modalPass.ver ? 'text' : 'password'"
             class="control" autocomplete="new-password" />
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

.grid2 { display: grid; grid-template-columns: 1fr 1fr; gap: 0.8rem; }
@media (max-width: 520px) { .grid2 { grid-template-columns: 1fr; } }

.aviso { margin: -0.4rem 0 0; font-size: 0.8rem; }
.pista { font-size: 0.78rem; }
.pestanas { display: flex; gap: 0.3rem; margin-bottom: 0.4rem; }
.nueva-persona { border: 1px dashed var(--border); border-radius: var(--radio-sm); padding: 0.8rem; }
.aviso-ci { margin: 0.7rem 0 0; font-size: 0.85rem; color: var(--danger); }

.persona-box { position: relative; }
.elegida { color: var(--ok); font-size: 0.78rem; font-weight: 600; }
.sugerencias {
  list-style: none; margin: 0.3rem 0 0; padding: 0.3rem; position: absolute; top: 100%; left: 0; right: 0;
  z-index: 20; max-height: 240px; overflow-y: auto; box-shadow: var(--sombra-md);
}
.sugerencias li { padding: 0.5rem 0.6rem; border-radius: var(--radio-sm); cursor: pointer; font-size: 0.9rem; }
.sugerencias li:hover { background: var(--panel-2); }
/* Una persona que ya tiene login no es elegible: se ve, pero no responde al clic. */
.sugerencias li.ocupada { opacity: 0.55; cursor: not-allowed; }
.sello { margin-left: 0.4rem; }
</style>
