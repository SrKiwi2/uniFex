<script setup>
/**
 * Control de ventas: la rendición de cuentas de la feria. Lo vendido, lo recaudado (con
 * comprobante) y lo que falta rendir, por venta, por vendedor y por categoría, con todas las
 * celdas del registro.
 *
 * <h2>Rendido = comprobante adjunto</h2>
 * Una venta está rendida si tiene su comprobante adjunto: la foto del depósito, el voucher o el
 * recibo. Banco y número no se exigen. Rendir lo que falta es adjuntar ese comprobante desde la
 * ficha de la venta.
 *
 * <h2>Un filtro para todo</h2>
 * Los filtros de arriba valen para las cuatro pestañas y para las descargas: el Excel que se
 * baja es exactamente lo que se estaba mirando. Se filtra en el SERVIDOR porque lo recaudado,
 * el estado o el código de expositor se calculan allí.
 *
 * <h2>Tablero y tabla, en la misma recarga</h2>
 * Se piden juntos cada vez que cambia un filtro o se adjunta un comprobante: si el tablero
 * dijera un monto por rendir y la tabla otro, aunque fuera un segundo, nadie sabría a cuál creer.
 */
import { ref, computed, watch, onMounted } from 'vue';
import { apiFetch } from '../api';
import { toast } from '../ui/toast';
import { descargarArchivo, nombreSeguro } from '../ui/descargas';
import TableroVentas from '../components/control-ventas/TableroVentas.vue';
import FichaVenta from '../components/control-ventas/FichaVenta.vue';
import { bs, ent, pct, fecha, ESTADOS } from '../components/control-ventas/formato';

const pestana = ref('tablero'); // tablero | ventas | vendedores | categorias
const cargando = ref(true);
const recargando = ref(false);
const error = ref('');

const opciones = ref({ vendedores: [], categorias: [], puedeAdjuntar: false });
const tablero = ref(null);
const ventas = ref([]);

const filtros = ref({ vendedor: '', categoria: '', desde: '', hasta: '', estado: '', q: '' });
const hayFiltros = computed(() => Object.values(filtros.value).some((v) => String(v).trim() !== ''));

function parametros(extra = {}) {
  const p = new URLSearchParams();
  for (const [k, v] of Object.entries({ ...filtros.value, ...extra })) {
    if (String(v).trim() !== '') p.set(k, String(v).trim());
  }
  const qs = p.toString();
  return qs ? `?${qs}` : '';
}

async function leer(ruta) {
  const r = await apiFetch(ruta);
  if (!r.ok) throw new Error('No se pudo cargar el control de ventas');
  return r.json();
}

/*
 * Una recarga que llega tarde no pisa a una más nueva: al escribir en la búsqueda salen varias
 * peticiones seguidas y la respuesta de "uni" podría llegar después de la de "union".
 */
let turno = 0;
async function cargar() {
  const mio = ++turno;
  recargando.value = true;
  try {
    const qs = parametros();
    const [t, v] = await Promise.all([
      leer(`/api/app/control-ventas${qs}`),
      leer(`/api/app/control-ventas/ventas${qs}`),
    ]);
    if (mio !== turno) return;
    tablero.value = t;
    ventas.value = v;
    error.value = '';
  } catch (e) {
    if (mio === turno) error.value = e.message;
  } finally {
    if (mio === turno) {
      cargando.value = false;
      recargando.value = false;
    }
  }
}

onMounted(async () => {
  try {
    opciones.value = await leer('/api/app/control-ventas/filtros');
  } catch (e) {
    toast(e.message, 'error');
  }
  await cargar();
});

// La búsqueda espera a que se deje de escribir; el resto de filtros recarga al instante.
let esperaTexto = null;
// Se vigila una CLAVE sin la búsqueda: si el getter leyera `q`, cada tecla recargaría al
// instante y la espera de abajo no serviría de nada.
watch(() => {
  const f = filtros.value;
  return [f.vendedor, f.categoria, f.desde, f.hasta, f.estado].join('|');
}, () => cargar());
watch(() => filtros.value.q, () => {
  clearTimeout(esperaTexto);
  esperaTexto = setTimeout(cargar, 400);
});

function limpiarFiltros() {
  filtros.value = { vendedor: '', categoria: '', desde: '', hasta: '', estado: '', q: '' };
}

/** Desde la tabla de vendedores: ver las ventas de uno. */
function verVentasDe(vendedorId) {
  filtros.value.vendedor = vendedorId ? String(vendedorId) : '';
  pestana.value = 'ventas';
}

// ---- ventas: orden y paginado en el cliente (unos cientos de filas)
const orden = ref('fecha');
const pagina = ref(1);
const POR_PAGINA = 50;
const ordenadas = computed(() => {
  const xs = ventas.value.slice();
  const n = (v) => Number(v || 0);
  if (orden.value === 'porRendir') xs.sort((a, b) => n(b.porRendir) - n(a.porRendir));
  else if (orden.value === 'total') xs.sort((a, b) => n(b.totalVendido) - n(a.totalVendido));
  else if (orden.value === 'entidad') xs.sort((a, b) => (a.entidad || '').localeCompare(b.entidad || '', 'es'));
  return xs;
});
const visibles = computed(() => ordenadas.value.slice(0, pagina.value * POR_PAGINA));
watch([ventas, orden], () => { pagina.value = 1; });

const totalesVentas = computed(() => ventas.value.reduce((t, v) => ({
  puestos: t.puestos + (v.cantidadPuestos || 0),
  extras: t.extras + (v.extras || 0),
  total: t.total + Number(v.totalVendido || 0),
  recaudado: t.recaudado + Number(v.totalRecaudado || 0),
  porRendir: t.porRendir + Number(v.porRendir || 0),
}), { puestos: 0, extras: 0, total: 0, recaudado: 0, porRendir: 0 }));

// ---- ficha
const fichaId = ref(null);

function alCambiar() {
  // Adjuntar un comprobante cambia lo recaudado y lo por rendir de todo el tablero.
  cargar();
}

// ---- descargas
const bajando = ref('');
async function bajar(formato) {
  if (bajando.value) return;
  bajando.value = formato;
  try {
    const archivo = formato === 'pdf' ? 'control-ventas.pdf' : 'control-ventas.xlsx';
    await descargarArchivo(`/api/app/control-ventas/${formato}${parametros()}`, archivo);
    toast('Reporte descargado', 'ok');
  } catch (e) {
    toast(e.message || 'No se pudo descargar', 'error');
  } finally {
    bajando.value = '';
  }
}

/** La hoja de rendición de cuentas de un vendedor: su PDF para revisar y firmar. */
async function bajarRendicion(v) {
  if (bajando.value || !v.usuarioId) return;
  bajando.value = `rendicion-${v.usuarioId}`;
  try {
    await descargarArchivo(`/api/app/control-ventas/pdf${parametros({ vendedor: v.usuarioId })}`,
      `rendicion-${nombreSeguro(v.vendedor)}.pdf`);
    toast('Rendición descargada', 'ok');
  } catch (e) {
    toast(e.message || 'No se pudo descargar', 'error');
  } finally {
    bajando.value = '';
  }
}

const vendedores = computed(() => tablero.value?.vendedores || []);
const categorias = computed(() => tablero.value?.categorias || []);
const totalCategorias = computed(() => categorias.value.reduce((t, c) => ({
  vendidos: t.vendidos + c.puestosVendidos,
  vendibles: t.vendibles + c.puestosTotales,
  exentos: t.exentos + c.exentos,
  vendido: t.vendido + Number(c.vendidoBs || 0),
  recaudado: t.recaudado + Number(c.recaudadoBs || 0),
}), { vendidos: 0, vendibles: 0, exentos: 0, vendido: 0, recaudado: 0 }));
</script>

<template>
  <div class="control">
    <!-- Filtros: una sola fila sobre todo, valen para las cuatro pestañas y las descargas. -->
    <section class="card filtros">
      <select v-model="filtros.vendedor" class="control-campo" aria-label="Vendedor">
        <option value="">Todos los vendedores</option>
        <option v-for="v in opciones.vendedores" :key="v.id" :value="String(v.id)">{{ v.nombre }}</option>
      </select>
      <select v-model="filtros.categoria" class="control-campo" aria-label="Categoría">
        <option value="">Todas las categorías</option>
        <option v-for="c in opciones.categorias" :key="c.id" :value="String(c.id)">{{ c.nombre }}</option>
      </select>
      <select v-model="filtros.estado" class="control-campo" aria-label="Estado del comprobante">
        <option value="">Con y sin comprobante</option>
        <option v-for="(e, k) in ESTADOS" :key="k" :value="k">{{ e.texto }}</option>
      </select>
      <label class="fecha-campo"><span>Desde</span><input v-model="filtros.desde" type="date" class="control-campo" /></label>
      <label class="fecha-campo"><span>Hasta</span><input v-model="filtros.hasta" type="date" class="control-campo" /></label>
      <input v-model="filtros.q" type="search" class="control-campo busqueda"
             placeholder="Buscar entidad, representante, NIT, C.I., celular, puesto…" aria-label="Buscar" />
      <div class="acciones-filtro">
        <button v-if="hayFiltros" class="btn btn-fantasma btn-sm" @click="limpiarFiltros">Limpiar</button>
        <button class="btn btn-fantasma btn-sm" :disabled="recargando" @click="cargar">
          {{ recargando ? 'Actualizando…' : 'Actualizar' }}
        </button>
        <button class="btn btn-sm" :disabled="!!bajando" @click="bajar('excel')">
          {{ bajando === 'excel' ? 'Generando…' : 'Excel completo' }}
        </button>
        <button class="btn btn-sm" :disabled="!!bajando" @click="bajar('pdf')">
          {{ bajando === 'pdf' ? 'Generando…' : (filtros.vendedor ? 'PDF rendición' : 'PDF resumen') }}
        </button>
      </div>
    </section>

    <nav class="pestanas" role="tablist">
      <button v-for="p in [
                { k: 'tablero', t: 'Tablero' },
                { k: 'ventas', t: `Ventas (${ventas.length})` },
                { k: 'vendedores', t: 'Por vendedor' },
                { k: 'categorias', t: 'Por categoría' },
              ]"
              :key="p.k" role="tab" class="pestana" :class="{ activa: pestana === p.k }"
              :aria-selected="pestana === p.k" @click="pestana = p.k">
        {{ p.t }}
      </button>
    </nav>

    <p v-if="error" class="card aviso-error">{{ error }}</p>
    <div v-else-if="cargando" class="vacio">Cargando el control de ventas…</div>

    <template v-else-if="tablero">
      <!-- ============================================================ TABLERO -->
      <TableroVentas v-if="pestana === 'tablero'" :tablero="tablero" />

      <!-- ============================================================ VENTAS -->
      <section v-else-if="pestana === 'ventas'" class="card bloque">
        <header class="cab">
          <div>
            <h3>Registro de ventas</h3>
            <p class="muted chico">
              {{ ent(ventas.length) }} operaciones · {{ ent(totalesVentas.puestos) }} puestos ·
              {{ ent(totalesVentas.extras) }} extras · vendido {{ bs(totalesVentas.total) }} Bs ·
              recaudado {{ bs(totalesVentas.recaudado) }} Bs · por rendir {{ bs(totalesVentas.porRendir) }} Bs.
              Toca una fila para ver todas sus celdas y adjuntar el comprobante que falte.
            </p>
          </div>
          <label class="orden">
            <span class="muted chico">Ordenar</span>
            <select v-model="orden" class="control-campo">
              <option value="fecha">Más recientes</option>
              <option value="porRendir">Mayor por rendir</option>
              <option value="total">Mayor importe</option>
              <option value="entidad">Entidad (A-Z)</option>
            </select>
          </label>
        </header>

        <div class="tabla-scroll">
          <table class="tabla ventas">
            <thead>
              <tr>
                <th>Venta</th><th>Fecha</th><th>Vendedor</th><th>Expositor</th><th>Representante</th>
                <th>Celular</th><th>Categoría</th><th>Puestos</th>
                <th class="num">Vendido</th><th class="num">Recaudado</th><th class="num">Por rendir</th>
                <th>Comprobante</th><th class="num">Credenciales</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="v in visibles" :key="v.inscripcionId" class="clicable" tabindex="0"
                  @click="fichaId = v.inscripcionId" @keydown.enter="fichaId = v.inscripcionId">
                <td class="codigo">
                  <strong>#{{ v.inscripcionId }}</strong>
                  <span class="muted chico">{{ v.codigoVenta }}</span>
                </td>
                <td class="nowrap">{{ fecha(v.fecha, true) }}</td>
                <td class="corto" :title="v.vendedor">{{ v.vendedor || '—' }}</td>
                <td class="entidad">
                  <strong>{{ v.entidad || '—' }}</strong>
                  <span class="muted chico">
                    {{ v.codigoExpositor }}<template v-if="v.comprasExpositor > 1"> · {{ v.comprasExpositor }} compras</template>
                  </span>
                </td>
                <td class="corto" :title="v.representante">{{ v.representante || '—' }}</td>
                <td class="nowrap">{{ v.celularRepresentante || v.celularTitular || '—' }}</td>
                <td class="corto" :title="v.subcategorias">{{ v.categorias || '—' }}</td>
                <td class="corto" :title="v.puestos">
                  <strong>{{ v.cantidadPuestos }}</strong> <span class="muted">· {{ v.puestos }}</span>
                  <span v-if="v.extras" class="muted chico"> + {{ v.extras }} extra</span>
                </td>
                <td class="num">{{ bs(v.totalVendido) }}</td>
                <td class="num">{{ bs(v.totalRecaudado) }}</td>
                <td class="num" :class="{ debe: Number(v.porRendir) > 0 }">{{ bs(v.porRendir) }}</td>
                <td><span class="badge" :class="ESTADOS[v.estadoPago]?.clase">{{ ESTADOS[v.estadoPago]?.texto }}</span></td>
                <td class="num chico">{{ v.credencialesEmitidas }} de {{ v.credencialesTotal }}</td>
              </tr>
              <tr v-if="!ventas.length"><td colspan="13" class="vacio">Ninguna venta con estos filtros.</td></tr>
            </tbody>
          </table>
        </div>
        <button v-if="visibles.length < ordenadas.length" class="btn btn-fantasma" @click="pagina++">
          Ver {{ Math.min(POR_PAGINA, ordenadas.length - visibles.length) }} más
          ({{ visibles.length }} de {{ ordenadas.length }})
        </button>
      </section>

      <!-- ============================================================ VENDEDORES -->
      <section v-else-if="pestana === 'vendedores'" class="card bloque">
        <header class="cab">
          <div>
            <h3>Rendición de cuentas por vendedor</h3>
            <p class="muted chico">
              Operaciones vigentes de cada vendedor. <strong>Recaudado</strong> = lo respaldado con el
              comprobante adjunto a la venta (foto, voucher o recibo; banco y número no se exigen).
              <strong>Por rendir</strong> = lo vendido que aún no tiene comprobante. «Rendición (PDF)»
              baja su hoja para revisar y firmar.
            </p>
          </div>
        </header>
        <div class="tabla-scroll">
          <table class="tabla densa">
            <thead>
              <!-- Cabecera en dos niveles: con catorce cifras seguidas no se sabe de un vistazo
                   cuál «vendido» es de puestos y cuál de extras. -->
              <tr class="grupos">
                <th rowspan="2">Vendedor</th><th rowspan="2" class="num">Operac.</th>
                <th colspan="3" class="grupo">Puestos</th>
                <th colspan="3" class="grupo">Credenciales extra</th>
                <th colspan="4" class="grupo">Total</th>
                <th rowspan="2" class="num grupo">Credenciales<br />emitidas</th>
              </tr>
              <tr>
                <th class="num">Cant.</th><th class="num">Vendido</th><th class="num">Recaudado</th>
                <th class="num">Cant.</th><th class="num">Vendido</th><th class="num">Recaudado</th>
                <th class="num">Vendido</th><th class="num">Recaudado</th><th class="num">Por rendir</th><th class="num">% vendido</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="v in vendedores" :key="v.usuarioId ?? 'sin'">
                <!-- Las acciones van bajo el nombre y no en una última columna: la tabla es ancha y
                     al final quedaban fuera de la vista, detrás del desplazamiento lateral. -->
                <td class="vendedor">
                  <strong>{{ v.vendedor }}</strong>
                  <span class="muted chico usuario">{{ v.usuario }}</span>
                  <span class="acciones-vendedor">
                    <button class="enlace" @click="verVentasDe(v.usuarioId)">Ver ventas</button>
                    <button v-if="v.usuarioId" class="enlace" :disabled="!!bajando" @click="bajarRendicion(v)">
                      {{ bajando === `rendicion-${v.usuarioId}` ? 'Generando…' : 'Rendición (PDF)' }}
                    </button>
                  </span>
                </td>
                <td class="num">{{ ent(v.operaciones) }}</td>
                <td class="num inicio-grupo">{{ ent(v.puestos) }}</td>
                <td class="num">{{ bs(v.importePuestos) }}</td>
                <td class="num"><strong>{{ bs(v.recaudadoPuestos) }}</strong></td>
                <td class="num inicio-grupo">{{ ent(v.extras) }}</td>
                <td class="num">{{ bs(v.importeExtras) }}</td>
                <td class="num">{{ bs(v.recaudadoExtras) }}</td>
                <td class="num inicio-grupo">{{ bs(v.totalVendido) }}</td>
                <td class="num"><strong>{{ bs(v.totalRecaudado) }}</strong></td>
                <td class="num" :class="{ debe: Number(v.porRendir) > 0 }">
                  {{ bs(v.porRendir) }}
                  <span v-if="v.ventasPorRendir" class="chico"><br />{{ v.ventasPorRendir }} venta(s)</span>
                </td>
                <td class="num">{{ pct(v.porcentajeVendido) }}</td>
                <td class="num inicio-grupo">{{ ent(v.credencialesEmitidas) }} de {{ ent(v.credencialesTotal) }}</td>
              </tr>
              <tr v-if="!vendedores.length"><td colspan="13" class="vacio">Sin ventas con estos filtros.</td></tr>
            </tbody>
            <tfoot v-if="vendedores.length > 1">
              <tr>
                <th>Total</th>
                <th class="num">{{ ent(tablero.resumen.operaciones) }}</th>
                <th class="num inicio-grupo">{{ ent(tablero.resumen.puestos) }}</th>
                <th class="num">{{ bs(tablero.resumen.importePuestos) }}</th>
                <th class="num">{{ bs(tablero.resumen.recaudadoPuestos) }}</th>
                <th class="num inicio-grupo">{{ ent(tablero.resumen.extras) }}</th>
                <th class="num">{{ bs(tablero.resumen.importeExtras) }}</th>
                <th class="num">{{ bs(tablero.resumen.recaudadoExtras) }}</th>
                <th class="num inicio-grupo">{{ bs(tablero.resumen.totalVendido) }}</th>
                <th class="num">{{ bs(tablero.resumen.totalRecaudado) }}</th>
                <th class="num">{{ bs(tablero.resumen.porRendir) }}</th>
                <th class="num">100 %</th>
                <th class="num inicio-grupo">{{ ent(tablero.resumen.credencialesEmitidas) }} de {{ ent(tablero.resumen.credencialesTotal) }}</th>
              </tr>
            </tfoot>
          </table>
        </div>
      </section>

      <!-- ============================================================ CATEGORÍAS -->
      <section v-else class="card bloque">
        <header class="cab">
          <div>
            <h3>Ventas de puestos por categoría</h3>
            <p class="muted chico">
              Sin credenciales extra, que no tienen categoría. <strong>Recaudado</strong>: lo respaldado
              con comprobante, repartido entre las casetas de cada venta según su precio. Salen también
              las categorías sin ventas: son las que hay que mirar.
            </p>
          </div>
        </header>
        <div class="tabla-scroll">
          <table class="tabla">
            <thead>
              <tr>
                <th>Categoría</th><th class="num">Vendidos</th><th class="num">Vendibles</th>
                <th class="num">% ocupación</th><th class="num">% de puestos</th><th class="num">Exentos</th>
                <th class="num">Vendido Bs</th><th class="num">% ingresos</th><th class="num">Recaudado Bs</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="c in categorias" :key="c.categoria">
                <td>{{ c.categoria }}</td>
                <td class="num">{{ ent(c.puestosVendidos) }}</td>
                <td class="num">{{ ent(c.puestosTotales) }}</td>
                <td class="num">{{ pct(c.porcentajeOcupacion) }}</td>
                <td class="num">{{ pct(c.porcentajePuestos) }}</td>
                <td class="num">{{ ent(c.exentos) }}</td>
                <td class="num"><strong>{{ bs(c.vendidoBs) }}</strong></td>
                <td class="num">{{ pct(c.porcentajeIngresos) }}</td>
                <td class="num">{{ bs(c.recaudadoBs) }}</td>
              </tr>
            </tbody>
            <tfoot>
              <tr>
                <th>Total</th>
                <th class="num">{{ ent(totalCategorias.vendidos) }}</th>
                <th class="num">{{ ent(totalCategorias.vendibles) }}</th>
                <th class="num">{{ pct(totalCategorias.vendibles ? (totalCategorias.vendidos * 100) / totalCategorias.vendibles : 0) }}</th>
                <th class="num">100 %</th>
                <th class="num">{{ ent(totalCategorias.exentos) }}</th>
                <th class="num">{{ bs(totalCategorias.vendido) }}</th>
                <th class="num">100 %</th>
                <th class="num">{{ bs(totalCategorias.recaudado) }}</th>
              </tr>
            </tfoot>
          </table>
        </div>
      </section>
    </template>

    <FichaVenta v-if="fichaId" :id="fichaId" :opciones="opciones"
                @cerrar="fichaId = null" @cambio="alCambiar" />
  </div>
</template>

<style scoped>
.control { display: flex; flex-direction: column; gap: 1rem; min-width: 0; }
.muted { color: var(--muted); }
.chico { font-size: 0.8rem; }
.nowrap { white-space: nowrap; }
.aviso-error { padding: 0.9rem; }

.filtros { padding: 0.8rem 1rem; display: flex; flex-wrap: wrap; gap: 0.6rem; align-items: flex-end; }
.control-campo {
  padding: 0.5rem 0.65rem; font: inherit; font-size: 0.9rem; color: var(--text);
  background: var(--panel); border: 1px solid var(--border); border-radius: var(--radio-sm);
  min-width: 0; max-width: 100%;
}
.control-campo:focus { outline: 2px solid var(--acento); outline-offset: -1px; }
.fecha-campo { display: flex; flex-direction: column; gap: 0.15rem; font-size: 0.72rem; color: var(--muted); font-weight: 700; }
.busqueda { flex: 1 1 16rem; }
.acciones-filtro { display: flex; flex-wrap: wrap; gap: 0.4rem; margin-left: auto; }

.pestanas { display: flex; gap: 0.3rem; flex-wrap: wrap; border-bottom: 1px solid var(--border); }
.pestana {
  font: inherit; font-weight: 700; font-size: 0.92rem; cursor: pointer; background: transparent;
  color: var(--muted); border: 0; border-bottom: 2px solid transparent; padding: 0.55rem 0.9rem;
}
.pestana:hover { color: var(--text); }
.pestana.activa { color: var(--acento); border-bottom-color: var(--acento); }

.bloque { padding: 1rem; display: flex; flex-direction: column; gap: 0.8rem; min-width: 0; }
.cab { display: flex; justify-content: space-between; align-items: flex-start; gap: 0.8rem; flex-wrap: wrap; }
.cab h3 { margin: 0; font-size: 1.05rem; }
.cab p { margin: 0.2rem 0 0; line-height: 1.45; max-width: 75ch; }
.orden { display: flex; align-items: center; gap: 0.4rem; }

.tabla-scroll { overflow-x: auto; min-width: 0; max-width: 100%; }
.tabla th, .tabla td { padding: 0.55rem 0.6rem; vertical-align: top; }
.tabla .num { text-align: right; font-variant-numeric: tabular-nums; white-space: nowrap; }
.tabla tfoot th { border-top: 2px solid var(--border); color: var(--text); font-size: 0.85rem; text-transform: none; letter-spacing: 0; }
.ventas td { font-size: 0.86rem; }
/* Muchas columnas de cifras: más juntas, para que «Por rendir» —la que se busca al rendir
   cuentas— se vea sin desplazar la tabla en un portátil. */
.densa th, .densa td { padding: 0.5rem 0.4rem; font-size: 0.82rem; }
.densa th { font-size: 0.7rem; }
.densa .grupos th { border-bottom: 0; padding-bottom: 0.15rem; }
.densa .grupo { text-align: center; color: var(--text); border-left: 1px solid var(--border); }
.densa .inicio-grupo { border-left: 1px solid var(--border); }
.codigo strong, .entidad strong { display: block; }
.entidad { min-width: 12rem; max-width: 18rem; }
/* Una línea por fila: con los nombres completos partidos en tres, 50 ventas ocupaban cinco
   pantallas. El nombre entero sale al pasar el ratón y en la ficha. */
.corto { max-width: 12rem; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.clicable { cursor: pointer; }
.clicable:focus-visible { outline: 2px solid var(--acento); outline-offset: -2px; }
/* Ámbar y no rojo: falta un comprobante, no hay una avería (mismo criterio que Dirección). */
.debe { color: var(--tramite); font-weight: 700; }
.vendedor { min-width: 10.5rem; }
/* Por clase y no `.vendedor > span`: ese selector pesaba más que `.acciones-vendedor` y le
   quitaba el flex, así que los dos enlaces salían pegados. */
.vendedor strong, .vendedor .usuario { display: block; }
.acciones-vendedor { display: flex; gap: 0.8rem; margin-top: 0.2rem; }
.enlace {
  font: inherit; font-size: 0.8rem; font-weight: 600; color: var(--acento); background: none;
  border: 0; padding: 0; cursor: pointer;
}
.enlace:hover { text-decoration: underline; }
.enlace:disabled { opacity: 0.5; cursor: default; }

@media (max-width: 640px) {
  .acciones-filtro { margin-left: 0; }
  .filtros > select, .fecha-campo { flex: 1 1 45%; }
}
</style>
