<script setup>
import { ref, computed, onMounted } from 'vue';
import { apiFetch } from '../api';
import { useAuthStore } from '../stores/auth';
import { toast } from '../ui/toast';
import UiModal from '../components/UiModal.vue';
import { useAcademicoStore } from '../stores/academico';

const auth = useAuthStore();
const academico = useAcademicoStore();
const vendedores = ref([]);
const cargando = ref(false);
const seccion = ref('vendedores'); // vendedores | facultad

// Filtros de la tabla. El área es el que se pidió ("¿quiénes son los de ACYT?"); el texto
// acompaña porque una vez recortado a un área siguen siendo doce nombres.
const filtroArea = ref('');   // '' = todas · 'sin' = los que aún no tienen carrera
const filtroTexto = ref('');

/**
 * Los vendedores que se ven, tras el área y el texto.
 *
 * "Sin carrera" es una opción del filtro y no un olvido: al aplicar V35 NADIE tiene carrera
 * todavía, y sin esa opción la única forma de encontrar a quien falta por asignar sería
 * recorrer la lista entera a ojo.
 */
const vendedoresFiltrados = computed(() => {
  const q = filtroTexto.value.trim().toLowerCase();
  return vendedores.value.filter((v) => {
    if (filtroArea.value === 'sin' && v.areaId) return false;
    if (filtroArea.value && filtroArea.value !== 'sin' && String(v.areaId) !== filtroArea.value) return false;
    if (!q) return true;
    return [v.username, v.persona, v.carrera, v.areaSigla]
      .some((c) => (c || '').toLowerCase().includes(q));
  });
});

/** Cuántos vendedores hay en cada área, para poder decirlo en el propio filtro. */
const conteoPorArea = computed(() => {
  const m = new Map();
  for (const v of vendedores.value) {
    const clave = v.areaId ? String(v.areaId) : 'sin';
    m.set(clave, (m.get(clave) || 0) + 1);
  }
  return m;
});

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
const soloLibres = ref(false);      // ocultar las que ya lleva algun otro vendedor
const cargandoCatalogo = ref(false);
const guardando = ref(false);

// Asignación masiva por facultad/área: suma casetas a todos los vendedores del área.
const areaMasiva = ref('');
const busquedaMasiva = ref('');
const seleccionMasiva = ref(new Set());
const colapsadasMasiva = ref(new Set());
const rangosMasivos = ref({});
const soloLibresMasivo = ref(false);
const guardandoMasivo = ref(false);

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
      catalogo.value.filter((p) => laLleva(p, vendedorId)).map((p) => p.id),
    );
    originales.value = new Set(seleccion.value);
  } catch (e) {
    toast(e.message, 'error');
  } finally {
    cargandoCatalogo.value = false;
  }
}

/** ¿La lleva este vendedor? */
const laLleva = (p, vid) => (p.habilitados || []).some((h) => h.id === vid);

/** Los OTROS vendedores que ya la llevan. Vacio = la lleva solo este, o nadie. */
const otrosQueLlevan = (p, vid) => (p.habilitados || []).filter((h) => h.id !== vid);

/**
 * Las casetas que no lleva ningun otro vendedor.
 *
 * Es solo un filtro de pantalla para repartir territorios sin pisarse. NO es una restriccion:
 * una caseta que ya lleva otro se puede habilitar igual, y es lo normal cuando varios atienden
 * el mismo sector.
 */
const sinOtroVendedor = computed(() => {
  const vid = vendedorSel.value?.id;
  return catalogo.value.filter((p) => otrosQueLlevan(p, vid).length === 0);
});

/**
 * Agrupadas por categoria, que es como se piensan ("dame las PYMES de la 1 a la 20"), no como
 * una lista plana de 119 elementos donde hay que buscar a ojo.
 */
const grupos = computed(() => {
  const q = busquedaPuesto.value.trim().toLowerCase();
  const vid = vendedorSel.value?.id;
  const fuente = soloLibres.value ? sinOtroVendedor.value : catalogo.value;
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
    // Cuantas de esta categoria lleva ademas otro vendedor. Es informacion, no un impedimento:
    // se pueden habilitar igual. Sirve para ver de un vistazo donde se esta compartiendo.
    deOtros: catalogo.value.filter((p) => p.categoriaId === g.id && otrosQueLlevan(p, vid).length).length,
    // Vendidas: siguen siendo asignables a proposito (ver la nota del template), pero el
    // administrador tiene que saber cuantas de ese grupo ya no se pueden volver a vender.
    vendidas: g.casetas.filter((p) => p.estado === 'O').length,
  }));
});

const vendedoresDelAreaMasiva = computed(() => {
  if (!areaMasiva.value) return [];
  return vendedores.value.filter((v) => String(v.areaId) === areaMasiva.value);
});

const sinOtroVendedorMasivo = computed(() => catalogo.value.filter((p) => !(p.habilitados || []).length));

const gruposMasivos = computed(() => {
  const q = busquedaMasiva.value.trim().toLowerCase();
  const fuente = soloLibresMasivo.value ? sinOtroVendedorMasivo.value : catalogo.value;
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
    elegidas: g.casetas.filter((p) => seleccionMasiva.value.has(p.id)).length,
    compartidas: g.casetas.filter((p) => (p.habilitados || []).length).length,
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

function alternarColapsoMasivo(id) {
  const s = new Set(colapsadasMasiva.value);
  s.has(id) ? s.delete(id) : s.add(id);
  colapsadasMasiva.value = s;
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

  const vid = vendedorSel.value?.id;
  let tocadas = 0;
  let compartidas = 0;
  for (const p of grupo.casetas) {
    if (!pedidos.has(String(p.codigo).trim())) continue;
    if (marcar && otrosQueLlevan(p, vid).length) compartidas++;
    if (marcar) seleccion.value.add(p.id);
    else seleccion.value.delete(p.id);
    tocadas++;
  }
  seleccion.value = new Set(seleccion.value);
  rangos.value = { ...rangos.value, [grupo.id]: '' };

  if (!tocadas) { toast('Ninguna caseta con esos números en ' + grupo.nombre, 'error'); return; }
  // Se avisa de las compartidas sin impedirlas: es una decision deliberada del administrador,
  // pero conviene que sepa que esas casetas las lleva alguien mas.
  const cola = compartidas ? ` (${compartidas} las lleva también otro vendedor)` : '';
  toast(`${marcar ? 'Marcadas' : 'Desmarcadas'} ${tocadas} en ${grupo.nombre}${cola}`, 'ok');
}

async function prepararMasivo(forzar = false) {
  seccion.value = 'facultad';
  if (catalogo.value.length && !forzar) return;
  cargandoCatalogo.value = true;
  colapsadasMasiva.value = new Set();
  rangosMasivos.value = {};
  try {
    const r = await apiFetch('/api/app/vendedores/puestos-asignables');
    if (!r.ok) { toast('No se pudo cargar el catálogo de casetas', 'error'); return; }
    catalogo.value = await r.json();
  } catch (e) {
    toast(e.message, 'error');
  } finally {
    cargandoCatalogo.value = false;
  }
}

function alternarMasivo(p) {
  seleccionMasiva.value.has(p.id) ? seleccionMasiva.value.delete(p.id) : seleccionMasiva.value.add(p.id);
  seleccionMasiva.value = new Set(seleccionMasiva.value);
}

function marcarGrupoMasivo(grupo, marcar) {
  for (const p of grupo.casetas) {
    if (marcar) seleccionMasiva.value.add(p.id);
    else seleccionMasiva.value.delete(p.id);
  }
  seleccionMasiva.value = new Set(seleccionMasiva.value);
}

function aplicarRangoMasivo(grupo, marcar) {
  const texto = rangosMasivos.value[grupo.id];
  const pedidos = codigosDelRango(texto);
  if (!pedidos.size) { toast('Escribe un rango, por ejemplo 1-20', 'error'); return; }

  let tocadas = 0;
  for (const p of grupo.casetas) {
    if (!pedidos.has(String(p.codigo).trim())) continue;
    if (marcar) seleccionMasiva.value.add(p.id);
    else seleccionMasiva.value.delete(p.id);
    tocadas++;
  }
  seleccionMasiva.value = new Set(seleccionMasiva.value);
  rangosMasivos.value = { ...rangosMasivos.value, [grupo.id]: '' };
  toast(tocadas ? `${marcar ? 'Marcadas' : 'Desmarcadas'} ${tocadas} en ${grupo.nombre}` : 'Ninguna caseta con esos números', tocadas ? 'ok' : 'error');
}

async function guardarMasivo() {
  if (guardandoMasivo.value) return;
  if (!areaMasiva.value) { toast('Selecciona una facultad', 'error'); return; }
  if (!seleccionMasiva.value.size) { toast('Selecciona al menos una caseta', 'error'); return; }
  if (!confirm(`Se asignarán ${seleccionMasiva.value.size} caseta(s) a ${vendedoresDelAreaMasiva.value.length} vendedor(es) de esta facultad. ¿Continuar?`)) return;

  guardandoMasivo.value = true;
  try {
    const r = await apiFetch(`/api/app/vendedores/areas/${areaMasiva.value}/puestos`, {
      method: 'POST',
      body: JSON.stringify({ puestoIds: [...seleccionMasiva.value] }),
    });
    const d = await r.json().catch(() => ({}));
    if (!r.ok || !d.ok) { toast(d.mensaje || 'No se pudo guardar la asignación masiva', 'error'); return; }
    toast(`${d.mensaje}. Nuevas: ${d.asignacionesNuevas}`, 'ok');
    seleccionMasiva.value = new Set();
    await Promise.all([prepararMasivo(true), cargarVendedores()]);
  } catch (e) {
    toast(e.message, 'error');
  } finally {
    guardandoMasivo.value = false;
  }
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
    // Y la tabla de atrás, que es donde se lee el recuento por categoría que acaba de cambiar.
    await Promise.all([cargarCatalogo(vendedorSel.value.id), cargarVendedores()]);
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
  // El catálogo solo llena el desplegable del filtro: si falla, la tabla ya está pintada.
  academico.asegurar().catch((e) => toast(e.message, 'error'));
});

const esAdmin = computed(() => auth.puedeEditarPlano);
</script>

<template>
  <div class="vendedores-vista">
    <header class="cabecera-vista">
      <h1>Gestión de Vendedores</h1>
      <span class="muted">
        {{ vendedoresFiltrados.length }} de {{ vendedores.length }}
        vendedor{{ vendedores.length !== 1 ? 'es' : '' }}
      </span>
    </header>

    <div class="tabs-vendedores">
      <button class="tab-vendedor" :class="{ activo: seccion === 'vendedores' }" @click="seccion = 'vendedores'">
        <span>Por vendedor</span>
        <small>Asignación individual</small>
      </button>
      <button class="tab-vendedor" :class="{ activo: seccion === 'facultad' }" @click="prepararMasivo()">
        <span>Por facultad</span>
        <small>Asignar a todos</small>
      </button>
    </div>

    <template v-if="seccion === 'vendedores'">
      <div class="barra-filtros">
        <select v-model="filtroArea" class="control select-area">
          <option value="">Todas las áreas</option>
          <option v-for="a in academico.areas" :key="a.id" :value="String(a.id)">
            {{ a.sigla }} ({{ conteoPorArea.get(String(a.id)) || 0 }})
          </option>
          <option value="sin">Sin carrera ({{ conteoPorArea.get('sin') || 0 }})</option>
        </select>
        <input v-model="filtroTexto" class="control busca" placeholder="Buscar por usuario, nombre o carrera…" />
      </div>

      <div v-if="cargando" class="cargando">Cargando…</div>
      <div v-else-if="vendedores.length === 0" class="vacio">
        No hay vendedores registrados. Crea usuarios con rol <strong>ADMINISTRATIVO</strong> desde <router-link to="/usuarios">Usuarios</router-link>.
      </div>
      <div v-else-if="vendedoresFiltrados.length === 0" class="vacio">
        Ningún vendedor con ese filtro. La carrera se asigna en la ficha de la persona, desde
        <router-link to="/personas">Personas</router-link>.
      </div>
      <div v-else class="tabla-scroll">
        <table class="tabla">
          <thead>
            <tr>
              <th>Usuario</th>
              <th>Nombre</th>
              <th>Carrera</th>
              <th>Estado</th>
              <th>Categorías</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="v in vendedoresFiltrados" :key="v.id">
              <td>{{ v.username }}</td>
              <td>{{ v.persona || '—' }}</td>
              <td>
                <template v-if="v.carrera">
                  <span class="badge badge-info" :title="academico.etiquetaArea({ sigla: v.areaSigla, nombre: v.areaNombre })">{{ v.areaSigla }}</span>
                  <span class="carrera-nombre">{{ v.carrera }}</span>
                </template>
                <span v-else class="muted">Sin carrera</span>
              </td>
              <td>
                <span class="badge" :class="v.estado === 'ACTIVO' ? 'badge-ok' : v.estado === 'INACTIVO' ? 'badge-muted' : 'badge-danger'">
                  {{ v.estado }}
                </span>
              </td>
              <td>
                <div v-if="v.categorias?.length" class="lista-cats">
                  <span v-for="c in v.categorias" :key="c.categoriaId" class="chip-cat">
                    {{ c.categoria }} <strong>{{ c.cantidad }}</strong>
                  </span>
                  <span class="muted total-casetas">{{ v.totalPuestos }} en total</span>
                </div>
                <span v-else class="muted">Sin casetas habilitadas</span>
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
    </template>

    <section v-else class="card masivo">
      <div class="masivo-head">
        <div>
          <p class="eyebrow">Asignación masiva</p>
          <h2>Asignar casetas a una facultad</h2>
          <p class="muted">Las casetas seleccionadas se agregan a todos los vendedores de la facultad. No se quita nada de lo que ya tienen.</p>
        </div>
        <button class="btn btn-primario" :disabled="guardandoMasivo || !areaMasiva || !seleccionMasiva.size" @click="guardarMasivo">
          {{ guardandoMasivo ? 'Guardando…' : 'Asignar a todos' }}
        </button>
      </div>

      <div class="barra-filtros masivo-filtros">
        <select v-model="areaMasiva" class="control select-area">
          <option value="">Seleccionar facultad</option>
          <option v-for="a in academico.areas" :key="a.id" :value="String(a.id)">
            {{ a.sigla }} - {{ a.nombre }} ({{ conteoPorArea.get(String(a.id)) || 0 }} vendedores)
          </option>
        </select>
        <input v-model="busquedaMasiva" class="control busca" placeholder="Buscar por categoría o número…" />
        <label class="check-otros">
          <input type="checkbox" v-model="soloLibresMasivo" />
          Solo sin vendedor
        </label>
      </div>

      <div class="resumen-masivo">
        <div><span>Facultad</span><strong>{{ areaMasiva ? vendedoresDelAreaMasiva.length : 0 }} vendedores</strong></div>
        <div><span>Casetas seleccionadas</span><strong>{{ seleccionMasiva.size }}</strong></div>
        <div><span>Asignaciones posibles</span><strong>{{ seleccionMasiva.size * vendedoresDelAreaMasiva.length }}</strong></div>
      </div>

      <div v-if="cargandoCatalogo" class="vacio">Cargando casetas…</div>
      <div v-else-if="!gruposMasivos.length" class="vacio">No hay casetas que mostrar.</div>
      <div v-else class="grupos">
        <section v-for="g in gruposMasivos" :key="g.id" class="grupo">
          <header class="cab-grupo" @click="alternarColapsoMasivo(g.id)">
            <span class="flecha">{{ colapsadasMasiva.has(g.id) ? '▸' : '▾' }}</span>
            <strong>{{ g.nombre }}</strong>
            <span class="badge" :class="g.elegidas ? 'badge-ok' : 'badge-muted'">{{ g.elegidas }} / {{ g.casetas.length }}</span>
            <span v-if="g.vendidas" class="badge badge-vendida">{{ g.vendidas }} vendida{{ g.vendidas > 1 ? 's' : '' }}</span>
            <span v-if="g.compartidas" class="muted de-otros">{{ g.compartidas }} ya asignada{{ g.compartidas > 1 ? 's' : '' }}</span>
            <span class="crecer"></span>
            <span class="acciones-grupo" @click.stop>
              <input v-model="rangosMasivos[g.id]" class="control control-rango" placeholder="1-20" @keyup.enter="aplicarRangoMasivo(g, true)" />
              <button class="btn btn-sm btn-primario" title="Asignar ese rango" @click="aplicarRangoMasivo(g, true)">＋</button>
              <button class="btn btn-sm btn-fantasma" title="Quitar ese rango" @click="aplicarRangoMasivo(g, false)">−</button>
              <button class="btn btn-sm btn-fantasma" title="Todas" @click="marcarGrupoMasivo(g, true)">Todas</button>
              <button class="btn btn-sm btn-fantasma" title="Ninguna" @click="marcarGrupoMasivo(g, false)">Ninguna</button>
            </span>
          </header>

          <div v-if="!colapsadasMasiva.has(g.id)" class="rejilla">
            <button
              v-for="p in g.casetas"
              :key="p.id"
              type="button"
              class="caseta"
              :class="{ elegida: seleccionMasiva.has(p.id), compartida: (p.habilitados || []).length, vendida: p.estado === 'O' }"
              :title="(p.habilitados || []).length ? 'Ya la llevan: ' + p.habilitados.map((h) => h.username).join(', ') : (p.estado === 'O' ? 'Vendida' : 'Libre')"
              @click="alternarMasivo(p)">
              {{ p.codigo }}
              <span v-if="(p.habilitados || []).length" class="punto-compartida">•</span>
            </button>
          </div>
        </section>
      </div>
    </section>
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
          <input type="checkbox" v-model="soloLibres" />
          Ocultar las que ya lleva otro
        </label>
      </div>

      <p class="muted ayuda">
        Escribe un rango por categoría, por ejemplo <strong>1-20</strong> o <strong>3, 7, 12-15</strong>,
        y pulsa <strong>+</strong> para asignarlas o <strong>−</strong> para quitarlas.
        Una caseta puede llevarla <strong>más de un vendedor</strong>: la vende quien la reserve
        primero, y al resto les sale ocupada. Las <strong>vendidas</strong> también se habilitan:
        quien la vendió necesita seguir viéndola en su mapa.
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
            <span v-if="g.deOtros" class="muted de-otros">{{ g.deOtros }} compartida{{ g.deOtros > 1 ? 's' : '' }}</span>
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
                compartida: otrosQueLlevan(p, vendedorSel?.id).length,
                vendida: p.estado === 'O',
              }"
              :title="otrosQueLlevan(p, vendedorSel?.id).length
                ? 'La lleva también ' + otrosQueLlevan(p, vendedorSel?.id).map((h) => h.username).join(', ')
                : (p.estado === 'O' ? 'Vendida' : 'Libre')"
              @click="alternar(p)">
              {{ p.codigo }}
              <!-- El punto avisa de que esa caseta la lleva alguien mas. No la bloquea: solo
                   evita compartirla sin darse cuenta. -->
              <span v-if="otrosQueLlevan(p, vendedorSel?.id).length" class="punto-compartida">•</span>
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
.tabs-vendedores { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0.75rem; padding: 0.35rem; border: 1px solid var(--border); border-radius: var(--radio); background: linear-gradient(135deg, var(--panel-2), var(--panel)); box-shadow: var(--sombra); }
.tab-vendedor { border: 0; border-radius: calc(var(--radio) - 0.25rem); padding: 0.9rem 1rem; background: transparent; color: var(--texto); text-align: left; cursor: pointer; transition: 0.18s ease; }
.tab-vendedor span { display: block; font-weight: 900; }
.tab-vendedor small { display: block; margin-top: 0.2rem; color: var(--muted); font-weight: 700; }
.tab-vendedor:hover { background: color-mix(in srgb, var(--acento) 10%, transparent); }
.tab-vendedor.activo { background: linear-gradient(135deg, var(--acento), var(--acento-2)); color: #1d4ed8; box-shadow: 0 12px 28px color-mix(in srgb, var(--acento) 30%, transparent); }
.tab-vendedor.activo small { color: #1d4ed8; }
.barra-filtros { display: flex; gap: 0.6rem; flex-wrap: wrap; }
.select-area { min-width: 200px; }
.busca { flex: 1; min-width: 220px; }
.carrera-nombre { margin-left: 0.4rem; font-size: 0.85rem; }
.total-casetas { font-size: 0.75rem; align-self: center; }
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
/* Compartida: se puede elegir igual, asi que NADA de tacharla ni de bajarle la opacidad como
   cuando estaba prohibida. Solo un punto que avisa de que la lleva alguien mas. */
.caseta.compartida { position: relative; }
.caseta .punto-compartida {
  position: absolute; top: 1px; right: 3px;
  font-size: 1rem; line-height: 1; color: var(--aviso, #d08700);
}
.caseta.vendida:not(.elegida) { border-style: dashed; }

.resumen-cambios { margin-right: auto; font-size: 0.9rem; color: var(--muted); display: flex; gap: 0.5rem; align-items: center; }
.resumen-cambios strong { color: var(--ok); font-size: 1rem; }
.resumen-cambios .resta { color: var(--danger); }

.badge-vendida { background: color-mix(in srgb, var(--ocupado) 18%, transparent); color: var(--ocupado); }
.caseta.vendida:not(.elegida) { border-color: var(--ocupado); color: var(--ocupado); border-style: dashed; }
.caseta.vendida.elegida { background: var(--ocupado); border-color: var(--ocupado); color: #fff; }
.masivo { padding: 1.1rem; overflow: visible; }
.masivo-head { display: flex; justify-content: space-between; align-items: flex-start; gap: 1rem; margin-bottom: 1rem; }
.masivo-head h2 { margin: 0.15rem 0 0; }
.eyebrow { margin: 0; color: var(--acento); text-transform: uppercase; letter-spacing: 0.08em; font-size: 0.72rem; font-weight: 800; }
.masivo-filtros { align-items: center; margin-bottom: 0.9rem; }
.resumen-masivo { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 0.7rem; margin-bottom: 1rem; }
.resumen-masivo > div { padding: 0.85rem 1rem; border: 1px solid var(--border); border-radius: var(--radio-sm); background: linear-gradient(135deg, var(--panel), var(--panel-2)); }
.resumen-masivo span { display: block; color: var(--muted); font-size: 0.76rem; font-weight: 800; text-transform: uppercase; letter-spacing: 0.04em; }
.resumen-masivo strong { display: block; margin-top: 0.25rem; color: var(--acento); font-size: 1.1rem; }
@media (max-width: 720px) {
  .tabs-vendedores, .resumen-masivo { grid-template-columns: 1fr; }
  .masivo-head { flex-direction: column; }
  .masivo-head .btn { width: 100%; }
}
</style>
