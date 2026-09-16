<script setup>
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue';
import { apiFetch } from '../api';
import { toast } from '../ui/toast';
import { descargarRecibo, compartirRecibo, descargarReciboExtra } from '../ui/descargas';
import { alerta, aviso } from '../ui/alerta';
import { mostrarCarga, ocultarCarga, cambiarTextoCarga } from '../ui/cargando';
import { url as urlApi } from '../config';
import { usePuestosStore } from '../stores/puestos.js';
import UiModal from '../components/UiModal.vue';
import FotosResponsables from '../components/FotosResponsables.vue';
import ArchivoPreview from '../components/ArchivoPreview.vue';
import CampoCelular from '../components/CampoCelular.vue';

const tienda = usePuestosStore();
const items = ref([]);       // filas de fn_get_inscripciones: una por (inscripción, categoría)
const resumen = ref({ cantidad: 0, total: 0 });
const cargando = ref(true);
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

/** Por inscripcion: { conComprobante, sinFoto }. Lo manda el listado. */
const faltantes = ref({});

/**
 * Que le falta a esta venta, en palabras cortas y en orden de urgencia.
 *
 * Sin el comprobante no se emite ninguna credencial, asi que va primero. La lista lo enseña
 * para que no haga falta abrir venta por venta a ver cual tiene trabajo pendiente.
 */
function faltaDe(id) {
  const f = faltantes.value[id];
  if (!f) return [];
  const falta = [];
  if (!f.conComprobante) falta.push('comprobante');
  if (f.sinFoto > 0) falta.push(f.sinFoto === 1 ? '1 foto' : `${f.sinFoto} fotos`);
  return falta;
}

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
    // Lo que le falta a cada venta, para poder marcarlo EN LA LISTA. Llega en la misma
    // respuesta: preguntarlo por fila serian decenas de peticiones.
    const m = {};
    for (const p of d.pendientes || []) m[p.id] = p;
    faltantes.value = m;
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
// Incluye las de CONTADO: marcar contado dice como se pago, no que exista el recibo, y sin
// recibo la credencial del expositor no se puede emitir.
const pendientes = ref([]);
const subiendo = ref(null);   // id de la inscripcion cuyo comprobante esta subiendo
const entradaArchivo = ref(null);
const idParaComprobante = ref(null);
const archivoSeleccionado = ref(null); // para vista previa

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

/** Archivo elegido -> abre vista previa para confirmar antes de subir. */
function onArchivoElegido(evento) {
  const archivo = evento.target.files?.[0];
  evento.target.value = ''; // permitir volver a elegir el mismo archivo
  if (!archivo) return;
  archivoSeleccionado.value = archivo;
}

/** Sube el comprobante confirmado desde la vista previa. */
async function confirmarSubidaComprobante(archivo) {
  const id = idParaComprobante.value;
  if (!archivo || !id) return;

  subiendo.value = id;
  // Subir una foto con la red de la feria puede tardar bastante y no hay nada que lo delate.
  mostrarCarga('Subiendo el comprobante…');
  try {
    const datos = new FormData();
    datos.append('archivo', archivo);
    const r = await apiFetch(`/api/app/inscripciones/${id}/comprobante`, {
      method: 'POST', body: datos,
    });
    const d = await r.json().catch(() => ({}));
    if (!r.ok || !d.ok) { await alerta(d.mensaje || 'No se pudo subir el comprobante', 'error', 0); return; }
    await cargarPendientes();
    if (ficha.value?.id === id) await abrirFicha(id);
    ocultarCarga();
    await aviso('Ya consta el comprobante de pago de esta venta.', 'ok', 3000, 'Comprobante adjuntado');
  } catch (e) {
    await alerta(e.message, 'error', 0);
  } finally {
    ocultarCarga();
    subiendo.value = null;
    idParaComprobante.value = null;
    archivoSeleccionado.value = null;
  }
}

/**
 * Baja el recibo. Va por el mismo helper que el registro de la venta para que se comporte
 * igual en los dos sitios: en el APK, abrir un blob en una pestaña no hace nada.
 */
function verRecibo(id, entidad = null) {
  // Misma carpeta que el dia de la venta: se busca por el nombre del expositor, no por fecha.
  return descargarRecibo(id, entidad);
}

// ---------------------------------------------------------------- ficha de la venta
/*
 * Toda la venta en una ventana, y desde ahi se corrige.
 *
 * La tabla de antes tenia siete columnas y en un telefono solo se veian tres: habia que
 * arrastrarla de lado para leer el total. Y para ver quien atiende la caseta o si falta una
 * foto no habia sitio en ninguna columna. Ahora la lista da lo justo para reconocer la venta
 * —entidad, casetas, total— y el resto vive aqui.
 *
 * El detalle llega en UNA peticion (`/detalle`) en vez de tres. En la feria la red es mala y
 * tres esperas seguidas se notan.
 */
const ficha = ref(null);         // { id, entidad, pago, casetas, responsables }
const cargandoFicha = ref(false);
const editando = ref(null);      // 'entidad' | id del responsable | null
const guardando = ref(false);
const borrador = reactive({});

/** El comprobante que ya se subió, a tamaño mirable. Antes solo se podía reemplazar. */
const verComprobante = ref(false);

async function abrirFicha(id) {
  ficha.value = null;
  cargandoFicha.value = true;
  editando.value = null;
  verComprobante.value = false;
  try {
    const r = await apiFetch(`/api/app/inscripciones/${id}/detalle`);
    const d = await r.json().catch(() => ({}));
    if (!r.ok || d.ok === false) { await alerta(d.mensaje || 'No se pudo abrir la venta', 'error', 0); return; }
    ficha.value = d;
    cargarCupo(id);
  } catch (e) {
    await alerta(e.message, 'error', 0);
  } finally {
    cargandoFicha.value = false;
  }
}

/*
 * ---------------------------------------------------------------- responsables de mas
 *
 * Cada caseta da derecho a DOS responsables, o sea a dos credenciales. Pasada esa cuenta, el
 * cliente puede seguir sumando gente pagando 15 Bs por cabeza, con su comprobante.
 *
 * El cupo se pide al abrir la ficha y NO se calcula aqui: quien decide si cobra es el servidor,
 * y si la pantalla hiciera su propia cuenta las dos podrian discrepar — y la que se ve es esta,
 * asi que el vendedor le diria un precio al cliente que luego el servidor no acepta.
 */
const cupo = ref(null);
const modalResp = reactive({
  abierto: false, nombre: '', paterno: '', materno: '', ci: '', celular: '', comprobante: null, foto: null,
});
const guardandoResp = ref(false);

async function cargarCupo(id) {
  cupo.value = null;
  try {
    const r = await apiFetch(`/api/app/inscripciones/${id}/responsables/cupo`);
    if (r.ok) cupo.value = await r.json();
  } catch {
    // Sin cupo el boton no aparece: es preferible a ofrecer un alta que el servidor rechazara.
  }
}

function abrirAgregarResponsable() {
  Object.assign(modalResp, {
    abierto: true, nombre: '', paterno: '', materno: '', ci: '', celular: '', comprobante: null, foto: null,
  });
}

function elegirComprobanteResp(e) {
  const f = e.target.files?.[0];
  modalResp.comprobante = f || null;
}

function elegirFotoResp(e) {
  const f = e.target.files?.[0];
  modalResp.foto = f || null;
}

async function subirFotoResponsableNuevo(inscripcionId, responsableId) {
  if (!modalResp.foto) return null;
  const datos = new FormData();
  datos.append('archivo', modalResp.foto);
  const r = await apiFetch(`/api/app/inscripciones/${inscripcionId}/responsables/${responsableId}/foto`, {
    method: 'POST',
    body: datos,
  });
  const d = await r.json().catch(() => ({}));
  if (!r.ok || d.ok === false) throw new Error(d.mensaje || 'No se pudo subir la foto del responsable');
  return true;
}

async function enviarWhatsAppResponsableNuevo(inscripcionId, responsableId) {
  const r = await apiFetch('/api/app/credenciales/whatsapp', {
    method: 'POST',
    body: JSON.stringify({ inscripcionId, responsables: [responsableId] }),
  });
  const d = await r.json().catch(() => ({}));
  if (!r.ok || d.ok === false) throw new Error(d.mensaje || 'No se pudo enviar la credencial por WhatsApp');
  return d.credenciales || 1;
}

async function guardarResponsableNuevo() {
  if (guardandoResp.value || !ficha.value) return;
  if (!modalResp.nombre.trim()) { await alerta('Falta el nombre', 'error', 0); return; }
  if (!modalResp.ci.trim()) { await alerta('Falta el C.I.', 'error', 0); return; }
  if (!modalResp.foto) { await alerta('Adjunta la foto para enviar la credencial por WhatsApp.', 'error', 0); return; }
  // El cobro sin comprobante es el agujero por el que se pierden los pagos: se corta aqui y el
  // servidor lo vuelve a comprobar, porque esta pantalla no es una garantia.
  if (!cupo.value?.dentroDelDerecho && !modalResp.comprobante) {
    await alerta('Este responsable tiene un costo de 15 Bs: adjunta el comprobante del pago.', 'error', 0);
    return;
  }
  guardandoResp.value = true;
  mostrarCarga('Agregando al responsable…');
  let velado = true;
  try {
    const fd = new FormData();
    fd.append('nombre', modalResp.nombre.trim().toUpperCase());
    fd.append('paterno', modalResp.paterno.trim().toUpperCase());
    fd.append('materno', modalResp.materno.trim().toUpperCase());
    fd.append('ci', modalResp.ci.trim());
    fd.append('celular', modalResp.celular.trim());
    if (modalResp.comprobante) fd.append('comprobante', modalResp.comprobante);

    const inscripcionId = ficha.value.id;
    const r = await apiFetch(`/api/app/inscripciones/${inscripcionId}/responsables`,
      { method: 'POST', body: fd });
    const d = await r.json().catch(() => ({}));
    if (!r.ok || d.ok === false) { await alerta(d.mensaje || 'No se pudo agregar', 'error', 0); return; }

    /*
     * A partir de aqui el responsable YA EXISTE en la base. Lo que queda —foto y WhatsApp— son
     * pasos de mas: si fallan, se avisa, pero no se puede decir "no se pudo agregar" ni dejar
     * al vendedor sin saber que la persona ya esta registrada. Por eso cada uno va en su propio
     * try y lo unico que se lleva un fallo es el texto del aviso final.
     */
    let avisoExtra = '';
    if (d.responsableId && modalResp.foto) {
      cambiarTextoCarga('Subiendo la foto del responsable…');
      try {
        await subirFotoResponsableNuevo(inscripcionId, d.responsableId);
        cambiarTextoCarga('Enviando la credencial por WhatsApp…');
        await enviarWhatsAppResponsableNuevo(inscripcionId, d.responsableId);
        avisoExtra = '\n\nSe envió por WhatsApp la credencial de este responsable.';
      } catch (e) {
        avisoExtra = `\n\nEl responsable quedó registrado, pero no se pudo completar: ${e.message}.`
          + ' Puedes subir la foto desde esta misma ficha y reenviar la credencial desde Credenciales.';
      }
    }

    modalResp.abierto = false;
    // Se recarga la ficha ANTES del aviso: al cerrarlo, lo que queda detras ya esta al dia.
    await abrirFicha(inscripcionId);
    // El velo baja ANTES de abrir ningun dialogo. Aunque ya no lo tape (el velo bajo a 1500),
    // dejar girando un indicador de progreso detras de un mensaje de "listo" es mentir sobre
    // que algo sigue trabajando.
    ocultarCarga();
    velado = false;
    await alerta(d.cobrado
      ? `Responsable agregado. Se registró el cobro de ${d.monto} Bs.${avisoExtra}`
      : `Responsable agregado.${avisoExtra}`, 'ok');
  } catch (e) {
    if (velado) { ocultarCarga(); velado = false; }
    await alerta(e.message, 'error', 0);
  } finally {
    guardandoResp.value = false;
    // Solo si sigue puesto: `ocultarCarga` lleva un contador y bajarlo dos veces por una
    // subida deja el contador en cero con otra operacion todavia en curso.
    if (velado) ocultarCarga();
  }
}

function cerrarFicha() {
  ficha.value = null;
  editando.value = null;
}

/** Pasa a modo edicion con una copia de lo que hay: cancelar tiene que dejarlo como estaba. */
function editarEntidad() {
  const e = ficha.value?.entidad || {};
  Object.keys(borrador).forEach((k) => delete borrador[k]);
  Object.assign(borrador, {
    entidadNombre: e.nombre || '', nit: e.nit || '', descripcion: e.descripcion || '',
    representanteLegal: e.representanteLegal || '', ciRepresentante: e.ciRepresentante || '',
    celularRepresentante: e.celularRepresentante || '',
    entidadBancaria: e.entidadBancaria || '', numComprobante: e.numComprobante || '',
  });
  editando.value = 'entidad';
}

function editarResponsable(r) {
  Object.keys(borrador).forEach((k) => delete borrador[k]);
  // `r.nombre` viene con los apellidos pegados, para pintarlo de una vez. Aqui hace falta el
  // nombre de pila suelto, y se obtiene QUITANDO los apellidos que llegan aparte: partir la
  // cadena por espacios adivinaria mal con dos nombres de pila y guardaria esa adivinanza.
  const apellidos = [r.paterno, r.materno].filter(Boolean).join(' ');
  const pila = apellidos && r.nombre?.endsWith(apellidos)
    ? r.nombre.slice(0, -apellidos.length).trim()
    : (r.nombre || '');
  Object.assign(borrador, {
    nombre: pila, paterno: r.paterno || '', materno: r.materno || '',
    ci: r.ci || '', celular: r.celular || '',
  });
  editando.value = r.id;
}

async function guardarEdicion() {
  if (guardando.value || !ficha.value) return;
  const id = ficha.value.id;
  const esEntidad = editando.value === 'entidad';
  guardando.value = true;
  mostrarCarga('Guardando los cambios…');
  try {
    const ruta = esEntidad
      ? `/api/app/inscripciones/${id}/entidad`
      : `/api/app/inscripciones/${id}/responsables/${editando.value}`;
    const r = await apiFetch(ruta, { method: 'PATCH', body: JSON.stringify({ ...borrador }) });
    const d = await r.json().catch(() => ({}));
    if (!r.ok || d.ok === false) { await alerta(d.mensaje || 'No se pudo guardar', 'error', 0); return; }
    editando.value = null;
    // Se recarga la ficha y la lista: el nombre de la entidad sale en las dos.
    await Promise.all([abrirFicha(id), cargar()]);
    ocultarCarga();
    await aviso(esEntidad ? 'Los datos de la entidad quedaron actualizados.'
                          : 'Los datos del responsable quedaron actualizados.',
                'ok', 3000, 'Cambios guardados');
  } catch (e) {
    await alerta(e.message, 'error', 0);
  } finally {
    ocultarCarga();
    guardando.value = false;
  }
}

/** Adjuntar el comprobante desde la ficha, que es donde se ve que falta. */
function comprobanteDesdeFicha() {
  if (ficha.value) elegirComprobante(ficha.value.id);
}

/** El backend devuelve `/files/...`; en el APK hay que anteponerle el servidor. */
const urlArchivo = (ruta) => (ruta ? urlApi(ruta) : '');
const esPdf = (ruta) => /\.pdf($|\?)/i.test(ruta || '');

/** Cuantos responsables no tienen foto todavia: es lo que frena la credencial. */
const sinFoto = computed(() =>
  (ficha.value?.responsables || []).filter((r) => !r.tieneFoto).length);

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

  <!-- Entrada de archivo única y oculta: en el móvil abre la cámara o la galería.
       `capture` no se fuerza a propósito — muchos comprobantes ya están en la galería
       como captura de la transferencia. -->
  <input ref="entradaArchivo" type="file" accept="image/jpeg,image/png,application/pdf"
         class="oculto" @change="onArchivoElegido" />

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
        <!-- Aqui SOLO se adjunta. El recibo y el compartir viven en la ficha de la venta:
             en una lista que existe para decir "falta el comprobante", un boton de recibo
             confunde sobre cual es la accion que se espera. -->
        <button class="btn btn-primario btn-sm" :disabled="subiendo === p.id"
                @click="elegirComprobante(p.id)">
          {{ subiendo === p.id ? 'Subiendo…' : '📷 Adjuntar' }}
        </button>
      </li>
    </ul>
  </section>

  <!-- Vista previa del comprobante antes de subir -->
  <ArchivoPreview
    v-if="archivoSeleccionado"
    :archivo="archivoSeleccionado"
    :texto-confirmar="'Subir comprobante'"
    :texto-cancelar="'Cancelar'"
    :previsualizar-imagen="true"
    @confirmar="confirmarSubidaComprobante"
    @cancelar="archivoSeleccionado = null"
  />

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

    <!-- La tabla de siete columnas se cambio por una LISTA.
         En un telefono de 400 px aquella tabla mostraba tres columnas y para leer el total
         habia que arrastrarla de lado; el resto de los datos no cabia en ninguna columna. La
         lista da lo justo para reconocer la venta y el resto se abre en la ficha. -->
    <section class="card lista-ventas">
      <header class="cab">
        <h2>Entidades registradas</h2>
        <span class="cuenta">{{ inscripciones.length }}</span>
      </header>

      <div v-if="cargando" class="vacio">Cargando…</div>
      <div v-else-if="inscripciones.length === 0" class="vacio">
        Aún no tienes ventas confirmadas.
      </div>
      <ul v-else class="ventas">
        <li v-for="ins in inscripciones" :key="ins.id" class="venta"
            :class="{ pendiente: faltaDe(ins.id).length }" @click="abrirFicha(ins.id)">
          <div class="principal">
            <strong class="nombre">{{ ins.entidad }}</strong>
            <!-- Que le falta, dicho en la propia fila. Antes habia que abrir cada venta para
                 descubrirlo, y con decenas de ventas eso no lo hace nadie. -->
            <span v-for="f in faltaDe(ins.id)" :key="f" class="badge badge-danger">falta {{ f }}</span>
            <span v-if="solicitudes[ins.id]" class="badge solicitud"
                  :class="`solicitud-${solicitudes[ins.id].estado.toLowerCase()}`"
                  :title="solicitudes[ins.id].respuesta || solicitudes[ins.id].motivo">
              {{ etiquetaSolicitud(solicitudes[ins.id]) }}
            </span>
          </div>
          <div class="secundario">
            <span class="cats">{{ ins.categorias.join(', ') }}</span>
            <span class="sep">·</span>
            <span>{{ fecha(ins.fecha) }}</span>
            <span class="badge" :class="ins.contado ? 'badge-ok' : 'badge-muted'">
              {{ ins.contado ? 'Contado' : 'Crédito' }}
            </span>
          </div>
          <div class="derecha">
            <strong class="total">{{ bs(ins.total) }}</strong>
            <!-- Que se puede tocar tiene que decirlo la fila, no adivinarse. -->
            <span class="ver">Ver detalle ›</span>
          </div>
        </li>
      </ul>
    </section>

    <!-- Ficha de la venta: todo lo registrado, y desde aqui se corrige. -->
    <!-- Mas ancha que el resto de dialogos: lleva datos en dos columnas y tres botones con
         palabras en el pie, y a 480 px eso se partia en dos filas. -->
    <UiModal v-if="ficha || cargandoFicha" :titulo="ficha?.entidad?.nombre || 'Venta'"
             ancho="1200px" @cerrar="cerrarFicha">
      <div v-if="cargandoFicha" class="vacio">Cargando…</div>
      <div v-else-if="ficha" class="ficha">
        <div class="columna-datos">
          <!-- Lo que falta, primero: es sobre lo que hay que actuar. -->
          <div v-if="!ficha.pago.conComprobante || sinFoto" class="falta-credencial">
            <strong>Falta para la credencial:</strong>
            <ul>
              <li v-if="!ficha.pago.conComprobante">el comprobante de pago</li>
              <li v-if="sinFoto">{{ sinFoto }} foto{{ sinFoto === 1 ? '' : 's' }} de responsable</li>
            </ul>
          </div>

          <!-- ENTIDAD -->
          <section class="grupo g-entidad">
            <header>
              <h3><span class="ico">🏢</span>Entidad</h3>
              <button v-if="editando !== 'entidad'" class="btn btn-fantasma btn-sm"
                      @click="editarEntidad">✏️ Modificar</button>
            </header>

            <dl v-if="editando !== 'entidad'" class="datos">
              <div><dt>Nombre</dt><dd>{{ ficha.entidad.nombre || '—' }}</dd></div>
              <div><dt>Rubro</dt><dd>{{ ficha.entidad.descripcion || '—' }}</dd></div>
              <div><dt>Tipo</dt><dd>{{ ficha.entidad.tipo || '—' }}</dd></div>
              <div><dt>NIT</dt><dd>{{ ficha.entidad.nit || '—' }}</dd></div>
              <div><dt>Responsable legal</dt><dd>{{ ficha.entidad.representanteLegal || '—' }}</dd></div>
              <div><dt>C.I.</dt><dd>{{ ficha.entidad.ciRepresentante || '—' }}</dd></div>
              <div><dt>Celular</dt><dd>{{ ficha.entidad.celularRepresentante || '—' }}</dd></div>
              <div v-if="ficha.entidad.entidadBancaria"><dt>Entidad bancaria</dt><dd>{{ ficha.entidad.entidadBancaria }}</dd></div>
              <div v-if="ficha.entidad.numComprobante"><dt>N.º comprobante</dt><dd>{{ ficha.entidad.numComprobante }}</dd></div>
            </dl>

            <div v-else class="form">
              <label class="campo"><span>Nombre de la entidad</span>
                <input class="control mayus" v-model="borrador.entidadNombre" /></label>
              <div class="dos">
                <label class="campo"><span>Rubro</span>
                  <input class="control mayus" v-model="borrador.descripcion" /></label>
                <label class="campo"><span>NIT</span>
                  <input class="control" inputmode="numeric" v-model="borrador.nit" /></label>
              </div>
              <label class="campo"><span>Responsable legal</span>
                <input class="control mayus" v-model="borrador.representanteLegal" /></label>
              <div class="dos">
                <label class="campo"><span>C.I.</span>
                  <input class="control" inputmode="numeric" v-model="borrador.ciRepresentante" /></label>
                <label class="campo"><span>Celular</span>
                  <CampoCelular v-model="borrador.celularRepresentante" /></label>
              </div>
              <div class="separador"></div>
              <label class="campo"><span>Entidad bancaria</span>
                <input class="control mayus" v-model="borrador.entidadBancaria" placeholder="Ej. Banco Unión" /></label>
              <label class="campo"><span>N.º de comprobante</span>
                <input class="control" type="text" inputmode="text" v-model="borrador.numComprobante" placeholder="Número o código del comprobante" /></label>
              <div class="acciones-form">
                <button class="btn btn-fantasma" @click="editando = null">Cancelar</button>
                <button class="btn btn-primario" :disabled="guardando" @click="guardarEdicion">
                  {{ guardando ? 'Guardando…' : 'Guardar' }}
                </button>
              </div>
            </div>
          </section>

          <!-- CASETAS -->
          <section class="grupo g-casetas">
            <header><h3><span class="ico">🏬</span>Casetas</h3></header>
            <div v-if="!ficha.casetas.length" class="muted">Sin casetas registradas.</div>
            <div v-else class="puestos">
              <span v-for="(c, i) in ficha.casetas" :key="i" class="chip-puesto"
                    :style="c.color ? { background: c.color + '22', color: c.color } : null">
                {{ c.categoria }} {{ c.codigo }}
              </span>
            </div>
          </section>

          <!-- PAGO Y COMPROBANTE -->
          <section class="grupo g-pago">
            <header>
              <h3><span class="ico">🧾</span>Pago</h3>
              <span class="badge" :class="ficha.pago.conComprobante ? 'badge-ok' : 'badge-danger'">
                {{ ficha.pago.conComprobante ? 'con comprobante' : 'sin comprobante' }}
              </span>
            </header>
            <dl class="datos">
              <div><dt>Forma</dt><dd>{{ ficha.pago.contado ? 'Al contado' : 'Crédito' }}</dd></div>
              <div v-if="ficha.pago.entidadBancaria"><dt>Entidad bancaria</dt><dd>{{ ficha.pago.entidadBancaria }}</dd></div>
              <div v-if="ficha.pago.numComprobante"><dt>N.º comprobante</dt><dd>{{ ficha.pago.numComprobante }}</dd></div>
            </dl>

            <!-- El comprobante SE VE. Antes solo se ofrecia reemplazarlo, asi que no habia forma
                 de comprobar que lo subido fuera lo correcto sin bajarlo por otro camino. -->
            <div v-if="ficha.pago.conComprobante" class="comprobante">
              <button class="btn btn-sm" @click="verComprobante = !verComprobante">
                {{ verComprobante ? 'Ocultar comprobante' : '👁 Ver comprobante' }}
              </button>
              <a class="btn btn-fantasma btn-sm" :href="urlArchivo(ficha.pago.comprobanteUrl)"
                 target="_blank" rel="noopener">Abrir aparte</a>
              <div v-if="verComprobante" class="visor-comp">
                <img v-if="!esPdf(ficha.pago.comprobanteUrl)" :src="urlArchivo(ficha.pago.comprobanteUrl)"
                     alt="Comprobante de pago" />
                <p v-else class="muted">
                  El comprobante es un PDF. Tócalo en «Abrir aparte» para verlo.
                </p>
              </div>
            </div>
            <p v-else class="muted">
              Todavía no se subió el comprobante. Sin él no se puede emitir la credencial.
            </p>
          </section>

        </div>

        <!-- RESPONSABLES -->
        <section class="grupo g-responsables">
          <header>
            <h3><span class="ico">👥</span>Responsables</h3>
            <span v-if="sinFoto" class="badge badge-danger">{{ sinFoto }} sin foto</span>
            <span v-else-if="ficha.responsables.length" class="badge badge-ok">fotos completas</span>
          </header>
          <div v-if="!ficha.responsables.length" class="muted">Sin responsables registrados.</div>
          <ul v-else class="responsables">
            <li v-for="r in ficha.responsables" :key="r.id">
              <template v-if="editando !== r.id">
                <div class="quien">
                  <strong>{{ r.nombre }}</strong>
                  <span class="muted">C.I. {{ r.ci || '—' }}<template v-if="r.celular"> · {{ r.celular }}</template></span>
                  <!-- Quien entro por encima del derecho y pago. Con el enlace a SU comprobante:
                       el del pago de la venta es otro, y confundirlos al cuadrar es fácil. -->
                  <span v-if="r.esExtra" class="extra-linea muted">
                    <span class="badge badge-extra">extra · {{ r.montoExtra }} Bs</span>
                    <a v-if="r.comprobanteExtraUrl" :href="urlArchivo(r.comprobanteExtraUrl)"
                       target="_blank" rel="noopener">ver su comprobante</a>
                    <span v-else class="sin-comp">sin comprobante</span>
                    <button class="btn btn-fantasma btn-sm" @click="descargarReciboExtra(ficha.id, r.id, ficha.entidad?.nombre)">
                      🧾 Recibo extra
                    </button>
                  </span>
                </div>
                <span class="badge" :class="r.tieneFoto ? 'badge-ok' : 'badge-danger'">
                  {{ r.tieneFoto ? 'con foto' : 'sin foto' }}
                </span>
                <button class="btn btn-fantasma btn-sm" @click="editarResponsable(r)">✏️</button>
              </template>
              <div v-else class="form">
                <div class="dos">
                  <label class="campo"><span>Nombre</span>
                    <input class="control mayus" v-model="borrador.nombre" /></label>
                  <label class="campo"><span>C.I.</span>
                    <input class="control" inputmode="numeric" v-model="borrador.ci" /></label>
                </div>
                <div class="dos">
                  <label class="campo"><span>Apellido paterno</span>
                    <input class="control mayus" v-model="borrador.paterno" /></label>
                  <label class="campo"><span>Apellido materno</span>
                    <input class="control mayus" v-model="borrador.materno" /></label>
                </div>
                <label class="campo"><span>Celular</span>
                  <CampoCelular v-model="borrador.celular" /></label>
                <div class="acciones-form">
                  <button class="btn btn-fantasma" @click="editando = null">Cancelar</button>
                  <button class="btn btn-primario" :disabled="guardando" @click="guardarEdicion">
                    {{ guardando ? 'Guardando…' : 'Guardar' }}
                  </button>
                </div>
              </div>
            </li>
          </ul>
          <!-- Las fotos se gestionan con el componente que ya existe para eso. -->
          <FotosResponsables :inscripcion-id="ficha.id" />

          <!-- El derecho y lo que cuesta pasarse. Se dice ANTES de abrir el formulario: que el
               cobro aparezca despues de escribir los datos, con el cliente delante, es lo que
               hace quedar mal al vendedor. -->
          <div v-if="cupo" class="cupo">
            <p class="muted">
              {{ cupo.casetas }} caseta{{ cupo.casetas === 1 ? '' : 's' }} dan derecho a
              <strong>{{ cupo.derecho }} responsables</strong>
              ({{ cupo.registrados }} usados<template v-if="cupo.extras">, {{ cupo.extras }} de pago</template>).
            </p>
            <button class="btn" @click="abrirAgregarResponsable">
              ＋ Agregar responsable
              <template v-if="!cupo.dentroDelDerecho"> · {{ cupo.costoSiguiente }} Bs</template>
            </button>
          </div>
        </section>
      </div>

      <template #pie>
        <!-- El recibo es la accion que mas se repite, asi que es el boton grande y con
             palabras. Antes era un 🧾 suelto y nadie sabia que hacia. -->
        <button class="btn btn-grande" :disabled="!ficha"
                @click="ficha && comprobanteDesdeFicha()">
          {{ ficha?.pago?.conComprobante ? '📷 Cambiar comprobante' : '📷 Adjuntar comprobante' }}
        </button>
        <button class="btn btn-grande" :disabled="!ficha" @click="ficha && compartirRecibo(ficha.id)">
          📤 Compartir
        </button>
        <button class="btn btn-primario btn-grande" :disabled="!ficha"
                @click="ficha && verRecibo(ficha.id, ficha.entidad?.nombre)">
          🧾 Imprimir recibo
        </button>
      </template>
    </UiModal>

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

  <!-- ------------------------------------------------- agregar un responsable a la venta -->
  <UiModal v-if="modalResp.abierto" titulo="Agregar responsable" @cerrar="modalResp.abierto = false">
    <div class="form-resp">
      <p v-if="cupo && !cupo.dentroDelDerecho" class="card aviso-cobro">
        Ya se usaron los <strong>{{ cupo.derecho }}</strong> responsables que dan sus
        {{ cupo.casetas }} caseta{{ cupo.casetas === 1 ? '' : 's' }}.
        Este tiene un costo de <strong>{{ cupo.costoSiguiente }} Bs</strong> y hace falta
        el comprobante del pago.
      </p>
      <p v-else class="muted">
        Está dentro de los {{ cupo?.derecho }} responsables que dan sus casetas: no tiene costo.
      </p>

      <div class="dos">
        <label class="campo"><span>Nombre *</span>
          <input class="control mayus" v-model="modalResp.nombre" /></label>
        <label class="campo"><span>C.I. *</span>
          <input class="control" inputmode="numeric" v-model="modalResp.ci" /></label>
      </div>
      <div class="dos">
        <label class="campo"><span>Apellido paterno</span>
          <input class="control mayus" v-model="modalResp.paterno" /></label>
        <label class="campo"><span>Apellido materno</span>
          <input class="control mayus" v-model="modalResp.materno" /></label>
      </div>
      <label class="campo"><span>Celular</span>
        <CampoCelular v-model="modalResp.celular" /></label>

      <div class="campo">
        <span>Foto para la credencial *</span>
        <!-- Sin `capture`: igual que comprobantes, muchas fotos ya estan en galeria. -->
        <input id="foto-resp-nuevo" class="oculto" type="file" accept="image/jpeg,image/png"
               @change="elegirFotoResp" />
        <label for="foto-resp-nuevo" class="btn">
          📷 {{ modalResp.foto ? 'Cambiar foto' : 'Adjuntar foto' }}
        </label>
        <span v-if="modalResp.foto" class="muted">{{ modalResp.foto.name }}</span>
        <span class="formato-permitido">Formatos: JPG, PNG</span>
      </div>

      <div v-if="cupo && !cupo.dentroDelDerecho" class="campo">
        <span>Comprobante del pago *</span>
        <!-- Sin `capture`: forzar la camara quita la galeria en Android, y el comprobante
             muchas veces ya esta en el telefono. Mismo criterio que el resto del modulo. -->
        <input id="comp-resp" class="oculto" type="file" accept="image/jpeg,image/png,application/pdf"
               @change="elegirComprobanteResp" />
        <label for="comp-resp" class="btn">
          📷 {{ modalResp.comprobante ? 'Cambiar comprobante' : 'Adjuntar comprobante' }}
        </label>
        <span v-if="modalResp.comprobante" class="muted">{{ modalResp.comprobante.name }}</span>
        <span class="formato-permitido">Formatos: JPG, PNG, PDF</span>
      </div>

      <p class="muted chico">
        Si adjuntas la foto ahora, se enviará por WhatsApp solo la credencial de esta persona.
      </p>
    </div>
    <template #pie>
      <button class="btn btn-fantasma" @click="modalResp.abierto = false">Cancelar</button>
      <button class="btn btn-primario" :disabled="guardandoResp" @click="guardarResponsableNuevo">
        {{ guardandoResp ? 'Guardando…' : 'Agregar' }}
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
.puestos { display: flex; flex-wrap: wrap; gap: 0.4rem; }
.chip-puesto { background: var(--acento-suave); color: var(--acento); border-radius: 999px; padding: 0.25rem 0.65rem; font-size: 0.85rem; font-weight: 600; }

/* ---- lista de entidades ---- */
.lista-ventas { padding: 0; }
.cab { display: flex; align-items: center; gap: 0.6rem; padding: 0.9rem 1.1rem; border-bottom: 1px solid var(--border); }
.cab h2 { margin: 0; font-size: 1.05rem; }
.cab .cuenta { background: var(--acento-suave); color: var(--acento); border-radius: 999px; padding: 0.05rem 0.55rem; font-size: 0.8rem; font-weight: 700; }
.ventas { list-style: none; margin: 0; padding: 0; }
/*
 * Una fila = una entidad, en rejilla de dos columnas: a la izquierda quien es, a la derecha
 * cuanto y la invitacion a abrir. En movil la derecha se pasa debajo en vez de salirse, que
 * es lo que hacia la tabla.
 */
.venta {
  display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: 0.15rem 0.8rem;
  padding: 0.85rem 1.1rem; border-bottom: 1px solid var(--border); cursor: pointer;
}
.venta:last-child { border-bottom: none; }
/* Franja ambar a la izquierda: se recorre la lista con la vista y las que tienen trabajo
   pendiente saltan sin tener que leer las insignias una por una. */
.venta.pendiente {
  border-left: 4px solid var(--tramite);
  background: color-mix(in srgb, var(--tramite) 5%, transparent);
}
.venta:hover, .venta:focus-within { background: var(--panel-2); }
.principal { display: flex; align-items: center; gap: 0.5rem; flex-wrap: wrap; min-width: 0; }
.nombre { font-size: 1.02rem; }
.secundario {
  grid-column: 1; display: flex; align-items: center; gap: 0.4rem; flex-wrap: wrap;
  color: var(--muted); font-size: 0.85rem;
}
.secundario .cats { font-weight: 600; }
.derecha { grid-column: 2; grid-row: 1 / span 2; display: flex; flex-direction: column; align-items: flex-end; justify-content: center; gap: 0.2rem; }
.total { font-variant-numeric: tabular-nums; font-size: 1.05rem; white-space: nowrap; }
.ver { color: var(--acento); font-size: 0.82rem; font-weight: 700; white-space: nowrap; }

/* ---- ficha de la venta ---- */
.ficha { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); gap: 1.1rem; align-items: start; }
.columna-datos { display: flex; flex-direction: column; gap: 1.1rem; min-width: 0; }
.ficha > .g-responsables { min-width: 0; overflow-wrap: anywhere; }
/*
 * OJO con el nombre: esta clase se llamaba `.pendiente` a secas y chocaba con `.venta.pendiente`
 * de la lista. El selector sin calificar alcanzaba tambien a las filas, asi que una venta con
 * trabajo pendiente salia con el nombre de la entidad y el importe en ROJO, como si la venta
 * estuviera mal. Dos cosas distintas no pueden compartir un nombre tan generico.
 */
.falta-credencial {
  padding: 0.7rem 0.9rem; border-radius: var(--radio-sm);
  background: var(--danger-suave); color: var(--danger); font-size: 0.9rem;
}
.falta-credencial ul { margin: 0.25rem 0 0; padding-left: 1.1rem; }
/*
 * Cada bloque con su color y su icono.
 *
 * La ficha lleva cuatro cosas distintas —quien es, que compro, si pago y quien atiende— y
 * puestas una detras de otra en el mismo gris se leian como un muro. La franja de color de la
 * izquierda y el icono dan el "donde estoy" de un vistazo, sin tener que leer el titulo.
 */
.grupo {
  display: flex; flex-direction: column; gap: 0.6rem;
  padding: 0.75rem 0.9rem; border-radius: var(--radio-sm);
  background: var(--panel-2); border-left: 3px solid var(--borde-grupo, var(--border));
}
.grupo > header { display: flex; align-items: center; justify-content: space-between; gap: 0.6rem; flex-wrap: wrap; }
.grupo h3 {
  margin: 0; font-size: 0.95rem; display: flex; align-items: center; gap: 0.45rem;
  color: var(--borde-grupo, var(--texto));
}
.grupo h3 .ico { font-size: 1.05rem; }
.g-entidad      { --borde-grupo: var(--acento); }
.g-casetas      { --borde-grupo: #0ea5e9; }
.g-pago         { --borde-grupo: var(--tramite); }
.g-responsables { --borde-grupo: #a855f7; }

/* ---- comprobante ---- */
.comprobante { display: flex; flex-wrap: wrap; gap: 0.5rem; align-items: center; }
.visor-comp { flex-basis: 100%; margin-top: 0.3rem; }
.visor-comp img {
  width: 100%; max-height: 60vh; object-fit: contain;
  border-radius: var(--radio-sm); border: 1px solid var(--border); background: var(--panel);
}
.datos { display: grid; grid-template-columns: 1fr 1fr; gap: 0.6rem; margin: 0; }
.datos dt { font-size: 0.72rem; text-transform: uppercase; letter-spacing: 0.04em; color: var(--muted); font-weight: 700; }
.datos dd { margin: 0.1rem 0 0; font-size: 0.93rem; overflow-wrap: anywhere; }
.form { display: flex; flex-direction: column; gap: 0.7rem; }
.dos { display: grid; gap: 0.7rem; grid-template-columns: 1fr 1fr; }
.acciones-form { display: flex; gap: 0.5rem; justify-content: flex-end; }
.mayus { text-transform: uppercase; }
.mayus::placeholder { text-transform: none; }
.cupo { margin-top: 0.75rem; display: flex; flex-direction: column; gap: 0.4rem; align-items: flex-start; }
.cupo p { margin: 0; line-height: 1.5; }
.form-resp { display: flex; flex-direction: column; gap: 0.85rem; }
.form-resp .campo { display: flex; flex-direction: column; gap: 0.3rem; }
.aviso-cobro { padding: 0.7rem; line-height: 1.5; margin: 0; }
.chico { font-size: 0.85rem; }
.formato-permitido { font-size: 0.72rem; color: var(--muted); margin-left: auto; }
.oculto { position: absolute; width: 1px; height: 1px; opacity: 0; pointer-events: none; }
.responsables { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 0.5rem; }
.responsables li {
  display: flex; align-items: center; gap: 0.6rem; flex-wrap: wrap;
  padding: 0.6rem 0.7rem; border-radius: var(--radio-sm); background: var(--panel-2);
}
.responsables .quien { display: flex; flex-direction: column; min-width: 0; flex: 1; }
.extra-linea { display: flex; align-items: center; gap: 0.4rem; flex-wrap: wrap; font-size: 0.82rem; margin-top: 0.15rem; }
.badge-extra { background: color-mix(in srgb, #a855f7 18%, transparent); color: #7e22ce; font-weight: 700; }
.extra-linea .sin-comp { color: var(--danger); font-weight: 600; }
.responsables .form { flex-basis: 100%; }
/* El recibo y el comprobante se pulsan de pie: botones con palabras, no un emoji suelto. */
.btn-grande { min-height: 48px; font-size: 0.98rem; font-weight: 700; }

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

@media (max-width: 800px) {
  .ficha { grid-template-columns: minmax(0, 1fr); }
}

@media (max-width: 560px) {
  .pendientes li { align-items: flex-start; }
  .pendientes .quien { flex-basis: 100%; }

  /* En el telefono el total y el "ver detalle" se pasan debajo: apretados a la derecha
     dejaban el nombre de la entidad en dos letras por linea. */
  .venta { grid-template-columns: 1fr; }
  .derecha {
    grid-column: 1; grid-row: auto; flex-direction: row; align-items: center;
    justify-content: space-between; margin-top: 0.35rem;
  }
  .datos, .dos { grid-template-columns: 1fr; }
  .acciones-form .btn { flex: 1; min-height: 46px; }
  .btn-grande { width: 100%; }
}
</style>
