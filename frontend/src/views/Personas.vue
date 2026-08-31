<script setup>
import { ref, reactive, computed, onMounted } from 'vue';
import UiModal from '../components/UiModal.vue';
import { apiFetch } from '../api';
import { toast } from '../ui/toast';

const personas = ref([]);
const cargando = ref(true);
const filtro = ref('');
const guardando = ref(false);
// Ocupado: evita dobles clicks en las acciones de fila (eliminar).
const ocupado = ref(false);
const modal = reactive({ abierto: false, editando: null, nombre: '', paterno: '', materno: '',
  ci: '', correo: '', celular: '' });

const filtradas = computed(() => {
  const q = filtro.value.trim().toLowerCase();
  if (!q) return personas.value;
  return personas.value.filter((p) =>
    [p.nombreCompleto, p.ci, p.correo].some((c) => (c || '').toLowerCase().includes(q)));
});

async function cargar() {
  cargando.value = true;
  try {
    const r = await apiFetch('/api/app/personas');
    personas.value = await r.json();
  } catch (e) {
    toast(e.message, 'error');
  } finally {
    cargando.value = false;
  }
}

function abrirCrear() {
  Object.assign(modal, { abierto: true, editando: null, nombre: '', paterno: '', materno: '',
    ci: '', correo: '', celular: '' });
}
function abrirEditar(p) {
  Object.assign(modal, { abierto: true, editando: p.id, nombre: p.nombre || '', paterno: p.paterno || '',
    materno: p.materno || '', ci: p.ci || '', correo: p.correo || '', celular: p.celular || '' });
}

async function guardar() {
  if (!modal.nombre.trim()) return toast('El nombre es obligatorio.', 'error');
  if (!modal.paterno.trim() && !modal.materno.trim()) return toast('Debe tener al menos un apellido.', 'error');
  if (!modal.ci.trim()) return toast('El C.I. es obligatorio.', 'error');

  guardando.value = true;
  try {
    const url = modal.editando ? `/api/app/personas/${modal.editando}` : '/api/app/personas';
    const metodo = modal.editando ? 'PATCH' : 'POST';
    const body = {
      nombre: modal.nombre, paterno: modal.paterno, materno: modal.materno,
      ci: modal.ci, correo: modal.correo, celular: modal.celular,
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

async function eliminar(p) {
  if (ocupado.value) return;
  if (!confirm(`¿Eliminar a "${p.nombreCompleto}"?`)) return;
  ocupado.value = true;
  try {
    const r = await apiFetch(`/api/app/personas/${p.id}`, { method: 'DELETE' });
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
      <input v-model="filtro" class="control busca" placeholder="Buscar por nombre, C.I. o correo…" />
      <button class="btn btn-primario" @click="abrirCrear">＋ Nueva persona</button>
    </div>

    <p class="muted nota">Personas del sistema (empleados y usuarios). Los responsables de entidades se gestionan aparte.</p>

    <div class="card">
      <div v-if="cargando" class="vacio">Cargando…</div>
      <div v-else-if="filtradas.length === 0" class="vacio">No hay personas que mostrar.</div>
      <table v-else class="tabla">
        <thead>
          <tr><th>Nombre</th><th>C.I.</th><th>Correo</th><th>Celular</th><th>Usuario</th><th></th></tr>
        </thead>
        <tbody>
          <tr v-for="p in filtradas" :key="p.id">
            <td><strong>{{ p.nombreCompleto }}</strong></td>
            <td>{{ p.ci }}</td>
            <td>{{ p.correo || '—' }}</td>
            <td>{{ p.celular || '—' }}</td>
            <td>
              <span class="badge" :class="p.tieneUsuario ? 'badge-ok' : 'badge-muted'">
                {{ p.tieneUsuario ? 'Sí' : 'No' }}
              </span>
            </td>
            <td class="acciones">
              <button class="btn btn-sm btn-fantasma" :disabled="ocupado" title="Editar" @click="abrirEditar(p)">✏️</button>
              <button class="btn btn-sm btn-peligro" :disabled="ocupado" title="Eliminar" @click="eliminar(p)">🗑</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <UiModal v-if="modal.abierto" :titulo="modal.editando ? 'Editar persona' : 'Nueva persona'"
             @cerrar="modal.abierto = false" ancho="540px">
      <div class="grid2">
        <label class="campo"><span>Nombre *</span><input v-model="modal.nombre" class="control" /></label>
        <label class="campo"><span>C.I. *</span><input v-model="modal.ci" class="control" /></label>
        <label class="campo"><span>Apellido paterno</span><input v-model="modal.paterno" class="control" /></label>
        <label class="campo"><span>Apellido materno</span><input v-model="modal.materno" class="control" /></label>
        <label class="campo"><span>Correo</span><input v-model="modal.correo" type="email" class="control" /></label>
        <label class="campo"><span>Celular</span><input v-model="modal.celular" class="control" /></label>
      </div>
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
.grid2 { display: grid; grid-template-columns: 1fr 1fr; gap: 0.8rem; }
@media (max-width: 520px) { .grid2 { grid-template-columns: 1fr; } }
</style>
