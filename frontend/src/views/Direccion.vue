<script setup>
/**
 * Tablero de dirección: cómo va la feria, en vivo.
 *
 * Lo mira quien dirige (rol ASESORIA) además de administración. Responde cuatro preguntas, en
 * el orden en que se hacen: cuánto queda por vender, quién está vendiendo, cuánto se cobró de
 * verdad, y si el ritmo sube o baja.
 *
 * <h2>Se actualiza solo</h2>
 * Escucha `/topic/puestos` —el mismo canal que el mapa— y vuelve a pedir el análisis cuando
 * una caseta cambia de estado. Se AGRUPAN los avisos: registrar una venta de veinte casetas
 * son veinte mensajes, y recalcular veinte veces seguidas serían veinte consultas pesadas para
 * enseñar el mismo número. Con la espera, es una.
 *
 * <h2>Todo sale de UNA petición</h2>
 * `GET /api/app/analisis` trae las cuatro cosas juntas. En cuatro peticiones separadas la
 * pantalla se llenaría a trompicones y, peor, podrían venir de momentos distintos: el total de
 * arriba de hace un segundo y el desglose de hace tres.
 */
import { ref, computed, watch, onMounted, onUnmounted } from 'vue';
import { apiFetch } from '../api';
import { toast } from '../ui/toast';
import { descargarPdf, descargarArchivo } from '../ui/descargas';
import { usePuestosStore } from '../stores/puestos';
import BarraProgreso from '../components/BarraProgreso.vue';
import BarrasHorizontales from '../components/BarrasHorizontales.vue';
import LineaTiempo from '../components/LineaTiempo.vue';

const tienda = usePuestosStore();

const cargando = ref(true);
const error = ref('');
const actualizado = ref(0);
const datos = ref({ ocupacion: [], vendedores: [], cobros: null, avance: [] });

// ---- filtros, en el cliente ----
//
// Son unas decenas de filas: filtrar aquí es instantáneo y no cuesta una consulta por cada
// tecla. Si algún día la feria creciera hasta que esto se note, el filtro se baja al servidor
// sin tocar la pantalla.
const filtroCategoria = ref('');
const soloConVentas = ref(false);
const dias = ref(0); // 0 = todo

const bs = (n) => Number(n || 0).toLocaleString('es-BO', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
const ent = (n) => Number(n || 0).toLocaleString('es-BO');

async function cargar(silencioso = false) {
  if (!silencioso) cargando.value = true;
  try {
    const r = await apiFetch('/api/app/analisis');
    if (!r.ok) throw new Error('No se pudo cargar el tablero');
    datos.value = await r.json();
    error.value = '';
    actualizado.value = Date.now();
  } catch (e) {
    error.value = e.message;
  } finally {
    cargando.value = false;
  }
}

// ---- lo que se ve, ya filtrado ----

/*
 * Ordenadas por DINERO QUE QUEDA POR VENDER, de más a menos.
 *
 * El servidor las devuelve por nombre, que es el orden con el que se administran; aquí la
 * pregunta es otra: "¿dónde queda dinero?". Alfabético, una categoría de 103 casetas sin
 * vender puede caer la novena y pasar desapercibida entre catorce filas. Para encontrar UNA
 * concreta está el filtro de arriba.
 */
const ocupacion = computed(() => (datos.value.ocupacion || [])
  .filter((o) => !filtroCategoria.value || o.categoria === filtroCategoria.value)
  .slice()
  .sort((a, b) => Number(b.bsPorVender || 0) - Number(a.bsPorVender || 0)));

const categorias = computed(() => (datos.value.ocupacion || []).map((o) => o.categoria).filter(Boolean));

const totales = computed(() => ocupacion.value.reduce((t, o) => ({
  vendidas: t.vendidas + o.vendidas,
  enTramite: t.enTramite + o.enTramite,
  libres: t.libres + o.libres,
  bloqueadas: t.bloqueadas + o.bloqueadas,
  total: t.total + o.total,
  bsVendido: t.bsVendido + Number(o.bsVendido || 0),
  bsPorVender: t.bsPorVender + Number(o.bsPorVender || 0),
}), { vendidas: 0, enTramite: 0, libres: 0, bloqueadas: 0, total: 0, bsVendido: 0, bsPorVender: 0 }));

/** Sobre lo VENDIBLE: una categoría con la mitad bloqueada por obra no está al 50 % de ventas. */
const pctVendido = computed(() => {
  const vendible = totales.value.total - totales.value.bloqueadas;
  return vendible <= 0 ? 0 : (totales.value.vendidas / vendible) * 100;
});

const barrasOcupacion = computed(() => ocupacion.value.map((o) => ({
  etiqueta: o.categoria || '(sin categoría)',
  valor: o.vendidas,
  total: Math.max(0, o.total - o.bloqueadas),
  detalle: `${o.enTramite} en trámite · ${o.libres} libres`
    + (o.bloqueadas ? ` · ${o.bloqueadas} bloqueadas` : '')
    + ` · ${bs(o.bsVendido)} Bs vendidos, ${bs(o.bsPorVender)} por vender`,
})));

const vendedores = computed(() => (datos.value.vendedores || [])
  .filter((v) => !soloConVentas.value || v.ventas > 0));

const barrasVendedores = computed(() => vendedores.value
  .slice(0, 12)
  .map((v) => ({ label: v.vendedor || '(sin nombre)', valor: Number(v.totalBs || 0) })));

const avance = computed(() => {
  const todo = datos.value.avance || [];
  if (!dias.value) return todo;
  return todo.slice(-dias.value);
});

const puntosAvance = computed(() => avance.value.map((d) => ({
  x: d.fecha ? new Date(d.fecha + 'T00:00:00').toLocaleDateString('es-BO', { day: '2-digit', month: 'short' }) : '—',
  y: Number(d.acumuladoBs || 0),
})));

const cobros = computed(() => datos.value.cobros || { cortes: [], pendientes: [], totalCobrado: 0, totalPendiente: 0 });

/** Las más antiguas primero: son las que hay que reclamar hoy. */
const pendientes = computed(() => (cobros.value.pendientes || []).slice(0, 15));

// ---- descargas ----
const bajando = ref('');
async function bajar(nombre, formato) {
  if (bajando.value) return;
  bajando.value = `${nombre}-${formato}`;
  try {
    const ruta = `/api/app/analisis/${nombre}/${formato}`;
    const archivo = `${nombre}.${formato === 'pdf' ? 'pdf' : 'xlsx'}`;
    if (formato === 'pdf') await descargarPdf(ruta, archivo);
    else await descargarArchivo(ruta, archivo);
    toast('Reporte descargado', 'ok');
  } catch (e) {
    toast(e.message || 'No se pudo descargar', 'error');
  } finally {
    bajando.value = '';
  }
}

/*
 * ---- en vivo ----
 *
 * No hace falta un canal nuevo: el store ya mantiene UNA conexión WebSocket para toda la
 * aplicación y aplica cada cambio de caseta sobre su lista. Aquí basta con vigilar una señal
 * barata derivada de esa lista —cuántas hay vendidas y cuántas en trámite— y recalcular
 * cuando se mueva. Abrir otra conexión desde esta pantalla duplicaría los mensajes.
 *
 * Se vigila el CONTEO y no la lista entera: un `watch` profundo sobre ~550 objetos se dispara
 * con cualquier cambio de geometría del Editor, que no altera ni una cifra de este tablero.
 */
const pulso = computed(() => {
  let vendidas = 0;
  let tramite = 0;
  for (const p of tienda.puestos) {
    if (p.estado === 'O') vendidas++;
    else if (p.estado === 'T') tramite++;
  }
  return `${vendidas}/${tramite}/${tienda.puestos.length}`;
});

let pendienteRecarga = null;
let dejarDeVigilar = null;

onMounted(async () => {
  await cargar();
  await tienda.asegurar();
  // `watch` y no `watchEffect`: hay que ignorar el primer valor, que es el estado con el que
  // se acaba de cargar la pantalla. Si no, se recargaría dos veces al entrar.
  dejarDeVigilar = watch(pulso, () => {
    // Agrupado: registrar una venta de veinte casetas son veinte mensajes, y recalcular veinte
    // veces seguidas serían veinte consultas pesadas para enseñar el mismo número.
    if (pendienteRecarga) return;
    pendienteRecarga = setTimeout(() => {
      pendienteRecarga = null;
      cargar(true);
    }, 1500);
  });
});

onUnmounted(() => {
  if (dejarDeVigilar) dejarDeVigilar();
  if (pendienteRecarga) clearTimeout(pendienteRecarga);
});

const hace = computed(() => {
  if (!actualizado.value) return '';
  const s = Math.round((Date.now() - actualizado.value) / 1000);
  return s < 60 ? 'hace un momento' : `hace ${Math.round(s / 60)} min`;
});
</script>

<template>
  <div class="direccion">
    <p v-if="error" class="card aviso-error">{{ error }}</p>
    <div v-else-if="cargando" class="vacio">Cargando el tablero…</div>

    <template v-else>
      <!-- Cifras de cabecera. Son números sueltos, no gráficos: un gráfico de una sola cifra
           es un adorno que tapa el dato. -->
      <section class="tarjetas">
        <div class="card kpi principal">
          <span class="rotulo">Vendido</span>
          <strong class="grande">{{ bs(totales.bsVendido) }} <span class="moneda">Bs</span></strong>
          <span class="pie">{{ ent(totales.vendidas) }} casetas · {{ Number(pctVendido).toFixed(1) }}% de lo vendible</span>
        </div>
        <div class="card kpi">
          <span class="rotulo">Por vender</span>
          <strong>{{ bs(totales.bsPorVender) }} <span class="moneda">Bs</span></strong>
          <span class="pie">{{ ent(totales.libres) }} libres · {{ ent(totales.enTramite) }} en trámite</span>
        </div>
        <div class="card kpi">
          <span class="rotulo">Cobrado</span>
          <strong>{{ bs(cobros.totalCobrado) }} <span class="moneda">Bs</span></strong>
          <span class="pie">con comprobante entregado</span>
        </div>
        <div class="card kpi" :class="{ alerta: Number(cobros.totalPendiente) > 0 }">
          <span class="rotulo">Pendiente de cobro</span>
          <strong>{{ bs(cobros.totalPendiente) }} <span class="moneda">Bs</span></strong>
          <span class="pie">{{ ent((cobros.pendientes || []).length) }} ventas sin comprobante</span>
        </div>
      </section>

      <!-- Los filtros, en una fila sobre los gráficos. -->
      <section class="card filtros">
        <select v-model="filtroCategoria" class="control">
          <option value="">Todas las categorías</option>
          <option v-for="c in categorias" :key="c" :value="c">{{ c }}</option>
        </select>
        <select v-model.number="dias" class="control">
          <option :value="0">Todo el periodo</option>
          <option :value="7">Últimos 7 días con ventas</option>
          <option :value="30">Últimos 30 días con ventas</option>
        </select>
        <label class="check">
          <input v-model="soloConVentas" type="checkbox" />
          Solo vendedores con ventas
        </label>
        <span class="muted actualizado">Actualizado {{ hace }}</span>
        <button class="btn btn-fantasma" @click="cargar()">Actualizar</button>
      </section>

      <div class="rejilla">
        <!-- 1. Cuánto queda por vender -->
        <section class="card bloque">
          <header class="cab">
            <div>
              <h3>Ocupación del plano</h3>
              <p class="muted chico">Cuánto se vendió de lo que se podía vender, por categoría.</p>
            </div>
            <div class="descargas">
              <button class="btn btn-fantasma chico" :disabled="!!bajando" @click="bajar('ocupacion', 'pdf')">PDF</button>
              <button class="btn btn-fantasma chico" :disabled="!!bajando" @click="bajar('ocupacion', 'excel')">Excel</button>
            </div>
          </header>
          <BarraProgreso :items="barrasOcupacion" vacio="Todavía no hay casetas en el plano." />
        </section>

        <!-- 2. Quién vende -->
        <section class="card bloque">
          <header class="cab">
            <div>
              <h3>Ranking de vendedores</h3>
              <p class="muted chico">Por importe vendido. Se muestran los 12 primeros.</p>
            </div>
            <div class="descargas">
              <button class="btn btn-fantasma chico" :disabled="!!bajando" @click="bajar('vendedores', 'pdf')">PDF</button>
              <button class="btn btn-fantasma chico" :disabled="!!bajando" @click="bajar('vendedores', 'excel')">Excel</button>
            </div>
          </header>
          <BarrasHorizontales :items="barrasVendedores" :formato="(v) => bs(v) + ' Bs'" />

          <!-- La tabla no es un extra: es la lectura alternativa que exige tener un gráfico,
               y además trae área, carrera y ticket medio, que no caben en una barra. -->
          <details class="detalle-tabla">
            <summary>Ver la tabla completa ({{ vendedores.length }})</summary>
            <div class="tabla-scroll">
              <table class="tabla">
                <thead>
                  <tr><th>Vendedor</th><th>Área</th><th>Carrera</th>
                      <th class="num">Ventas</th><th class="num">Casetas</th>
                      <th class="num">Total Bs</th><th class="num">Media</th></tr>
                </thead>
                <tbody>
                  <tr v-for="v in vendedores" :key="v.usuarioId">
                    <td>{{ v.vendedor || '(sin nombre)' }}</td>
                    <td>{{ v.area || '—' }}</td>
                    <td class="carrera">{{ v.carrera || '—' }}</td>
                    <td class="num">{{ ent(v.ventas) }}</td>
                    <td class="num">{{ ent(v.casetas) }}</td>
                    <td class="num">{{ bs(v.totalBs) }}</td>
                    <td class="num">{{ bs(v.ticketMedio) }}</td>
                  </tr>
                  <tr v-if="!vendedores.length"><td colspan="7" class="vacio">Sin ventas todavía.</td></tr>
                </tbody>
              </table>
            </div>
          </details>
        </section>

        <!-- 3. Cuánto se cobró -->
        <section class="card bloque">
          <header class="cab">
            <div>
              <h3>Cobros</h3>
              <p class="muted chico">
                El comprobante manda sobre la forma de pago: marcar «contado» dice cómo se pagó,
                no que el recibo exista.
              </p>
            </div>
            <div class="descargas">
              <button class="btn btn-fantasma chico" :disabled="!!bajando" @click="bajar('cobros', 'pdf')">PDF</button>
              <button class="btn btn-fantasma chico" :disabled="!!bajando" @click="bajar('cobros', 'excel')">Excel</button>
            </div>
          </header>
          <div class="tabla-scroll">
            <table class="tabla">
              <thead><tr><th>Concepto</th><th class="num">Ventas</th><th class="num">Casetas</th><th class="num">Bs</th></tr></thead>
              <tbody>
                <tr v-for="c in cobros.cortes" :key="c.concepto">
                  <td>{{ c.concepto }}</td>
                  <td class="num">{{ ent(c.ventas) }}</td>
                  <td class="num">{{ ent(c.casetas) }}</td>
                  <td class="num">{{ bs(c.totalBs) }}</td>
                </tr>
                <tr v-if="!cobros.cortes.length"><td colspan="4" class="vacio">Sin ventas todavía.</td></tr>
              </tbody>
            </table>
          </div>

          <template v-if="pendientes.length">
            <h4 class="sub">Las más antiguas sin comprobante</h4>
            <ul class="pendientes">
              <li v-for="p in pendientes" :key="p.inscripcionId">
                <span class="quien">
                  <strong>{{ p.entidad || '(sin entidad)' }}</strong>
                  <span class="muted"> · {{ p.vendedor || '—' }}</span>
                </span>
                <span class="cuanto">
                  {{ bs(p.totalBs) }} Bs
                  <!-- Los días son la razón de que esta lista exista: dicen a quién llamar hoy. -->
                  <span class="dias" :class="{ viejo: p.dias >= 7 }">{{ p.dias }} d</span>
                </span>
              </li>
            </ul>
          </template>
        </section>

        <!-- 4. A qué ritmo -->
        <section class="card bloque ancho">
          <header class="cab">
            <div>
              <h3>Avance en el tiempo</h3>
              <p class="muted chico">Bolivianos acumulados. Solo días con ventas.</p>
            </div>
            <div class="descargas">
              <button class="btn btn-fantasma chico" :disabled="!!bajando" @click="bajar('avance', 'pdf')">PDF</button>
              <button class="btn btn-fantasma chico" :disabled="!!bajando" @click="bajar('avance', 'excel')">Excel</button>
            </div>
          </header>
          <LineaTiempo :puntos="puntosAvance" :formato="(v) => bs(v) + ' Bs'" :alto="200" />
          <p v-if="avance.length" class="muted chico pie-linea">
            {{ avance.length }} día(s) con ventas ·
            {{ ent(avance[avance.length - 1].acumuladoCasetas) }} casetas acumuladas
          </p>
        </section>
      </div>
    </template>
  </div>
</template>

<style scoped>
.direccion { display: flex; flex-direction: column; gap: 1rem; }
.vacio { padding: 2rem; text-align: center; color: var(--muted); }
.aviso-error { padding: 0.9rem; }
.muted { color: var(--muted); }
.chico { font-size: 0.82rem; }

/* auto-fit: con cuatro tarjetas fijas, en una pantalla mediana la última se salía. */
.tarjetas { display: grid; gap: 0.75rem; grid-template-columns: repeat(auto-fit, minmax(min(210px, 100%), 1fr)); }
.kpi { padding: 1rem; display: flex; flex-direction: column; gap: 0.2rem; }
.rotulo { font-size: 0.72rem; font-weight: 800; text-transform: uppercase; letter-spacing: 0.06em; color: var(--muted); }
.kpi strong { font-size: 1.5rem; font-variant-numeric: tabular-nums; line-height: 1.15; }
/* La cifra que se mira primero, más grande que las demás: si todas pesan igual, no hay
   jerarquía y hay que leer las cuatro para encontrar la que importa. */
.kpi.principal strong.grande { font-size: 2rem; color: var(--acento); }
.moneda { font-size: 0.9rem; font-weight: 600; color: var(--muted); }
.pie { font-size: 0.8rem; color: var(--muted); }
/* Ámbar y no rojo: hay dinero por cobrar, no una avería. */
.kpi.alerta { border-left: 3px solid var(--tramite); }

.filtros { padding: 0.8rem 1rem; display: flex; gap: 0.7rem; align-items: center; flex-wrap: wrap; }
.check { display: flex; align-items: center; gap: 0.4rem; font-size: 0.9rem; white-space: nowrap; }
.actualizado { margin-left: auto; font-size: 0.82rem; }

/* `minmax(min(340px, 100%), 1fr)` y no `minmax(340px, 1fr)`: con el mínimo fijo, en una
   pantalla de 400 px la pista de la rejilla seguía midiendo 340 y —peor— `1fr` equivale a
   `minmax(auto, 1fr)`, cuyo mínimo es el min-content del contenido. Una tabla dentro hacía
   crecer la columna a 577 px y la página entera se desplazaba de lado. El `min(…, 100%)` deja
   que la pista se encoja con la ventana, y el `min-width: 0` de abajo corta la otra mitad del
   problema: sin él, el contenido sigue imponiendo su ancho mínimo. */
.rejilla { display: grid; gap: 1rem; grid-template-columns: repeat(auto-fit, minmax(min(340px, 100%), 1fr)); }
.bloque { padding: 1rem; display: flex; flex-direction: column; gap: 0.8rem; min-width: 0; }
.bloque.ancho { grid-column: 1 / -1; }
/* `nowrap` y el título encogible: con `wrap`, la descripción de un bloque empujaba los botones
   a la línea de abajo y la del bloque de al lado no, así que cada tarjeta colocaba sus
   descargas en un sitio distinto. Ahora los botones se quedan siempre arriba a la derecha y lo
   que cede es el texto, que para eso se puede partir en varias líneas. */
.cab { display: flex; justify-content: space-between; align-items: flex-start; gap: 0.8rem; flex-wrap: nowrap; }
.cab > div:first-child { min-width: 0; }
.cab h3 { margin: 0; font-size: 1.05rem; }
.cab p { margin: 0.15rem 0 0; line-height: 1.4; }
.descargas { display: flex; gap: 0.35rem; flex: none; }
.btn.chico { padding: 0.3rem 0.6rem; font-size: 0.8rem; }

/* La tabla es lo único que puede pasarse de ancho; se desplaza ella, no la página. */
/* `max-width: 100%` ademas del `min-width: 0`: sin el, la tabla de siete columnas estira su
   propio contenedor y el desplazamiento lateral se lo come la PAGINA en vez de la tabla. A 400
   px eso deja el tablero moviendose de lado, que es exactamente lo que se quiere evitar. */
.tabla-scroll { overflow-x: auto; min-width: 0; max-width: 100%; }
.detalle-tabla { min-width: 0; }
.tabla { width: 100%; }
.tabla .num { text-align: right; font-variant-numeric: tabular-nums; white-space: nowrap; }
.carrera { max-width: 16ch; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.detalle-tabla summary { cursor: pointer; font-size: 0.88rem; color: var(--acento); }
.sub { margin: 0.4rem 0 0; font-size: 0.9rem; }

.pendientes { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 0.35rem; }
.pendientes li {
  display: flex; justify-content: space-between; align-items: baseline; gap: 0.8rem;
  padding-bottom: 0.35rem; border-bottom: 1px solid var(--border); font-size: 0.9rem;
}
.pendientes li:last-child { border-bottom: none; }
.quien { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.cuanto { white-space: nowrap; font-variant-numeric: tabular-nums; }
.dias {
  margin-left: 0.4rem; padding: 0.05rem 0.4rem; border-radius: 999px;
  font-size: 0.72rem; font-weight: 700; color: var(--muted);
  border: 1px solid var(--border);
}
/* A partir de una semana deja de ser "reciente" y pasa a ser algo que reclamar. */
.dias.viejo { color: var(--tramite); border-color: color-mix(in srgb, var(--tramite) 45%, transparent); }
.pie-linea { margin: 0; }

@media (max-width: 480px) {
  .rejilla { grid-template-columns: 1fr; }
  .actualizado { margin-left: 0; }
}
</style>
