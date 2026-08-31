<script setup>
import { ref, reactive, computed, onMounted, onActivated, onDeactivated, onUnmounted } from 'vue';
import { useRouter } from 'vue-router';
import PanZoom from '../components/PanZoom.vue';
import { apiFetch } from '../api';
import { useAuthStore } from '../stores/auth';
import { usePuestosStore } from '../stores/puestos';
import { estiloPin } from '../mapa';

/*
 * Diseñador del plano. De 531 casetas solo unas pocas estan colocadas, asi que la herramienta
 * se optimiza para el trabajo por lotes: colocar una linea de N, seleccionar por caja, mover
 * y escalar el grupo entero. Colocarlas de una en una seria inviable.
 *
 * Los cambios de geometria se acumulan en memoria (con deshacer/rehacer) y se guardan de golpe.
 * Las altas y bajas de casetas, en cambio, viajan al servidor al instante: son irreversibles
 * desde aqui y no tiene sentido acumularlas.
 */

const FORMAS = [
  { id: 'cuadrado', icono: '■' },
  { id: 'circulo', icono: '●' },
  { id: 'triangulo', icono: '▲' },
];
const ESCALA_MIN = 0.2;
const ESCALA_MAX = 6;
const CLIC_MAXIMO = 0.008; // arrastre por debajo de esto = un toque, no una linea

// Casetas y tiempo real compartidos con el Mapa y el Tablero (ver stores/puestos.js).
// El editor NO tenia WebSocket: al centralizarlo lo gana, y ahora ve en vivo lo que
// venden los demas mientras se rediseña el plano.
const auth = useAuthStore();
const router = useRouter();
const tienda = usePuestosStore();
const puestos = computed(() => tienda.puestos);
const catSel = ref(null);
const modo = ref('mapa'); // mapa | colocar | seleccionar
const seleccion = ref(new Set());
const dirty = ref(new Set());
/** Devuelto por el store al registrar el guardia de cambios sin guardar; se llama al desmontar. */
let quitarGuardia = null;
const mensaje = ref('');
// Ocupado: una operacion de servidor por vez (guardar, crear, eliminar…).
const ocupado = ref(false);
/** `plano` es la IMAGEN: da el rectangulo de referencia para normalizar coordenadas.
 *  `lienzo` es el contenedor: es quien captura el puntero, porque la imagen lleva
 *  pointer-events:none y no puede recibir la captura. */
const plano = ref(null);
const lienzo = ref(null);
const porLinea = ref(8);
// Rejilla opcional: al activarla, la colocación, el arrastre, las flechas y el pegado
// se alinean a pasos de 2% del plano (PASO_REJILLA).
const rejilla = ref(false);
const PASO_REJILLA = 0.02;
const aRejilla = (v) => (rejilla.value ? Math.round(v / PASO_REJILLA) * PASO_REJILLA : v);
/** Casetas copiadas (geometría + categoría); pegar las duplica como casetas nuevas. */
const portapapeles = ref([]);
const vecesPegado = ref(0);
const nueva = reactive({
  abierto: false, nombre: '', cantidad: 10, color: '#2563eb', forma: 'cuadrado',
  // Precio de venta de cada caseta y su medida. Sin precio, la venta se registraria en 0.
  precioBase: 0, tamano: '3x3',
});

// ---- historial de deshacer/rehacer ----
const historial = ref([]);
const indice = ref(-1);
const puedeDeshacer = computed(() => indice.value >= 0);
const puedeRehacer = computed(() => indice.value < historial.value.length - 1);

const buscar = (id) => puestos.value.find((p) => p.id === id);
const geom = (p) => ({ id: p.id, mapaX: p.mapaX, mapaY: p.mapaY, mapaEscala: p.mapaEscala ?? 1 });

function marcarSucio(id) {
  const s = new Set(dirty.value);
  s.add(id);
  dirty.value = s;
}

function aplicarGeom(g) {
  const p = buscar(g.id);
  if (!p) return;
  p.mapaX = g.mapaX;
  p.mapaY = g.mapaY;
  p.mapaEscala = g.mapaEscala;
  marcarSucio(g.id);
}

/** Registra un paso reversible. `antes` se captura ANTES de mutar. */
function registrar(antes) {
  const despues = antes.map((g) => geom(buscar(g.id))).filter(Boolean);
  historial.value = historial.value.slice(0, indice.value + 1);
  historial.value.push({ antes, despues });
  indice.value = historial.value.length - 1;
}

/** Envuelve una mutacion sobre `ids` de modo que quede en el historial. */
function conHistoria(ids, mutar) {
  const antes = ids.map((id) => geom(buscar(id))).filter(Boolean);
  if (!antes.length) return;
  mutar();
  registrar(antes);
}

function deshacer() {
  if (!puedeDeshacer.value) return;
  historial.value[indice.value].antes.forEach(aplicarGeom);
  indice.value--;
  mensaje.value = 'Deshecho';
}
function rehacer() {
  if (!puedeRehacer.value) return;
  indice.value++;
  historial.value[indice.value].despues.forEach(aplicarGeom);
  mensaje.value = 'Rehecho';
}

// ---- categorias ----
const categorias = computed(() => {
  const m = new Map();
  for (const p of puestos.value) {
    if (!m.has(p.categoriaId))
      m.set(p.categoriaId, {
        id: p.categoriaId, nombre: p.categoria, color: p.color, forma: p.forma,
        tamanoMapa: p.tamanoMapa, precio: p.precio, total: 0, puestas: 0,
      });
    const c = m.get(p.categoriaId);
    c.total++;
    if (p.mapaX != null) c.puestas++;
  }
  return [...m.values()].sort((a, b) => b.total - a.total);
});
const activa = computed(() => categorias.value.find((c) => c.id === catSel.value));
const porColocar = computed(() =>
  puestos.value
    .filter((p) => p.categoriaId === catSel.value && p.mapaX == null)
    .sort((a, b) => (parseInt(a.codigo) || 0) - (parseInt(b.codigo) || 0)),
);
const colocadas = computed(() => puestos.value.filter((p) => p.mapaX != null));
const seleccionadas = computed(() => [...seleccion.value].map(buscar).filter(Boolean));

// ---- coordenadas ----
/** Convierte un evento de puntero a coordenada normalizada 0..1 del plano.
 *  getBoundingClientRect ya devuelve el rect transformado, asi que funciona bajo zoom. */
function norm(e) {
  const r = plano.value.getBoundingClientRect();
  return { x: (e.clientX - r.left) / r.width, y: (e.clientY - r.top) / r.height };
}

// ---- interaccion con el plano ----
// caja y linea NO son reactivos: sus estilos se pintan directo al DOM (pintarCaja/pintarGuia)
// y solo el encendido/apagado del overlay es reactivo. Asi un arrastre no re-renderiza el
// plano entero (500+ casetas) por cada pixel.
const caja = { activo: false, x0: 0, y0: 0, x1: 0, y1: 0 };
const linea = { activo: false, x0: 0, y0: 0, x1: 0, y1: 0 };
const cajaActiva = ref(false);
const lineaActiva = ref(false);
const cajaEl = ref(null);
const guiaEl = ref(null);
let arrastre = null; // { antes, x0, y0, movido, actual } al mover una seleccion

/** Nodos DOM de los pines: permiten moverlos en vivo sin pasar por Vue. */
const pinEls = new Map();

function pintarGuia() {
  const el = guiaEl.value;
  if (!el) return;
  el.style.left = `${linea.x0 * 100}%`;
  el.style.top = `${linea.y0 * 100}%`;
  el.style.width = `${Math.hypot(linea.x1 - linea.x0, linea.y1 - linea.y0) * 100}%`;
  el.style.transform = `rotate(${Math.atan2(linea.y1 - linea.y0, linea.x1 - linea.x0)}rad)`;
}

function pintarCaja() {
  const el = cajaEl.value;
  if (!el) return;
  el.style.left = `${Math.min(caja.x0, caja.x1) * 100}%`;
  el.style.top = `${Math.min(caja.y0, caja.y1) * 100}%`;
  el.style.width = `${Math.abs(caja.x1 - caja.x0) * 100}%`;
  el.style.height = `${Math.abs(caja.y1 - caja.y0) * 100}%`;
}

function onDown(e) {
  if (modo.value === 'mapa') return;
  lienzo.value.setPointerCapture(e.pointerId);
  const p = norm(e);

  if (modo.value === 'colocar') {
    if (catSel.value == null) { mensaje.value = 'Elige o crea una categoría.'; return; }
    linea.activo = true; lineaActiva.value = true;
    linea.x0 = linea.x1 = p.x;
    linea.y0 = linea.y1 = p.y;
    pintarGuia();
    return;
  }
  // seleccionar: arrastrar sobre el fondo dibuja una caja
  caja.activo = true; cajaActiva.value = true;
  caja.x0 = caja.x1 = p.x;
  caja.y0 = caja.y1 = p.y;
  pintarCaja();
}

function onPinDown(e, p) {
  if (modo.value !== 'seleccionar') return;
  e.stopPropagation();
  lienzo.value.setPointerCapture(e.pointerId);

  if (e.shiftKey || e.ctrlKey) alternar(p.id);
  else if (!seleccion.value.has(p.id)) seleccion.value = new Set([p.id]);

  const q = norm(e);
  arrastre = { antes: seleccionadas.value.map(geom), x0: q.x, y0: q.y, movido: false, actual: { dx: 0, dy: 0 } };
}

function onMove(e) {
  if (linea.activo) { const p = norm(e); linea.x1 = p.x; linea.y1 = p.y; pintarGuia(); return; }
  if (caja.activo) { const p = norm(e); caja.x1 = p.x; caja.y1 = p.y; pintarCaja(); return; }
  if (arrastre) {
    const q = norm(e);
    const dx = q.x - arrastre.x0;
    const dy = q.y - arrastre.y0;
    if (Math.abs(dx) + Math.abs(dy) > 0.0005) arrastre.movido = true;
    arrastre.actual = { dx, dy };
    // Movimiento en vivo SIN pasar por Vue: se escribe el estilo directo al DOM de cada pin.
    // El estado reactivo se confirma de una sola vez al soltar (onUp).
    for (const g of arrastre.antes) {
      const el = pinEls.get(g.id);
      if (!el) continue;
      el.style.left = `${Math.min(1, Math.max(0, aRejilla(g.mapaX + dx))) * 100}%`;
      el.style.top = `${Math.min(1, Math.max(0, aRejilla(g.mapaY + dy))) * 100}%`;
    }
  }
}

function onUp() {
  if (arrastre) {
    // Un arrastre entero es UN paso del historial, no uno por pixel. Al soltar se confirma
    // el estado reactivo (un solo re-render, y el DOM ya tiene los valores, asi que el
    // parche de Vue no vuelve a escribir) y se marca el lote completo como sucio.
    if (arrastre.movido) {
      const { dx, dy } = arrastre.actual;
      const sucios = new Set(dirty.value);
      for (const g of arrastre.antes) {
        const p = buscar(g.id);
        if (!p) continue;
        p.mapaX = Math.min(1, Math.max(0, aRejilla(g.mapaX + dx)));
        p.mapaY = Math.min(1, Math.max(0, aRejilla(g.mapaY + dy)));
        sucios.add(g.id);
      }
      dirty.value = sucios;
      registrar(arrastre.antes);
    }
    arrastre = null;
    return;
  }
  if (caja.activo) {
    caja.activo = false; cajaActiva.value = false;
    const x1 = Math.min(caja.x0, caja.x1), x2 = Math.max(caja.x0, caja.x1);
    const y1 = Math.min(caja.y0, caja.y1), y2 = Math.max(caja.y0, caja.y1);
    if (Math.abs(x2 - x1) < 0.002 && Math.abs(y2 - y1) < 0.002) { seleccion.value = new Set(); return; }
    const dentro = colocadas.value.filter((p) => p.mapaX >= x1 && p.mapaX <= x2 && p.mapaY >= y1 && p.mapaY <= y2);
    seleccion.value = new Set(dentro.map((p) => p.id));
    mensaje.value = `${dentro.length} caseta(s) seleccionadas`;
    return;
  }
  if (!linea.activo) return;
  linea.activo = false; lineaActiva.value = false;
  colocar();
}

/** Toque = coloca una caseta; arrastre = reparte N a lo largo de la linea. */
function colocar() {
  const disp = porColocar.value;
  if (!disp.length) { mensaje.value = 'No quedan casetas por colocar en esta categoría.'; return; }
  const dist = Math.hypot(linea.x1 - linea.x0, linea.y1 - linea.y0);

  if (dist < CLIC_MAXIMO) {
    conHistoria([disp[0].id], () => {
      const p = disp[0];
      p.mapaX = aRejilla(linea.x1); p.mapaY = aRejilla(linea.y1);
      marcarSucio(p.id);
    });
    mensaje.value = `Colocada ${activa.value?.nombre} ${disp[0].codigo}`;
    return;
  }
  const n = Math.min(porLinea.value, disp.length);
  const ids = disp.slice(0, n).map((p) => p.id);
  conHistoria(ids, () => {
    for (let i = 0; i < n; i++) {
      const t = n === 1 ? 0 : i / (n - 1);
      const p = disp[i];
      p.mapaX = aRejilla(linea.x0 + (linea.x1 - linea.x0) * t);
      p.mapaY = aRejilla(linea.y0 + (linea.y1 - linea.y0) * t);
      marcarSucio(p.id);
    }
  });
  mensaje.value = `Colocadas ${n} de ${activa.value?.nombre}`;
}

function alternar(id) {
  const s = new Set(seleccion.value);
  s.has(id) ? s.delete(id) : s.add(id);
  seleccion.value = s;
}

// ---- herramientas sobre la seleccion ----
function escalar(factor) {
  const ids = [...seleccion.value];
  if (!ids.length) { mensaje.value = 'Selecciona casetas primero.'; return; }
  conHistoria(ids, () => {
    for (const p of seleccionadas.value) {
      p.mapaEscala = Math.min(ESCALA_MAX, Math.max(ESCALA_MIN, (p.mapaEscala ?? 1) * factor));
      marcarSucio(p.id);
    }
  });
  mensaje.value = `${ids.length} caseta(s) ${factor > 1 ? 'agrandadas' : 'achicadas'}`;
}

function desplazar(dx, dy) {
  const ids = [...seleccion.value];
  if (!ids.length) return;
  conHistoria(ids, () => {
    for (const p of seleccionadas.value) {
      p.mapaX = Math.min(1, Math.max(0, aRejilla(p.mapaX + dx)));
      p.mapaY = Math.min(1, Math.max(0, aRejilla(p.mapaY + dy)));
      marcarSucio(p.id);
    }
  });
}

/** Quita del plano sin borrar: la caseta vuelve a la lista de "por colocar". */
function quitarDelMapa() {
  const ids = [...seleccion.value];
  if (!ids.length) { mensaje.value = 'Selecciona casetas primero.'; return; }
  conHistoria(ids, () => {
    for (const p of seleccionadas.value) {
      p.mapaX = null; p.mapaY = null;
      marcarSucio(p.id);
    }
  });
  seleccion.value = new Set();
  mensaje.value = `${ids.length} caseta(s) quitadas del plano (siguen existiendo)`;
}

/** Baja logica en el servidor. Irreversible desde aqui, y el backend la rechaza si tiene ventas. */
async function eliminarCasetas() {
  if (ocupado.value) return;
  const objetivo = seleccionadas.value;
  if (!objetivo.length) { mensaje.value = 'Selecciona casetas primero.'; return; }
  const rotulo = objetivo.map((p) => `${p.categoria} ${p.codigo}`).join(', ');
  if (!confirm(`¿Eliminar ${objetivo.length} caseta(s)?\n\n${rotulo}\n\nNo se puede deshacer. Las que tengan ventas serán rechazadas.`)) return;

  ocupado.value = true;
  try {
    let ok = 0;
    const rechazadas = [];
    for (const p of objetivo) {
      const r = await apiFetch(`/api/app/puestos/${p.id}`, { method: 'DELETE' });
      if (r.ok) ok++;
      else rechazadas.push(`${p.categoria} ${p.codigo}`);
    }
    seleccion.value = new Set();
    await cargar();
    mensaje.value = rechazadas.length
      ? `Eliminadas ${ok}. Rechazadas (vendidas o reservadas): ${rechazadas.join(', ')}`
      : `Eliminadas ${ok} caseta(s)`;
  } finally {
    ocupado.value = false;
  }
}

/** Bloquea las libres (X por reparación) y desbloquea las bloqueadas. Reversible. */
async function alternarBloqueo() {
  if (ocupado.value) return;
  const objetivo = seleccionadas.value.filter((p) => p.estado === 'L' || p.estado === 'X');
  if (!objetivo.length) { mensaje.value = 'Selecciona casetas libres o bloqueadas.'; return; }

  ocupado.value = true;
  try {
    let ok = 0;
    const rechazadas = [];
    for (const p of objetivo) {
      const accion = p.estado === 'L' ? 'bloquear' : 'desbloquear';
      const r = await apiFetch(`/api/app/puestos/${p.id}/${accion}`, { method: 'POST' });
      if (r.ok) ok++;
      else rechazadas.push(`${p.categoria} ${p.codigo}`);
    }
    await cargar();
    mensaje.value = rechazadas.length
      ? `Bloqueadas/desbloqueadas ${ok}. Rechazadas: ${rechazadas.join(', ')}`
      : `Bloqueadas/desbloqueadas ${ok} caseta(s)`;
  } finally {
    ocupado.value = false;
  }
}

/** Copia la geometría (y categoría) de las seleccionadas; no toca el servidor. */
function copiarSeleccion() {
  const sel = seleccionadas.value.filter((p) => p.mapaX != null);
  if (!sel.length) { mensaje.value = 'Selecciona casetas colocadas primero.'; return; }
  portapapeles.value = sel.map((p) => ({
    categoriaId: p.categoriaId, tamano: p.tamano || '3x3',
    mapaX: p.mapaX, mapaY: p.mapaY, mapaEscala: p.mapaEscala ?? 1,
  }));
  vecesPegado.value = 0;
  mensaje.value = `${portapapeles.value.length} caseta(s) copiadas. Pega con Ctrl+V.`;
}

/**
 * Duplica lo copiado como casetas NUEVAS (alta inmediata en el servidor, igual que
 * "agregar caseta") y las coloca desplazadas. Cada pegado corre 3% más respecto al
 * anterior, para no superponerse; con rejilla activa el desplazamiento se alinea.
 */
async function pegar() {
  if (ocupado.value) return;
  if (!portapapeles.value.length) { mensaje.value = 'Primero copia casetas (Ctrl+C).'; return; }
  ocupado.value = true;
  vecesPegado.value++;
  try {
    const despl = aRejilla(0.03) * vecesPegado.value;
    const usadosPorCat = new Map();
    const posiciones = [];
    for (const c of portapapeles.value) {
      let usados = usadosPorCat.get(c.categoriaId);
      if (!usados) {
        usados = new Set(puestos.value.filter((p) => p.categoriaId === c.categoriaId).map((p) => parseInt(p.codigo) || 0));
        usadosPorCat.set(c.categoriaId, usados);
      }
      let codigo = 1;
      while (usados.has(codigo)) codigo++;
      usados.add(codigo);
      const r = await apiFetch('/api/app/puestos', {
        method: 'POST',
        body: JSON.stringify({ categoriaId: c.categoriaId, codigo: String(codigo), tamano: c.tamano }),
      });
      const d = await r.json();
      if (!d.ok) continue;
      posiciones.push({
        id: d.id,
        x: Math.min(1, Math.max(0, aRejilla(c.mapaX) + despl)),
        y: Math.min(1, Math.max(0, aRejilla(c.mapaY) + despl)),
        escala: c.mapaEscala,
      });
    }
    if (!posiciones.length) { mensaje.value = 'No se pudo pegar: el servidor rechazó las altas.'; return; }
    await apiFetch('/api/app/puestos/posiciones', { method: 'POST', body: JSON.stringify(posiciones) });
    await cargar();
    seleccion.value = new Set(posiciones.map((p) => p.id));
    mensaje.value = `Pegadas ${posiciones.length} caseta(s).`;
  } catch (e) {
    mensaje.value = e.message;
  } finally {
    ocupado.value = false;
  }
}

/** Añade una caseta suelta a la categoría activa; queda sin colocar. */
async function agregarCaseta() {
  if (ocupado.value) return;
  if (!activa.value) { mensaje.value = 'Elige una categoría.'; return; }
  ocupado.value = true;
  try {
    const usados = puestos.value.filter((p) => p.categoriaId === catSel.value).map((p) => parseInt(p.codigo) || 0);
    const codigo = String(Math.max(0, ...usados) + 1);
    const r = await apiFetch('/api/app/puestos', {
      method: 'POST',
      body: JSON.stringify({ categoriaId: catSel.value, codigo, tamano: '3x3' }),
    });
    const d = await r.json();
    if (!d.ok) { mensaje.value = d.mensaje || 'No se pudo crear'; return; }
    await cargar();
    modo.value = 'colocar';
    mensaje.value = `Caseta ${activa.value.nombre} ${codigo} creada. Tócala en el plano para colocarla.`;
  } finally {
    ocupado.value = false;
  }
}

/** Renombra la categoría activa. */
async function renombrar(nombre) {
  const limpio = (nombre || '').trim();
  if (!activa.value || !limpio || limpio === activa.value.nombre) return;
  await editarActiva('nombre', limpio);
  mensaje.value = `Renombrada a "${limpio}"`;
}

/**
 * Lleva la categoría a la cantidad indicada. El backend crea o anula según haga falta,
 * pero nunca sacrifica una caseta vendida o reservada: si al reducir quedan de esas, lo avisa.
 */
async function aplicarCantidad(valor) {
  if (ocupado.value) return;
  if (!activa.value) return;
  const n = parseInt(valor, 10);
  if (isNaN(n) || n < 0) { mensaje.value = 'Cantidad inválida.'; return; }
  if (n === activa.value.total) return;
  ocupado.value = true;
  try {
    const r = await apiFetch(`/api/app/categorias/${activa.value.id}/cantidad`, {
      method: 'PATCH', body: JSON.stringify({ cantidad: n }),
    });
    const d = await r.json();
    if (!d.ok) { mensaje.value = d.mensaje || 'No se pudo ajustar la cantidad.'; return; }
    await cargar();
    const partes = [];
    if (d.creadas) partes.push(`${d.creadas} creada(s)`);
    if (d.anuladas) partes.push(`${d.anuladas} eliminada(s)`);
    if (d.noQuitadas) partes.push(`${d.noQuitadas} no se pudieron quitar (vendidas o reservadas)`);
    mensaje.value = partes.length ? partes.join(', ') : 'Sin cambios';
  } finally {
    ocupado.value = false;
  }
}

/** Elimina la categoría entera. El servidor la rechaza si alguna caseta tiene ventas. */
async function eliminarCategoria() {
  if (ocupado.value) return;
  if (!activa.value) return;
  const c = activa.value;
  if (!confirm(`¿Eliminar la categoría "${c.nombre}" y sus ${c.total} caseta(s)?\n\nNo se puede deshacer. Si alguna tiene ventas o reservas, se rechazará por completo.`)) return;
  ocupado.value = true;
  try {
    const r = await apiFetch(`/api/app/categorias/${c.id}`, { method: 'DELETE' });
    const d = await r.json();
    if (!d.ok) { mensaje.value = d.mensaje || 'No se pudo eliminar.'; return; }
    catSel.value = null;
    await cargar();
    mensaje.value = `Categoría "${c.nombre}" eliminada (${d.eliminadas} caseta(s))`;
  } finally {
    ocupado.value = false;
  }
}

// ---- guardado ----
async function guardar() {
  if (ocupado.value) return;
  const pos = [...dirty.value].map((id) => {
    const p = buscar(id);
    return { id, x: p.mapaX, y: p.mapaY, escala: p.mapaEscala ?? 1 };
  });
  ocupado.value = true;
  try {
    const r = await apiFetch('/api/app/puestos/posiciones', { method: 'POST', body: JSON.stringify(pos) });
    const d = await r.json();
    mensaje.value = `Guardadas ${d.guardadas} casetas ✔`;
    dirty.value = new Set();
    historial.value = [];
    indice.value = -1;
  } catch (e) {
    mensaje.value = e.message;
  } finally {
    ocupado.value = false;
  }
}

// ---- apariencia de la categoria (afecta a todas sus casetas) ----
async function editarActiva(campo, valor) {
  if (ocupado.value) return;
  if (!activa.value) return;
  ocupado.value = true;
  try {
    const r = await apiFetch(`/api/app/categorias/${activa.value.id}`, {
      method: 'PATCH', body: JSON.stringify({ [campo]: valor }),
    });
    if (!r.ok) { mensaje.value = 'No se pudo actualizar la categoría'; return; }
    for (const p of puestos.value) if (p.categoriaId === activa.value.id) p[campo] = valor;
  } finally {
    ocupado.value = false;
  }
}

/**
 * Cambia el precio de la categoria. Va aparte de `editarActiva` porque el campo no se
 * llama igual a los dos lados: en la categoria es `precioBase` y en cada caseta llega
 * como `precio`. Cambiarlo NO altera las ventas ya hechas: el costo se congela en la
 * inscripcion al vender.
 */
// ---- fotos y referencia de las casetas seleccionadas ----
const entradaFoto = ref(null);

/**
 * Sube UNA foto y la asocia a TODAS las casetas seleccionadas.
 *
 * El lote no es un extra: una feria tiene filas de casetas identicas, y subir la misma
 * imagen de una en una para 40 casetas no lo hace nadie. El archivo se guarda una sola vez
 * en el servidor; las casetas comparten la ruta.
 */
async function subirFoto(evento) {
  const archivo = evento.target.files?.[0];
  evento.target.value = '';
  if (!archivo) return;
  const ids = [...seleccion.value];
  if (!ids.length) { mensaje.value = 'Selecciona casetas primero.'; return; }

  ocupado.value = true;
  try {
    const datos = new FormData();
    datos.append('archivo', archivo);
    for (const id of ids) datos.append('puestos', id);
    const r = await apiFetch('/api/app/puestos/fotos', { method: 'POST', body: datos });
    const d = await r.json().catch(() => ({}));
    mensaje.value = d.mensaje || (r.ok ? 'Foto agregada' : 'No se pudo subir la foto');
  } catch (e) {
    mensaje.value = e.message;
  } finally {
    ocupado.value = false;
  }
}

/** Guarda la referencia de ubicación en todas las casetas seleccionadas. */
async function guardarReferencia(texto) {
  const ids = [...seleccion.value];
  if (!ids.length) return;
  ocupado.value = true;
  try {
    for (const id of ids) {
      await apiFetch(`/api/app/puestos/${id}/referencia`, {
        method: 'PATCH', body: JSON.stringify({ referencia: texto }),
      });
      const p = buscar(id);
      if (p) p.referencia = texto;
    }
    mensaje.value = `Ubicación guardada en ${ids.length} caseta(s)`;
  } catch (e) {
    mensaje.value = e.message;
  } finally {
    ocupado.value = false;
  }
}

async function cambiarPrecio(valor) {
  if (ocupado.value || !activa.value) return;
  const precio = Number(valor);
  if (!Number.isFinite(precio) || precio < 0) { mensaje.value = 'Precio inválido.'; return; }
  ocupado.value = true;
  try {
    const r = await apiFetch(`/api/app/categorias/${activa.value.id}`, {
      method: 'PATCH', body: JSON.stringify({ precioBase: precio }),
    });
    if (!r.ok) { mensaje.value = 'No se pudo cambiar el precio'; return; }
    for (const p of puestos.value) if (p.categoriaId === activa.value.id) p.precio = precio;
    mensaje.value = `Precio de "${activa.value.nombre}": ${precio} Bs`;
  } catch (e) {
    mensaje.value = e.message;
  } finally {
    ocupado.value = false;
  }
}

async function crearCategoria() {
  if (ocupado.value) return;
  if (!nueva.nombre.trim()) { mensaje.value = 'Ponle un nombre a la categoría.'; return; }
  ocupado.value = true;
  try {
    const r = await apiFetch('/api/app/categorias', {
      method: 'POST',
      body: JSON.stringify({
        nombre: nueva.nombre, cantidad: nueva.cantidad, color: nueva.color, forma: nueva.forma,
        precioBase: nueva.precioBase, tamano: nueva.tamano,
      }),
    });
    const d = await r.json();
    if (!d.ok) { mensaje.value = d.mensaje || 'Error'; return; }
    await cargar();
    catSel.value = d.id;
    modo.value = 'colocar';
    nueva.abierto = false;
    mensaje.value = `Categoría "${nueva.nombre}" creada (${nueva.cantidad} casetas a ${nueva.precioBase} Bs). Modo Colocar activo.`;
    nueva.nombre = '';
  } catch (e) {
    mensaje.value = e.message;
  } finally {
    ocupado.value = false;
  }
}

// ---- teclado ----
function onTecla(e) {
  if (e.target.tagName === 'INPUT') return;
  const paso = e.shiftKey ? 0.005 : 0.001;
  if (e.ctrlKey && e.key.toLowerCase() === 'z') { e.preventDefault(); e.shiftKey ? rehacer() : deshacer(); }
  else if (e.ctrlKey && e.key.toLowerCase() === 'y') { e.preventDefault(); rehacer(); }
  else if (e.ctrlKey && e.key.toLowerCase() === 'c') { e.preventDefault(); copiarSeleccion(); }
  else if (e.ctrlKey && e.key.toLowerCase() === 'v') { e.preventDefault(); pegar(); }
  else if (e.key === 'Escape') seleccion.value = new Set();
  else if (e.key === 'ArrowLeft') { e.preventDefault(); desplazar(-paso, 0); }
  else if (e.key === 'ArrowRight') { e.preventDefault(); desplazar(paso, 0); }
  else if (e.key === 'ArrowUp') { e.preventDefault(); desplazar(0, -paso); }
  else if (e.key === 'ArrowDown') { e.preventDefault(); desplazar(0, paso); }
}

async function cargar() {
  await tienda.recargar();
  if (catSel.value == null && categorias.value.length) catSel.value = categorias.value[0].id;
}

/** El WebSocket rechazo el token: sin tiempo real el plano miente, asi que salimos. */
function sesionCaducada() {
  auth.logout();
  router.push('/login');
}

onMounted(async () => {
  // Un broadcast trae la geometria que hay en el servidor. Si el usuario ya movio esa
  // caseta y aun no ha guardado, aceptar el mensaje entero le desharia el arrastre; el
  // guardia hace que se acepte el estado de venta pero se conserve la posicion local.
  quitarGuardia = tienda.protegerLocales((id) => dirty.value.has(id));
  await tienda.asegurar(sesionCaducada);
  if (catSel.value == null && categorias.value.length) catSel.value = categorias.value[0].id;
});
// El editor vive en KeepAlive (AppLayout): el teclado solo debe escuchar mientras
// la vista esta visible, no mientras esta en cache y el usuario esta en otro modulo.
onActivated(() => window.addEventListener('keydown', onTecla));
onDeactivated(() => window.removeEventListener('keydown', onTecla));
onUnmounted(() => {
  window.removeEventListener('keydown', onTecla);
  if (quitarGuardia) quitarGuardia();
});
</script>

<template>
  <div class="toolbar">
      <div class="grupo">
        <button :class="{ on: modo === 'mapa' }" @click="modo = 'mapa'" title="Navegar el plano">🖐 Mapa</button>
        <button :class="{ on: modo === 'colocar' }" @click="modo = 'colocar'" title="Tocar coloca; arrastrar coloca una línea">➕ Colocar</button>
        <button :class="{ on: modo === 'seleccionar' }" @click="modo = 'seleccionar'" title="Arrastrar selecciona; arrastrar un pin lo mueve">⬚ Seleccionar</button>
      </div>

      <label v-if="modo === 'colocar'" class="campo">
        Por línea <input type="number" min="1" max="60" v-model.number="porLinea" />
      </label>
      <label class="campo" title="Alinea la colocación, el arrastre y el pegado a pasos de 2%">
        <input type="checkbox" v-model="rejilla" /> ⧉ Rejilla
      </label>

      <div v-if="modo === 'seleccionar'" class="grupo">
        <span class="cuenta">{{ seleccion.size }} sel.</span>
        <button :disabled="ocupado || !seleccion.size" @click="copiarSeleccion" title="Copiar geometría (Ctrl+C)">📄 Copiar</button>
        <button :disabled="ocupado || !portapapeles.length" @click="pegar" title="Duplicar como casetas nuevas (Ctrl+V)">📋 Pegar</button>
        <button :disabled="ocupado || !seleccion.size" @click="escalar(1.15)" title="Agrandar">🔍+</button>
        <button :disabled="ocupado || !seleccion.size" @click="escalar(1 / 1.15)" title="Achicar">🔍−</button>
        <button :disabled="ocupado || !seleccion.size" @click="quitarDelMapa" title="Quitar del plano, sin borrar">⏏ Quitar</button>
        <button :disabled="ocupado || !seleccion.size" @click="alternarBloqueo" title="Bloquea las libres (reparación) y desbloquea las bloqueadas">🔒 Bloquear</button>
        <button :disabled="ocupado || !seleccion.size" class="peligro" @click="eliminarCasetas" title="Baja definitiva">🗑 Eliminar</button>
      </div>

      <!-- Documentar la caseta: lo que el vendedor le enseñará al cliente desde el mapa.
           Ambas acciones se aplican a TODA la selección: en una feria hay filas enteras de
           casetas iguales y hacerlo de una en una no lo haría nadie. -->
      <div class="grupo">
        <input ref="entradaFoto" type="file" accept="image/*" class="oculto" @change="subirFoto" />
        <button :disabled="ocupado || !seleccion.size" @click="entradaFoto?.click()"
                title="Sube una foto y la asigna a todas las casetas seleccionadas">📷 Foto</button>
        <input class="referencia" type="text" placeholder="Ubicación (ej. frente a puerta 3)"
               :disabled="ocupado || !seleccion.size"
               @change="guardarReferencia($event.target.value)"
               @keyup.enter="$event.target.blur()"
               title="Se guarda en todas las casetas seleccionadas" />
      </div>

      <div class="grupo">
        <button :disabled="!puedeDeshacer" @click="deshacer" title="Deshacer (Ctrl+Z)">↶</button>
        <button :disabled="!puedeRehacer" @click="rehacer" title="Rehacer (Ctrl+Shift+Z)">↷</button>
      </div>

      <div v-if="activa" class="grupo apariencia">
        <span class="et">{{ activa.nombre }}</span>
        <input type="color" :value="activa.color || '#2563eb'" title="Color"
               @change="editarActiva('color', $event.target.value)" />
        <button v-for="f in FORMAS" :key="f.id" class="forma"
                :class="{ on: (activa.forma || 'cuadrado') === f.id }"
                @click="editarActiva('forma', f.id)">{{ f.icono }}</button>
        <label class="campo" title="Tamaño base de toda la categoría">
          Tamaño
          <input type="range" min="0.004" max="0.05" step="0.001"
                 :value="activa.tamanoMapa ?? 0.012"
                 @change="editarActiva('tamanoMapa', Number($event.target.value))" />
        </label>
      </div>

      <button class="guardar" :disabled="ocupado || dirty.size === 0" @click="guardar">
        {{ ocupado ? 'Guardando…' : `Guardar (${dirty.size})` }}
      </button>
    </div>

    <p v-if="mensaje" class="msg">{{ mensaje }}</p>

    <div class="cuerpo">
      <aside>
        <button class="nueva-btn" @click="nueva.abierto = !nueva.abierto">＋ Nueva categoría</button>
        <div v-if="nueva.abierto" class="form">
          <input v-model="nueva.nombre" placeholder="Nombre" />
          <label>Cantidad <input type="number" min="1" v-model.number="nueva.cantidad" /></label>
          <label title="Precio de venta de cada caseta de esta categoría">
            Precio (Bs) <input type="number" min="0" step="1" v-model.number="nueva.precioBase" />
          </label>
          <label title="Medida que se le enseña al cliente, p. ej. 3x3">
            Medida <input v-model="nueva.tamano" placeholder="3x3" />
          </label>
          <label>Color <input type="color" v-model="nueva.color" /></label>
          <div class="formas">
            <button v-for="f in FORMAS" :key="f.id" :class="{ on: nueva.forma === f.id }" @click="nueva.forma = f.id">{{ f.icono }}</button>
          </div>
          <button class="crear" :disabled="ocupado" @click="crearCategoria">
            {{ ocupado ? 'Creando…' : 'Crear' }}
          </button>
        </div>

        <div v-if="activa" class="gestion" :key="activa.id">
          <label class="campo-col">
            Nombre
            <input :value="activa.nombre" @change="renombrar($event.target.value)"
                   @keyup.enter="$event.target.blur()" />
          </label>
          <label class="campo-col">
            Precio por caseta (Bs)
            <input type="number" min="0" step="1" :value="activa.precio"
                   @change="cambiarPrecio($event.target.value)"
                   @keyup.enter="$event.target.blur()" />
          </label>
          <p v-if="!(activa.precio > 0)" class="aviso-precio">
            Sin precio: estas casetas se venderían en 0 Bs.
          </p>
          <label class="campo-col">
            Cantidad de casetas
            <input type="number" min="0" :value="activa.total"
                   @change="aplicarCantidad($event.target.value)"
                   @keyup.enter="$event.target.blur()" />
          </label>
          <p class="hint">{{ activa.puestas }} colocadas de {{ activa.total }}. Subir crea; bajar quita las libres.</p>
          <div class="acciones">
            <button class="sec" :disabled="ocupado" @click="agregarCaseta">＋ Una caseta</button>
            <button class="peligro" :disabled="ocupado" @click="eliminarCategoria">🗑 Eliminar categoría</button>
          </div>
        </div>

        <ul>
          <li v-for="c in categorias" :key="c.id">
            <button :class="{ sel: catSel === c.id }" @click="catSel = c.id">
              <span class="sw" :style="{ background: c.color || '#94a3b8' }"></span>
              <span class="nom">{{ c.nombre }}</span>
              <span class="num">{{ c.puestas }}/{{ c.total }}</span>
            </button>
          </li>
        </ul>
      </aside>

      <PanZoom :selectMode="modo !== 'mapa'" :focus="{ x: 0.44, y: 0.34, scale: 2.4 }">
        <div class="plano" ref="lienzo" :class="`modo-${modo}`"
             @pointerdown="onDown" @pointermove="onMove" @pointerup="onUp" @pointercancel="onUp">
          <!-- width/height intrinsecos: reservan la proporcion antes de descargar,
               para que las casetas no se desplacen mientras carga el plano. -->
          <img
            ref="plano"
            src="/mapa.png"
            alt="Plano FEXPO UAP"
            width="1836"
            height="2376"
            decoding="async"
            draggable="false"
          />

          <div v-for="p in colocadas" :key="p.id"
               :ref="(el) => (el ? pinEls.set(p.id, el) : pinEls.delete(p.id))"
               class="pin" :class="[`forma-${p.forma || 'cuadrado'}`, { sel: seleccion.has(p.id) }]"
               :style="{ ...estiloPin(p), background: p.color || '#94a3b8' }"
               :title="`${p.categoria} ${p.codigo}`"
               @pointerdown="onPinDown($event, p)"></div>

          <div v-if="lineaActiva" ref="guia" class="guia"></div>

          <div v-if="cajaActiva" ref="caja" class="caja"></div>
        </div>
      </PanZoom>
    </div>
</template>

<style scoped>
.toolbar {
  display: flex; align-items: center; gap: 0.75rem; flex-wrap: wrap;
  padding: 0.6rem 1rem; border-bottom: 1px solid var(--border); background: var(--panel);
}
.grupo { display: flex; align-items: center; gap: 0.3rem; }
.toolbar button {
  border: 1px solid var(--border); background: #fff; border-radius: 8px;
  padding: 0.4rem 0.6rem; font-size: 0.88rem;
}
.toolbar button:disabled { opacity: 0.4; cursor: default; }
.toolbar button.on { background: #2563eb; color: #fff; border-color: #2563eb; }
.toolbar button.peligro:not(:disabled) { color: var(--ocupado); border-color: #fecaca; }
.cuenta { font-size: 0.85rem; color: var(--muted); min-width: 3.5rem; }
.campo { font-size: 0.85rem; display: flex; align-items: center; gap: 0.35rem; }
.campo input[type='number'] { width: 4rem; padding: 0.25rem; border: 1px solid var(--border); border-radius: 6px; }
.campo input[type='checkbox'] { accent-color: #2563eb; }
.apariencia { margin-left: auto; }
.apariencia .et { font-weight: 600; font-size: 0.9rem; }
.guardar { background: var(--libre) !important; color: #fff !important; border-color: var(--libre) !important; font-weight: 600; }
.guardar:disabled { background: #fff !important; color: inherit !important; border-color: var(--border) !important; }
.msg { margin: 0.5rem 1rem; background: #eff6ff; border: 1px solid #bfdbfe; color: #1e40af; padding: 0.45rem 0.7rem; border-radius: 8px; font-size: 0.9rem; }

.cuerpo { display: grid; grid-template-columns: 240px 1fr; gap: 1rem; padding: 0 1rem 1rem; }
@media (max-width: 780px) { .cuerpo { grid-template-columns: 1fr; } }

aside { display: flex; flex-direction: column; gap: 0.5rem; max-height: 78vh; overflow-y: auto; }
.nueva-btn { border: 1px dashed var(--border); background: #fff; border-radius: 8px; padding: 0.5rem; font-weight: 600; }
.nueva-btn.sec { border-style: solid; font-weight: 500; font-size: 0.85rem; }

.gestion {
  display: flex; flex-direction: column; gap: 0.5rem;
  border: 1px solid var(--border); border-radius: 8px; padding: 0.7rem; background: var(--panel);
}
.campo-col { display: flex; flex-direction: column; gap: 0.2rem; font-size: 0.78rem; color: var(--muted); }
.campo-col input { border: 1px solid var(--border); border-radius: 6px; padding: 0.4rem; font-size: 0.9rem; color: var(--text); }
.oculto { display: none; }
.referencia {
  border: 1px solid var(--border); border-radius: var(--radio-sm);
  padding: 0.3rem 0.5rem; font: inherit; font-size: 0.82rem; width: 15rem;
  background: var(--panel); color: var(--text);
}
.referencia:disabled { opacity: 0.5; }

.gestion .hint { margin: 0; font-size: 0.75rem; color: var(--muted); line-height: 1.4; }
/* Una categoría sin precio vende en 0 Bs: hay que verlo sin tener que buscarlo. */
.aviso-precio {
  margin: 0; font-size: 0.75rem; line-height: 1.4; font-weight: 600;
  color: var(--danger);
  background: var(--danger-suave);
  border: 1px solid color-mix(in srgb, var(--danger) 30%, transparent);
  border-radius: var(--radio-sm); padding: 0.35rem 0.5rem;
}
.gestion .acciones { display: flex; gap: 0.4rem; flex-wrap: wrap; }
.gestion .acciones button { flex: 1; border: 1px solid var(--border); background: #fff; border-radius: 6px; padding: 0.45rem; font-size: 0.82rem; }
.gestion .acciones .peligro { color: var(--ocupado); border-color: #fecaca; }
.form { display: flex; flex-direction: column; gap: 0.4rem; border: 1px solid var(--border); border-radius: 8px; padding: 0.6rem; background: var(--panel); }
.form input { border: 1px solid var(--border); border-radius: 6px; padding: 0.35rem; }
.form label { font-size: 0.85rem; display: flex; justify-content: space-between; align-items: center; }
.formas { display: flex; gap: 0.3rem; }
.formas button, .forma { border: 1px solid var(--border); background: #fff; border-radius: 6px; width: 2rem; height: 2rem; }
.formas button.on, .forma.on { background: #2563eb; color: #fff; }
.crear { background: var(--libre); color: #fff; border: none; border-radius: 6px; padding: 0.45rem; font-weight: 600; }

aside ul { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 2px; }
aside li button {
  width: 100%; display: flex; align-items: center; gap: 0.5rem; text-align: left;
  border: 1px solid transparent; background: none; border-radius: 6px; padding: 0.4rem 0.5rem; font-size: 0.85rem;
}
aside li button.sel { background: #eff6ff; border-color: #bfdbfe; }
.sw { width: 12px; height: 12px; border-radius: 3px; flex: none; }
.nom { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.num { color: var(--muted); font-variant-numeric: tabular-nums; }

.plano { position: relative; width: 100%; }
/* El fondo no participa del arrastre: si no, el navegador inicia su propio drag de imagen.
   `will-change/translateZ` le da al plano su propia capa de composicion, para que mover o
   recolorear una caseta no obligue a re-rasterizar la imagen entera (ver Mapa.vue). */
.plano img {
  width: 100%; display: block;
  pointer-events: none; user-select: none; -webkit-user-select: none;
  will-change: transform;
  transform: translateZ(0);
}
.modo-colocar { cursor: crosshair; }
.modo-seleccionar { cursor: default; }

.pin {
  position: absolute; aspect-ratio: 1; transform: translate(-50%, -50%);
  border: 1px solid rgba(255, 255, 255, 0.9); box-shadow: 0 1px 2px rgba(0, 0, 0, 0.35);
}
.modo-seleccionar .pin { cursor: move; }
.pin.sel { outline: 2px solid #0f172a; outline-offset: 1px; z-index: 4; }
.forma-cuadrado { border-radius: 2px; }
.forma-circulo { border-radius: 50%; }
.forma-triangulo { clip-path: polygon(50% 0%, 100% 100%, 0% 100%); border: none; }

.guia { position: absolute; height: 2px; background: #2563eb; transform-origin: 0 50%; opacity: 0.7; pointer-events: none; }
.caja { position: absolute; border: 1.5px dashed #2563eb; background: rgba(37, 99, 235, 0.08); pointer-events: none; }
</style>
