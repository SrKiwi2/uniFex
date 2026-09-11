<script setup>
import { ref, computed } from 'vue';
import { apiFetch } from '../api.js';
import { toast } from '../ui/toast.js';
import UiModal from './UiModal.vue';

const props = defineProps({
  notificacion: { type: Object, required: true },
  esAdmin: { type: Boolean, default: false },
  usuarioId: { type: Number, required: true },
});

const emit = defineEmits(['leida', 'respuesta', 'resolver']);

const expandida = ref(false);
const modalResponder = ref(false);
const modalResolver = ref(false);
const respuestaTexto = ref('');
const enviando = ref(false);

/** Formato de fecha legible. */
function formatearFecha(iso) {
  if (!iso) return '';
  const d = new Date(iso);
  return d.toLocaleString('es-BO', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' });
}

/** Clase CSS por tipo. */
function claseTipo(tipo) {
  const mapa = {
    SOLICITUD_CANCELACION: 'tipo-solicitud',
    APROBACION_CANCELACION: 'tipo-aprobada',
    RECHAZO_CANCELACION: 'tipo-rechazada',
    OBSERVACION_ADMIN: 'tipo-observacion',
    VENTA_REGISTRADA: 'tipo-venta',
    SISTEMA: 'tipo-sistema',
  };
  return mapa[tipo] || 'tipo-otro';
}

/** Etiqueta legible del tipo. */
function etiquetaTipo(tipo) {
  const mapa = {
    SOLICITUD_CANCELACION: 'Solicitud cancelación',
    APROBACION_CANCELACION: 'Aprobada',
    RECHAZO_CANCELACION: 'Rechazada',
    OBSERVACION_ADMIN: 'Observación',
    VENTA_REGISTRADA: 'Venta registrada',
    SISTEMA: 'Sistema',
  };
  return mapa[tipo] || tipo;
}

/** Ícono por tipo. */
function iconoTipo(tipo) {
  const mapa = {
    SOLICITUD_CANCELACION: '📋',
    APROBACION_CANCELACION: '✅',
    RECHAZO_CANCELACION: '❌',
    OBSERVACION_ADMIN: '💬',
    VENTA_REGISTRADA: '💰',
    SISTEMA: '⚙️',
  };
  return mapa[tipo] || '🔔';
}

/** Si es una observación admin → vendedor y está ABIERTA/RESPONDIDA. */
const esHiloAbierto = computed(() =>
  props.notificacion.tipo === 'OBSERVACION_ADMIN' &&
  ['ABIERTA', 'RESPONDIDA'].includes(props.notificacion.estadoHilo)
);

/** Si es admin y el hilo es RESPONDIDA → puede resolver. */
const puedeResolver = computed(() =>
  props.esAdmin &&
  props.notificacion.tipo === 'OBSERVACION_ADMIN' &&
  props.notificacion.estadoHilo === 'RESPONDIDA'
);

/** Si es vendedor y el hilo es ABIERTA → puede responder. */
const puedeResponder = computed(() =>
  !props.esAdmin &&
  props.notificacion.tipo === 'OBSERVACION_ADMIN' &&
  props.notificacion.estadoHilo === 'ABIERTA'
);

async function toggleExpandir() {
  expandida.value = !expandida.value;
  if (expandida.value && !props.notificacion.leida) {
    await marcarLeida();
  }
}

async function marcarLeida() {
  if (props.notificacion.leida) return;
  try {
    await apiFetch(`/api/app/notificaciones/${props.notificacion.id}/leer`, { method: 'POST' });
    props.notificacion.leida = true;
    props.notificacion.leidaEn = new Date().toISOString();
    emit('leida', props.notificacion.id);
  } catch (e) {
    toast(e.message, 'error');
  }
}

function abrirResponder() {
  respuestaTexto.value = '';
  modalResponder.value = true;
}

function abrirResolver() {
  respuestaTexto.value = '';
  modalResolver.value = true;
}

async function confirmarResponder() {
  const txt = respuestaTexto.value.trim();
  if (!txt) { toast('La respuesta es obligatoria', 'error'); return; }
  enviando.value = true;
  try {
    await apiFetch(`/api/app/notificaciones/${props.notificacion.id}/responder`, {
      method: 'POST',
      body: JSON.stringify({ respuesta: txt }),
    });
    modalResponder.value = false;
    toast('Respuesta enviada', 'ok');
    emit('respuesta', props.notificacion.id);
  } catch (e) {
    toast(e.message, 'error');
  } finally {
    enviando.value = false;
  }
}

async function confirmarResolver() {
  const txt = respuestaTexto.value.trim();
  if (!txt) { toast('La respuesta de cierre es obligatoria', 'error'); return; }
  enviando.value = true;
  try {
    await apiFetch(`/api/app/notificaciones/${props.notificacion.id}/resolver`, {
      method: 'POST',
      body: JSON.stringify({ respuesta: txt }),
    });
    modalResolver.value = false;
    toast('Hilo resuelto', 'ok');
    emit('resolver', props.notificacion.id);
  } catch (e) {
    toast(e.message, 'error');
  } finally {
    enviando.value = false;
  }
}
</script>

<template>
  <article class="notif-item" :class="[claseTipo(notificacion.tipo), { leida: notificacion.leida, expandida }]">
    <header class="cabecera" @click="toggleExpandir">
      <div class="icono-tipo">{{ iconoTipo(notificacion.tipo) }}</div>
      <div class="info">
        <div class="fila-principal">
          <strong class="asunto">{{ notificacion.asunto }}</strong>
          <span class="badge" :class="claseTipo(notificacion.tipo)">{{ etiquetaTipo(notificacion.tipo) }}</span>
        </div>
        <div class="meta">
          <span class="fecha">{{ formatearFecha(notificacion.fecha) }}</span>
          <span v-if="notificacion.inscripcionId" class="ref">Venta #{{ notificacion.inscripcionId }}</span>
          <span v-else-if="notificacion.puestoId" class="ref">Puesto #{{ notificacion.puestoId }}</span>
        </div>
      </div>
      <div class="acciones-cabecera">
        <span v-if="!notificacion.leida" class="punto-no-leida" title="No leída"></span>
        <span class="flecha" :class="{ rotada: expandida }">▼</span>
      </div>
    </header>

    <div class="cuerpo" v-show="expandida">
      <p class="cuerpo-texto">{{ notificacion.cuerpo }}</p>

      <!-- Hilo de observación -->
      <div v-if="notificacion.tipo === 'OBSERVACION_ADMIN'" class="hilo">
        <div v-if="notificacion.respuesta" class="respuesta">
          <div class="respuesta-cabecera">
            <span class="autor">
              {{ notificacion.respondidaPor ? `Respondido por ${notificacion.respondidaPor.nombre || 'admin'}` : 'Respondido' }}
            </span>
            <span class="fecha">{{ formatearFecha(notificacion.respondidaEn) }}</span>
          </div>
          <p class="respuesta-texto">{{ notificacion.respuesta }}</p>
        </div>

        <template v-if="esHiloAbierto">
          <div v-if="puedeResponder" class="responder-form">
            <label>Tu respuesta</label>
            <textarea v-model="respuestaTexto" rows="3" placeholder="Escribe tu respuesta..." maxlength="1000"></textarea>
            <div class="acciones-responder">
              <button class="btn btn-fantasma btn-sm" @click="modalResponder = false">Cancelar</button>
              <button class="btn btn-primario btn-sm" :disabled="enviando || !respuestaTexto.trim()" @click="confirmarResponder">
                {{ enviando ? 'Enviando…' : 'Enviar respuesta' }}
              </button>
            </div>
          </div>

          <div v-if="puedeResolver" class="resolver-form">
            <label>Respuesta de cierre (obligatoria)</label>
            <textarea v-model="respuestaTexto" rows="3" placeholder="Indica la resolución final..." maxlength="1000"></textarea>
            <div class="acciones-resolver">
              <button class="btn btn-fantasma btn-sm" @click="modalResolver = false">Cancelar</button>
              <button class="btn btn-peligro btn-sm" :disabled="enviando || !respuestaTexto.trim()" @click="confirmarResolver">
                {{ enviando ? 'Resolviendo…' : 'Marcar como resuelta' }}
              </button>
            </div>
          </div>
        </template>

        <div v-else-if="notificacion.estadoHilo === 'RESUELTA'" class="resuelta">
          <span class="badge badge-ok">✅ Resuelta</span>
          <p v-if="notificacion.respuesta">{{ notificacion.respuesta }}</p>
        </div>
      </div>
    </div>
  </article>
</template>

<style scoped>
.notif-item {
  border: 1px solid var(--border);
  border-radius: 10px;
  background: #fff;
  overflow: hidden;
  transition: box-shadow 0.15s, border-color 0.15s;
}
.notif-item:hover { box-shadow: var(--sombra-sm); }
.notif-item:not(.leida) {
  border-left: 3px solid var(--acento);
  background: color-mix(in srgb, var(--acento) 3%, #fff);
}
.notif-item.leida { opacity: 0.85; }

.cabecera {
  display: flex; align-items: flex-start; gap: 0.7rem;
  padding: 0.9rem 1rem; cursor: pointer;
}
.icono-tipo { font-size: 1.3rem; flex-shrink: 0; }
.info { flex: 1; min-width: 0; }
.fila-principal { display: flex; align-items: center; gap: 0.5rem; flex-wrap: wrap; }
.asunto { font-size: 0.95rem; line-height: 1.3; }
.badge {
  font-size: 0.68rem; font-weight: 700; padding: 0.1rem 0.45rem;
  border-radius: 999px; white-space: nowrap;
}
.meta { display: flex; gap: 0.6rem; margin-top: 0.25rem; font-size: 0.75rem; color: var(--muted); }
.fecha { white-space: nowrap; }
.ref { font-family: monospace; background: var(--panel); padding: 0.05rem 0.3rem; border-radius: 4px; }
.acciones-cabecera { display: flex; align-items: center; gap: 0.5rem; flex-shrink: 0; }
.punto-no-leida {
  width: 8px; height: 8px; border-radius: 50%;
  background: var(--acento); flex-shrink: 0; animation: pulso 1.5s infinite;
}
@keyframes pulso { 0%,100%{opacity:1} 50%{opacity:0.4} }
.flecha { font-size: 0.7rem; color: var(--muted); transition: transform 0.15s; }
.flecha.rotada { transform: rotate(180deg); }

.cuerpo {
  padding: 0 1rem 1rem; border-top: 1px solid var(--border);
  animation: abrir 0.15s ease;
}
@keyframes abrir { from { opacity: 0; transform: translateY(-4px); } to { opacity: 1; transform: none; } }
.cuerpo-texto { margin: 0.6rem 0; line-height: 1.5; white-space: pre-wrap; }

.hilo { margin-top: 0.8rem; padding-top: 0.8rem; border-top: 1px dashed var(--border); }
.respuesta { background: var(--panel); border-radius: 8px; padding: 0.7rem; margin-bottom: 0.8rem; }
.respuesta-cabecera { display: flex; justify-content: space-between; font-size: 0.75rem; color: var(--muted); margin-bottom: 0.3rem; }
.respuesta-texto { margin: 0; line-height: 1.5; }

.responder-form, .resolver-form { margin-top: 0.8rem; display: flex; flex-direction: column; gap: 0.4rem; }
.responder-form label, .resolver-form label { font-size: 0.8rem; font-weight: 600; }
.responder-form textarea, .resolver-form textarea {
  border: 1px solid var(--border); border-radius: 8px; padding: 0.5rem; font: inherit; resize: vertical;
}
.acciones-responder, .acciones-resolver { display: flex; justify-content: flex-end; gap: 0.5rem; }
.resuelta { color: var(--ok); font-weight: 600; font-size: 0.85rem; }

/* Colores de badge por tipo */
.tipo-solicitud .badge { background: var(--tramite-suave, #fff3cd); color: #8a6d1a; }
.tipo-aprobada .badge { background: var(--ok-suave, #d4edda); color: #1e6b34; }
.tipo-rechazada .badge { background: var(--danger-suave); color: var(--danger); }
.tipo-observacion .badge { background: var(--acento-suave); color: var(--acento); }
.tipo-venta .badge { background: var(--ok-suave); color: var(--ok); }
.tipo-sistema .badge { background: var(--muted-suave); color: var(--muted); }
.tipo-otro .badge { background: var(--panel); color: var(--texto); }
</style>