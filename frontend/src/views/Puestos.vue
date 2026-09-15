<script setup>
import { computed, onMounted, ref } from 'vue';
import { apiFetch } from '../api';
import { toast } from '../ui/toast';

const cargando = ref(false);
const guardando = ref(false);
const puestos = ref([]);
const categorias = ref([]);
const editados = ref({});
const filtro = ref({ texto: '', categoria: '', estado: '', soloPropio: false });

const estados = {
  L: 'Libre',
  T: 'En trámite',
  O: 'Ocupado',
  X: 'Bloqueado',
};

const visibles = computed(() => {
  const q = filtro.value.texto.trim().toLowerCase();
  return puestos.value.filter((p) => {
    if (filtro.value.categoria && String(p.categoriaId) !== filtro.value.categoria) return false;
    if (filtro.value.estado && p.estado !== filtro.value.estado) return false;
    // "¿a cuáles les puse precio especial?" es la pregunta que se hace al volver a esta
    // pantalla, y sin este filtro había que recorrer 500 filas a ojo para contestarla.
    if (filtro.value.soloPropio && !p.precioPropio) return false;
    if (!q) return true;
    return [p.codigo, p.categoria, p.tamano, p.referencia]
      .some((v) => String(v || '').toLowerCase().includes(q));
  });
});

/** Cuántas casetas llevan precio propio, en toda la feria y no solo en lo filtrado. */
const conPrecioPropio = computed(() => puestos.value.filter((p) => p.precioPropio).length);

const cambios = computed(() => puestos.value
  .filter((p) => codigoEditado(p) !== codigoOriginal(p))
  .map((p) => ({ id: p.id, codigo: codigoEditado(p) })));

/*
 * ---- precio propio de cada caseta (V37) ----
 *
 * El caso: se crea la categoría con su precio y se colocan todas sus casetas; después resulta
 * que algunas valen distinto. Aquí se les pone su precio sin tocar las demás.
 *
 * Se edita como TEXTO y no como número porque hay tres estados y un número solo distingue dos:
 * vacío = "usa el de tu categoría", 0 = "esta es gratis, lo digo a propósito", y cualquier otro
 * = ese precio. Con un `Number`, el vacío se vuelve 0 y regalaría la caseta.
 */
const precios = ref({});

/** Lo que vale la categoría de esta caseta, para enseñarlo al lado de la casilla vacía. */
function precioCategoria(p) {
  const c = categorias.value.find((x) => x.id === p.categoriaId);
  return Number(c?.precioBase ?? 0).toLocaleString('es-BO');
}

/** El precio propio ORIGINAL, como texto. Vacío si la caseta no tenía uno. */
function precioOriginal(p) {
  return p.precioPropio ? String(p.precio ?? '') : '';
}

function precioEditado(p) {
  return String(precios.value[p.id] ?? '').trim();
}

const tocado = (p) => codigoEditado(p) !== codigoOriginal(p)
  || precioEditado(p) !== precioOriginal(p);

/**
 * Los precios que de verdad cambiaron.
 *
 * `precio: null` es lo que se manda al vaciar la casilla, y significa "devuélvela al precio de
 * su categoría". No es un campo que falte: es la orden. El servidor lo escribe tal cual.
 */
const cambiosPrecio = computed(() => puestos.value
  .filter((p) => precioEditado(p) !== precioOriginal(p))
  .map((p) => ({
    id: p.id,
    precio: precioEditado(p) === '' ? null : Number(precioEditado(p)),
  })));

/** Precios mal escritos. Se avisa antes de mandar nada, no después de un 409. */
const preciosInvalidos = computed(() => puestos.value
  .filter((p) => {
    const v = precioEditado(p);
    if (v === '') return false;
    const n = Number(v);
    return !Number.isFinite(n) || n < 0;
  })
  .map((p) => p.codigo));

function codigoOriginal(p) {
  return String(p.codigo || '').trim();
}

function codigoEditado(p) {
  return String(editados.value[p.id] ?? p.codigo ?? '').trim();
}

function badgeEstado(estado) {
  if (estado === 'L') return 'badge-ok';
  if (estado === 'T') return 'badge-info';
  if (estado === 'O') return 'badge-danger';
  return 'badge-muted';
}

async function cargar() {
  cargando.value = true;
  try {
    const [rp, rc] = await Promise.all([
      apiFetch('/api/app/puestos'),
      apiFetch('/api/app/catalogo'),
    ]);
    if (!rp.ok) throw new Error('No se pudieron cargar los puestos');
    puestos.value = (await rp.json()).filter((p) => p.activo !== false)
      .sort((a, b) => String(a.categoria || '').localeCompare(String(b.categoria || ''), 'es')
        || Number(a.codigo) - Number(b.codigo)
        || String(a.codigo || '').localeCompare(String(b.codigo || ''), 'es'));
    categorias.value = rc.ok ? await rc.json() : [];
    editados.value = Object.fromEntries(puestos.value.map((p) => [p.id, p.codigo || '']));
    precios.value = Object.fromEntries(puestos.value.map((p) => [p.id, precioOriginal(p)]));
  } catch (e) {
    toast(e.message, 'error');
  } finally {
    cargando.value = false;
  }
}

function descartar() {
  editados.value = Object.fromEntries(puestos.value.map((p) => [p.id, p.codigo || '']));
  precios.value = Object.fromEntries(puestos.value.map((p) => [p.id, precioOriginal(p)]));
}

const hayCambios = computed(() => cambios.value.length > 0 || cambiosPrecio.value.length > 0);

/**
 * Guarda numeración y precios.
 *
 * Son DOS peticiones porque son dos operaciones con reglas distintas: renumerar es todo o nada
 * (una permutación con un número repetido a medias deja el plano incoherente), y los precios
 * son independientes entre sí. Meterlos en un solo endpoint obligaría a que el fallo de un
 * número tirase también los precios, que no tienen nada que ver.
 *
 * Los precios van PRIMERO: si algo falla, lo que queda sin guardar son los números, y esos se
 * ven en pantalla. Un precio a medias no se nota hasta que alguien vende.
 */
async function guardar() {
  if (guardando.value || !hayCambios.value) return;

  const vacios = cambios.value.filter((c) => !String(c.codigo || '').trim());
  if (vacios.length) { toast('No puede quedar un puesto sin número', 'error'); return; }
  if (preciosInvalidos.value.length) {
    toast(`Precio no válido en la caseta ${preciosInvalidos.value.slice(0, 3).join(', ')}`, 'error');
    return;
  }

  const partes = [];
  if (cambiosPrecio.value.length) partes.push(`el precio de ${cambiosPrecio.value.length} puesto(s)`);
  if (cambios.value.length) partes.push(`el número de ${cambios.value.length} puesto(s)`);
  if (!confirm(`Se cambiará ${partes.join(' y ')}. ¿Continuar?`)) return;

  guardando.value = true;
  try {
    if (cambiosPrecio.value.length) {
      const r = await apiFetch('/api/app/puestos/precios', {
        method: 'PATCH',
        body: JSON.stringify(cambiosPrecio.value),
      });
      const d = await r.json().catch(() => ({}));
      if (!r.ok || !d.ok) { toast(d.mensaje || 'No se pudieron guardar los precios', 'error'); return; }
      toast(d.mensaje || 'Precios guardados', 'ok');
    }
    if (cambios.value.length) {
      const r = await apiFetch('/api/app/puestos/codigos', {
        method: 'PATCH',
        body: JSON.stringify(cambios.value),
      });
      const d = await r.json().catch(() => ({}));
      if (!r.ok || !d.ok) { toast(d.mensaje || 'No se pudo guardar la numeración', 'error'); return; }
      toast(d.mensaje || 'Numeración guardada', 'ok');
    }
    await cargar();
  } catch (e) {
    toast(e.message, 'error');
  } finally {
    guardando.value = false;
  }
}

onMounted(cargar);
</script>

<template>
  <div class="puestos-vista">
    <section class="hero card">
      <div>
        <p class="eyebrow">Numeración y precios</p>
        <h2>Puestos</h2>
        <p class="muted">
          Consulta los puestos activos, cambia su número y ponle a una caseta un precio distinto
          al de su categoría, sin tocar las demás.
        </p>
      </div>
      <div class="resumen">
        <div><span>Puestos</span><strong>{{ visibles.length }}</strong></div>
        <!-- Cuenta las dos cosas: con solo los números, cambiar un precio dejaba el contador
             en 0 y el botón de guardar parecía no tener nada que hacer. -->
        <div><span>Cambios</span><strong>{{ cambios.length + cambiosPrecio.length }}</strong></div>
        <div><span>Precio propio</span><strong>{{ conPrecioPropio }}</strong></div>
      </div>
    </section>

    <section class="card filtros">
      <input v-model="filtro.texto" class="control buscar" placeholder="Buscar por número, categoría o referencia…" />
      <select v-model="filtro.categoria" class="control">
        <option value="">Todas las categorías</option>
        <option v-for="c in categorias" :key="c.id" :value="String(c.id)">{{ c.nombre }}</option>
      </select>
      <select v-model="filtro.estado" class="control">
        <option value="">Todos los estados</option>
        <option v-for="(nombre, clave) in estados" :key="clave" :value="clave">{{ nombre }}</option>
      </select>
      <label class="solo-propio">
        <input v-model="filtro.soloPropio" type="checkbox" />
        Solo con precio propio
      </label>
      <button class="btn btn-fantasma" :disabled="cargando" @click="cargar">Actualizar</button>
      <button class="btn btn-fantasma" :disabled="!hayCambios || guardando" @click="descartar">Descartar</button>
      <button class="btn btn-primario" :disabled="!hayCambios || guardando" @click="guardar">
        {{ guardando ? 'Guardando…' : 'Guardar cambios' }}
      </button>
    </section>

    <div v-if="cargando" class="vacio">Cargando puestos…</div>
    <section v-else class="card tabla-card">
      <table class="tabla">
        <thead>
          <tr>
            <th>Categoría</th>
            <th>Número actual</th>
            <th>Nuevo número</th>
            <th>Estado</th>
            <th>Tamaño</th>
            <!-- Vacío = usa el de su categoría. Es la columna entera la que lo explica, no
                 cada celda: repetir "usa el de la categoría" en 500 filas es ruido. -->
            <th class="col-precio">Precio propio<span class="ayuda-th">vacío = el de su categoría</span></th>
            <th>Referencia</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="p in visibles" :key="p.id" :class="{ cambiado: tocado(p) }">
            <td><strong>{{ p.categoria || 'Sin categoría' }}</strong></td>
            <td><span class="codigo">{{ p.codigo || '—' }}</span></td>
            <td>
              <input v-model="editados[p.id]" class="control control-codigo" maxlength="30" />
            </td>
            <td><span class="badge" :class="badgeEstado(p.estado)">{{ estados[p.estado] || p.estado }}</span></td>
            <td>{{ p.tamano || '—' }}</td>
            <td class="col-precio">
              <input v-model="precios[p.id]" class="control control-precio" type="number"
                     min="0" step="0.01" inputmode="decimal"
                     :placeholder="precioCategoria(p)" />
              <!-- El precio de la categoría, al lado: sin él, dejar la casilla vacía es decidir
                   a ciegas. El del marcador de posición se ve al escribir y desaparece. -->
              <span class="pie-precio">
                {{ precioEditado(p) === '' ? `categoría: ${precioCategoria(p)} Bs` : 'propio' }}
              </span>
            </td>
            <td class="referencia">{{ p.referencia || '—' }}</td>
          </tr>
          <tr v-if="!visibles.length"><td colspan="7" class="vacio">Sin puestos con esos filtros.</td></tr>
        </tbody>
      </table>
    </section>
  </div>
</template>

<style scoped>
.puestos-vista { display: flex; flex-direction: column; gap: 1rem; }
.hero { display: flex; justify-content: space-between; gap: 1rem; padding: 1.2rem; background: linear-gradient(135deg, color-mix(in srgb, var(--acento) 14%, var(--panel)), var(--panel)); }
.eyebrow { margin: 0; color: var(--acento); text-transform: uppercase; letter-spacing: 0.08em; font-size: 0.72rem; font-weight: 900; }
.hero h2 { margin: 0.15rem 0 0; font-size: 1.45rem; }
.muted { color: var(--muted); }
.hero p { margin: 0.35rem 0 0; }
.resumen { display: flex; gap: 0.7rem; }
.resumen > div { min-width: 110px; padding: 0.85rem 1rem; border: 1px solid var(--border); border-radius: var(--radio-sm); background: var(--panel); text-align: center; }
.resumen span { display: block; color: var(--muted); font-size: 0.74rem; font-weight: 800; text-transform: uppercase; }
.resumen strong { display: block; margin-top: 0.25rem; color: var(--acento); font-size: 1.45rem; }
.filtros {
  padding: 1rem; display: grid; gap: 0.65rem; align-items: center;
  /* auto-fit: con el filtro nuevo eran siete columnas fijas y en una pantalla mediana la
     ultima se salia de la tarjeta. */
  grid-template-columns: minmax(200px, 1fr) repeat(auto-fit, minmax(150px, auto));
}
.solo-propio { display: flex; align-items: center; gap: 0.4rem; white-space: nowrap; font-size: 0.9rem; }
.tabla-card { padding: 0; overflow: auto; }
.tabla { width: 100%; border-collapse: collapse; font-size: 0.88rem; }
.tabla th, .tabla td { padding: 0.75rem 1rem; border-bottom: 1px solid var(--border); text-align: left; vertical-align: middle; }
.tabla th { background: var(--panel); color: var(--muted); font-size: 0.74rem; text-transform: uppercase; letter-spacing: 0.05em; }
.tabla tr:hover td { background: var(--panel-2); }
.tabla tr.cambiado td { background: color-mix(in srgb, var(--acento) 9%, transparent); }
.codigo { display: inline-flex; min-width: 3rem; justify-content: center; padding: 0.25rem 0.55rem; border-radius: 999px; background: var(--panel-2); border: 1px solid var(--border); font-weight: 900; }
.control-codigo { max-width: 140px; font-weight: 900; text-align: center; }

/* Precio propio. La columna se lee de arriba abajo comparando importes, asi que el numero va
   alineado a la derecha: con el texto centrado, 800 y 1200 no se pueden comparar de un vistazo. */
.col-precio { white-space: nowrap; }
.control-precio { max-width: 120px; text-align: right; font-variant-numeric: tabular-nums; }
.ayuda-th { display: block; font-weight: 400; font-size: 0.72rem; color: var(--muted); }
.pie-precio { display: block; margin-top: 0.15rem; font-size: 0.72rem; color: var(--muted); }
.referencia { max-width: 340px; white-space: normal; color: var(--muted); }
.badge { padding: 0.18rem 0.55rem; border-radius: 999px; font-size: 0.7rem; font-weight: 800; }
.badge-info { background: var(--acento-suave); color: var(--acento); }
.badge-ok { background: var(--ok-suave); color: var(--ok); }
.badge-muted { background: var(--muted-suave); color: var(--muted); }
.badge-danger { background: var(--danger-suave); color: var(--danger); }
.vacio { padding: 2rem 1rem; text-align: center; color: var(--muted); }
@media (max-width: 980px) {
  .hero { flex-direction: column; }
  .resumen { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .filtros { grid-template-columns: 1fr 1fr; }
  .buscar { grid-column: 1 / -1; }
}
@media (max-width: 620px) {
  .resumen, .filtros { grid-template-columns: 1fr; }
}
</style>
