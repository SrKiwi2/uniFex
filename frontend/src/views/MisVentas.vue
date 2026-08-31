<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue';
import { apiFetch } from '../api';
import { toast } from '../ui/toast';
import { usePuestosStore } from '../stores/puestos.js';
import UiModal from '../components/UiModal.vue';

const tienda = usePuestosStore();
const items = ref([]);       // filas de fn_get_inscripciones: una por (inscripción, categoría)
const resumen = ref({ cantidad: 0, total: 0 });
const cargando = ref(true);
const expandida = ref(null); // id_inscripción abierta
const puestos = ref([]);     // detalle de la inscripción expandida
const ediciones = ref([]);   // ediciones de la feria (V6), para el selector
const edicionSel = ref(null); // null = la edición ACTIVA

// --- cancelacion con aprobacion (V11) ---
// El vendedor SOLICITA cancelar con un motivo; administracion aprueba o rechaza;
// solo con la solicitud aprobada se habilita el boton de cancelar. Todo asincrono:
// llega un aviso por WebSocket y esta pantalla se refresca sola.
const solicitudes = ref({});       // inscripcionId -> solicitud (o ausente si nunca se pidio)
const modalSolicitar = ref(null);  // venta en espera de confirmacion de solicitud
const motivo = ref('');
const enviando = ref(false);       // peticion de solicitud en vuelo
const cancelandoId = ref(null);    // venta cuya cancelacion (ya aprobada) esta en vuelo

const bs = (n) => 'Bs ' + Number(n || 0).toLocaleString('es-BO', { minimumFractionDigits: 2 });

/** Nombre de la edición que se está mostrando (para el encabezado). */
const edicionVisible = computed(() => {
  const e = ediciones.value.find((x) => x.id === edicionSel.value);
  if (e) return e.nombre;
  const activa = ediciones.value.find((x) => x.activa);
  return activa ? activa.nombre : '';
});

async function cargarEdiciones() {
  try {
    const r = await apiFetch('/api/app/ediciones');
    ediciones.value = await r.json();
  } catch { /* sin selector si el endpoint no responde */ }
}

const conEdicion = (base) => (edicionSel.value ? `${base}?edicion=${edicionSel.value}` : base);

/** Agrupa las filas por inscripción: cada una con su entidad, fecha, pago, categorías y total. */
const inscripciones = computed(() => {
  const m = new Map();
  for (const f of items.value) {
    const id = f.id_inscripcion;
    if (!m.has(id)) {
      m.set(id, {
        id, entidad: f.nombre_entidad, tipo: f.tipo_entidad,
        fecha: f.fecha_registro, contado: f.pago_contado,
        categorias: [], total: 0,
      });
    }
    const ins = m.get(id);
    ins.categorias.push(f.categoria);
    ins.total += Number(f.total_costo || 0);
  }
  return [...m.values()].sort((a, b) => new Date(b.fecha) - new Date(a.fecha));
});

async function cargar() {
  cargando.value = true;
  try {
    const r = await apiFetch(conEdicion('/api/app/mis-ventas'));
    const d = await r.json();
    items.value = d.items || [];
    resumen.value = { cantidad: d.cantidad || 0, total: d.total || 0 };
  } catch (e) {
    toast(e.message, 'error');
  } finally {
    cargando.value = false;
  }
}

/** Estado de la solicitud de cancelacion de CADA venta propia, en un mapa por inscripcion. */
async function cargarSolicitudes() {
  try {
    const r = await apiFetch('/api/app/mis-solicitudes-cancelacion');
    if (!r.ok) return;
    const lista = await r.json();
    solicitudes.value = {};
    for (const s of lista) {
      // Solo la ultima solicitud de cada venta: un vendedor puede reintentar tras un rechazo.
      if (!solicitudes.value[s.inscripcionId] || s.id > solicitudes.value[s.inscripcionId].id) {
        solicitudes.value[s.inscripcionId] = s;
      }
    }
  } catch { /* la pantalla sigue siendo util sin esta seccion */ }
}

async function alternar(id) {
  if (expandida.value === id) { expandida.value = null; return; }
  expandida.value = id;
  puestos.value = [];
  try {
    const r = await apiFetch(conEdicion(`/api/app/mis-ventas/${id}/puestos`));
    if (r.ok) puestos.value = await r.json();
  } catch (e) {
    toast(e.message, 'error');
  }
}

const fecha = (f) => (f ? new Date(f).toLocaleDateString('es-BO') : '');

// ---------------------------------------------------------------- cancelacion

function abrirSolicitud(ins) {
  modalSolicitar.value = ins;
  motivo.value = '';
}

/** El vendedor pide cancelar su venta con un motivo: queda a la espera del admin. */
async function confirmarSolicitud() {
  const texto = motivo.value.trim();
  if (!texto) { toast('El motivo es obligatorio', 'error'); return; }
  const id = modalSolicitar.value?.id;
  if (!id) return;

  enviando.value = true;
  try {
    const r = await apiFetch(`/api/app/inscripciones/${id}/solicitar-cancelacion`, {
      method: 'POST',
      body: JSON.stringify({ motivo: texto }),
    });
    const d = await r.json().catch(() => ({}));
    if (!r.ok || !d.ok) { toast(d.mensaje || 'No se pudo enviar la solicitud', 'error'); return; }
    toast(d.mensaje || 'Solicitud enviada', 'ok');
    modalSolicitar.value = null;
    await cargarSolicitudes();
  } catch (e) {
    toast(e.message, 'error');
  } finally {
    enviando.value = false;
  }
}

/**
 * Cancela la venta SOLO cuando administracion ya aprobo la solicitud (V11).
 * El motivo no se pide aqui: es el que se escribio al solicitar.
 */
async function cancelarVenta(id) {
  cancelandoId.value = id;
  try {
    const r = await apiFetch(`/api/app/inscripciones/${id}/cancelar`, {
      method: 'POST',
      body: JSON.stringify({}), // el backend usa el motivo de la solicitud aprobada
    });
    const d = await r.json().catch(() => ({}));
    if (!r.ok || !d.ok) { toast(d.mensaje || 'No se pudo cancelar la venta', 'error'); return; }
    toast(d.mensaje || 'Venta cancelada', 'ok');
    await Promise.all([cargar(), cargarSolicitudes()]);
  } catch (e) {
    toast(e.message, 'error');
  } finally {
    cancelandoId.value = null;
  }
}

/** Etiqueta corta del estado de la solicitud, para el badge de la fila. */
const etiquetaSolicitud = (s) => ({
  PENDIENTE: 'En espera',
  APROBADA: 'Aprobada',
  RECHAZADA: 'Rechazada',
}[s?.estado] || '');

// ---------------------------------------------------------------- pendientes
// Ventas propias sin comprobante. Van arriba y con los dias que llevan asi, porque una
// venta cobrada a medias es lo unico de esta pantalla sobre lo que hay que ACTUAR.
const pendientes = ref([]);
const subiendo = ref(null);   // id de la inscripcion cuyo comprobante esta subiendo
const entradaArchivo = ref(null);
const idParaComprobante = ref(null);

async function cargarPendientes() {
  try {
    const r = await apiFetch('/api/app/inscripciones/mis-pendientes');
    if (r.ok) pendientes.value = await r.json();
  } catch { /* la pantalla sigue siendo util sin esta seccion */ }
}

/** Abre el selector de archivo (en el móvil, la cámara) para esa venta. */
function elegirComprobante(id) {
  idParaComprobante.value = id;
  entradaArchivo.value?.click();
}

async function subirComprobante(evento) {
  const archivo = evento.target.files?.[0];
  const id = idParaComprobante.value;
  evento.target.value = ''; // permitir volver a elegir el mismo archivo
  if (!archivo || !id) return;

  subiendo.value = id;
  try {
    const datos = new FormData();
    datos.append('archivo', archivo);
    const r = await apiFetch(`/api/app/inscripciones/${id}/comprobante`, {
      method: 'POST', body: datos,
    });
    const d = await r.json().catch(() => ({}));
    if (!r.ok || !d.ok) { toast(d.mensaje || 'No se pudo subir el comprobante', 'error'); return; }
    toast('Comprobante adjuntado', 'ok');
    await cargarPendientes();
  } catch (e) {
    toast(e.message, 'error');
  } finally {
    subiendo.value = null;
    idParaComprobante.value = null;
  }
}

/**
 * Descarga el recibo. Se pide con apiFetch (lleva el token) y se abre desde un blob:
 * un <a href> normal no puede mandar la cabecera Authorization, asi que caeria en un 401.
 */
async function verRecibo(id) {
  try {
    const r = await apiFetch(`/api/app/inscripciones/${id}/recibo`);
    if (!r.ok) { toast('No se pudo generar el recibo', 'error'); return; }
    const url = URL.createObjectURL(await r.blob());
    window.open(url, '_blank');
    // Liberar el objeto: si no, el blob se queda en memoria toda la sesion.
    setTimeout(() => URL.revokeObjectURL(url), 60000);
  } catch (e) {
    toast(e.message, 'error');
  }
}

// ---------------------------------------------------------------- tiempo real
/** Un aviso del backend: aprobaron o rechazaron mi solicitud. Recargar y avisar. */
function onNotificacion(n) {
  if (!n || !['APROBACION_CANCELACION', 'RECHAZO_CANCELACION'].includes(n.tipo)) return;
  toast(n.mensaje || 'Tu solicitud de cancelacion fue resuelta',
        n.tipo === 'APROBACION_CANCELACION' ? 'ok' : 'error');
  cargarSolicitudes();
  cargar();
}

let quitarOyente = null;
onMounted(() => {
  tienda.asegurar();
  quitarOyente = tienda.registrarNotificaciones(onNotificacion);
  cargarEdiciones();
  cargar();
  cargarPendientes();
  cargarSolicitudes();
});
onUnmounted(() => { if (quitarOyente) quitarOyente(); });
</script>

<template>
  <div class="ediciones">
    <span class="muted">Edición</span>
    <select v-model="edicionSel" @change="cargar" title="Filtrar ventas por edición">
      <option :value="null">Activa ({{ edicionVisible }})</option>
      <option v-for="e in ediciones.filter((x) => !x.activa)" :key="e.id" :value="e.id">
        {{ e.nombre }}
      </option>
    </select>
    <span v-if="edicionSel" class="muted">mostrando {{ edicionVisible }}</span>
  </div>

  <!-- Entrada de archivo unica y oculta: en el móvil abre la cámara o la galería.
       `capture` no se fuerza a propósito — muchos comprobantes ya están en la galería
       como captura de la transferencia. -->
  <input ref="entradaArchivo" type="file" accept="image/*,application/pdf"
         class="oculto" @change="subirComprobante" />

  <!-- Pendientes primero: es lo único de esta pantalla sobre lo que hay que actuar. -->
  <section v-if="pendientes.length" class="card pendientes">
    <header>
      <h2>Falta el comprobante</h2>
      <span class="cuenta">{{ pendientes.length }}</span>
    </header>
    <ul>
      <li v-for="p in pendientes" :key="p.id">
        <div class="quien">
          <strong>{{ p.entidad }}</strong>
          <span class="muted">{{ bs(p.total) }}</span>
        </div>
        <span class="dias" :class="{ urgente: p.diasSinComprobante >= 3 }">
          {{ p.diasSinComprobante === 0 ? 'hoy' : `hace ${p.diasSinComprobante} día${p.diasSinComprobante === 1 ? '' : 's'}` }}
        </span>
        <button class="btn btn-primario btn-sm" :disabled="subiendo === p.id"
                @click="elegirComprobante(p.id)">
          {{ subiendo === p.id ? 'Subiendo…' : '📷 Adjuntar' }}
        </button>
        <button class="btn btn-fantasma btn-sm" title="Ver recibo" @click="verRecibo(p.id)">🧾</button>
      </li>
    </ul>
  </section>

  <div class="tarjetas">
      <div class="card kpi">
        <span class="muted">Inscripciones</span>
        <strong>{{ resumen.cantidad }}</strong>
      </div>
      <div class="card kpi">
        <span class="muted">Total vendido</span>
        <strong>{{ bs(resumen.total) }}</strong>
      </div>
    </div>

    <div class="card">
      <div v-if="cargando" class="vacio">Cargando…</div>
      <div v-else-if="inscripciones.length === 0" class="vacio">
        Aún no tienes ventas confirmadas.
      </div>
      <table v-else class="tabla">
        <thead>
          <tr><th>Entidad</th><th>Tipo</th><th>Categorías</th><th>Pago</th><th>Fecha</th><th class="der">Total</th><th></th></tr>
        </thead>
        <tbody>
          <template v-for="ins in inscripciones" :key="ins.id">
            <tr class="fila-ins" @click="alternar(ins.id)">
              <td>
                <strong>{{ ins.entidad }}</strong>
                <!-- Estado de la solicitud de cancelacion (V11) -->
                <span v-if="solicitudes[ins.id]" class="badge solicitud"
                      :class="`solicitud-${solicitudes[ins.id].estado.toLowerCase()}`"
                      :title="solicitudes[ins.id].respuesta || solicitudes[ins.id].motivo">
                  {{ etiquetaSolicitud(solicitudes[ins.id]) }}
                </span>
              </td>
              <td>{{ ins.tipo }}</td>
              <td>{{ ins.categorias.join(', ') }}</td>
              <td>
                <span class="badge" :class="ins.contado ? 'badge-ok' : 'badge-muted'">
                  {{ ins.contado ? 'Contado' : 'Crédito' }}
                </span>
              </td>
              <td>{{ fecha(ins.fecha) }}</td>
              <td class="der">{{ bs(ins.total) }}</td>
              <td class="acciones">
                <!-- .stop: la fila entera despliega el detalle; el boton no debe hacerlo. -->
                <button class="btn btn-fantasma btn-sm" title="Ver recibo"
                        @click.stop="verRecibo(ins.id)">🧾</button>
                <!-- Cancelar: solo despues de la aprobacion de administracion (V11). -->
                <button v-if="solicitudes[ins.id]?.estado === 'APROBADA'"
                        class="btn btn-peligro btn-sm" title="Administracion aprobo la cancelacion"
                        :disabled="cancelandoId === ins.id" @click.stop="cancelarVenta(ins.id)">
                  {{ cancelandoId === ins.id ? 'Cancelando…' : 'Cancelar venta' }}
                </button>
                <button v-else-if="!solicitudes[ins.id]" class="btn btn-fantasma btn-sm"
                        title="Pedir a administracion que habilite la cancelacion"
                        @click.stop="abrirSolicitud(ins)">Solicitar cancelación</button>
              </td>
            </tr>
            <tr v-if="expandida === ins.id" class="detalle">
              <td colspan="7">
                <div v-if="puestos.length === 0" class="muted">Sin detalle de puestos.</div>
                <div v-else class="puestos">
                  <span v-for="(p, i) in puestos" :key="i" class="chip-puesto">
                    {{ p.categoria || p.nombre_categoria || 'Caseta' }} {{ p.codigo || p.codigo_puesto || '' }}
                  </span>
                </div>
                <div v-if="solicitudes[ins.id]?.estado === 'PENDIENTE'" class="aviso-espera">
                  Tu solicitud de cancelación está en espera de la revisión de administración.
                </div>
                <div v-else-if="solicitudes[ins.id]?.estado === 'RECHAZADA'" class="aviso-rechazo">
                  <strong>Solicitud rechazada.</strong>
                  {{ solicitudes[ins.id].respuesta || 'Sin respuesta registrada.' }}
                </div>
                <div v-else-if="solicitudes[ins.id]?.estado === 'APROBADA'" class="aviso-aprobada">
                  Solicitud aprobada por {{ solicitudes[ins.id].resueltoPor || 'administración' }}.
                  Ya puedes cancelar la venta.
                </div>
              </td>
            </tr>
          </template>
        </tbody>
      </table>
    </div>

  <!-- Solicitud de cancelacion: el vendedor pide con motivo, administracion decide. -->
  <UiModal v-if="modalSolicitar" titulo="Solicitar cancelación" @cerrar="modalSolicitar = null">
    <p class="aviso">
      Se pedirá la cancelación de la venta de <strong>{{ modalSolicitar.entidad }}</strong>
      (<strong>{{ modalSolicitar.categorias.length }}</strong> caseta(s), {{ bs(modalSolicitar.total) }}).
      La solicitud quedará <strong>en espera</strong> de la revisión de administración;
      te avisaremos aquí cuando la aprueben o la rechacen. Solo entonces podrás cancelar.
    </p>
    <label class="etq" for="motivo-solicitud">Motivo (obligatorio)</label>
    <textarea id="motivo-solicitud" v-model="motivo" class="control motivo-texto"
              rows="3" placeholder="Ej.: el cliente desistió del pago / doble reserva…"
              maxlength="500"></textarea>
    <template #pie>
      <button class="btn btn-fantasma" @click="modalSolicitar = null">Volver</button>
      <button class="btn btn-primario" :disabled="enviando || !motivo.trim()" @click="confirmarSolicitud">
        {{ enviando ? 'Enviando…' : 'Enviar solicitud' }}
      </button>
    </template>
  </UiModal>
</template>

<style scoped>
.ediciones { display: flex; align-items: center; gap: 0.5rem; margin-bottom: 1.2rem; }
.ediciones select { border: 1px solid var(--border); border-radius: 8px; padding: 0.4rem 0.6rem; background: #fff; }
.tarjetas { display: grid; gap: 1rem; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); margin-bottom: 1.2rem; }
.kpi { padding: 1.1rem 1.3rem; display: flex; flex-direction: column; gap: 0.3rem; }
.kpi strong { font-size: 1.7rem; }
.der { text-align: right; }
.fila-ins { cursor: pointer; }
.detalle td { background: var(--panel-2); }
.puestos { display: flex; flex-wrap: wrap; gap: 0.4rem; }
.chip-puesto { background: var(--acento-suave); color: var(--acento); border-radius: 999px; padding: 0.2rem 0.6rem; font-size: 0.8rem; font-weight: 600; }
.acciones { text-align: right; white-space: nowrap; }

/* ---- solicitud de cancelacion (V11) ---- */
.solicitud { margin-left: 0.4rem; }
.solicitud-pendiente { background: var(--tramite-suave, #fff3cd); color: #8a6d1a; }
.solicitud-aprobada { background: var(--ok-suave, #d4edda); color: #1e6b34; }
.solicitud-rechazada { background: var(--danger-suave); color: var(--danger); }
.aviso-espera { margin-top: 0.6rem; color: #8a6d1a; font-size: 0.85rem; }
.aviso-rechazo { margin-top: 0.6rem; color: var(--danger); font-size: 0.85rem; }
.aviso-aprobada { margin-top: 0.6rem; color: #1e6b34; font-size: 0.85rem; }

/* ---- pendientes de comprobante ---- */
.oculto { display: none; }
.pendientes {
  margin-bottom: 1.2rem; padding: 0;
  border-left: 3px solid var(--tramite);
}
.pendientes header {
  display: flex; align-items: center; gap: 0.6rem;
  padding: 0.85rem 1.1rem; border-bottom: 1px solid var(--border);
}
.pendientes h2 { margin: 0; font-size: 1rem; }
.pendientes .cuenta {
  background: var(--tramite); color: #fff; border-radius: 999px;
  padding: 0.05rem 0.5rem; font-size: 0.78rem; font-weight: 700;
}
.pendientes ul { list-style: none; margin: 0; padding: 0; }
.pendientes li {
  display: flex; align-items: center; gap: 0.7rem; flex-wrap: wrap;
  padding: 0.7rem 1.1rem; border-bottom: 1px solid var(--border);
}
.pendientes li:last-child { border-bottom: none; }
.pendientes .quien { display: flex; flex-direction: column; min-width: 0; flex: 1; }
.pendientes .dias { font-size: 0.8rem; color: var(--muted); font-weight: 600; white-space: nowrap; }
/* A partir de tres días deja de ser un olvido y pasa a ser un problema de cobro. */
.pendientes .dias.urgente { color: var(--danger); }

@media (max-width: 560px) {
  .pendientes li { align-items: flex-start; }
  .pendientes .quien { flex-basis: 100%; }
}
</style>
