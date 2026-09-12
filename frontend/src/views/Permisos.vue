<script setup>
import { ref, computed, onMounted } from 'vue';
import { apiFetch } from '../api';
import { toast } from '../ui/toast';
import { alerta } from '../ui/alerta';
import { usePermisosStore } from '../stores/permisos';

/*
 * Que ve cada rol.
 *
 * Se pinta como MATRIZ —pantallas en filas, roles en columnas— y no como "elige un rol y
 * marca lo suyo", porque la pregunta que se hace quien administra casi siempre es comparativa:
 * "¿quien puede entrar a Credenciales?". Con una columna por rol eso se lee de un vistazo; rol
 * por rol habria que abrir seis pantallas y recordar.
 *
 * Se guarda por rol y solo los que cambiaron.
 */

const propio = usePermisosStore();

const cargando = ref(true);
const guardando = ref(false);
const catalogo = ref([]);          // { clave, titulo, grupo }
const roles = ref([]);             // { rolId, rol, sistema, loVeTodo, pantallas[] }
const marcadas = ref(new Map());   // rolId -> Set(clave)
const original = ref(new Map());

const grupos = computed(() => {
  const g = new Map();
  for (const p of catalogo.value) {
    if (!g.has(p.grupo)) g.set(p.grupo, []);
    g.get(p.grupo).push(p);
  }
  return [...g.entries()].map(([nombre, pantallas]) => ({ nombre, pantallas }));
});

async function cargar() {
  cargando.value = true;
  try {
    const [rc, rm] = await Promise.all([
      apiFetch('/api/app/permisos/catalogo'),
      apiFetch('/api/app/permisos'),
    ]);
    if (!rc.ok || !rm.ok) throw new Error('No se pudieron cargar los permisos');
    catalogo.value = await rc.json();
    roles.value = await rm.json();

    const m = new Map();
    const o = new Map();
    for (const r of roles.value) {
      m.set(r.rolId, new Set(r.pantallas));
      o.set(r.rolId, new Set(r.pantallas));
    }
    marcadas.value = m;
    original.value = o;
  } catch (e) {
    toast(e.message, 'error');
  } finally {
    cargando.value = false;
  }
}

const tiene = (rol, clave) => rol.loVeTodo || Boolean(marcadas.value.get(rol.rolId)?.has(clave));

function alternar(rol, clave) {
  if (rol.loVeTodo) return;
  const m = new Map(marcadas.value);
  const s = new Set(m.get(rol.rolId));
  s.has(clave) ? s.delete(clave) : s.add(clave);
  m.set(rol.rolId, s);
  marcadas.value = m;
}

/** Marca o desmarca un grupo entero para un rol: "CONTROL no ve nada de Venta". */
function alternarGrupo(rol, grupo) {
  if (rol.loVeTodo) return;
  const s = new Set(marcadas.value.get(rol.rolId));
  const todas = grupo.pantallas.every((p) => s.has(p.clave));
  for (const p of grupo.pantallas) todas ? s.delete(p.clave) : s.add(p.clave);
  const m = new Map(marcadas.value);
  m.set(rol.rolId, s);
  marcadas.value = m;
}

const cambiados = computed(() => roles.value.filter((r) => {
  if (r.loVeTodo) return false;
  const a = original.value.get(r.rolId) || new Set();
  const b = marcadas.value.get(r.rolId) || new Set();
  return a.size !== b.size || [...b].some((x) => !a.has(x));
}));

async function guardar() {
  if (guardando.value || !cambiados.value.length) return;
  guardando.value = true;
  try {
    for (const r of cambiados.value) {
      const res = await apiFetch(`/api/app/permisos/${r.rolId}`, {
        method: 'PUT',
        body: JSON.stringify({ rol: r.rol, pantallas: [...marcadas.value.get(r.rolId)] }),
      });
      const d = await res.json().catch(() => ({}));
      if (!res.ok || !d.ok) throw new Error(d.mensaje || `No se pudo guardar ${r.rol}`);
    }
    toast(`Permisos guardados (${cambiados.value.length} rol(es))`, 'ok');
    await cargar();
    // Si me cambie los permisos a mi mismo, el menu tiene que reflejarlo sin recargar.
    propio.limpiar();
    await propio.asegurar();
  } catch (e) {
    alerta(e.message, 'error', 0);
  } finally {
    guardando.value = false;
  }
}

function descartar() {
  const m = new Map();
  for (const [k, v] of original.value) m.set(k, new Set(v));
  marcadas.value = m;
}

onMounted(cargar);
</script>

<template>
  <div class="permisos">
    <p class="aviso">
      <strong>Esto decide qué se VE:</strong> el menú y a qué pantallas se entra. No sustituye a
      los permisos del servidor — quitar una casilla esconde el enlace, pero lo que impide de
      verdad una acción es la comprobación que hace el sistema al pedirla.
    </p>

    <div v-if="cargando" class="vacio">Cargando permisos…</div>

    <template v-else>
      <div class="tabla-scroll">
        <table class="matriz">
          <thead>
            <tr>
              <th class="esq">Pantalla</th>
              <th v-for="r in roles" :key="r.rolId" :class="{ todo: r.loVeTodo }">
                {{ r.rol }}
                <small v-if="r.loVeTodo">ve todo</small>
                <small v-else>{{ (marcadas.get(r.rolId) || { size: 0 }).size }} pantallas</small>
              </th>
            </tr>
          </thead>
          <tbody>
            <template v-for="g in grupos" :key="g.nombre">
              <tr class="grupo">
                <th>{{ g.nombre }}</th>
                <td v-for="r in roles" :key="r.rolId">
                  <button v-if="!r.loVeTodo" class="todos" title="Marcar o desmarcar el grupo"
                          @click="alternarGrupo(r, g)">⇅</button>
                </td>
              </tr>
              <tr v-for="p in g.pantallas" :key="p.clave">
                <th class="pantalla">{{ p.titulo }}</th>
                <td v-for="r in roles" :key="r.rolId">
                  <!-- El super usuario sale marcado y en gris: es mas honesto que enseñarlo
                       vacio o dejar que alguien crea que puede limitarlo. -->
                  <input type="checkbox" :checked="tiene(r, p.clave)" :disabled="r.loVeTodo"
                         :title="r.loVeTodo ? 'El SUPER USUARIO ve todo siempre' : `${r.rol} · ${p.titulo}`"
                         @change="alternar(r, p.clave)" />
                </td>
              </tr>
            </template>
          </tbody>
        </table>
      </div>

      <div class="pie">
        <span v-if="cambiados.length" class="pendiente">
          {{ cambiados.length }} rol{{ cambiados.length === 1 ? '' : 'es' }} sin guardar
        </span>
        <span class="crecer"></span>
        <button class="btn btn-fantasma" :disabled="!cambiados.length || guardando" @click="descartar">
          Descartar
        </button>
        <button class="btn btn-primario" :disabled="!cambiados.length || guardando" @click="guardar">
          {{ guardando ? 'Guardando…' : 'Guardar cambios' }}
        </button>
      </div>
    </template>
  </div>
</template>

<style scoped>
.permisos { display: flex; flex-direction: column; gap: 1rem; }
.aviso {
  margin: 0; padding: 0.7rem 0.9rem; border-radius: var(--radio-sm);
  background: color-mix(in srgb, var(--acento) 9%, var(--panel));
  border: 1px solid color-mix(in srgb, var(--acento) 35%, transparent);
  font-size: 0.86rem; line-height: 1.5;
}
.vacio { padding: 2.5rem; text-align: center; color: var(--muted); }

.tabla-scroll { overflow-x: auto; }
.matriz { border-collapse: collapse; width: 100%; font-size: 0.88rem; }
.matriz th, .matriz td { border-bottom: 1px solid var(--border); padding: 0.45rem 0.7rem; }
.matriz thead th {
  position: sticky; top: 0; background: var(--panel); text-align: center;
  font-size: 0.78rem; line-height: 1.3; white-space: nowrap;
}
.matriz thead th small { display: block; font-weight: 400; color: var(--muted); font-size: 0.68rem; }
.matriz thead th.todo { color: var(--muted); }
/* La primera columna se queda fija: con seis roles la tabla se desplaza, y sin esto no se
   sabe de que pantalla es la casilla que se esta marcando. */
.matriz .esq, .matriz tbody th {
  position: sticky; left: 0; background: var(--panel); text-align: left; z-index: 1;
}
.matriz .esq { z-index: 2; }
.matriz tbody td { text-align: center; }
.matriz input { width: 17px; height: 17px; cursor: pointer; }
.matriz input:disabled { cursor: default; opacity: 0.45; }

.grupo th {
  font-size: 0.72rem; text-transform: uppercase; letter-spacing: 0.05em;
  color: var(--muted); background: var(--panel-2) !important; font-weight: 700;
}
.grupo td { background: var(--panel-2); }
.todos {
  font: inherit; cursor: pointer; border: none; background: none;
  color: var(--muted); font-size: 0.9rem; line-height: 1; padding: 0 0.2rem;
}
.todos:hover { color: var(--acento); }
.pantalla { font-weight: 500; }

.pie { display: flex; align-items: center; gap: 0.6rem; }
.pendiente { font-size: 0.85rem; color: var(--tramite); font-weight: 700; }
</style>
