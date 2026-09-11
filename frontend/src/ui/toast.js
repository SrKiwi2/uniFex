import { ref } from 'vue';
import { alerta } from './alerta.js';

/**
 * Notificaciones efímeras (toast simple) para mensajes menores.
 * Para errores y advertencias importantes, usa `alerta()` del módulo alerta.js
 * que muestra un modal con botón Confirmar y/o temporizador.
 *
 * Reexporta la API original para compatibilidad.
 */
export const toasts = ref([]);
let secuencia = 0;

/** Toast simple (compatibilidad): ok/info → toast efímero; error/advertencia → modal alerta. */
export function toast(mensaje, tipo = 'info', ms = 3500) {
  // Errores y advertencias importantes → modal con confirmación
  if (tipo === 'error' || tipo === 'advertencia') {
    return alerta(mensaje, tipo, ms === 3500 ? 5000 : ms);
  }
  // ok/info → toast efímero original
  const id = ++secuencia;
  toasts.value.push({ id, mensaje, tipo });
  setTimeout(() => cerrar(id), ms);
  return id;
}

export function cerrar(id) {
  toasts.value = toasts.value.filter((t) => t.id !== id);
}