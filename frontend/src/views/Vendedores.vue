<script setup>
import { ref, computed, onMounted } from 'vue';
import { apiFetch } from '../api';
import { useAuthStore } from '../stores/auth';
import { toast } from '../ui/toast';
import UiModal from '../components/UiModal.vue';

const auth = useAuthStore();
const vendedores = ref([]);
const cargando = ref(false);

// Modales
const modalPuestos = ref(null);
const vendedorSel = ref(null);
const buscando = ref(false);


// Puestos: el modal trabaja sobre una copia en memoria y guarda una sola vez.
const busquedaPuesto = ref('');
const catalogo = ref([]);           // todas las casetas vivas, con categoria y dueño
const seleccion = ref(new Set());   // lo que quedara asignado a este vendedor
const originales = ref(new Set());  // lo que tenia al abrir, para saber que cambio
const colapsadas = ref(new Set());  // categorias plegadas
const rangos = ref({});             // texto del cuadro de rango, por categoria
const verDeOtros = ref(false);      // mostrar las casetas de otros vendedores, en gris
const cargandoCatalogo = ref(false);
const guardando = ref(false);

async function cargarVendedores() {
  cargando.value = true;
  try {
    const r = await apiFetch('/api/app/vendedores');
    if (r.ok) vendedores.value = await r.json();
  } catch (e) {
    toast(e.message, 'error');
  } finally {
    cargando.value = false;
  }
}















function abrirPuestos(v) {
  vendedorSel.value = v;
  cargarCatalogo(v.id);
  modalPuestos.value = true;
}

/**
 * Catalogo completo en UNA peticion: cada caseta con su categoria y su dueño actual.
 * Agrupar, filtrar y marcar se hace aqui, en memoria, para que tocar una casilla no cueste
 * un viaje al servidor.
 */
async function cargarCatalogo(vendedorId) {
  cargandoCatalogo.value = true;
  colapsadas.value = new Set();
  rangos.value = {};
  try {
    const r = await apiFetch('/api/app/vendedores/puestos-asignables');
    if (!r.ok) { toast('No se pudo cargar el catálogo de casetas', 'error'); return; }
    catalogo.value = await r.json();
    // La seleccion arranca en lo que ya tiene, y a partir de ahi se edita en local.
    seleccion.value = new Set(
      catalogo.value.filter((p) => p.asignadoAId === vendedorId).map((p) => p.id),
    );
    originales.value = new Set(seleccion.value);
  } catch (e) {
    toast(e.message, 'error');
  } finally {
    cargandoCatalogo.value = false;
  }
}

/** Las casetas que este vendedor puede tomar: las libres y las que ya son suyas. */
const disponiblesParaEste = computed(() => {
  const vid = vendedorSel.value?.id;
  return catalogo.value.filter((p) => !p.asignadoAId || p.asignadoAId === vid);
});

/**
 * Agrupadas por categoria, que es como se piensan ("dame las PYMES de la 1 a la 20"), no como
 * una lista plana de 119 elementos donde hay que buscar a ojo.
 */
const grupos = computed(() => {
  const q = busquedaPuesto.value.trim().toLowerCase();
  const vid = vendedorSel.value?.id;
  const fuente = verDeOtros.value ? catalogo.value : disponiblesParaEste.value;
  const porCategoria = new Map();

  for (const p of fuente) {
    if (q && !(`${p.categoria} ${p.codigo}`.toLowerCase().includes(q))) continue;
    if (!porCategoria.has(p.categoriaId)) {
      porCategoria.set(p.categoriaId, { id: p.categoriaId, nombre: p.categoria, casetas: [] });
    }
    porCategoria.get(p.categoriaId).casetas.push(p);
  }

  return [...porCategoria.values()].map((g) => ({
    ...g,
    elegidas: g.casetas.filter((p) => seleccion.value.has(p.id)).length,
    // Cuantas de esta categoria estan tomadas por otro, para poder decirlo sin listarlas.
    deOtros: catalogo.value.filter((p) => p.categoriaId === g.id && p.asignadoAId && p.asignadoAId !== vid).length,
    // Vendidas: siguen siendo asignables a proposito (ver la nota del template), pero el
    // administrador tiene que saber cuantas de ese grupo ya no se pueden volver a vender.
    vendidas: g.casetas.filter((p) => p.estado === 'O').length,
  }));
});

const hayCambios = computed(() =>
  seleccion.value.size !== originales.value.size ||
  [...seleccion.value].some((id) => !originales.value.has(id)));

const resumenCambios = computed(() => {
  const suma = [...seleccion.value].filter((id) => !originales.value.has(id)).length;
  const resta = [...originales.value].filter((id) => !seleccion.value.has(id)).length;
  return { suma, resta };
});

function alternar(p) {
  if (p.asignadoAId && p.asignadoAId !== vendedorSel.value?.id) return; // es de otro
  if (seleccion.value.has(p.id)) {
    // Quitarle una caseta YA VENDIDA es casi siempre un error: la venta es suya y dejaria de
    // verla en su mapa. No se prohibe (puede hacer falta al reorganizar territorios), pero se
    // avisa, porque desde aqui no se ve que esa caseta tiene una venta detras.
    if (p.estado === 'O' && !confirm(`La caseta ${p.codigo} está vendida. Si se la quitas, el vendedor dejará de verla en su mapa. ¿Quitarla igual?`)) return;
    seleccion.value.delete(p.id);
  } else seleccion.value.add(p.id);
  seleccion.value = new Set(seleccion.value); // dispara la reactividad
}

function marcarGrupo(grupo, marcar) {
  for (const p of grupo.casetas) {
    if (p.asignadoAId && p.asignadoAId !== vendedorSel.value?.id) continue;
    if (marcar) seleccion.value.add(p.id);
    else seleccion.value.delete(p.id);
  }
  seleccion.value = new Set(seleccion.value);
}

function alternarColapso(id) {
  const s = new Set(colapsadas.value);
  s.has(id) ? s.delete(id) : s.add(id);
  colapsadas.value = s;
}

/**
 * Interpreta lo que se escribe en el cuadro de rango: "1-20", "3,7,12", "1-5, 9, 20-24".
 * Devuelve el conjunto de codigos pedidos, como texto, para casar con `codigo` de la caseta.
 *
 * Se compara por CODIGO y no por posicion en la lista: el codigo es lo que el vendedor ve en
 * el plano y lo que dice en voz alta, y no tiene por que ser correlativo si se anularon casetas.
 */
function codigosDelRango(texto) {
  const pedidos = new Set();
  for (const parte of (texto || '').split(',')) {
    const t = parte.trim();
    if (!t) continue;
    const m = t.match(/^(\d+)\s*(?:-|a|al)\s*(\d+)$/i);
    if (m) {
      const desde = Number(m[1]);
      const hasta = Number(m[2]);
      for (let n = Math.min(desde, hasta); n <= Math.max(desde, hasta); n++) pedidos.add(String(n));
    } else {
      pedidos.add(t);
    }
  }
  return pedidos;
}

/** Aplica el rango escrito a una categoria: marca o desmarca de golpe. */
function aplicarRango(grupo, marcar) {
  const texto = rangos.value[grupo.id];
  const pedidos = codigosDelRango(texto);
  if (!pedidos.size) { toast('Escribe un rango, por ejemplo 1-20', 'error'); return; }

  let tocadas = 0;
  let deOtros = 0;
  for (const p of grupo.casetas) {
    if (!pedidos.has(String(p.codigo).trim())) continue;
    if (p.asignadoAId && p.asignadoAId !== vendedorSel.value?.id) { deOtros++; continue; }
    if (marcar) seleccion.value.add(p.id);
    else seleccion.value.delete(p.id);
    tocadas++;
  }
  seleccion.value = new Set(seleccion.value);
  rangos.value = { ...rangos.value, [grupo.id]: '' };

  if (!tocadas && !deOtros) { toast('Ninguna caseta con esos números en ' + grupo.nombre, 'error'); return; }
  const cola = deOtros ? ` (${deOtros} son de otro vendedor)` : '';
  toast(`${marcar ? 'Marcadas' : 'Desmarcadas'} ${tocadas} en ${grupo.nombre}${cola}`, 'ok');
}

/** Guarda la seleccion entera de una vez: dos consultas en el servidor, no una por casilla. */
async function guardarPuestos() {
  if (guardando.value) return;
  guardando.value = true;
  try {
    const r = await apiFetch(`/api/app/vendedores/${vendedorSel.value.id}/puestos`, {
      method: 'PUT',
      body: JSON.stringify({ puestoIds: [...seleccion.value] }),
    });
    const d = await r.json().catch(() => ({}));
    if (!r.ok || !d.ok) { toast(d.mensaje || 'No se pudo guardar', 'error'); return; }
    toast(d.mensaje, d.noDisponibles?.length ? 'error' : 'ok');
    await cargarCatalogo(vendedorSel.value.id);
  } catch (e) {
    toast(e.message, 'error');
  } finally {
    guardando.value = false;
  }
}

function descartarCambios() {
  seleccion.value = new Set(originales.value);
}

onMounted(async () => {
  await cargarVendedores();
});

const esAdmin = computed(() => auth.puedeEditarPlano);
</script>

<template>
  <div class="vendedores-vista">
    <header class="cabecera-vista">
      <h1>Gestión de Vendedores</h1>
      <span class="muted">{{ vendedores.length }} vendedor{{ vendedores.length !== 1 ? 'es' : '' }}</span>
    </header>

    <div v-if="cargando" class="cargando">Cargando…</div>
    <div v-else-if="vendedores.length === 0" class="vacio">
      No hay vendedores registrados. Crea usuarios con rol <strong>ADMINISTRATIVO</strong> desde <router-link to="/usuarios">Usuarios</router-link>.
    </div>
    <div v-else class="tabla-scroll">
      <table class="tabla">
        <thead>
          <tr>
            <th>Usuario</th>
            <th>Nombre</th>
            <th>Rol</th>
            <th>Estado</th>
            <th>Categorías</th>
            <th>Puestos asignados</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="v in vendedores" :key="v.id">
            <td>{{ v.username }}</td>
            <td>{{ v.persona ? (v.persona.nombre + ' ' + v.persona.paterno) : '—' }}</td>
            <td><span class="badge badge-info">{{ v.rol?.nombre }}</span></td>
            <td>
              <span class="badge" :class="v.estado === 'ACTIVO' ? 'badge-ok' : v.estado === 'INACTIVO' ? 'badge-muted' : 'badge-danger'">
                {{ v.estado }}
              </span>
            </td>
            <td>
              <span class="muted">—</span>
              <!-- TODO: mostrar conteo cuando el backend lo exponga -->
            </td>
            <td class="acciones">
              <button v-if="esAdmin" class="btn btn-fantasma btn-sm" @click="abrirPuestos(v)" title="Asignar puestos">
                🏪 Puestos
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>


  <!-- Modal Puestos: agrupado por categoría, con rangos y guardado en lote -->
  <UiModal
    v-if="modalPuestos"
    :titulo="'Casetas de ' + (vendedorSel?.username || '')"
    @cerrar="modalPuestos = false; busquedaPuesto = ''"
    ancho="900px">
    <div class="cuerpo-modal">
      <div class="barra-puestos">
        <input v-model="busquedaPuesto" class="control" placeholder="Buscar por categoría o número…" />
        <label class="check-otros">
          <input type="checkbox" v-model="verDeOtros" />
          Ver las de otros vendedores
        </label>
      </div>

      <p class="muted ayuda">
        Escribe un rango por categoría, por ejemplo <strong>1-20</strong> o <strong>3, 7, 12-15</strong>,
        y pulsa <strong>+</strong> para asignarlas o <strong>−</strong> para quitarlas.
        Las casetas de otro vendedor no se pueden tomar. Las <strong>vendidas</strong> sí se
        asignan: quien la vendió necesita seguir viéndola en su mapa.
      </p>

      <div v-if="cargandoCatalogo" class="vacio">Cargando casetas…</div>
      <div v-else-if="!grupos.length" class="vacio">No hay casetas que mostrar.</div>

      <div v-else class="grupos">
        <section v-for="g in grupos" :key="g.id" class="grupo">
          <header class="cab-grupo" @click="alternarColapso(g.id)">
            <span class="flecha">{{ colapsadas.has(g.id) ? '▸' : '▾' }}</span>
            <strong>{{ g.nombre }}</strong>
            <span class="badge" :class="g.elegidas ? 'badge-ok' : 'badge-muted'">
              {{ g.elegidas }} / {{ g.casetas.length }}
            </span>
            <span v-if="g.vendidas" class="badge badge-vendida">{{ g.vendidas }} vendida{{ g.vendidas > 1 ? 's' : '' }}</span>
            <span v-if="g.deOtros" class="muted de-otros">{{ g.deOtros }} de otros</span>
            <span class="crecer"></span>
            <span class="acciones-grupo" @click.stop>
              <input
                v-model="rangos[g.id]"
                class="control control-rango"
                placeholder="1-20"
                @keyup.enter="aplicarRango(g, true)" />
              <button class="btn btn-sm btn-primario" title="Asignar ese rango" @click="aplicarRango(g, true)">＋</button>
              <button class="btn btn-sm btn-fantasma" title="Quitar ese rango" @click="aplicarRango(g, false)">−</button>
              <button class="btn btn-sm btn-fantasma" title="Todas" @click="marcarGrupo(g, true)">Todas</button>
              <button class="btn btn-sm btn-fantasma" title="Ninguna" @click="marcarGrupo(g, false)">Ninguna</button>
            </span>
          </header>

          <div v-if="!colapsadas.has(g.id)" class="rejilla">
            <button
              v-for="p in g.casetas"
              :key="p.id"
              type="button"
              class="caseta"
              :class="{
                elegida: seleccion.has(p.id),
                ajena: p.asignadoAId && p.asignadoAId !== vendedorSel?.id,
                vendida: p.estado === 'O',
              }"
              :disabled="p.asignadoAId && p.asignadoAId !== vendedorSel?.id"
              :title="p.asignadoAId && p.asignadoAId !== vendedorSel?.id
                ? 'Asignada a ' + p.asignadoA
                : (p.estado === 'O' ? 'Vendida' : 'Libre')"
              @click="alternar(p)">
              {{ p.codigo }}
            </button>
          </div>
        </section>
      </div>
    </div>

    <template #pie>
      <span v-if="hayCambios" class="resumen-cambios">
        <strong v-if="resumenCambios.suma">+{{ resumenCambios.suma }}</strong>
        <strong v-if="resumenCambios.resta" class="resta">−{{ resumenCambios.resta }}</strong>
        sin guardar
      </span>
      <button class="btn btn-fantasma" :disabled="!hayCambios || guardando" @click="descartarCambios">Descartar</button>
      <button class="btn btn-fantasma" @click="modalPuestos = false">Cerrar</button>
      <button class="btn btn-primario" :disabled="!hayCambios || guardando" @click="guardarPuestos">
        {{ guardando ? 'Guardando…' : 'Guardar' }}
      </button>
    </template>
  </UiModal>
</template>

<style scoped>

.vendedores-vista { display: flex; flex-direction: column; gap: 1rem; }
.cabecera-vista { display: flex; align-items: center; justify-content: space-between; }
.cargando, .vacio { padding: 2rem; text-align: center; color: var(--muted); }
.tabla-scroll { overflow-x: auto; }
.tabla { width: 100%; border-collapse: collapse; font-size: 0.88rem; }
.tabla th, .tabla td { padding: 0.7rem 1rem; text-align: left; border-bottom: 1px solid var(--border); }
.tabla th { background: var(--panel); font-weight: 700; color: var(--muted); font-size: 0.75rem; text-transform: uppercase; letter-spacing: 0.05em; }
.tabla tr:hover td { background: var(--panel-2); }
.acciones { display: flex; gap: 0.35rem; }
.badge { padding: 0.15rem 0.5rem; border-radius: 999px; font-size: 0.68rem; font-weight: 700; }
.badge-info { background: var(--acento-suave); color: var(--acento); }
.badge-ok { background: var(--ok-suave); color: var(--ok); }
.badge-muted { background: var(--muted-suave); color: var(--muted); }
.badge-danger { background: var(--danger-suave); color: var(--danger); }
.chip-cat { display: inline-flex; align-items: center; gap: 0.2rem; background: var(--acento-suave); color: var(--acento); padding: 0.15rem 0.5rem; border-radius: 999px; font-size: 0.75rem; font-weight: 600; margin: 0.15rem; }
.lista-cats { display: flex; flex-wrap: wrap; gap: 0.3rem; }
.chip-cat-asignada { display: inline-flex; align-items: center; gap: 0.3rem; background: var(--ok-suave); color: var(--ok); padding: 0.2rem 0.5rem; border-radius: 999px; font-size: 0.8rem; font-weight: 600; }
.btn-quitar { background: none; border: none; color: var(--ok); cursor: pointer; padding: 0; font-size: 0.7rem; line-height: 1; }
.btn-quitar:hover { color: var(--danger); }
.seccion { margin-bottom: 1.2rem; padding-bottom: 1rem; border-bottom: 1px solid var(--border); }
.seccion:last-child { border-bottom: none; margin-bottom: 0; padding-bottom: 0; }
.seccion h4 { margin: 0 0 0.6rem; font-size: 0.9rem; }
.fila-select { display: flex; gap: 0.5rem; align-items: center; }
.select-cat { flex: 1; border: 1px solid var(--border); border-radius: 8px; padding: 0.4rem 0.6rem; background: #fff; }
.lista-puestos { max-height: 360px; overflow-y: auto; display: flex; flex-direction: column; gap: 0.4rem; padding-right: 0.5rem; }
.item-puesto { display: flex; align-items: center; justify-content: space-between; padding: 0.6rem 0.8rem; border: 1px solid var(--border); border-radius: 8px; background: var(--panel); transition: background 0.15s; }
.item-puesto:hover { background: var(--panel-2); }
.item-puesto.seleccionado { border-color: var(--ok); background: var(--ok-suave); }
.info-puesto { display: flex; flex-direction: column; gap: 0.15rem; min-width: 0; }
.info-puesto .muted { font-size: 0.75rem; }
.input-busqueda { width: 100%; border: 1px solid var(--border); border-radius: 8px; padding: 0.5rem 0.8rem; background: #fff; }
.fila-busqueda { margin-bottom: 0.8rem; }
.cuerpo-modal { max-height: 60vh; overflow-y: auto; }

/* ---- Modal de casetas: rejilla agrupada por categoria ---- */
.barra-puestos { display: flex; gap: 1rem; align-items: center; flex-wrap: wrap; }
.barra-puestos .control { flex: 1; min-width: 250px; padding: 0.6rem 0.8rem; }
.check-otros { display: flex; align-items: center; gap: 0.4rem; font-size: 0.9rem; color: var(--muted); white-space: nowrap; }
.ayuda { font-size: 0.85rem; margin: 1rem 0; line-height: 1.4; }

/* ELIMINADO EL MAX-HEIGHT Y OVERFLOW AQUÍ PARA EVITAR DOBLE SCROLL */
.cuerpo-modal { max-height: 75vh; overflow-y: auto; padding-right: 0.5rem; }
.grupos { display: flex; flex-direction: column; gap: 0.8rem; padding-bottom: 1rem; }

.grupo { border: 1px solid var(--border); border-radius: var(--radio-sm); overflow: hidden; }

/* Mejora de espaciado en la cabecera */
.cab-grupo {
  display: flex; align-items: center; gap: 0.8rem; padding: 0.8rem 1rem;
  background: var(--panel-2); cursor: pointer; flex-wrap: wrap;
}
.flecha { width: 1rem; color: var(--muted); font-weight: bold; }
.de-otros { font-size: 0.8rem; }

/* CLASE CLAVE QUE FALTABA PARA EMPUJAR LOS BOTONES A LA DERECHA */
.crecer { flex-grow: 1; min-width: 1rem; }

.acciones-grupo { display: flex; align-items: center; gap: 0.4rem; flex-wrap: wrap; cursor: default; }
.control-rango { width: 100px; padding: 0.4rem 0.5rem; font-size: 0.85rem; text-align: center; }

/* Cuadrícula más grande y con más espacio */
.rejilla {
  display: grid; 
  /* Aumentamos de 52px a 70px para que los números respiren */
  grid-template-columns: repeat(auto-fill, minmax(70px, 1fr));
  gap: 0.5rem; 
  padding: 1rem;
  background: var(--fondo);
}

.caseta {
  font: inherit; font-size: 0.9rem; font-weight: 700; cursor: pointer;
  padding: 0.6rem 0.2rem; border-radius: var(--radio-sm);
  border: 1px solid var(--border); background: var(--panel); color: var(--text);
  min-height: 45px; transition: all 0.15s ease;
}
.caseta:hover:not(:disabled) { border-color: var(--acento); transform: translateY(-1px); box-shadow: 0 2px 4px rgba(0,0,0,0.05); }
.caseta.elegida { background: var(--acento); border-color: var(--acento); color: var(--acento-texto); }
.caseta.ajena { opacity: 0.4; cursor: not-allowed; text-decoration: line-through; background: var(--panel-2); }
.caseta.vendida:not(.elegida) { border-style: dashed; }

.resumen-cambios { margin-right: auto; font-size: 0.9rem; color: var(--muted); display: flex; gap: 0.5rem; align-items: center; }
.resumen-cambios strong { color: var(--ok); font-size: 1rem; }
.resumen-cambios .resta { color: var(--danger); }

.badge-vendida { background: color-mix(in srgb, var(--ocupado) 18%, transparent); color: var(--ocupado); }
.caseta.vendida:not(.elegida) { border-color: var(--ocupado); color: var(--ocupado); border-style: dashed; }
.caseta.vendida.elegida { background: var(--ocupado); border-color: var(--ocupado); color: #fff; }
</style>