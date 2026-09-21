<script setup>
/**
 * Pantallas por USUARIO (V49): las que se le dan a una persona concreta, además de las de su rol.
 *
 * Lo que ve un usuario = lo de su rol + lo suyo. Aquí las del rol salen marcadas y en gris
 * —se cambian en «Por rol»— y solo se marcan o desmarcan las propias. Así se habilita
 * «Inscripciones» a una persona sin cambiarle el rol ni abrírselo a todos los de su rol.
 *
 * En Inscripciones, Credenciales y Control de ventas, tener la pantalla también da acceso a sus
 * datos en el servidor; quien no es administración ve solo las ventas que registró él.
 */
import { ref, computed, onMounted } from 'vue';
import { apiFetch, jsonOError } from '../api';
import { toast } from '../ui/toast';
import { usePermisosStore } from '../stores/permisos';
import { useAuthStore } from '../stores/auth';

const props = defineProps({
  /** [{ clave, titulo, grupo }], el mismo catálogo que la matriz por rol. */
  catalogo: { type: Array, default: () => [] },
});

/* Pantallas donde darla también da acceso a los datos (recortados a lo propio). Espejo de las
   que usan @acceso.puede(...) en el servidor; en las demás, el servidor sigue pidiendo el rol. */
const CON_DATOS = ['inscripciones', 'credenciales', 'control-ventas'];

const propio = usePermisosStore();
const auth = useAuthStore();

const usuarios = ref([]);
const busqueda = ref('');
const elegidoId = ref(null);
const detalle = ref(null);        // { usuarioId, nombre, rol, loVeTodo, delRol[], propias[] }
const marcadas = ref(new Set());  // propias en edición
const cargando = ref(true);
const cargandoDetalle = ref(false);
const guardando = ref(false);

const grupos = computed(() => {
  const g = new Map();
  for (const p of props.catalogo) {
    if (!g.has(p.grupo)) g.set(p.grupo, []);
    g.get(p.grupo).push(p);
  }
  return [...g.entries()].map(([nombre, pantallas]) => ({ nombre, pantallas }));
});

const filtrados = computed(() => {
  const q = busqueda.value.trim().toLowerCase();
  const xs = q ? usuarios.value.filter((u) => `${u.nombre} ${u.username} ${u.rol}`.toLowerCase().includes(q))
    : usuarios.value;
  return xs.slice(0, 200);
});

async function cargarUsuarios() {
  cargando.value = true;
  try {
    usuarios.value = await jsonOError(await apiFetch('/api/app/permisos/usuarios'), 'No se pudieron cargar los usuarios');
  } catch (e) {
    toast(e.message, 'error');
  } finally {
    cargando.value = false;
  }
}

async function elegir(u) {
  elegidoId.value = u.usuarioId;
  cargandoDetalle.value = true;
  try {
    detalle.value = await jsonOError(await apiFetch(`/api/app/permisos/usuario/${u.usuarioId}`),
      'No se pudieron cargar los permisos del usuario');
    marcadas.value = new Set(detalle.value.propias);
  } catch (e) {
    detalle.value = null;
    toast(e.message, 'error');
  } finally {
    cargandoDetalle.value = false;
  }
}

const delRol = computed(() => new Set(detalle.value?.delRol || []));
const estado = (clave) => {
  if (detalle.value?.loVeTodo || delRol.value.has(clave)) return 'rol';
  return marcadas.value.has(clave) ? 'propia' : 'no';
};

function alternar(clave) {
  if (estado(clave) === 'rol') return;
  const s = new Set(marcadas.value);
  s.has(clave) ? s.delete(clave) : s.add(clave);
  marcadas.value = s;
}

const cambiado = computed(() => {
  if (!detalle.value) return false;
  const a = new Set(detalle.value.propias);
  const b = marcadas.value;
  return a.size !== b.size || [...b].some((x) => !a.has(x));
});

async function guardar() {
  if (!detalle.value || guardando.value) return;
  guardando.value = true;
  try {
    const r = await apiFetch(`/api/app/permisos/usuario/${detalle.value.usuarioId}`, {
      method: 'PUT', body: JSON.stringify({ pantallas: [...marcadas.value] }),
    });
    const d = await r.json().catch(() => ({}));
    if (!r.ok || !d.ok) throw new Error(d.mensaje || 'No se pudo guardar');
    toast(`Permisos de ${detalle.value.nombre} guardados`, 'ok');
    const u = usuarios.value.find((x) => x.usuarioId === detalle.value.usuarioId);
    if (u) u.propias = d.pantallas.length;
    detalle.value = { ...detalle.value, propias: d.pantallas };
    marcadas.value = new Set(d.pantallas);
    // Si me los cambié a mí mismo, el menú tiene que reflejarlo sin recargar.
    if (auth.id === detalle.value.usuarioId) {
      propio.limpiar();
      await propio.asegurar();
    }
  } catch (e) {
    toast(e.message, 'error');
  } finally {
    guardando.value = false;
  }
}

onMounted(cargarUsuarios);
</script>

<template>
  <div class="por-usuario">
    <aside class="card lista">
      <input v-model="busqueda" type="search" class="control" placeholder="Buscar usuario, nombre o rol…"
             aria-label="Buscar usuario" />
      <div v-if="cargando" class="vacio">Cargando…</div>
      <ul v-else>
        <li v-for="u in filtrados" :key="u.usuarioId">
          <button class="usuario" :class="{ activo: u.usuarioId === elegidoId }" @click="elegir(u)">
            <span class="nombre">{{ u.nombre }}</span>
            <span class="meta">
              {{ u.username }} · {{ u.rol || 'sin rol' }}
              <span v-if="u.propias" class="badge badge-ok">+{{ u.propias }}</span>
            </span>
          </button>
        </li>
        <li v-if="!filtrados.length" class="vacio">Ningún usuario coincide.</li>
      </ul>
    </aside>

    <section class="card detalle">
      <p v-if="!detalle && !cargandoDetalle" class="vacio">Elige un usuario para ver y cambiar sus pantallas.</p>
      <p v-else-if="cargandoDetalle" class="vacio">Cargando…</p>
      <template v-else>
        <header class="cab">
          <div>
            <h3>{{ detalle.nombre }}</h3>
            <p class="muted chico">{{ detalle.username }} · rol <strong>{{ detalle.rol || 'sin rol' }}</strong></p>
          </div>
          <div class="botones">
            <button class="btn btn-fantasma btn-sm" :disabled="!cambiado || guardando"
                    @click="marcadas = new Set(detalle.propias)">Descartar</button>
            <button class="btn btn-primario btn-sm" :disabled="!cambiado || guardando || detalle.loVeTodo" @click="guardar">
              {{ guardando ? 'Guardando…' : 'Guardar' }}
            </button>
          </div>
        </header>

        <p v-if="detalle.loVeTodo" class="nota">Es SUPER USUARIO: ve todas las pantallas siempre.</p>
        <p v-else class="nota chico">
          <span class="muestra rol"></span> por su rol (se cambia en «Por rol») ·
          <span class="muestra propia"></span> dada a este usuario ·
          <strong>★</strong> también da acceso a los datos: si no es administración, solo a <em>sus</em> ventas.
        </p>

        <div class="grupos">
          <fieldset v-for="g in grupos" :key="g.nombre" class="grupo">
            <legend>{{ g.nombre }}</legend>
            <label v-for="p in g.pantallas" :key="p.clave" class="casilla" :class="estado(p.clave)">
              <input type="checkbox" :checked="estado(p.clave) !== 'no'" :disabled="estado(p.clave) === 'rol'"
                     @change="alternar(p.clave)" />
              <span>{{ p.titulo }}<strong v-if="CON_DATOS.includes(p.clave)" title="Da acceso a los datos"> ★</strong></span>
              <small v-if="estado(p.clave) === 'rol' && !detalle.loVeTodo">por rol</small>
            </label>
          </fieldset>
        </div>
      </template>
    </section>
  </div>
</template>

<style scoped>
.por-usuario { display: grid; grid-template-columns: minmax(min(260px, 100%), 320px) minmax(0, 1fr); gap: 1rem; align-items: start; }
@media (max-width: 760px) { .por-usuario { grid-template-columns: 1fr; } }
.muted { color: var(--muted); }
.chico { font-size: 0.82rem; }
.vacio { padding: 1.5rem; text-align: center; color: var(--muted); }

.lista { padding: 0.7rem; display: flex; flex-direction: column; gap: 0.6rem; max-height: 70vh; min-width: 0; }
.lista ul { list-style: none; margin: 0; padding: 0; overflow-y: auto; display: flex; flex-direction: column; gap: 0.2rem; }
.usuario {
  width: 100%; text-align: left; font: inherit; cursor: pointer; background: transparent; color: var(--text);
  border: 1px solid transparent; border-radius: var(--radio-sm); padding: 0.45rem 0.6rem;
  display: flex; flex-direction: column; gap: 0.1rem;
}
.usuario:hover { background: var(--panel-2); }
.usuario.activo { border-color: var(--acento); background: var(--acento-suave); }
.usuario .nombre { font-weight: 600; font-size: 0.9rem; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.usuario .meta { font-size: 0.76rem; color: var(--muted); display: flex; gap: 0.3rem; align-items: center; flex-wrap: wrap; }

.detalle { padding: 1rem; display: flex; flex-direction: column; gap: 0.8rem; min-width: 0; }
.cab { display: flex; justify-content: space-between; align-items: flex-start; gap: 0.8rem; flex-wrap: wrap; }
.cab h3 { margin: 0; font-size: 1.05rem; }
.cab p { margin: 0.15rem 0 0; }
.botones { display: flex; gap: 0.4rem; }
.nota { margin: 0; line-height: 1.6; }
.muestra { display: inline-block; width: 0.8rem; height: 0.8rem; border-radius: 3px; vertical-align: -1px; }
.muestra.rol { background: color-mix(in srgb, var(--muted) 35%, transparent); }
.muestra.propia { background: var(--acento); }

.grupos { display: grid; gap: 0.8rem; grid-template-columns: repeat(auto-fit, minmax(min(230px, 100%), 1fr)); }
.grupo { border: 1px solid var(--border); border-radius: var(--radio-sm); padding: 0.5rem 0.7rem 0.7rem; margin: 0; min-width: 0; }
.grupo legend { font-size: 0.72rem; font-weight: 800; text-transform: uppercase; letter-spacing: 0.05em; color: var(--muted); padding: 0 0.3rem; }
.casilla { display: flex; align-items: center; gap: 0.45rem; padding: 0.25rem 0; font-size: 0.88rem; cursor: pointer; }
.casilla input { width: 16px; height: 16px; flex: none; }
.casilla small { margin-left: auto; color: var(--muted); font-size: 0.7rem; }
.casilla.rol { color: var(--muted); cursor: default; }
.casilla.propia span { color: var(--acento); font-weight: 600; }
</style>
