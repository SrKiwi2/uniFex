<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue';
import { apiFetch } from '../api';
import { url } from '../config';
import { toast } from '../ui/toast';
import { usePuestosStore } from '../stores/puestos.js';
import UiModal from '../components/UiModal.vue';

const tienda = usePuestosStore();
const pestaña = ref('activas');   // 'activas' | 'canceladas' | 'solicitudes'
const conteos = ref({ activas: 0, canceladas: 0 });
const inscripciones = ref([]);
const cargando = ref(true);
const filtro = ref('');
const expandida = ref(null);      // id de la fila desplegada
const detalle = ref(null);        // respuesta de GET /inscripciones/{id}

// --- cancelacion (V10) ---
const modalCancelar = ref(null);  // inscripcion en espera de confirmacion
const motivo = ref('');
const cancelando = ref(false);    // peticion en vuelo

// --- solicitudes de cancelacion (V11) ---
const solicitudes = ref([]);      // pendientes, en orden de llegada
const resueltas = ref([]);        // historico de aprobadas/rechazadas
const resolviendo = ref(null);    // id de la solicitud que se esta resolviendo
const modalRechazo = ref(null);   // solicitud en espera de la respuesta del rechazo
const respuestaRechazo = ref('');
const cargandoSolicitudes = ref(false);

const bs = (n) => 'Bs ' + Number(n || 0).toLocaleString('es-BO', { minimumFractionDigits: 2 });
const fecha = (f) => (f ? new Date(f).toLocaleDateString('es-BO') : '');
const fechaHora = (f) => (f ? new Date(f).toLocaleString('es-BO') : '');

const filtradas = computed(() => {
  const q = filtro.value.trim().toLowerCase();
  const base = [...inscripciones.value].sort((a, b) => new Date(b.fecha || b.fechaCancelacion) - new Date(a.fecha || a.fechaCancelacion));
  if (!q) return base;
  return base.filter((i) =>
    [i.entidad, i.promotor, i.nit, i.tipoEntidad, i.motivoCancelacion].some((c) => (c || '').toLowerCase().includes(q))
    || (i.categorias || []).some((c) => c.toLowerCase().includes(q)));
});

// Totales del conjunto filtrado (pie de tabla).
const totales = computed(() => filtradas.value.reduce((acc, i) => {
  acc.total += Number(i.total || 0);
  acc.puestos += i.cantidadPuestos || 0;
  return acc;
}, { total: 0, puestos: 0 }));

/** Trae las dos listas (contadores de las pestanas) y muestra la de la pestana activa. */
async function cargar() {
  cargando.value = true;
  try {
    const [activas, canceladas] = await Promise.all([
      apiFetch('/api/app/inscripciones').then((r) => r.json()),
      apiFetch('/api/app/inscripciones?canceladas=true').then((r) => r.json()),
    ]);
    conteos.value = { activas: activas.length, canceladas: canceladas.length };
    inscripciones.value = pestaña.value === 'activas' ? activas : canceladas;
  } catch (e) {
    toast(e.message, 'error');
  } finally {
    cargando.value = false;
  }
}

function cambiarPestaña(p) {
  if (p === pestaña.value) return;
  pestaña.value = p;
  expandida.value = null;
  detalle.value = null;
  filtro.value = '';
  if (p === 'solicitudes') {
    cargarSolicitudes();
  } else {
    cargar();
  }
}

/** Trae la cola de solicitudes de cancelacion (V11): pendientes y resueltas. */
async function cargarSolicitudes() {
  cargandoSolicitudes.value = true;
  try {
    const [pend, res] = await Promise.all([
      apiFetch('/api/app/solicitudes-cancelacion/pendientes').then((r) => r.json()),
      apiFetch('/api/app/solicitudes-cancelacion/resueltas').then((r) => r.json()),
    ]);
    solicitudes.value = pend || [];
    resueltas.value = res || [];
  } catch (e) {
    toast(e.message, 'error');
  } finally {
    cargandoSolicitudes.value = false;
  }
}

/** Administracion aprueba: el vendedor queda habilitado y se le avisa por WebSocket. */
async function aprobar(s) {
  if (resolviendo.value) return;
  resolviendo.value = s.id;
  try {
    const r = await apiFetch(`/api/app/solicitudes-cancelacion/${s.id}/aprobar`, { method: 'POST' });
    const d = await r.json().catch(() => ({}));
    if (!r.ok || !d.ok) { toast(d.mensaje || 'No se pudo aprobar la solicitud', 'error'); return; }
    toast('Solicitud aprobada. El vendedor ya puede cancelar.', 'ok');
    cargarSolicitudes();
  } catch (e) {
    toast(e.message, 'error');
  } finally {
    resolviendo.value = null;
  }
}

function abrirRechazo(s) {
  modalRechazo.value = s;
  respuestaRechazo.value = '';
}

/** Administracion rechaza con respuesta obligatoria; el vendedor recibe el aviso. */
async function confirmarRechazo() {
  const texto = respuestaRechazo.value.trim();
  if (!texto) { toast('La respuesta del rechazo es obligatoria', 'error'); return; }
  const s = modalRechazo.value;
  if (!s) return;

  resolviendo.value = s.id;
  try {
    const r = await apiFetch(`/api/app/solicitudes-cancelacion/${s.id}/rechazar`, {
      method: 'POST',
      body: JSON.stringify({ respuesta: texto }),
    });
    const d = await r.json().catch(() => ({}));
    if (!r.ok || !d.ok) { toast(d.mensaje || 'No se pudo rechazar la solicitud', 'error'); return; }
    toast('Solicitud rechazada', 'info');
    modalRechazo.value = null;
    cargarSolicitudes();
  } catch (e) {
    toast(e.message, 'error');
  } finally {
    resolviendo.value = null;
  }
}

/** Despliega la fila y pide el detalle completo (incluye la auditoria). */
async function alternar(id) {
  if (expandida.value === id) { expandida.value = null; detalle.value = null; return; }
  expandida.value = id;
  detalle.value = null;
  try {
    const r = await apiFetch(`/api/app/inscripciones/${id}`);
    if (r.ok) detalle.value = await r.json();
    else toast('No se pudo cargar el detalle', 'error');
  } catch (e) {
    // La fila sigue mostrando lo del listado aunque el detalle falle.
    toast(e.message, 'error');
  }
}

function abrirCancelar(i) {
  modalCancelar.value = i;
  motivo.value = '';
}

/** Cancela la venta. El backend exige el motivo; aqui se exige antes de mandarlo. */
async function confirmarCancelacion() {
  const texto = motivo.value.trim();
  if (!texto) { toast('El motivo es obligatorio', 'error'); return; }
  const id = modalCancelar.value?.id;
  if (!id) return;

  cancelando.value = true;
  try {
    const r = await apiFetch(`/api/app/inscripciones/${id}/cancelar`, {
      method: 'POST',
      body: JSON.stringify({ motivo: texto }),
    });
    const d = await r.json().catch(() => ({}));
    if (!r.ok || !d.ok) { toast(d.mensaje || 'No se pudo cancelar la venta', 'error'); return; }
    toast(d.mensaje || 'Venta cancelada', 'ok');
    modalCancelar.value = null;
    await cargar(); // el mapa se recolorea solo, via WebSocket
  } catch (e) {
    toast(e.message, 'error');
  } finally {
    cancelando.value = false;
  }
}

const etiquetaAccion = (a) => ({
  REGISTRO: 'Registro de venta',
  COMPROBANTE: 'Comprobante adjuntado',
  CANCELACION: 'Cancelación',
  SOLICITUD_CANCELACION: 'Solicitud de cancelación',
  APROBACION_CANCELACION: 'Cancelación aprobada',
  RECHAZO_CANCELACION: 'Cancelación rechazada',
}[a] || a);

const origenLabel = (o) => (o === 'APK' ? 'APK (móvil)' : 'Web');

// Tiempo real (V11): cuando llega una solicitud nueva, la cola se refresca sola.
function onNotificacion(n) {
  if (!n || n.tipo !== 'SOLICITUD_CANCELACION') return;
  toast(`Nueva ${n.mensaje || 'solicitud de cancelación'}`, 'info');
  cargarSolicitudes();
}

let quitarOyente = null;
onMounted(() => {
  tienda.asegurar();
  quitarOyente = tienda.registrarNotificaciones(onNotificacion);
  cargar();
});
onUnmounted(() => { if (quitarOyente) quitarOyente(); });
</script>

<template>
  <!-- Pestanas: las activas son las ventas vigentes; las canceladas, el historico;
       las solicitudes, la cola de cancelacion con aprobacion (V11). -->
  <div class="pestanas" role="tablist">
    <button class="pestana" :class="{ activa: pestaña === 'activas' }" role="tab"
            @click="cambiarPestaña('activas')">
      Activas <span class="cuenta">{{ conteos.activas }}</span>
    </button>
    <button class="pestana" :class="{ activa: pestaña === 'canceladas' }" role="tab"
            @click="cambiarPestaña('canceladas')">
      Canceladas <span class="cuenta cuenta-roja">{{ conteos.canceladas }}</span>
    </button>
    <button class="pestana" :class="{ activa: pestaña === 'solicitudes' }" role="tab"
            @click="cambiarPestaña('solicitudes')">
      Solicitudes <span class="cuenta cuenta-aviso">{{ solicitudes.length }}</span>
    </button>
  </div>

  <template v-if="pestaña !== 'solicitudes'">
  <div class="fila entre encabezado">
    <input v-model="filtro" class="control busca" placeholder="Buscar por entidad, vendedor, NIT, categoría o motivo…" />
    <span class="muted conteo">{{ filtradas.length }} inscripciones</span>
  </div>

  <div class="card">
    <div v-if="cargando" class="vacio">Cargando…</div>
    <div v-else-if="filtradas.length === 0" class="vacio">
      {{ pestaña === 'activas' ? 'No hay inscripciones que mostrar.' : 'No hay inscripciones canceladas.' }}
    </div>
    <table v-else class="tabla">
      <thead>
        <tr>
          <th>Entidad</th><th>Vendedor</th><th>Tipo</th><th>Categorías</th>
          <th class="der">Casetas</th><th>Pago</th><th>Fecha</th><th class="der">Total</th><th></th>
        </tr>
      </thead>
      <tbody>
        <template v-for="i in filtradas" :key="i.id">
          <tr class="fila-ins" @click="alternar(i.id)">
            <td>
              <strong>{{ i.entidad || '—' }}</strong>
              <div class="nit muted" v-if="i.nit">NIT {{ i.nit }}</div>
              <span v-if="i.motivoCancelacion" class="motivo" :title="i.motivoCancelacion">
                Motivo: {{ i.motivoCancelacion }}
              </span>
            </td>
            <td>{{ i.promotor || '—' }}</td>
            <td>{{ i.tipoEntidad || '—' }}</td>
            <td>
              <span v-for="c in i.categorias" :key="c" class="chip">{{ c }}</span>
            </td>
            <td class="der">{{ i.cantidadPuestos }}</td>
            <td>
              <span class="badge" :class="i.motivoCancelacion ? 'badge-danger' : (i.pagoContado ? 'badge-ok' : 'badge-muted')">
                {{ i.motivoCancelacion ? 'Cancelada' : (i.pagoContado ? 'Contado' : 'Crédito') }}
              </span>
            </td>
            <td>{{ fecha(i.fecha) }}</td>
            <td class="der"><strong>{{ bs(i.total) }}</strong></td>
            <td class="acciones">
              <button v-if="!i.motivoCancelacion" class="btn btn-peligro btn-sm"
                      title="Cancelar la venta y liberar sus casetas"
                      @click.stop="abrirCancelar(i)">Cancelar</button>
            </td>
          </tr>
          <tr v-if="expandida === i.id" class="detalle">
            <td colspan="9">
              <div v-if="!detalle" class="muted">Cargando detalle…</div>
              <div v-else class="detalle-cont">
                <div class="bloque">
                  <span class="etq">Entidad</span>
                  <div>{{ detalle.entidad || '—' }}</div>
                  <div class="muted" v-if="detalle.nit">NIT {{ detalle.nit }}</div>
                  <div class="muted" v-if="detalle.tipoEntidad">{{ detalle.tipoEntidad }}</div>
                  <div class="muted" v-if="detalle.representanteLegal">Rep. legal: {{ detalle.representanteLegal }} (CI {{ detalle.ciRepresentante || '—' }})</div>
                  <div class="muted" v-if="detalle.objeto">{{ detalle.objeto }}</div>
                </div>
                <div class="bloque">
                  <span class="etq">Responsables</span>
                  <div v-for="(r, idx) in detalle.responsables" :key="idx">
                    <strong>{{ r.nombreCompleto || '—' }}</strong>
                    <span class="chip" :class="r.esTitular ? 'chip-titular' : ''">{{ r.esTitular ? 'Titular' : 'Acompañante' }}</span>
                    <div class="muted">CI {{ r.ci || '—' }} · {{ r.correo || 'sin correo' }} · {{ r.celular || 'sin celular' }}</div>
                  </div>
                  <div v-if="!detalle.responsables?.length" class="muted">Sin responsables</div>
                </div>
                <div class="bloque">
                  <span class="etq">Casetas (costo congelado)</span>
                  <div v-for="(p, idx) in detalle.puestos" :key="idx">
                    <span class="chip-puesto">{{ p.categoria }} {{ p.codigo }}</span>
                    <span class="muted"> {{ bs(p.costo) }}</span>
                  </div>
                  <div v-if="!detalle.puestos?.length" class="muted">Sin casetas</div>
                </div>
                <div class="bloque">
                  <span class="etq">Pago</span>
                  <div>
                    <span class="badge" :class="detalle.pagoContado ? 'badge-ok' : 'badge-muted'">
                      {{ detalle.pagoContado ? 'Contado' : 'Crédito' }}
                    </span>
                    <span v-if="detalle.numComprobante" class="muted"> · Nº {{ detalle.numComprobante }}</span>
                    <span v-if="detalle.entidadBancaria" class="muted"> · {{ detalle.entidadBancaria }}</span>
                  </div>
                  <a v-if="detalle.imgComprobante" :href="url(`/files/${detalle.imgComprobante}`)"
                     target="_blank" rel="noopener" class="btn btn-sm">Ver comprobante ↗</a>
                  <div class="muted" v-if="detalle.edicion">Edición: {{ detalle.edicion }}</div>
                  <div class="muted" v-if="detalle.fechaCompra">Venta: {{ fechaHora(detalle.fechaCompra) }}</div>
                  <div class="muted" v-if="detalle.promotor">Registró: {{ detalle.promotor }}</div>
                </div>
                <div v-if="detalle.motivoCancelacion" class="bloque cancelacion">
                  <span class="etq">Cancelación</span>
                  <div class="motivo-detalle">{{ detalle.motivoCancelacion }}</div>
                  <div class="muted">{{ fechaHora(detalle.fechaCancelacion) }} · {{ detalle.canceladaPor || '—' }} · {{ origenLabel(detalle.origenCancelacion) }}</div>
                </div>
                <div class="bloque auditoria">
                  <span class="etq">Auditoría</span>
                  <div v-if="!detalle.auditoria?.length" class="muted">Sin eventos registrados.</div>
                  <ul v-else class="traza">
                    <li v-for="(a, idx) in detalle.auditoria" :key="idx">
                      <span class="chip" :class="a.accion === 'CANCELACION' ? 'chip-rojo' : ''">{{ etiquetaAccion(a.accion) }}</span>
                      <div class="traza-cuerpo">
                        <div>{{ a.detalle || '—' }}</div>
                        <div class="muted">{{ fechaHora(a.fecha) }} · {{ a.usuarioNombre || '—' }} · {{ origenLabel(a.origen) }}</div>
                      </div>
                    </li>
                  </ul>
                </div>
              </div>
            </td>
          </tr>
        </template>
      </tbody>
      <tfoot v-if="!cargando && filtradas.length">
        <tr class="pie">
          <td colspan="4">Totales ({{ filtradas.length }})</td>
          <td class="der">{{ totales.puestos }}</td>
          <td colspan="3"></td>
          <td class="der"><strong>{{ bs(totales.total) }}</strong></td>
        </tr>
      </tfoot>
    </table>
  </div>
  </template>

  <!-- Cola de solicitudes de cancelacion (V11). El admin aprueba o rechaza; el
       vendedor se entera por WebSocket sin recargar nada. -->
  <template v-else>
    <div class="card">
      <div v-if="cargandoSolicitudes" class="vacio">Cargando…</div>
      <div v-else-if="solicitudes.length === 0" class="vacio">
        No hay solicitudes de cancelación pendientes.
      </div>
      <table v-else class="tabla">
        <thead>
          <tr><th>Entidad</th><th>Vendedor</th><th>Motivo</th><th>Recibida</th><th></th></tr>
        </thead>
        <tbody>
          <tr v-for="s in solicitudes" :key="s.id">
            <td><strong>{{ s.entidad || '—' }}</strong></td>
            <td>{{ s.vendedor || '—' }}</td>
            <td class="motivo-celda" :title="s.motivo">{{ s.motivo }}</td>
            <td>{{ fechaHora(s.fechaSolicitud) }}</td>
            <td class="acciones">
              <button class="btn btn-ok btn-sm" :disabled="resolviendo === s.id"
                      @click="aprobar(s)">Aprobar</button>
              <button class="btn btn-peligro btn-sm" :disabled="resolviendo === s.id"
                      @click="abrirRechazo(s)">Rechazar</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div class="card resueltas" v-if="resueltas.length">
      <header>
        <h2>Resueltas recientes</h2>
        <span class="muted">{{ resueltas.length }}</span>
      </header>
      <table class="tabla">
        <thead>
          <tr><th>Entidad</th><th>Vendedor</th><th>Decisión</th><th>Respuesta</th><th>Resuelta</th></tr>
        </thead>
        <tbody>
          <tr v-for="s in resueltas.slice(0, 20)" :key="s.id">
            <td>{{ s.entidad || '—' }}</td>
            <td>{{ s.vendedor || '—' }}</td>
            <td>
              <span class="badge" :class="s.estado === 'APROBADA' ? 'badge-ok' : 'badge-danger'">
                {{ s.estado === 'APROBADA' ? 'Aprobada' : 'Rechazada' }}
              </span>
            </td>
            <td class="motivo-celda">{{ s.respuesta || '—' }}</td>
            <td>{{ fechaHora(s.fechaResolucion) }} · {{ s.resueltoPor || '—' }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </template>

  <UiModal v-if="modalCancelar" titulo="Cancelar venta" @cerrar="modalCancelar = null">
    <p class="aviso">
      Se cancelará la venta de <strong>{{ modalCancelar.entidad }}</strong>
      (<strong>{{ modalCancelar.cantidadPuestos }}</strong> caseta(s), {{ bs(modalCancelar.total) }}).
      Sus casetas volverán a estar <strong>disponibles en el mapa</strong> y la venta
      quedará en el historico de canceladas con este motivo.
    </p>
    <label class="etq" for="motivo-cancelacion">Motivo (obligatorio)</label>
    <textarea id="motivo-cancelacion" v-model="motivo" class="control motivo-texto"
              rows="3" placeholder="Ej.: el cliente desistió del pago / doble reserva…"
              maxlength="500"></textarea>
    <template #pie>
      <button class="btn btn-fantasma" @click="modalCancelar = null">Volver</button>
      <button class="btn btn-peligro" :disabled="cancelando || !motivo.trim()" @click="confirmarCancelacion">
        {{ cancelando ? 'Cancelando…' : 'Cancelar venta' }}
      </button>
    </template>
  </UiModal>

  <!-- Rechazo de una solicitud: la respuesta es obligatoria, es lo que vera el vendedor. -->
  <UiModal v-if="modalRechazo" titulo="Rechazar solicitud de cancelación" @cerrar="modalRechazo = null">
    <p class="aviso">
      Se rechazará la solicitud de <strong>{{ modalRechazo.vendedor || '—' }}</strong> para la
      venta de <strong>{{ modalRechazo.entidad || '—' }}</strong>.
      La respuesta le llegará al vendedor al instante.
    </p>
    <label class="etq" for="respuesta-rechazo">Respuesta al vendedor (obligatoria)</label>
    <textarea id="respuesta-rechazo" v-model="respuestaRechazo" class="control motivo-texto"
              rows="3" placeholder="Ej.: el comprobante de pago aún no se puede verificar…"
              maxlength="500"></textarea>
    <template #pie>
      <button class="btn btn-fantasma" @click="modalRechazo = null">Volver</button>
      <button class="btn btn-peligro" :disabled="resolviendo === modalRechazo.id || !respuestaRechazo.trim()"
              @click="confirmarRechazo">
        {{ resolviendo === modalRechazo.id ? 'Rechazando…' : 'Rechazar solicitud' }}
      </button>
    </template>
  </UiModal>
</template>

<style scoped>
.pestanas { display: flex; gap: 0.4rem; margin-bottom: 1rem; border-bottom: 1px solid var(--border); }
.pestana {
  background: transparent; border: none; border-bottom: 2px solid transparent;
  padding: 0.55rem 0.9rem; font-size: 0.95rem; color: var(--muted); cursor: pointer;
  display: inline-flex; align-items: center; gap: 0.45rem;
}
.pestana:hover { color: var(--texto); }
.pestana.activa { color: var(--acento); border-bottom-color: var(--acento); font-weight: 700; }
.cuenta {
  background: var(--panel-2); border: 1px solid var(--border); border-radius: 999px;
  padding: 0.05rem 0.5rem; font-size: 0.75rem; font-weight: 700;
}
.cuenta-roja { background: var(--danger-suave); color: var(--danger); }
.cuenta-aviso { background: var(--tramite-suave, #fff3cd); color: #8a6d1a; border-color: transparent; }

.motivo-celda { max-width: 320px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.resueltas { margin-top: 1.2rem; padding: 0; }
.resueltas header {
  display: flex; align-items: center; gap: 0.6rem;
  padding: 0.85rem 1.1rem; border-bottom: 1px solid var(--border);
}
.resueltas h2 { margin: 0; font-size: 1rem; }

.encabezado { margin-bottom: 1rem; gap: 0.8rem; }
.busca { max-width: 380px; }
.conteo { font-size: 0.9rem; }
.der { text-align: right; font-variant-numeric: tabular-nums; }
.fila-ins { cursor: pointer; }
.nit { font-size: 0.75rem; }
.motivo {
  display: block; max-width: 340px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
  font-size: 0.75rem; color: var(--danger); font-weight: 600;
}
.chip { display: inline-block; background: var(--panel-2); border: 1px solid var(--border); border-radius: 999px; padding: 0.1rem 0.5rem; font-size: 0.75rem; margin: 0.1rem 0.15rem 0.1rem 0; }
.chip-titular { background: var(--acento-suave); color: var(--acento); border-color: transparent; font-weight: 700; }
.chip-rojo { background: var(--danger-suave); color: var(--danger); border-color: transparent; font-weight: 700; }
.detalle td { background: var(--panel-2); }
.detalle-cont { display: flex; flex-wrap: wrap; gap: 1.5rem; padding: 0.4rem 0; }
.bloque { display: flex; flex-direction: column; gap: 0.3rem; min-width: 200px; max-width: 420px; }
.etq { font-size: 0.72rem; text-transform: uppercase; letter-spacing: 0.04em; color: var(--muted); font-weight: 700; }
.chips { display: flex; flex-wrap: wrap; gap: 0.3rem; max-width: 480px; }
.chip-puesto { background: var(--acento-suave); color: var(--acento); border-radius: 999px; padding: 0.15rem 0.55rem; font-size: 0.78rem; font-weight: 600; }
.cancelacion { border-left: 3px solid var(--danger); padding-left: 0.7rem; background: var(--danger-suave); border-radius: 0 8px 8px 0; padding: 0.5rem 0.7rem; }
.motivo-detalle { font-weight: 600; color: var(--danger); }
.auditoria { flex-basis: 100%; max-width: none; }
.traza { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 0.5rem; }
.traza li { display: flex; gap: 0.6rem; align-items: flex-start; }
.traza-cuerpo { display: flex; flex-direction: column; }
.pie td { border-top: 2px solid var(--border); font-weight: 600; }
.aviso { font-size: 0.9rem; line-height: 1.45; }
.motivo-texto { width: 100%; resize: vertical; font-family: inherit; }
</style>
