<script setup>
/**
 * El tablero de Control de ventas: las cifras de la rendición de cuentas y los gráficos.
 *
 * Todos los gráficos son de UNA serie (el acento), como el tablero de dirección: una magnitud
 * por fila no necesita paleta categórica ni leyenda de colores, y así nadie tiene que distinguir
 * dos tonos vecinos. El detalle de cada gráfico está en su pestaña, en tabla.
 */
import { computed } from 'vue';
import BarrasHorizontales from '../BarrasHorizontales.vue';
import BarraProgreso from '../BarraProgreso.vue';
import LineaTiempo from '../LineaTiempo.vue';
import { bs, ent, pct } from './formato';

const props = defineProps({
  tablero: { type: Object, required: true },
});

const r = computed(() => props.tablero.resumen);
const n = (v) => Number(v || 0);

const pctRecaudado = computed(() => (n(r.value.totalVendido) > 0
  ? (n(r.value.totalRecaudado) * 100) / n(r.value.totalVendido) : 0));

/* Lo RECAUDADO por los puestos que vendió cada uno: la pregunta de dirección. Ordenado por lo
   recaudado, no por lo vendido; vendido y extras están en «Por vendedor». */
const barrasVendedor = computed(() => (props.tablero.vendedores || [])
  .map((v) => ({ label: v.vendedor || '(sin usuario)', valor: n(v.recaudadoPuestos) }))
  .sort((a, b) => b.valor - a.valor)
  .slice(0, 12));

const barrasCategoria = computed(() => (props.tablero.categorias || [])
  .filter((c) => Number(c.vendidoBs) > 0)
  .map((c) => ({ label: `${c.categoria} (${pct(c.porcentajeIngresos)})`, valor: n(c.vendidoBs) })));

/* Rendición y credenciales: cada fila es "cuánto se lleva de lo que corresponde". */
const rendicionBs = computed(() => [{
  etiqueta: 'Recaudado con comprobante',
  valor: Math.round(n(r.value.totalRecaudado)),
  total: Math.round(n(r.value.totalVendido)),
  detalle: `Por rendir ${bs(r.value.porRendir)} Bs`,
}]);
const rendicionCantidades = computed(() => [
  {
    etiqueta: 'Ventas con comprobante',
    valor: r.value.ventasConComprobante,
    total: r.value.operaciones - r.value.ventasSinCosto,
    detalle: `${ent(r.value.ventasPorRendir + r.value.ventasParciales)} por rendir · ${ent(r.value.ventasSinCosto)} sin costo no cuentan`,
  },
  {
    etiqueta: 'Credenciales emitidas',
    valor: r.value.credencialesEmitidas,
    total: r.value.credencialesTotal,
    detalle: `Corresponden ${ent(r.value.credencialesIncluidas)} incluidas (puestos × 2) + ${ent(r.value.credencialesExtras)} extra · ${ent(r.value.responsablesRegistrados)} responsables registrados`,
  },
]);
const formatoBs = (v) => `${ent(v)} Bs`;

const estados = computed(() => [
  { etiqueta: 'Con comprobante', valor: r.value.ventasConComprobante },
  { etiqueta: 'Comprobante parcial', valor: r.value.ventasParciales },
  { etiqueta: 'Sin comprobante (por rendir)', valor: r.value.ventasPorRendir },
  { etiqueta: 'Sin costo (exentas)', valor: r.value.ventasSinCosto },
].map((e) => ({ ...e, total: r.value.operaciones })));

const puntosAvance = computed(() => (props.tablero.avance || []).map((d) => ({
  x: d.fecha ? new Date(`${d.fecha}T00:00:00`).toLocaleDateString('es-BO', { day: '2-digit', month: 'short' }) : '—',
  y: n(d.acumuladoBs),
})));
</script>

<template>
  <div class="tablero">
    <!-- Cifras de cabecera: números sueltos, no gráficos. La primera pesa más porque es la
         que se busca primero. -->
    <section class="kpis">
      <div class="card kpi principal">
        <span class="rotulo">Total vendido</span>
        <strong class="grande">{{ bs(r.totalVendido) }} <span class="moneda">Bs</span></strong>
        <span class="pie">{{ bs(r.importePuestos) }} por puestos + {{ bs(r.importeExtras) }} por extras</span>
      </div>
      <div class="card kpi">
        <span class="rotulo">Recaudado (rendido)</span>
        <strong>{{ bs(r.totalRecaudado) }} <span class="moneda">Bs</span></strong>
        <span class="pie">{{ bs(r.recaudadoPuestos) }} por puestos + {{ bs(r.recaudadoExtras) }} por extras · {{ pct(pctRecaudado) }}</span>
      </div>
      <div class="card kpi" :class="{ alerta: n(r.porRendir) > 0 }">
        <span class="rotulo">Por rendir</span>
        <strong>{{ bs(r.porRendir) }} <span class="moneda">Bs</span></strong>
        <span class="pie">{{ ent(r.ventasPorRendir + r.ventasParciales) }} venta(s) sin comprobante</span>
      </div>
      <div class="card kpi">
        <span class="rotulo">Puestos vendidos</span>
        <strong>{{ ent(r.puestos) }}</strong>
        <span class="pie">
          {{ ent(r.operaciones) }} operaciones · {{ ent(r.expositores) }} expositores
          <template v-if="r.puestosExentos"> · {{ ent(r.puestosExentos) }} exentos</template>
          · {{ ent(r.carpasCorresponden) }} carpas
        </span>
      </div>
      <div class="card kpi">
        <span class="rotulo">Credenciales emitidas</span>
        <strong>{{ ent(r.credencialesEmitidas) }} <span class="moneda">de {{ ent(r.credencialesTotal) }}</span></strong>
        <span class="pie">{{ ent(r.credencialesIncluidas) }} incluidas + {{ ent(r.extras) }} extra ({{ bs(r.importeExtras) }} Bs)</span>
      </div>
    </section>

    <div class="rejilla">
      <section class="card bloque">
        <header>
          <h3>Recaudado por vendedor · puestos</h3>
          <p class="muted chico">Lo que entró, con comprobante, por los puestos que vendió cada uno. Los 12 primeros; vendido, extras y el resto en «Por vendedor».</p>
        </header>
        <BarrasHorizontales :items="barrasVendedor" :formato="(v) => bs(v) + ' Bs'" />
      </section>

      <section class="card bloque">
        <header>
          <h3>Ingresos por categoría</h3>
          <p class="muted chico">Venta de puestos, con su porcentaje del total. Sin credenciales extra; las categorías sin ingresos están en «Por categoría».</p>
        </header>
        <BarrasHorizontales :items="barrasCategoria" :formato="(v) => bs(v) + ' Bs'" />
      </section>

      <section class="card bloque">
        <header>
          <h3>Rendición y credenciales</h3>
          <p class="muted chico">
            Una venta está rendida si tiene su comprobante adjunto (foto, voucher o recibo); banco y
            número no se exigen.
          </p>
        </header>
        <BarraProgreso :items="rendicionBs" :formato="formatoBs" />
        <BarraProgreso :items="rendicionCantidades" />
      </section>

      <section class="card bloque">
        <header>
          <h3>Estado de las ventas</h3>
          <p class="muted chico">Sobre {{ ent(r.operaciones) }} operaciones.</p>
        </header>
        <BarraProgreso :items="estados" vacio="Sin ventas con estos filtros." />
      </section>

      <section class="card bloque ancho">
        <header>
          <h3>Avance de ventas</h3>
          <p class="muted chico">Bolivianos vendidos acumulados. Solo días con ventas.</p>
        </header>
        <LineaTiempo :puntos="puntosAvance" :formato="(v) => bs(v) + ' Bs'" :alto="200" />
      </section>
    </div>
  </div>
</template>

<style scoped>
.tablero { display: flex; flex-direction: column; gap: 1rem; min-width: 0; }
.muted { color: var(--muted); }
.chico { font-size: 0.82rem; }

/* 160 px: las cinco caben en una fila en un portátil. */
.kpis { display: grid; gap: 0.75rem; grid-template-columns: repeat(auto-fit, minmax(min(160px, 100%), 1fr)); }
.kpi { padding: 1rem; display: flex; flex-direction: column; gap: 0.2rem; min-width: 0; }
.rotulo { font-size: 0.72rem; font-weight: 800; text-transform: uppercase; letter-spacing: 0.06em; color: var(--muted); }
.kpi strong { font-size: 1.45rem; font-variant-numeric: tabular-nums; line-height: 1.15; }
.kpi.principal strong.grande { font-size: 1.7rem; color: var(--acento); }
.moneda { font-size: 0.85rem; font-weight: 600; color: var(--muted); }
.pie { font-size: 0.78rem; color: var(--muted); }
/* Ámbar y no rojo: falta un comprobante, no hay una avería. */
.kpi.alerta { border-left: 3px solid var(--tramite); }

/* 420 px: dos columnas en escritorio, así los cuatro bloques forman un 2x2 sin huecos.
   Ver Direccion.vue: min(…, 100%) y min-width: 0 evitan que una pista empuje la página de lado. */
.rejilla { display: grid; gap: 1rem; grid-template-columns: repeat(auto-fit, minmax(min(420px, 100%), 1fr)); }
.bloque { padding: 1rem; display: flex; flex-direction: column; gap: 0.9rem; min-width: 0; }
.bloque.ancho { grid-column: 1 / -1; }
.bloque h3 { margin: 0; font-size: 1.02rem; }
.bloque header p { margin: 0.15rem 0 0; }
</style>
