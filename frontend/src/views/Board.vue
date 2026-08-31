<script setup>
import { ref, computed, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { apiFetch } from '../api';
import { useAuthStore } from '../stores/auth';
import { usePuestosStore } from '../stores/puestos';
import { toast } from '../ui/toast';

const auth = useAuthStore();
const router = useRouter();
// Casetas y tiempo real compartidos con el Mapa y el Editor (ver stores/puestos.js).
const tienda = usePuestosStore();
const puestos = computed(() => tienda.puestos);
const cargando = computed(() => tienda.cargando);
// Ids con peticion en vuelo: evita el doble clic sobre la misma caseta sin congelar
// el resto del tablero.
const enPeticion = ref(new Set());

const porCategoria = computed(() => {
  const map = new Map();
  for (const p of puestos.value) {
    const cat = p.categoria || 'Sin categoría';
    if (!map.has(cat)) map.set(cat, []);
    map.get(cat).push(p);
  }
  return Array.from(map, ([categoria, items]) => ({ categoria, items }));
});

const resumen = computed(() => {
  const c = { L: 0, T: 0, O: 0, X: 0 };
  for (const p of puestos.value) c[p.estado] = (c[p.estado] || 0) + 1;
  return c;
});

const clase = { L: 'libre', T: 'tramite', O: 'ocupado', X: 'bloqueado' };

/** ¿La reserva en tramite de esta caseta es de quien esta mirando? */
const esMia = (p) => p.estado === 'T' && p.reservadoPor != null && p.reservadoPor === auth.id;

async function clickPuesto(p) {
  // Igual que en el Mapa: solo se bloquea la caseta tocada, no el tablero entero.
  if (enPeticion.value.has(p.id)) return;

  // Una caseta en tramite de otro vendedor no se toca: intentarlo solo producia un
  // parpadeo (verde un instante) y un 409.
  if (p.estado === 'T' && !esMia(p)) {
    toast(`Caseta ${p.codigo}: la tiene reservada otro vendedor`, 'info');
    return;
  }
  const accion = p.estado === 'L' ? 'reservar' : esMia(p) ? 'liberar' : null;
  if (!accion) return; // ocupado o bloqueado: no se toca

  // Se pinta antes de salir a la red; si el servidor rechaza, se revierte.
  const estadoPrevio = p.estado;
  const reservadoPrevio = p.reservadoPor ?? null;
  tienda.aplicar(accion === 'reservar'
    ? { ...p, estado: 'T', reservadoPor: auth.id }
    : { ...p, estado: 'L', reservadoPor: null });
  enPeticion.value = new Set(enPeticion.value).add(p.id);

  try {
    const res = await apiFetch(`/api/app/puestos/${p.id}/${accion}`, { method: 'POST' });
    const data = await res.json().catch(() => ({}));
    if (res.ok) {
      toast(`Caseta ${p.codigo}: ${data.mensaje ?? 'Listo'}`, 'ok');
    } else {
      tienda.aplicar({ ...p, estado: estadoPrevio, reservadoPor: reservadoPrevio });
      toast(`Caseta ${p.codigo}: ${data.mensaje ?? 'No disponible'}`, 'error');
    }
  } catch (e) {
    tienda.aplicar({ ...p, estado: estadoPrevio, reservadoPor: reservadoPrevio });
    toast(e.message, 'error');
  } finally {
    const s = new Set(enPeticion.value);
    s.delete(p.id);
    enPeticion.value = s;
  }
}

/** El WebSocket rechazo el token: sin tiempo real el tablero miente, asi que salimos. */
function sesionCaducada() {
  auth.logout();
  router.push('/login');
}

// No se desconecta al desmontar: la conexion es compartida y la cierra AppLayout al
// salir de la sesion. Esta vista vive en <KeepAlive>, donde onUnmounted no se dispara
// al navegar — ahi estaba la fuga de conexiones.
onMounted(() => tienda.asegurar(sesionCaducada));
</script>

<template>
  <div class="board">
    <div class="legend">
      <span class="chip libre">Libre · {{ resumen.L }}</span>
      <span class="chip tramite">En trámite · {{ resumen.T }}</span>
      <span class="chip ocupado">Ocupado · {{ resumen.O }}</span>
      <span class="chip bloqueado">Bloqueado · {{ resumen.X }}</span>
    </div>

    <p v-if="cargando" class="muted">Cargando casetas…</p>

    <section v-for="grupo in porCategoria" :key="grupo.categoria" class="grupo">
      <h2>{{ grupo.categoria }} <span class="muted">({{ grupo.items.length }})</span></h2>
      <div class="grid">
        <!-- v-memo: cambiar una caseta no debe repintar las 520 del tablero. -->
        <button
          v-for="p in grupo.items"
          :key="p.id"
          v-memo="[p.estado, p.reservadoPor === auth.id, p.codigo, p.tamano]"
          class="celda"
          :class="[clase[p.estado], { mia: esMia(p) }]"
          :title="`Caseta ${p.codigo} · ${p.tamano} · ${p.estado}${esMia(p) ? ' (tuya)' : p.estado === 'T' ? ' (de otro vendedor)' : ''}`"
          @click="clickPuesto(p)"
        >{{ p.codigo }}</button>
      </div>
    </section>
  </div>
</template>

<style scoped>
.board { max-width: 1100px; margin: 0 auto; padding: 1rem; }
header {
  display: flex; justify-content: space-between; align-items: center;
  padding-bottom: 0.75rem; border-bottom: 1px solid var(--border); margin-bottom: 1rem;
}
.salir {
  border: 1px solid var(--border); background: #fff; border-radius: 8px; padding: 0.35rem 0.7rem; margin-left: 0.5rem;
}
.legend { display: flex; gap: 0.5rem; flex-wrap: wrap; margin-bottom: 0.75rem; }
.chip { padding: 0.25rem 0.6rem; border-radius: 999px; font-size: 0.8rem; font-weight: 600; }
.muted { color: var(--muted); font-weight: 400; }
.grupo { margin-bottom: 1.5rem; }
.grupo h2 { font-size: 1rem; margin: 0 0 0.5rem; }
.grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(52px, 1fr)); gap: 6px; }
.celda {
  aspect-ratio: 1; border: none; border-radius: 8px; color: #fff;
  font-size: 0.8rem; font-weight: 700; display: grid; place-items: center;
  transition: transform 0.08s ease, filter 0.15s ease;
}
.celda:hover { filter: brightness(1.08); transform: translateY(-1px); }
.celda.ocupado, .celda.bloqueado, .celda.tramite { cursor: default; }
/* La reserva propia se distingue de la ajena con un borde, no con otro color. */
.celda.tramite.mia { cursor: pointer; outline: 2px solid var(--text); outline-offset: -2px; }
</style>
