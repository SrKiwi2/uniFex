<script setup>
import { ref, computed, watch } from 'vue';
import { apiFetch } from '../api';
import { ETIQUETA_ESTADO, CLASE_ESTADO } from '../mapa';
import { url } from '../config';

/*
 * Ficha de una caseta: lo que el vendedor le enseña al cliente.
 *
 * Aparece al tocar una caseta en el mapa. Reune lo que hace falta para decidir una compra
 * —como se ve de verdad, cuanto cuesta, que mide y donde esta— y ofrece la accion.
 *
 * En movil es una hoja que sube desde abajo, que es donde llega el pulgar; en pantalla
 * grande, un panel lateral.
 */
const props = defineProps({
  puesto: { type: Object, default: null },
  esMia: { type: Boolean, default: false },
  ocupado: { type: Boolean, default: false },
});
const emit = defineEmits(['cerrar', 'agregar', 'quitar']);

const fotos = ref([]);
const cargandoFotos = ref(false);
const indice = ref(0);

const accion = computed(() => {
  if (!props.puesto) return null;
  if (props.puesto.estado === 'L') return 'agregar';
  if (props.esMia) return 'quitar';
  return null; // de otro vendedor, vendida o bloqueada: no se toca
});

const motivoSinAccion = computed(() => {
  if (!props.puesto || accion.value) return '';
  if (props.puesto.estado === 'T') return 'La tiene reservada otro vendedor.';
  if (props.puesto.estado === 'O') return 'Ya está vendida.';
  if (props.puesto.estado === 'X') return 'Está bloqueada por reparación.';
  return '';
});

// Las fotos se piden al abrir la ficha, no con el mapa: son ~500 casetas y traerlas todas
// de golpe cargaria megas que casi nunca se miran.
watch(() => props.puesto?.id, async (id) => {
  fotos.value = [];
  indice.value = 0;
  if (!id) return;
  cargandoFotos.value = true;
  try {
    const r = await apiFetch(`/api/app/puestos/${id}/fotos`);
    if (r.ok) fotos.value = await r.json();
  } catch {
    /* sin fotos la ficha sigue siendo util: precio, medida y ubicacion */
  } finally {
    cargandoFotos.value = false;
  }
}, { immediate: true });

const precio = computed(() => Number(props.puesto?.precio || 0));
</script>

<template>
  <Teleport to="body">
    <Transition name="hoja">
      <div v-if="puesto" class="velo" @click.self="emit('cerrar')">
        <aside class="ficha" role="dialog" aria-label="Detalle de la caseta">
          <header>
            <div>
              <h2>{{ puesto.categoria }} {{ puesto.codigo }}</h2>
              <span class="chip" :class="CLASE_ESTADO[puesto.estado]">
                {{ ETIQUETA_ESTADO[puesto.estado] }}{{ esMia ? ' · tuya' : '' }}
              </span>
            </div>
            <button class="btn btn-fantasma btn-icono" aria-label="Cerrar" @click="emit('cerrar')">✕</button>
          </header>

          <!-- Fotos: lo que de verdad le enseña al cliente cómo es la caseta -->
          <div v-if="cargandoFotos" class="galeria vacia">Cargando fotos…</div>
          <div v-else-if="fotos.length" class="galeria">
            <!-- `url()` antepone el servidor cuando toca: el backend devuelve la ruta como
                 /files/..., que en el APK apuntaria al contenedor de la app y no cargaria. -->
            <img :src="url(fotos[indice].url)" :alt="fotos[indice].descripcion || 'Foto de la caseta'"
                 loading="lazy" decoding="async" />
            <div v-if="fotos.length > 1" class="puntos">
              <button v-for="(f, i) in fotos" :key="f.id" class="punto"
                      :class="{ on: i === indice }" :aria-label="`Foto ${i + 1}`"
                      @click="indice = i"></button>
            </div>
            <p v-if="fotos[indice].descripcion" class="pie">{{ fotos[indice].descripcion }}</p>
          </div>
          <div v-else class="galeria vacia">Sin fotos todavía</div>

          <dl class="datos">
            <div><dt>Precio</dt><dd class="precio">{{ precio > 0 ? precio.toLocaleString('es-BO') + ' Bs' : 'sin precio' }}</dd></div>
            <div><dt>Medida</dt><dd>{{ puesto.tamano || '—' }}</dd></div>
            <div v-if="puesto.referencia" class="ancho"><dt>Ubicación</dt><dd>{{ puesto.referencia }}</dd></div>
          </dl>

          <footer>
            <p v-if="motivoSinAccion" class="motivo">{{ motivoSinAccion }}</p>
            <button v-if="accion === 'agregar'" class="btn btn-primario grande"
                    :disabled="ocupado" @click="emit('agregar', puesto)">
              Agregar a la venta
            </button>
            <button v-else-if="accion === 'quitar'" class="btn btn-peligro grande"
                    :disabled="ocupado" @click="emit('quitar', puesto)">
              Quitar de la venta
            </button>
          </footer>
        </aside>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.velo {
  position: fixed; inset: 0; z-index: 80; background: rgba(2, 6, 23, 0.45);
  display: flex; align-items: flex-end; justify-content: center;
}
.ficha {
  background: var(--panel); width: 100%; max-width: 460px;
  border-radius: var(--radio) var(--radio) 0 0;
  box-shadow: var(--sombra-md); max-height: 88vh; overflow-y: auto;
  display: flex; flex-direction: column; gap: 0.9rem; padding: 1rem 1.1rem 1.2rem;
}
header { display: flex; align-items: flex-start; justify-content: space-between; gap: 0.6rem; }
header h2 { margin: 0 0 0.3rem; font-size: 1.15rem; }
.chip { padding: 0.15rem 0.55rem; border-radius: 999px; font-size: 0.75rem; font-weight: 700; }

.galeria { position: relative; border-radius: var(--radio-sm); overflow: hidden; background: var(--panel-2); }
.galeria img { width: 100%; display: block; aspect-ratio: 4 / 3; object-fit: cover; }
.galeria.vacia {
  display: grid; place-items: center; aspect-ratio: 4 / 3;
  color: var(--muted); font-size: 0.9rem; border: 1px dashed var(--border);
}
.puntos { position: absolute; bottom: 0.5rem; left: 0; right: 0; display: flex; justify-content: center; gap: 0.35rem; }
.punto {
  width: 8px; height: 8px; border-radius: 50%; border: none; padding: 0;
  background: rgba(255, 255, 255, 0.55);
}
.punto.on { background: #fff; }
.pie { margin: 0.4rem 0 0; font-size: 0.82rem; color: var(--muted); }

.datos { display: grid; grid-template-columns: 1fr 1fr; gap: 0.7rem; margin: 0; }
.datos .ancho { grid-column: 1 / -1; }
.datos dt { font-size: 0.72rem; text-transform: uppercase; letter-spacing: 0.04em; color: var(--muted); font-weight: 700; }
.datos dd { margin: 0.1rem 0 0; font-size: 0.95rem; }
.datos .precio { font-weight: 750; font-variant-numeric: tabular-nums; }

footer { display: flex; flex-direction: column; gap: 0.5rem; }
.motivo { margin: 0; font-size: 0.88rem; color: var(--muted); text-align: center; }
.grande { width: 100%; padding: 0.8rem; font-size: 1rem; }

/* En pantalla grande deja de ser una hoja y pasa a panel lateral. */
@media (min-width: 720px) {
  .velo { align-items: center; }
  .ficha { border-radius: var(--radio); max-height: 85vh; }
}

.hoja-enter-active, .hoja-leave-active { transition: opacity 0.18s ease; }
.hoja-enter-active .ficha, .hoja-leave-active .ficha { transition: transform 0.18s ease; }
.hoja-enter-from, .hoja-leave-to { opacity: 0; }
.hoja-enter-from .ficha, .hoja-leave-to .ficha { transform: translateY(1.5rem); }
@media (prefers-reduced-motion: reduce) {
  .hoja-enter-active, .hoja-leave-active,
  .hoja-enter-active .ficha, .hoja-leave-active .ficha { transition: none; }
}
</style>
