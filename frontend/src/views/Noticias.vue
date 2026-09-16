<script setup>
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue';
import UiModal from '../components/UiModal.vue';
import { apiFetch } from '../api';
import { url as urlApi } from '../config.js';
import { toast } from '../ui/toast';

// Noticias de la vista pública (V42, ver NoticiasApiController). Cada noticia es una foto o un
// video, su fecha, el día de la feria (Día 1, 2 o 3), un título y un texto que da contexto a ese
// medio. El carrusel público muestra las 10 últimas registradas y "todas las noticias" las
// agrupa por día; aquí se ven todas, de la última registrada a la primera.

const noticias = ref([]);
const cargando = ref(true);
const guardando = ref(false);
const eliminando = ref(false);

// Mismos formatos y tope que el servidor (FileStorageService / NoticiasService y
// spring.servlet.multipart.max-file-size): así el error sale aquí, claro, y no como un corte
// de la subida.
const TIPOS_MEDIO = ['image/png', 'image/jpeg', 'image/webp', 'image/gif', 'video/mp4', 'video/webm'];
const MAX_MEDIO_MB = 25;
const MAX_TITULO = 160;
const MAX_TEXTO = 2000;
const MAXIMO_PUBLICO = 10;
const DIAS = [1, 2, 3];

function hoyIso() {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

async function cargar() {
  cargando.value = true;
  try {
    const r = await apiFetch('/api/app/noticias');
    const d = await r.json().catch(() => null);
    // Ante un error (403, o un 500 porque V42 no se aplicó en esa base) llega un objeto en vez
    // de la lista: se muestra el motivo en lugar de romper la vista con "no es iterable".
    if (!r.ok || !Array.isArray(d)) {
      noticias.value = [];
      toast(d?.mensaje || `No se pudo cargar la lista (error ${r.status}).`, 'error');
      return;
    }
    noticias.value = d;
  } catch (e) {
    toast(e.message, 'error');
  } finally {
    cargando.value = false;
  }
}

// Mismo orden que el servidor: de la última registrada a la primera.
const ordenadas = computed(() => [...noticias.value].sort((a, b) => b.id - a.id));

// Las que hoy salen en el carrusel público, para marcarlas en la tabla.
const idsPublicas = computed(() => new Set(ordenadas.value.slice(0, MAXIMO_PUBLICO).map((n) => n.id)));

function aplicarLocal(noticia) {
  if (!noticia) return;
  const i = noticias.value.findIndex((x) => x.id === noticia.id);
  if (i >= 0) noticias.value.splice(i, 1, noticia);
  else noticias.value.push(noticia);
}

function fechaLegible(iso) {
  if (!iso) return '';
  // Mediodía fijo: new Date('2026-09-18') es medianoche UTC y en Bolivia cae el día anterior.
  return new Date(`${iso}T12:00:00`).toLocaleDateString('es-BO', { day: 'numeric', month: 'short', year: 'numeric' });
}

// ---- Modal de alta / edición ----

const vacio = { abierto: false, editando: null, fecha: '', dia: null, titulo: '', texto: '', medioActual: null,
  archivo: null, vistaPrevia: '', error: '' };
const modal = reactive({ ...vacio });
const arrastrando = ref(false);

const esVideoElegido = computed(() => modal.archivo?.type.startsWith('video/'));

function soltarVistaPrevia() {
  if (modal.vistaPrevia) URL.revokeObjectURL(modal.vistaPrevia);
  modal.vistaPrevia = '';
}

function abrirCrear() {
  soltarVistaPrevia();
  Object.assign(modal, vacio, { abierto: true, fecha: hoyIso() });
}

function abrirEditar(n) {
  soltarVistaPrevia();
  Object.assign(modal, vacio, {
    abierto: true, editando: n.id, fecha: n.fecha || '', dia: n.dia ?? null, titulo: n.titulo || '', texto: n.texto || '',
    medioActual: { tipo: n.medioTipo, url: n.urlMedio },
  });
}

function cerrarModal() {
  if (guardando.value) return;
  soltarVistaPrevia();
  modal.abierto = false;
}

function tamanoLegible(bytes) {
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

function elegirArchivo(archivo) {
  if (!archivo) return;
  modal.error = '';
  if (!TIPOS_MEDIO.includes(archivo.type)) {
    modal.error = 'Formato no admitido. Usa una foto (PNG, JPG, WEBP, GIF) o un video (MP4, WEBM).';
    return;
  }
  if (archivo.size > MAX_MEDIO_MB * 1024 * 1024) {
    modal.error = `El archivo pesa ${tamanoLegible(archivo.size)}; el máximo es ${MAX_MEDIO_MB} MB.`;
    return;
  }
  soltarVistaPrevia();
  modal.archivo = archivo;
  modal.vistaPrevia = URL.createObjectURL(archivo);
}

function onInputArchivo(e) {
  elegirArchivo(e.target.files?.[0]);
  e.target.value = ''; // permite volver a elegir el mismo archivo tras un error
}

function onSoltar(e) {
  arrastrando.value = false;
  elegirArchivo(e.dataTransfer?.files?.[0]);
}

async function guardar() {
  if (!modal.archivo && !modal.medioActual) return toast('La foto o el video es obligatorio.', 'error');
  if (!modal.fecha) return toast('La fecha es obligatoria.', 'error');
  if (!DIAS.includes(modal.dia)) return toast('Elige el día de la feria: Día 1, Día 2 o Día 3.', 'error');
  if (!modal.titulo.trim()) return toast('El título es obligatorio.', 'error');
  if (!modal.texto.trim()) return toast('El texto de la noticia es obligatorio.', 'error');

  guardando.value = true;
  try {
    const datos = new FormData();
    datos.append('fecha', modal.fecha);
    datos.append('dia', modal.dia);
    datos.append('titulo', modal.titulo);
    datos.append('texto', modal.texto);
    if (modal.archivo) datos.append('archivo', modal.archivo);
    const ruta = modal.editando ? `/api/app/noticias/${modal.editando}` : '/api/app/noticias';
    const r = await apiFetch(ruta, { method: 'POST', body: datos });
    // .catch: un error del servidor o de un proxy (p.ej. archivo demasiado grande) puede no venir en JSON.
    const d = await r.json().catch(() => null);
    if (!d?.ok) return toast(d?.mensaje || `No se pudo guardar (error ${r.status}).`, 'error');
    toast(d.mensaje, 'ok');
    aplicarLocal(d.noticia);
    soltarVistaPrevia();
    modal.abierto = false;
  } catch (e) {
    toast(e.message, 'error');
  } finally {
    guardando.value = false;
  }
}

// ---- Eliminar ----

const aEliminar = ref(null);

async function confirmarEliminar() {
  const n = aEliminar.value;
  if (!n || eliminando.value) return;
  eliminando.value = true;
  try {
    const r = await apiFetch(`/api/app/noticias/${n.id}`, { method: 'DELETE' });
    const d = await r.json().catch(() => null);
    if (!d?.ok) return toast(d?.mensaje || `No se pudo eliminar (error ${r.status}).`, 'error');
    toast(d.mensaje, 'ok');
    noticias.value = noticias.value.filter((x) => x.id !== n.id);
    aEliminar.value = null;
  } catch (e) {
    toast(e.message, 'error');
  } finally {
    eliminando.value = false;
  }
}

onMounted(cargar);
onUnmounted(soltarVistaPrevia);
</script>

<template>
  <div class="fila entre encabezado">
    <p class="muted nota">
      Las noticias de la vista pública de la feria. El carrusel muestra las {{ MAXIMO_PUBLICO }}
      últimas registradas de la edición activa; "Ver todas las noticias" las muestra agrupadas por día.
    </p>
    <button class="btn btn-primario" @click="abrirCrear">＋ Nueva noticia</button>
  </div>

  <div class="card">
    <div v-if="cargando" class="vacio">Cargando…</div>
    <div v-else-if="ordenadas.length === 0" class="vacio">
      No hay noticias todavía. Crea la primera con "＋ Nueva noticia".
    </div>
    <table v-else class="tabla">
      <thead>
        <tr><th>Medio</th><th>Día</th><th>Fecha</th><th>Noticia</th><th>Carrusel</th><th></th></tr>
      </thead>
      <tbody>
        <tr v-for="n in ordenadas" :key="n.id">
          <td>
            <div class="medio-celda">
              <video v-if="n.medioTipo === 'VIDEO'" :src="urlApi(n.urlMedio)" muted playsinline loop autoplay />
              <img v-else :src="urlApi(n.urlMedio)" :alt="n.titulo" />
            </div>
          </td>
          <td class="celda-fecha"><span class="badge">Día {{ n.dia }}</span></td>
          <td class="celda-fecha">{{ fechaLegible(n.fecha) }}</td>
          <td>
            <strong>{{ n.titulo }}</strong>
            <p class="muted resumen">{{ n.texto }}</p>
          </td>
          <td>
            <span v-if="idsPublicas.has(n.id)" class="badge">Visible</span>
            <span v-else class="muted fuera">Fuera de las {{ MAXIMO_PUBLICO }}</span>
          </td>
          <td class="acciones">
            <button class="btn btn-sm btn-fantasma" :disabled="eliminando" title="Editar" @click="abrirEditar(n)">✏️</button>
            <button class="btn btn-sm btn-peligro" :disabled="eliminando" title="Eliminar" @click="aEliminar = n">🗑</button>
          </td>
        </tr>
      </tbody>
    </table>
  </div>

  <UiModal v-if="modal.abierto" :titulo="modal.editando ? 'Editar noticia' : 'Nueva noticia'"
           ancho="600px" @cerrar="cerrarModal">
    <div class="formulario">
      <div class="campo">
        <span>Foto o video *</span>
        <label
          v-if="!modal.archivo && !modal.medioActual"
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
          <!-- Mismo recorte que el carrusel público (rectángulo horizontal, cover): lo que se ve
               aquí es lo que se verá allí. -->
          <div class="vista-previa-marco">
            <template v-if="modal.archivo">
              <video v-if="esVideoElegido" :src="modal.vistaPrevia" muted playsinline loop autoplay />
              <img v-else :src="modal.vistaPrevia" :alt="`Vista previa de ${modal.archivo.name}`" />
            </template>
            <template v-else>
              <video v-if="modal.medioActual.tipo === 'VIDEO'" :src="urlApi(modal.medioActual.url)" muted playsinline loop autoplay />
              <img v-else :src="urlApi(modal.medioActual.url)" alt="Foto actual" />
            </template>
          </div>
          <div class="vista-previa-pie">
            <span class="archivo-nombre" :title="modal.archivo?.name">
              {{ modal.archivo ? modal.archivo.name : 'Foto o video actual' }}
            </span>
            <span v-if="modal.archivo" class="muted">{{ tamanoLegible(modal.archivo.size) }}</span>
            <label class="btn btn-sm btn-fantasma" :class="{ deshabilitado: guardando }">
              <input type="file" class="oculto-visual" :accept="TIPOS_MEDIO.join(',')" :disabled="guardando"
                @change="onInputArchivo" />
              Cambiar
            </label>
          </div>
        </div>
        <p v-if="modal.error" class="error-archivo" role="alert">{{ modal.error }}</p>
      </div>

      <div class="fila-dia-fecha">
        <div class="campo">
          <span id="etiqueta-dia">Día de la feria *</span>
          <div class="selector-dia" role="radiogroup" aria-labelledby="etiqueta-dia">
            <button
              v-for="d in DIAS"
              :key="d"
              type="button"
              role="radio"
              class="btn btn-sm"
              :class="modal.dia === d ? 'btn-primario' : 'btn-fantasma'"
              :aria-checked="modal.dia === d"
              @click="modal.dia = d"
            >Día {{ d }}</button>
          </div>
        </div>
        <label class="campo"><span>Fecha de la noticia *</span>
          <input v-model="modal.fecha" type="date" class="control control-fecha" />
        </label>
      </div>
      <label class="campo"><span>Título *</span>
        <input v-model="modal.titulo" class="control" :maxlength="MAX_TITULO" placeholder="Ej. Inauguración de la FEXPO UAP 2026" />
      </label>
      <label class="campo"><span>Texto de la noticia *</span>
        <textarea v-model="modal.texto" class="control" rows="4" :maxlength="MAX_TEXTO"
          placeholder="Qué muestra la foto o el video y qué pasó"></textarea>
        <small class="muted contador">{{ modal.texto.length }} / {{ MAX_TEXTO }}</small>
      </label>
    </div>

    <template #pie>
      <button class="btn btn-fantasma" :disabled="guardando" @click="cerrarModal">Cancelar</button>
      <button class="btn btn-primario" :disabled="guardando" @click="guardar">
        {{ guardando ? 'Guardando…' : modal.editando ? 'Guardar cambios' : 'Publicar' }}
      </button>
    </template>
  </UiModal>

  <UiModal v-if="aEliminar" titulo="Eliminar noticia" ancho="440px" @cerrar="!eliminando && (aEliminar = null)">
    <p class="nota-eliminar">
      <strong>{{ aEliminar.titulo }}</strong> dejará de mostrarse en la vista pública al instante.
      El registro no se borra: queda guardado como eliminado.
    </p>
    <template #pie>
      <button class="btn btn-fantasma" :disabled="eliminando" @click="aEliminar = null">Cancelar</button>
      <button class="btn btn-peligro" :disabled="eliminando" @click="confirmarEliminar">
        {{ eliminando ? 'Eliminando…' : '🗑 Eliminar' }}
      </button>
    </template>
  </UiModal>
</template>

<style scoped>
.encabezado { margin-bottom: 0.6rem; gap: 0.8rem; align-items: flex-start; }
.nota { margin: 0; max-width: 60ch; }

.medio-celda {
  width: 96px;
  aspect-ratio: 16 / 9;
  border-radius: 8px;
  overflow: hidden;
  background: color-mix(in srgb, var(--muted) 15%, transparent);
}
.medio-celda img, .medio-celda video { width: 100%; height: 100%; object-fit: cover; display: block; }
.celda-fecha { white-space: nowrap; }
.resumen {
  margin: 0.2rem 0 0;
  font-size: 0.82rem;
  max-width: 48ch;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.fuera { font-size: 0.8rem; white-space: nowrap; }

.formulario { display: flex; flex-direction: column; gap: 0.8rem; }
.control-fecha { max-width: 220px; }
.fila-dia-fecha { display: flex; flex-wrap: wrap; gap: 0.8rem 1.4rem; }
.selector-dia { display: flex; gap: 0.4rem; }
.contador { align-self: flex-end; font-size: 0.75rem; }

/* Oculta el <input type=file> sin sacarlo del orden de tabulación. */
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

.vista-previa { border: 1px solid var(--border); border-radius: 10px; overflow: hidden; }
.vista-previa-marco { aspect-ratio: 16 / 9; background: color-mix(in srgb, var(--muted) 12%, transparent); }
.vista-previa-marco img, .vista-previa-marco video { display: block; width: 100%; height: 100%; object-fit: cover; }
.vista-previa-pie { display: flex; align-items: center; gap: 0.6rem; padding: 0.5rem 0.7rem; font-size: 0.85rem; }
.archivo-nombre { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-weight: 600; }
.vista-previa-pie label { position: relative; cursor: pointer; }
.vista-previa-pie label.deshabilitado { opacity: 0.5; cursor: default; }

.error-archivo {
  margin: 0.4rem 0 0;
  padding: 0.5rem 0.7rem;
  border-radius: 8px;
  font-size: 0.85rem;
  color: var(--danger);
  background: var(--danger-suave);
}
.nota-eliminar { margin: 0; line-height: 1.5; }
</style>
