<script setup>
import { ref, computed, onMounted, onUnmounted, watch } from 'vue';
import { useRouter } from 'vue-router';
import { useAuthStore } from '../stores/auth.js';
import { usePuestosStore } from '../stores/puestos.js';
import { useNotificacionesStore } from '../stores/notificaciones.js';
import { toast } from '../ui/toast.js';
import NotificacionItem from '../components/NotificacionItem.vue';

const router = useRouter();
const auth = useAuthStore();
const tienda = usePuestosStore();
const notifStore = useNotificacionesStore();

const cargando = computed(() => notifStore.cargando);
const error = computed(() => notifStore.error);

let quitarOyente = null;

onMounted(async () => {
  tienda.asegurar();
  quitarOyente = tienda.registrarNotificaciones(onNotificacionWS);
  await notifStore.cargar();
});

onUnmounted(() => { if (quitarOyente) quitarOyente(); });

/** Llega notificación por WebSocket → store la aplica a la lista. */
function onNotificacionWS(n) {
  notifStore.aplicarWS(n);
  // Si la vista está visible, no hacemos toast (el usuario la ve en la lista).
  // Si está en otra pestaña, el badge se actualiza solo por countNoLeidas.
}

/** Navega a la venta/puesto si la notificación tiene referencia. */
function irARef(notif) {
  if (notif.inscripcionId) {
    router.push({ name: 'MisVentas', query: { inscripcion: notif.inscripcionId } });
  } else if (notif.puestoId) {
    router.push({ name: 'Mapa', query: { puesto: notif.puestoId } });
  }
}

const tieneNoLeidas = computed(() => notifStore.countNoLeidas > 0);
</script>

<template>
  <div class="notificaciones-vista">
    <header class="cabecera-vista">
      <h1>Notificaciones</h1>
      <span v-if="tieneNoLeidas" class="badge-total">{{ notifStore.countNoLeidas }}</span>
    </header>

    <!-- Filtros -->
    <div class="filtros">
      <select v-model="notifStore.filtroLeida" class="select-filtro" title="Filtrar por estado">
        <option value="todas">Todas</option>
        <option value="no-leidas">No leídas</option>
        <option value="leidas">Leídas</option>
      </select>
      <select v-model="notifStore.filtroTipo" class="select-filtro" title="Filtrar por tipo">
        <option value="">Todos los tipos</option>
        <option v-for="t in notifStore.tiposDisponibles" :key="t" :value="t">{{ t }}</option>
      </select>
      <button v-if="tieneNoLeidas" class="btn btn-fantasma btn-sm" @click="notifStore.marcarTodasLeidas">
        Marcar todas como leídas
      </button>
    </div>

    <!-- Lista -->
    <div class="lista">
      <div v-if="cargando" class="cargando">Cargando…</div>
      <div v-else-if="error" class="error">{{ error }}</div>
      <div v-else-if="notifStore.filtradas.length === 0" class="vacio">
        No hay notificaciones{{ notifStore.filtroLeida !== 'todas' || notifStore.filtroTipo ? ' con esos filtros' : '' }}.
      </div>
      <div v-else class="items">
        <NotificacionItem
          v-for="n in notifStore.filtradas"
          :key="n.id"
          :notificacion="n"
          :es-admin="auth.puedeEditarPlano"
          :usuario-id="auth.id"
          @leida="() => {}"
          @respuesta="() => {}"
          @resolver="() => {}"
        />
      </div>
    </div>

    <!-- Entrada oculta para pull-to-refresh en móvil (opcional) -->
    <div class="pull-hint" v-if="notifStore.filtradas.length">Desliza para actualizar</div>
  </div>
</template>

<style scoped>
.notificaciones-vista { display: flex; flex-direction: column; gap: 1rem; height: 100%; }
.cabecera-vista { display: flex; align-items: center; justify-content: space-between; padding: 0.5rem 0; }
.cabecera-vista h1 { margin: 0; font-size: 1.4rem; }
.badge-total {
  background: var(--acento); color: #fff; border-radius: 999px;
  padding: 0.1rem 0.6rem; font-size: 0.8rem; font-weight: 700;
}
.filtros { display: flex; flex-wrap: wrap; gap: 0.5rem; align-items: center; }
.select-filtro { border: 1px solid var(--border); border-radius: 8px; padding: 0.4rem 0.6rem; background: #fff; min-width: 140px; }
.lista { flex: 1; overflow-y: auto; padding-right: 0.5rem; }
.cargando, .error, .vacio { padding: 2rem; text-align: center; color: var(--muted); }
.error { color: var(--danger); }
.items { display: flex; flex-direction: column; gap: 0.6rem; }
.pull-hint { text-align: center; color: var(--muted); font-size: 0.8rem; padding: 1rem 0; }
</style>