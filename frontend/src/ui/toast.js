import { ref } from 'vue';

/**
 * Notificaciones efímeras. `toast(mensaje, tipo)` desde cualquier vista; ToastHost.vue las pinta.
 * tipo: 'ok' | 'error' | 'info'. Se auto-descartan.
 */
export const toasts = ref([]);
let secuencia = 0;

export function toast(mensaje, tipo = 'info', ms = 3500) {
  const id = ++secuencia;
  toasts.value.push({ id, mensaje, tipo });
  setTimeout(() => cerrar(id), ms);
  return id;
}

export function cerrar(id) {
  toasts.value = toasts.value.filter((t) => t.id !== id);
}
