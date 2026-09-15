<script setup>
/**
 * Seguimiento en vivo: quien esta dentro ahora mismo y que esta haciendo.
 *
 * Se refresca sola cada pocos segundos. Es un sondeo y no un WebSocket a proposito: esta
 * pantalla la miran una o dos personas de administracion, mientras que un canal de difusion
 * obligaria a mandar un mensaje a TODOS los conectados cada vez que cualquiera cambia de
 * pantalla. Barato para quien mira, gratis para los treinta y cinco que venden.
 *
 * El dato que de verdad se busca aqui es "casetas en carrito": son ventas EMPEZADAS y sin
 * cerrar, con las casetas bloqueadas para los demas mientras tanto. Sale de la base —de las
 * casetas reservadas a su nombre—, no de lo que el cliente diga estar haciendo.
 *
 * No hay historial: al reiniciar el servidor la lista se vacia y se rehace sola en un minuto.
 */
import { ref, computed, onMounted, onUnmounted } from 'vue';
import { apiFetch } from '../api';
import { toast } from '../ui/toast';

const REFRESCO_MS = 5000;

const gente = ref([]);
const cargando = ref(true);
const error = ref('');
const actualizado = ref(0);
let temporizador = null;

const enLinea = computed(() => gente.value.filter((g) => g.enLinea));
const ausentes = computed(() => gente.value.filter((g) => !g.enLinea));
const vendiendo = computed(() => enLinea.value.filter((g) => g.registrando));

async function cargar() {
  try {
    const r = await apiFetch('/api/app/presencia');
    if (!r.ok) { error.value = 'No se pudo leer el seguimiento'; return; }
    gente.value = await r.json();
    error.value = '';
    actualizado.value = Date.now();
  } catch (e) {
    error.value = e.message;
  } finally {
    cargando.value = false;
  }
}

onMounted(() => {
  cargar();
  temporizador = setInterval(cargar, REFRESCO_MS);
});
onUnmounted(() => clearInterval(temporizador));

/** "hace 8 s", "hace 4 min". Un reloj absoluto obligaria a restar mentalmente. */
function hace(segundos) {
  const s = Number(segundos) || 0;
  if (s < 10) return 'ahora mismo';
  if (s < 60) return `hace ${Math.round(s)} s`;
  const m = Math.round(s / 60);
  if (m < 60) return `hace ${m} min`;
  return `hace ${Math.round(m / 60)} h`;
}

const iniciales = (n) => (n || '?').split(/\s+/).slice(0, 2).map((x) => x[0]).join('').toUpperCase();
</script>

<template>
  <p class="muted nota">
    Quién tiene la aplicación abierta en este momento y en qué pantalla está.
    <strong>Casetas en carrito</strong> son ventas empezadas y sin cerrar: esas casetas están
    bloqueadas para el resto de vendedores mientras dure la reserva.
  </p>

  <div class="tarjetas-resumen">
    <div class="card resumen"><strong>{{ enLinea.length }}</strong><span>en línea</span></div>
    <div class="card resumen"><strong>{{ vendiendo.length }}</strong><span>registrando una venta</span></div>
    <div class="card resumen"><strong>{{ ausentes.length }}</strong><span>sin actividad reciente</span></div>
  </div>

  <p v-if="error" class="card aviso-error">{{ error }}</p>
  <div v-else-if="cargando" class="vacio">Cargando…</div>
  <div v-else-if="!gente.length" class="vacio card">
    No hay nadie usando la aplicación en este momento.
  </div>

  <template v-else>
    <h3 class="titulo-seccion">En línea ({{ enLinea.length }})</h3>
    <p v-if="!enLinea.length" class="muted">Nadie con la aplicación abierta ahora mismo.</p>
    <ul v-else class="lista">
      <li v-for="g in enLinea" :key="g.usuarioId" class="card persona" :class="{ vendiendo: g.registrando }">
        <span class="avatar" :class="{ activo: true }">{{ iniciales(g.nombre || g.usuario) }}</span>
        <div class="datos">
          <div class="linea1">
            <strong>{{ g.nombre || g.usuario }}</strong>
            <span class="badge badge-muted">{{ g.rol }}</span>
            <span v-if="g.origen" class="badge badge-muted">{{ g.origen }}</span>
          </div>
          <div class="linea2">
            <span class="pantalla">{{ g.titulo || g.pantalla || 'sin pantalla' }}</span>
            <span class="muted">· {{ hace(g.inactivoSegundos) }}</span>
          </div>
          <!-- Las casetas, con su número: "3 casetas" no deja actuar; "7, 8 y 14" sí. -->
          <div v-if="g.registrando" class="linea3">
            <span class="badge badge-ok">registrando</span>
            <span>{{ g.casetasEnCarrito }} caseta{{ g.casetasEnCarrito === 1 ? '' : 's' }}
              en el carrito: {{ g.casetas.join(', ') }}</span>
          </div>
        </div>
      </li>
    </ul>

    <template v-if="ausentes.length">
      <h3 class="titulo-seccion">Sin actividad reciente ({{ ausentes.length }})</h3>
      <p class="muted chico">
        Estuvieron dentro hace poco y dejaron de dar señales: cerraron la aplicación, se quedaron
        sin señal o la tienen en segundo plano. Si alguno aparece con casetas en el carrito, esa
        venta quedó a medias.
      </p>
      <ul class="lista">
        <li v-for="g in ausentes" :key="g.usuarioId" class="card persona apagada">
          <span class="avatar">{{ iniciales(g.nombre || g.usuario) }}</span>
          <div class="datos">
            <div class="linea1">
              <strong>{{ g.nombre || g.usuario }}</strong>
              <span class="badge badge-muted">{{ g.rol }}</span>
            </div>
            <div class="linea2">
              <span class="pantalla">{{ g.titulo || g.pantalla || 'sin pantalla' }}</span>
              <span class="muted">· {{ hace(g.inactivoSegundos) }}</span>
            </div>
            <div v-if="g.registrando" class="linea3">
              <span class="badge badge-aviso">venta a medias</span>
              <span>{{ g.casetas.join(', ') }}</span>
            </div>
          </div>
        </li>
      </ul>
    </template>
  </template>
</template>

<style scoped>
.nota { margin: 0 0 1rem; line-height: 1.5; }
.chico { font-size: 0.85rem; }

.tarjetas-resumen { display: grid; grid-template-columns: repeat(3, 1fr); gap: 0.75rem; margin-bottom: 1.25rem; }
.resumen { padding: 0.8rem; display: flex; flex-direction: column; align-items: center; gap: 0.15rem; text-align: center; }
.resumen strong { font-size: 1.8rem; line-height: 1; }
.resumen span { font-size: 0.85rem; opacity: 0.75; }

.titulo-seccion { margin: 1.25rem 0 0.5rem; font-size: 1.05rem; }
.lista { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 0.6rem; }

.persona { display: flex; align-items: flex-start; gap: 0.75rem; padding: 0.75rem; }
.persona.vendiendo { outline: 1px solid var(--ok, #16a34a); }
.persona.apagada { opacity: 0.6; }

.avatar {
  flex: none; width: 40px; height: 40px; border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  font-weight: 700; font-size: 0.9rem;
  background: var(--panel-2, rgba(128,128,128,0.15));
}
/* El punto verde va en el avatar y no en una columna aparte: en 400 px no sobra una columna. */
.avatar.activo { box-shadow: 0 0 0 2px var(--ok, #16a34a); }

.datos { display: flex; flex-direction: column; gap: 0.2rem; min-width: 0; }
.linea1, .linea2, .linea3 { display: flex; align-items: center; gap: 0.4rem; flex-wrap: wrap; }
.pantalla { font-weight: 500; }
.aviso-error { padding: 0.8rem; }

@media (max-width: 480px) {
  .tarjetas-resumen { grid-template-columns: 1fr; }
  .resumen { flex-direction: row; justify-content: flex-start; gap: 0.5rem; }
  .resumen strong { font-size: 1.3rem; }
}
</style>
