<script setup>
import { ref, computed, watch, onUnmounted } from 'vue';

const props = defineProps({
  /** Archivo seleccionado (File object). */
  archivo: { type: File, default: null },
  /** Texto del botón de confirmar. */
  textoConfirmar: { type: String, default: 'Confirmar y subir' },
  /** Texto del botón de cancelar. */
  textoCancelar: { type: String, default: 'Cancelar' },
  /** Si se muestra vista previa de imagen (para imágenes). */
  previsualizarImagen: { type: Boolean, default: true },
  /** Tamaño máximo en bytes (default 25MB). */
  maxSize: { type: Number, default: 25 * 1024 * 1024 },
});

const emit = defineEmits(['confirmar', 'cancelar']);

const error = ref('');
const previsualizacionUrl = ref('');

const esImagen = computed(() => props.archivo && props.archivo.type.startsWith('image/'));
const tamanoLegible = computed(() => {
  if (!props.archivo) return '';
  const b = props.archivo.size;
  if (b < 1024) return `${b} B`;
  if (b < 1024 * 1024) return `${(b / 1024).toFixed(1)} KB`;
  return `${(b / (1024 * 1024)).toFixed(1)} MB`;
});

const excedeTamano = computed(() => props.archivo && props.archivo.size > props.maxSize);

function validar() {
  error.value = '';
  if (!props.archivo) {
    error.value = 'No hay archivo seleccionado';
    return false;
  }
  if (excedeTamano.value) {
    error.value = `El archivo supera el máximo permitido (${(props.maxSize / (1024 * 1024)).toFixed(0)} MB)`;
    return false;
  }
  return true;
}

function confirmar() {
  if (!validar()) return;
  limpiar();
  emit('confirmar', props.archivo);
}

function cancelar() {
  limpiar();
  emit('cancelar');
}

function limpiar() {
  error.value = '';
  if (previsualizacionUrl.value) {
    URL.revokeObjectURL(previsualizacionUrl.value);
    previsualizacionUrl.value = '';
  }
}

function onArchivoCambiado(e) {
  const archivo = e.target.files?.[0];
  if (archivo) {
    previsualizacionUrl.value = URL.createObjectURL(archivo);
  }
}

watch(() => props.archivo, (nuevo) => {
  if (nuevo && props.previsualizarImagen && esImagen.value) {
    previsualizacionUrl.value = URL.createObjectURL(nuevo);
  }
}, { immediate: true });

onUnmounted(limpiar);
</script>

<template>
  <div class="preview-modal-overlay" @click.self="cancelar" role="dialog" aria-modal="true" aria-labelledby="preview-titulo">
    <div class="preview-modal card">
      <header class="cabecera">
        <h2 id="preview-titulo">Vista previa del archivo</h2>
        <button class="btn btn-fantasma btn-icono" @click="cancelar" aria-label="Cerrar">✕</button>
      </header>

      <div class="cuerpo">
        <div class="info-archivo">
          <div class="icono-archivo" v-if="esImagen && previsualizacionUrl">
            <img :src="previsualizacionUrl" :alt="`Previsualización de ${props.archivo.name}`" loading="lazy" />
          </div>
          <div class="icono-archivo" v-else-if="esImagen">🖼️</div>
          <div class="icono-archivo" v-else>📄</div>

          <div class="detalles">
            <strong>{{ props.archivo?.name || 'Sin archivo' }}</strong>
            <span class="muted">{{ tamanoLegible }} · {{ props.archivo?.type || 'tipo desconocido' }}</span>
          </div>
        </div>

        <div v-if="error" class="error">{{ error }}</div>

        <div v-if="excedeTamano" class="advertencia">
          ⚠️ El archivo excede el tamaño máximo permitido ({{ (maxSize / (1024 * 1024)).toFixed(0) }} MB)
        </div>
      </div>

      <footer class="pie">
        <button class="btn btn-fantasma" @click="cancelar">{{ textoCancelar }}</button>
        <button class="btn btn-primario" :disabled="excedeTamano || !props.archivo" @click="confirmar">
          {{ textoConfirmar }}
        </button>
      </footer>
    </div>
  </div>
</template>

<style scoped>
.preview-modal-overlay {
  position: fixed; inset: 0; z-index: 1500;
  display: grid; place-items: center; padding: 1rem;
  background: rgba(2, 6, 23, 0.6); backdrop-filter: blur(3px);
}
.preview-modal {
  width: 100%; max-width: 480px; max-height: 90vh; overflow: auto;
  box-shadow: var(--sombra-lg);
  animation: subir 0.2s ease;
}
@keyframes subir { from { opacity: 0; transform: translateY(12px) scale(0.98); } to { opacity: 1; transform: none; } }
.cabecera { display: flex; align-items: center; justify-content: space-between; padding: 1rem 1.2rem 0.6rem; }
.cabecera h2 { margin: 0; font-size: 1.15rem; }
.cuerpo { padding: 1rem 1.2rem; display: flex; flex-direction: column; gap: 0.8rem; }
.info-archivo { display: flex; align-items: center; gap: 1rem; }
.icono-archivo { width: 80px; height: 80px; flex: none; border-radius: var(--radio); overflow: hidden; background: var(--panel); display: grid; place-items: center; font-size: 2rem; }
.icono-archivo img { width: 100%; height: 100%; object-fit: cover; }
.detalles { display: flex; flex-direction: column; gap: 0.2rem; min-width: 0; }
.detalles strong { font-size: 0.95rem; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.detalles .muted { font-size: 0.8rem; color: var(--muted); }
.error { color: var(--danger); font-size: 0.85rem; background: var(--danger-suave); padding: 0.5rem; border-radius: var(--radio-sm); }
.advertencia { color: var(--tramite); font-size: 0.85rem; background: var(--tramite-suave); padding: 0.5rem; border-radius: var(--radio-sm); }
.pie { display: flex; justify-content: flex-end; gap: 0.6rem; padding: 0.8rem 1.2rem; border-top: 1px solid var(--border); }
.pie .btn { min-width: 120px; }
</style>