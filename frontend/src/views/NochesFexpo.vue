<script setup>
import { ref, reactive, computed, onMounted } from 'vue';
import UiModal from '../components/UiModal.vue';
import { apiFetch } from '../api';
import { url as urlApi } from '../config.js';
import { toast } from '../ui/toast';

const noches = ref([]);
const cargando = ref(true);
const guardando = ref(false);
// Ocupado: evita dobles clicks en las acciones de fila (eliminar, subir medio).
const ocupado = ref(false);
const subiendoId = ref(null);

const vacioModal = { abierto: false, editando: null, fecha: '', titulo: '', descripcion: '',
  nombreArtista: '', color: '#e31e24', orden: 0 };
const modal = reactive({ ...vacioModal });

const inputsArchivo = reactive({});

async function cargar() {
  cargando.value = true;
  try {
    const r = await apiFetch('/api/app/noches-fexpo');
    const d = await r.json().catch(() => null);
    // Este endpoint devuelve una LISTA. Ante cualquier error (403, o un 500 porque la
    // migración V26 no se aplicó a esa base) llega un objeto {ok:false,...} en su lugar, y
    // asignarlo tal cual rompía la vista entera con "no es iterable" —un mensaje que no
    // dice nada del problema real. Se queda con la lista vacía y se muestra el motivo.
    if (!r.ok || !Array.isArray(d)) {
      noches.value = [];
      toast(d?.mensaje || `No se pudo cargar la lista (error ${r.status}).`, 'error');
      return;
    }
    noches.value = d;
  } catch (e) {
    toast(e.message, 'error');
  } finally {
    cargando.value = false;
  }
}

function abrirCrear() {
  // `abierto: true` va explícito y DESPUÉS de vacioModal: esa plantilla trae `abierto: false`
  // (es el estado inicial del modal), así que sin esto el Object.assign lo dejaba cerrado y
  // el botón "Nueva noche" no hacía nada.
  Object.assign(modal, vacioModal, { abierto: true, color: '#e31e24', orden: noches.value.length });
}
function abrirEditar(n) {
  Object.assign(modal, {
    abierto: true, editando: n.id, fecha: n.fecha || '', titulo: n.titulo || '',
    descripcion: n.descripcion || '', nombreArtista: n.nombreArtista || '',
    color: n.color || '#e31e24', orden: n.orden ?? 0,
  });
}

async function guardar() {
  if (!modal.fecha) return toast('La fecha es obligatoria.', 'error');
  if (!modal.titulo.trim()) return toast('El título es obligatorio.', 'error');

  guardando.value = true;
  try {
    const url = modal.editando ? `/api/app/noches-fexpo/${modal.editando}` : '/api/app/noches-fexpo';
    const metodo = modal.editando ? 'PATCH' : 'POST';
    const body = {
      fecha: modal.fecha, titulo: modal.titulo, descripcion: modal.descripcion,
      nombreArtista: modal.nombreArtista, color: modal.color, orden: Number(modal.orden) || 0,
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

async function eliminar(n) {
  if (ocupado.value) return;
  if (!confirm(`¿Eliminar la noche "${n.titulo}"?`)) return;
  ocupado.value = true;
  try {
    const r = await apiFetch(`/api/app/noches-fexpo/${n.id}`, { method: 'DELETE' });
    const d = await r.json();
    if (!d.ok) return toast(d.mensaje || 'Error', 'error');
    toast(d.mensaje, 'ok');
    await cargar();
  } finally {
    ocupado.value = false;
  }
}

function abrirSelectorArchivo(id) {
  inputsArchivo[id]?.click();
}

async function subirMedio(n, evento) {
  const archivo = evento.target.files?.[0];
  evento.target.value = '';
  if (!archivo) return;
  // 80MB: generoso para un video corto de fondo, pero evita que alguien suba un archivo
  // pensado para otra cosa y se coma el disco/la red móvil de quien visite la feria.
  if (archivo.size > 80 * 1024 * 1024) {
    return toast('El archivo es demasiado grande (máximo 80MB).', 'error');
  }
  subiendoId.value = n.id;
  try {
    const datos = new FormData();
    datos.append('archivo', archivo);
    const r = await apiFetch(`/api/app/noches-fexpo/${n.id}/medio`, { method: 'POST', body: datos });
    const d = await r.json();
    if (!d.ok) return toast(d.mensaje || 'No se pudo subir el archivo.', 'error');
    toast(d.mensaje, 'ok');
    await cargar();
  } catch (e) {
    toast(e.message, 'error');
  } finally {
    subiendoId.value = null;
  }
}

async function quitarMedio(n) {
  if (ocupado.value) return;
  if (!confirm('¿Quitar la foto/video de fondo de esta noche?')) return;
  ocupado.value = true;
  try {
    const r = await apiFetch(`/api/app/noches-fexpo/${n.id}/medio`, { method: 'DELETE' });
    const d = await r.json();
    if (!d.ok) return toast(d.mensaje || 'Error', 'error');
    toast(d.mensaje, 'ok');
    await cargar();
  } finally {
    ocupado.value = false;
  }
}

const ordenadas = computed(() =>
  [...noches.value].sort((a, b) => (a.fecha || '').localeCompare(b.fecha || '') || (a.orden - b.orden)));

onMounted(cargar);
</script>

<template>
  <div class="fila entre encabezado">
    <div>
      <p class="muted nota">
        La cartelera de artistas que se ve en la vista pública de la feria (sección "Noches de FEXPO").
        Solo se muestran las noches de la edición activa.
      </p>
    </div>
    <button class="btn btn-primario" @click="abrirCrear">＋ Nueva noche</button>
  </div>

  <div class="card">
    <div v-if="cargando" class="vacio">Cargando…</div>
    <div v-else-if="ordenadas.length === 0" class="vacio">
      No hay noches todavía. Crea la primera con "＋ Nueva noche".
    </div>
    <table v-else class="tabla">
      <thead>
        <tr><th>Medio</th><th>Fecha</th><th>Título</th><th>Artista</th><th></th></tr>
      </thead>
      <tbody>
        <tr v-for="n in ordenadas" :key="n.id">
          <td>
            <div class="medio-celda" :style="{ '--color-noche': n.color || '#e31e24' }">
              <video v-if="n.medioTipo === 'VIDEO'" :src="urlApi(n.urlMedio)" muted playsinline loop autoplay />
              <img v-else-if="n.urlMedio" :src="urlApi(n.urlMedio)" :alt="`Fondo de ${n.titulo}`" />
              <div v-else class="medio-vacio" aria-hidden="true">🎤</div>
            </div>
          </td>
          <td>{{ n.fecha }}</td>
          <td><strong>{{ n.titulo }}</strong></td>
          <td>{{ n.nombreArtista || '— por revelar —' }}</td>
          <td class="acciones">
            <input
              type="file"
              accept="image/png,image/jpeg,image/webp,image/gif,video/mp4,video/webm"
              class="oculto"
              :ref="(el) => (inputsArchivo[n.id] = el)"
              @change="subirMedio(n, $event)"
            />
            <button class="btn btn-sm btn-fantasma" :disabled="subiendoId === n.id" title="Subir foto o video"
              @click="abrirSelectorArchivo(n.id)">
              {{ subiendoId === n.id ? '⏳' : '📷' }}
            </button>
            <button v-if="n.urlMedio" class="btn btn-sm btn-fantasma" :disabled="ocupado" title="Quitar medio"
              @click="quitarMedio(n)">🚫</button>
            <button class="btn btn-sm btn-fantasma" :disabled="ocupado" title="Editar" @click="abrirEditar(n)">✏️</button>
            <button class="btn btn-sm btn-peligro" :disabled="ocupado" title="Eliminar" @click="eliminar(n)">🗑</button>
          </td>
        </tr>
      </tbody>
    </table>
  </div>

  <UiModal v-if="modal.abierto" :titulo="modal.editando ? 'Editar noche' : 'Nueva noche'"
           @cerrar="modal.abierto = false" ancho="560px">
    <div class="grid2">
      <label class="campo"><span>Fecha *</span><input v-model="modal.fecha" type="date" class="control" /></label>
      <label class="campo"><span>Orden (desempate en la misma fecha)</span>
        <input v-model="modal.orden" type="number" class="control" />
      </label>
      <label class="campo campo-ancho"><span>Título *</span>
        <input v-model="modal.titulo" class="control" placeholder="Ej. Día 1" />
      </label>
      <label class="campo campo-ancho"><span>Descripción</span>
        <textarea v-model="modal.descripcion" class="control" rows="3"
          placeholder="Qué pasa esa noche (se ve en la tarjeta pública)"></textarea>
      </label>
      <label class="campo campo-ancho"><span>Nombre del artista</span>
        <input v-model="modal.nombreArtista" class="control" placeholder="Vacío = &quot;Artista por revelar&quot;" />
      </label>
      <label class="campo"><span>Color de acento</span>
        <input v-model="modal.color" type="color" class="control control-color" />
      </label>
    </div>
    <p v-if="!modal.editando" class="muted nota-modal">
      Después de guardar, podrás subir la foto o el video de fondo desde la fila de la tabla.
    </p>
    <template #pie>
      <button class="btn btn-fantasma" @click="modal.abierto = false">Cancelar</button>
      <button class="btn btn-primario" :disabled="guardando" @click="guardar">
        {{ guardando ? 'Guardando…' : 'Guardar' }}
      </button>
    </template>
  </UiModal>
</template>

<style scoped>
.encabezado { margin-bottom: 0.6rem; gap: 0.8rem; align-items: flex-start; }
.nota { margin: 0; max-width: 60ch; }
.nota-modal { margin: 0.8rem 0 0; font-size: 0.82rem; }
.grid2 { display: grid; grid-template-columns: 1fr 1fr; gap: 0.8rem; }
.campo-ancho { grid-column: 1 / -1; }
.control-color { padding: 0.2rem; height: 2.4rem; cursor: pointer; }
@media (max-width: 520px) { .grid2 { grid-template-columns: 1fr; } }

.oculto { display: none; }

/* Miniatura de la fila: recorta foto/video por igual (object-fit: cover) para que ninguno
   se vea más grande que otro sin importar la proporción del archivo original. */
.medio-celda {
  width: 64px;
  height: 64px;
  border-radius: 8px;
  overflow: hidden;
  background: color-mix(in srgb, var(--color-noche) 18%, transparent);
  display: flex;
  align-items: center;
  justify-content: center;
}
.medio-celda img, .medio-celda video {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.medio-vacio { font-size: 1.4rem; opacity: 0.6; }
</style>
