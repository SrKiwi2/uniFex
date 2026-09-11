import { ref, computed } from 'vue';
import { defineStore } from 'pinia';
import { apiFetch } from '../api.js';

/**
 * Estado de la bandeja de notificaciones.
 * - Se hidrata desde la BD al abrir la pestaña "Notificaciones".
 * - El WebSocket (topic personal) ya la actualiza en tiempo real via puestosStore.registrarNotificaciones().
 * - Este store solo gestiona la UI: lista, filtros, hilos, acciones (leer, responder, resolver).
 */
export const useNotificacionesStore = defineStore('notificaciones', () => {
  const lista = ref([]);
  const cargando = ref(false);
  const error = ref('');

  // Filtros UI
  const filtroLeida = ref('todas'); // 'todas' | 'leidas' | 'no-leidas'
  const filtroTipo = ref('');       // string vacio = todas

  /** Cuenta de no leídas (para badge en barra). */
  const countNoLeidas = computed(() => lista.value.filter((n) => !n.leida).length);

  /** Lista filtrada para la vista. */
  const filtradas = computed(() => {
    let arr = lista.value;
    if (filtroLeida.value === 'leidas') arr = arr.filter((n) => n.leida);
    else if (filtroLeida.value === 'no-leidas') arr = arr.filter((n) => !n.leida);
    if (filtroTipo.value) arr = arr.filter((n) => n.tipo === filtroTipo.value);
    return arr;
  });

  /** Tipos únicos presentes en la lista (para selector de filtro). */
  const tiposDisponibles = computed(() =>
    [...new Set(lista.value.map((n) => n.tipo))].sort()
  );

  /** Carga la bandeja completa desde el servidor. */
  async function cargar() {
    cargando.value = true;
    error.value = '';
    try {
      const r = await apiFetch('/api/app/notificaciones');
      lista.value = await r.json();
    } catch (e) {
      error.value = e.message;
      throw e;
    } finally {
      cargando.value = false;
    }
  }

  /** Refresca solo el contador (para actualizar badge sin recargar lista). */
  async function refrescarContador() {
    try {
      const r = await apiFetch('/api/app/notificaciones/count');
      // El backend devuelve { count: N } — no actualizamos lista, solo si hace falta
    } catch {
      // Silencioso: el contador es secundario
    }
  }

  /** Aplica una notificación recibida por WebSocket a la lista local. */
  function aplicarWS(notificacion) {
    // La notificación WS trae: { tipo, asunto, cuerpo, inscripcionId, puestoId, fecha }
    // La BD ya la tiene; buscamos por inscripcionId + fecha aproximada o la insertamos arriba.
    // Para simplicidad: si no existe en lista, la preprendemos.
    const existe = lista.value.some((n) => n.inscripcionId === notificacion.inscripcionId && n.fecha === notificacion.fecha);
    if (!existe) {
      lista.value.unshift({
        id: Date.now() + Math.random(), // id temporal hasta que llegue la confirmación del sondeo
        ...notificacion,
        leida: false,
      });
    }
  }

  /** Marca una notificación como leída (local + servidor). */
  async function marcarLeida(id) {
    const n = lista.value.find((x) => x.id === id);
    if (!n || n.leida) return;
    try {
      await apiFetch(`/api/app/notificaciones/${id}/leer`, { method: 'POST' });
      n.leida = true;
      n.leidaEn = new Date().toISOString();
    } catch (e) {
      error.value = e.message;
    }
  }

  /** Marca todas como leídas. */
  async function marcarTodasLeidas() {
    const noLeidas = lista.value.filter((n) => !n.leida);
    if (!noLeidas.length) return;
    try {
      await apiFetch('/api/app/notificaciones/leer-todas', { method: 'POST' });
      noLeidas.forEach((n) => {
        n.leida = true;
        n.leidaEn = new Date().toISOString();
      });
    } catch (e) {
      error.value = e.message;
    }
  }

  /** Obtiene el hilo completo de una observación. */
  async function cargarHilo(notificacionPadreId) {
    try {
      const r = await apiFetch(`/api/app/notificaciones/${notificacionPadreId}/hilo`);
      return await r.json();
    } catch (e) {
      error.value = e.message;
      return [];
    }
  }

  /** Vendedor responde a observación de admin. */
  async function responder(notificacionPadreId, respuesta) {
    try {
      await apiFetch(`/api/app/notificaciones/${notificacionPadreId}/responder`, {
        method: 'POST',
        body: JSON.stringify({ respuesta }),
      });
      // El hilo se actualizará por WS o al recargar
    } catch (e) {
      error.value = e.message;
      throw e;
    }
  }

  /** Admin resuelve hilo (marca RESUELTA). */
  async function resolver(notificacionPadreId, respuesta) {
    try {
      await apiFetch(`/api/app/notificaciones/${notificacionPadreId}/resolver`, {
        method: 'POST',
        body: JSON.stringify({ respuesta }),
      });
    } catch (e) {
      error.value = e.message;
      throw e;
    }
  }

  /** Admin crea observación para un vendedor. */
  async function crearObservacion({ vendedorId, asunto, cuerpo, inscripcionId, puestoId }) {
    try {
      await apiFetch('/api/app/notificaciones/observacion', {
        method: 'POST',
        body: JSON.stringify({ vendedorId, asunto, cuerpo, inscripcionId, puestoId }),
      });
    } catch (e) {
      error.value = e.message;
      throw e;
    }
  }

  return {
    lista, cargando, error, countNoLeidas, filtradas, filtroLeida, filtroTipo, tiposDisponibles,
    cargar, refrescarContador, aplicarWS, marcarLeida, marcarTodasLeidas,
    cargarHilo, responder, resolver, crearObservacion,
  };
});