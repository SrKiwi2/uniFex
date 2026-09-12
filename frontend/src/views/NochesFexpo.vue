<script setup>
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue';
import UiModal from '../components/UiModal.vue';
import { apiFetch } from '../api';
import { url as urlApi } from '../config.js';
import { toast } from '../ui/toast';
import { escucharNoches } from '../nochesEnVivo.js';

const noches = ref([]);
const cargando = ref(true);
const guardando = ref(false);
// Ocupado: hay una acción de fila en vuelo (subir, quitar medio, eliminar). Bloquea los
// botones y evita que su modal se cierre antes de saber en qué terminó.
const ocupado = ref(false);

const vacioModal = { abierto: false, editando: null, fecha: '', titulo: '', descripcion: '',
  nombreArtista: '', color: '#e31e24', orden: 0 };
const modal = reactive({ ...vacioModal });

// Los mismos formatos que acepta FileStorageService. 25 MB es el tope real del servidor
// (spring.servlet.multipart.max-file-size): validarlo aquí evita que un archivo más grande
// pase el filtro del navegador y luego el servidor corte la subida con un error confuso.
const TIPOS_MEDIO = ['image/png', 'image/jpeg', 'image/webp', 'image/gif', 'video/mp4', 'video/webm'];
const MAX_MEDIO_MB = 25;

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

// Refleja al instante lo que devolvió el servidor, sin volver a pedir la lista entera. La
// difusión en vivo (escucharNoches, abajo) llega igual un momento después con la lista
// completa: es la que mantiene al día a los demás paneles abiertos y a la vista pública.
function aplicarLocal(noche) {
  if (!noche) return;
  const i = noches.value.findIndex((x) => x.id === noche.id);
  if (i >= 0) noches.value.splice(i, 1, noche);
  else noches.value.push(noche);
}

// ---- Modal de alta / edición ----

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
function cerrarEdicion() {
  if (!guardando.value) modal.abierto = false;
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
    aplicarLocal(d.noche);
  } catch (e) {
    toast(e.message, 'error');
  } finally {
    guardando.value = false;
  }
}

// ---- Modal de acciones de fila: subir medio, quitar medio, eliminar ----
// Un solo modal con la ficha de la noche arriba; lo que cambia según `tipo` es el cuerpo y
// el botón de confirmar.

const TITULOS_ACCION = {
  subir: 'Foto o video de fondo',
  quitar: 'Quitar foto o video',
  eliminar: 'Eliminar noche',
};

const accion = reactive({ tipo: null, noche: null, archivo: null, vistaPrevia: '', error: '' });
const arrastrando = ref(false);

const esVideoElegido = computed(() => accion.archivo?.type.startsWith('video/'));

function abrirAccion(tipo, n) {
  if (ocupado.value) return;
  soltarVistaPrevia();
  Object.assign(accion, { tipo, noche: n, archivo: null, error: '' });
}

function cerrarAccion() {
  if (ocupado.value) return;
  soltarVistaPrevia();
  Object.assign(accion, { tipo: null, noche: null, archivo: null, error: '' });
}

// La vista previa es una URL local (blob:) que retiene el archivo en memoria hasta que se
// libera: se suelta al cambiar de archivo, al cerrar el modal y al salir de la vista.
function soltarVistaPrevia() {
  if (accion.vistaPrevia) URL.revokeObjectURL(accion.vistaPrevia);
  accion.vistaPrevia = '';
}

function tamanoLegible(bytes) {
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

function elegirArchivo(archivo) {
  if (!archivo) return;
  accion.error = '';
  if (!TIPOS_MEDIO.includes(archivo.type)) {
    accion.error = 'Formato no admitido. Usa una foto (PNG, JPG, WEBP, GIF) o un video (MP4, WEBM).';
    return;
  }
  if (archivo.size > MAX_MEDIO_MB * 1024 * 1024) {
    accion.error = `El archivo pesa ${tamanoLegible(archivo.size)}; el máximo es ${MAX_MEDIO_MB} MB.`;
    return;
  }
  soltarVistaPrevia();
  accion.archivo = archivo;
  accion.vistaPrevia = URL.createObjectURL(archivo);
}

function onInputArchivo(e) {
  elegirArchivo(e.target.files?.[0]);
  e.target.value = ''; // permite volver a elegir el mismo archivo tras un error
}

function onSoltar(e) {
  arrastrando.value = false;
  elegirArchivo(e.dataTransfer?.files?.[0]);
}

async function confirmarAccion() {
  const n = accion.noche;
  if (!n || ocupado.value) return;
  const base = `/api/app/noches-fexpo/${n.id}`;
  if (accion.tipo === 'subir') {
    if (!accion.archivo) return;
    const datos = new FormData();
    datos.append('archivo', accion.archivo);
    await ejecutar(() => apiFetch(`${base}/medio`, { method: 'POST', body: datos }),
      (d) => aplicarLocal(d.noche));
  } else if (accion.tipo === 'quitar') {
    await ejecutar(() => apiFetch(`${base}/medio`, { method: 'DELETE' }),
      (d) => aplicarLocal(d.noche));
  } else if (accion.tipo === 'eliminar') {
    // Baja lógica: el backend solo marca la noche como eliminada (_estado = 'X'), la fila
    // sigue en la BD.
    await ejecutar(() => apiFetch(base, { method: 'DELETE' }),
      () => { noches.value = noches.value.filter((x) => x.id !== n.id); });
  }
}

// Si falla, el modal queda abierto con lo elegido, para reintentar o cancelar.
async function ejecutar(peticion, alTerminar) {
  ocupado.value = true;
  try {
    const r = await peticion();
    // .catch: un error del servidor o de un proxy puede no venir en JSON.
    const d = await r.json().catch(() => null);
    if (!d?.ok) return toast(d?.mensaje || `No se pudo completar la acción (error ${r.status}).`, 'error');
    toast(d.mensaje, 'ok');
    alTerminar(d);
    soltarVistaPrevia();
    Object.assign(accion, { tipo: null, noche: null, archivo: null, error: '' });
  } catch (e) {
    toast(e.message, 'error');
  } finally {
    ocupado.value = false;
  }
}

const ordenadas = computed(() =>
  [...noches.value].sort((a, b) => (a.fecha || '').localeCompare(b.fecha || '') || (a.orden - b.orden)));

// Cambios hechos desde OTRO panel (otra pestaña, otro admin) llegan por aquí sin recargar.
let dejarDeEscuchar = null;
onMounted(() => {
  cargar();
  dejarDeEscuchar = escucharNoches((lista) => {
    noches.value = lista;
  });
});
onUnmounted(() => {
  dejarDeEscuchar?.();
  soltarVistaPrevia();
});
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
            <button class="btn btn-sm btn-fantasma" :disabled="ocupado" title="Subir foto o video"
              @click="abrirAccion('subir', n)">📷</button>
            <button v-if="n.urlMedio" class="btn btn-sm btn-fantasma" :disabled="ocupado" title="Quitar foto o video"
              @click="abrirAccion('quitar', n)">🚫</button>
            <button class="btn btn-sm btn-fantasma" :disabled="ocupado" title="Editar" @click="abrirEditar(n)">✏️</button>
            <button class="btn btn-sm btn-peligro" :disabled="ocupado" title="Eliminar"
              @click="abrirAccion('eliminar', n)">🗑</button>
          </td>
        </tr>
      </tbody>
    </table>
  </div>

  <UiModal v-if="modal.abierto" :titulo="modal.editando ? 'Editar noche' : 'Nueva noche'"
           @cerrar="cerrarEdicion" ancho="560px">
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
      Después de guardar, podrás subir la foto o el video de fondo con el botón 📷 de su fila.
    </p>
    <template #pie>
      <button class="btn btn-fantasma" :disabled="guardando" @click="cerrarEdicion">Cancelar</button>
      <button class="btn btn-primario" :disabled="guardando" @click="guardar">
        {{ guardando ? 'Guardando…' : 'Guardar' }}
      </button>
    </template>
  </UiModal>

  <UiModal v-if="accion.tipo" :titulo="TITULOS_ACCION[accion.tipo]" ancho="480px" @cerrar="cerrarAccion">
    <div class="ficha">
      <div class="medio-celda" :style="{ '--color-noche': accion.noche.color || '#e31e24' }">
        <video v-if="accion.noche.medioTipo === 'VIDEO'" :src="urlApi(accion.noche.urlMedio)" muted playsinline loop autoplay />
        <img v-else-if="accion.noche.urlMedio" :src="urlApi(accion.noche.urlMedio)" alt="" />
        <div v-else class="medio-vacio" aria-hidden="true">🎤</div>
      </div>
      <div>
        <strong>{{ accion.noche.titulo }}</strong>
        <p class="muted">{{ accion.noche.fecha }} · {{ accion.noche.nombreArtista || 'Artista por revelar' }}</p>
      </div>
    </div>

    <template v-if="accion.tipo === 'subir'">
      <label
        v-if="!accion.archivo"
        class="zona-archivo"
        :class="{ activa: arrastrando }"
        @dragover.prevent="arrastrando = true"
        @dragleave="arrastrando = false"
        @drop.prevent="onSoltar"
      >
        <input type="file" class="oculto-visual" :accept="TIPOS_MEDIO.join(',')" @change="onInputArchivo" />
        <span class="zona-icono" aria-hidden="true">🖼️</span>
        <strong>Arrastra aquí una foto o un video</strong>
        <span class="muted">o haz clic para elegirlo · PNG, JPG, WEBP, GIF, MP4, WEBM · máx. {{ MAX_MEDIO_MB }} MB</span>
      </label>

      <div v-else class="vista-previa">
        <video v-if="esVideoElegido" :src="accion.vistaPrevia" muted playsinline loop autoplay />
        <img v-else :src="accion.vistaPrevia" :alt="`Vista previa de ${accion.archivo.name}`" />
        <div class="vista-previa-pie">
          <span class="archivo-nombre" :title="accion.archivo.name">{{ accion.archivo.name }}</span>
          <span class="muted">{{ tamanoLegible(accion.archivo.size) }}</span>
          <label class="btn btn-sm btn-fantasma" :class="{ deshabilitado: ocupado }">
            <input type="file" class="oculto-visual" :accept="TIPOS_MEDIO.join(',')" :disabled="ocupado"
              @change="onInputArchivo" />
            Cambiar
          </label>
        </div>
      </div>

      <p v-if="accion.error" class="error-accion" role="alert">{{ accion.error }}</p>
      <p class="muted nota-accion">
        <template v-if="accion.noche.urlMedio">Reemplaza la foto/video actual. </template>
        Se verá en la tarjeta de esta noche en la vista pública en cuanto se suba.
      </p>
    </template>

    <p v-else-if="accion.tipo === 'quitar'" class="muted nota-accion">
      La tarjeta de esta noche en la vista pública volverá a su fondo de color. El archivo queda
      guardado en el servidor: si lo necesitas, puedes volver a subirlo.
    </p>

    <p v-else-if="accion.tipo === 'eliminar'" class="muted nota-accion">
      Dejará de mostrarse en la vista pública de la feria al instante. El registro no se borra:
      queda guardado como eliminado.
    </p>

    <template #pie>
      <button class="btn btn-fantasma" :disabled="ocupado" @click="cerrarAccion">Cancelar</button>
      <button v-if="accion.tipo === 'subir'" class="btn btn-primario" :disabled="ocupado || !accion.archivo"
        @click="confirmarAccion">
        {{ ocupado ? 'Subiendo…' : '⬆ Subir' }}
      </button>
      <button v-else-if="accion.tipo === 'quitar'" class="btn btn-peligro" :disabled="ocupado" @click="confirmarAccion">
        {{ ocupado ? 'Quitando…' : '🚫 Quitar' }}
      </button>
      <button v-else class="btn btn-peligro" :disabled="ocupado" @click="confirmarAccion">
        {{ ocupado ? 'Eliminando…' : '🗑 Eliminar' }}
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

/* Modal de acciones */
.ficha { display: flex; align-items: center; gap: 0.8rem; }
.ficha .medio-celda { flex-shrink: 0; }
.ficha p { margin: 0.15rem 0 0; font-size: 0.85rem; }
.nota-accion { margin: 0; font-size: 0.85rem; line-height: 1.45; }

/* Oculta el <input type=file> sin sacarlo del orden de tabulación: el label que lo envuelve
   sigue siendo alcanzable con el teclado (display:none lo haría inalcanzable). */
.oculto-visual { position: absolute; width: 1px; height: 1px; opacity: 0; overflow: hidden; }

.zona-archivo {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.3rem;
  padding: 1.4rem 1rem;
  text-align: center;
  border: 2px dashed var(--border);
  border-radius: 10px;
  cursor: pointer;
  transition: border-color 0.15s ease, background 0.15s ease;
}
.zona-archivo:hover, .zona-archivo.activa, .zona-archivo:focus-within {
  border-color: var(--acento);
  background: var(--acento-suave);
}
.zona-archivo .muted { font-size: 0.8rem; }
.zona-icono { font-size: 1.8rem; }

/* Vista previa ENTERA (contain), como se verá en la tarjeta pública, que tampoco recorta. */
.vista-previa { border: 1px solid var(--border); border-radius: 10px; overflow: hidden; }
.vista-previa img, .vista-previa video {
  display: block;
  width: 100%;
  max-height: 240px;
  object-fit: contain;
  background: color-mix(in srgb, var(--muted) 12%, transparent);
}
.vista-previa-pie { display: flex; align-items: center; gap: 0.6rem; padding: 0.5rem 0.7rem; font-size: 0.85rem; }
.archivo-nombre { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-weight: 600; }
.vista-previa-pie label { position: relative; cursor: pointer; }
.vista-previa-pie label.deshabilitado { opacity: 0.5; cursor: default; }

.error-accion {
  margin: 0;
  padding: 0.5rem 0.7rem;
  border-radius: 8px;
  font-size: 0.85rem;
  color: var(--danger);
  background: var(--danger-suave);
}
</style>
