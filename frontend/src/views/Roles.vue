<script setup>
import { ref, reactive, computed, onMounted } from 'vue';
import UiModal from '../components/UiModal.vue';
import { apiFetch } from '../api';
import { toast } from '../ui/toast';

const roles = ref([]);
const cargando = ref(true);
const filtro = ref('');
const guardando = ref(false);
// Una operacion de fila por vez: evita el doble click que manda dos DELETE.
const ocupado = ref(false);

const modal = reactive({ abierto: false, editando: null, sistema: false, nombre: '', descripcion: '' });

const filtrados = computed(() => {
  const q = filtro.value.trim().toLowerCase();
  if (!q) return roles.value;
  return roles.value.filter((r) =>
    [r.nombre, r.descripcion, r.autoridad].some((c) => (c || '').toLowerCase().includes(q)));
});

// El nombre se guarda en MAYUSCULAS y sin espacios dobles porque de el sale el permiso de
// Spring (ROLE_<nombre con guiones bajos>). Se muestra la conversion mientras se escribe para
// que no sea una sorpresa al guardar; la normalizacion real la hace el backend igual.
const autoridadPrevia = computed(() => {
  const n = modal.nombre.trim().replace(/\s{2,}/g, ' ').toUpperCase();
  return n ? `ROLE_${n.replace(/ /g, '_')}` : '';
});

async function cargar() {
  cargando.value = true;
  try {
    const r = await apiFetch('/api/app/roles');
    roles.value = await r.json();
  } catch (e) {
    toast(e.message, 'error');
  } finally {
    cargando.value = false;
  }
}

function abrirCrear() {
  Object.assign(modal, { abierto: true, editando: null, sistema: false, nombre: '', descripcion: '' });
}
function abrirEditar(r) {
  Object.assign(modal, {
    abierto: true, editando: r.id, sistema: r.sistema,
    nombre: r.nombre || '', descripcion: r.descripcion || '',
  });
}

async function guardar() {
  if (!modal.nombre.trim()) return toast('El nombre del rol es obligatorio.', 'error');
  guardando.value = true;
  try {
    const url = modal.editando ? `/api/app/roles/${modal.editando}` : '/api/app/roles';
    const metodo = modal.editando ? 'PATCH' : 'POST';
    const r = await apiFetch(url, {
      method: metodo,
      body: JSON.stringify({ nombre: modal.nombre, descripcion: modal.descripcion }),
    });
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

async function eliminar(r) {
  if (ocupado.value) return;
  if (!confirm(`¿Eliminar el rol "${r.nombre}"?`)) return;
  ocupado.value = true;
  try {
    const res = await apiFetch(`/api/app/roles/${r.id}`, { method: 'DELETE' });
    const d = await res.json();
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
    <input v-model="filtro" class="control busca" placeholder="Buscar por nombre o permiso…" />
    <button class="btn btn-primario" @click="abrirCrear">＋ Nuevo rol</button>
  </div>

  <p class="muted nota">
    El rol decide qué puede hacer cada usuario. Los cinco roles marcados como
    <strong>del sistema</strong> los usa el propio código: se les puede cambiar la descripción,
    pero no el nombre ni eliminarlos.
  </p>

  <div class="card">
    <div v-if="cargando" class="vacio">Cargando…</div>
    <div v-else-if="filtrados.length === 0" class="vacio">No hay roles que mostrar.</div>
    <div v-else class="tabla-scroll">
      <table class="tabla">
        <thead>
          <tr><th>Rol</th><th>Descripción</th><th>Permiso</th><th>Usuarios</th><th></th></tr>
        </thead>
        <tbody>
          <tr v-for="r in filtrados" :key="r.id">
            <td>
              <strong>{{ r.nombre }}</strong>
              <span v-if="r.sistema" class="badge badge-muted sello">sistema</span>
            </td>
            <td class="desc">{{ r.descripcion || '—' }}</td>
            <td><code class="permiso">{{ r.autoridad }}</code></td>
            <td>
              <span class="badge" :class="r.usuarios ? 'badge-ok' : 'badge-muted'">{{ r.usuarios }}</span>
            </td>
            <td class="acciones">
              <button class="btn btn-sm btn-fantasma" :disabled="ocupado" title="Editar" @click="abrirEditar(r)">✏️</button>
              <button
                class="btn btn-sm btn-peligro"
                :disabled="ocupado || r.sistema || r.usuarios > 0"
                :title="r.sistema ? 'Rol del sistema: no se elimina'
                  : r.usuarios > 0 ? 'Tiene usuarios asignados' : 'Eliminar'"
                @click="eliminar(r)">🗑</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>

  <UiModal v-if="modal.abierto" :titulo="modal.editando ? 'Editar rol' : 'Nuevo rol'"
           @cerrar="modal.abierto = false" ancho="520px">
    <label class="campo">
      <span>Nombre del rol</span>
      <input v-model="modal.nombre" class="control" :disabled="modal.sistema" maxlength="40"
             placeholder="Por ejemplo: SUPERVISION" autocomplete="off" />
    </label>
    <p v-if="modal.sistema" class="muted aviso">
      Es un rol del sistema: el nombre está congelado porque el código lo busca por él.
      La descripción sí se puede cambiar.
    </p>
    <p v-else-if="autoridadPrevia" class="muted aviso">
      Se guardará como <strong>{{ modal.nombre.trim().replace(/\s{2,}/g, ' ').toUpperCase() }}</strong>
      y el permiso será <code class="permiso">{{ autoridadPrevia }}</code>.
    </p>

    <label class="campo">
      <span>Descripción</span>
      <textarea v-model="modal.descripcion" class="control" rows="3" maxlength="255"
                placeholder="Qué puede hacer quien tenga este rol"></textarea>
    </label>

    <template #pie>
      <button class="btn btn-fantasma" @click="modal.abierto = false">Cancelar</button>
      <button class="btn btn-primario" :disabled="guardando" @click="guardar">
        {{ guardando ? 'Guardando…' : 'Guardar' }}
      </button>
    </template>
  </UiModal>
</template>

<style scoped>
.encabezado { margin-bottom: 0.6rem; gap: 0.8rem; }
.busca { max-width: 360px; }
.nota { margin: 0 0 1rem; font-size: 0.85rem; }
.aviso { margin: -0.3rem 0 0; font-size: 0.8rem; }
.sello { margin-left: 0.45rem; vertical-align: middle; }
.desc { color: var(--muted); max-width: 34ch; }
.permiso { font-size: 0.78rem; background: var(--panel-2); padding: 0.1rem 0.4rem; border-radius: var(--radio-sm); }
textarea.control { resize: vertical; min-height: 4.5rem; }
</style>
