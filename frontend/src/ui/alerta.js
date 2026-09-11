import { ref, reactive } from 'vue';

/**
 * Alertas modales con confirmación — sustituyen a toasts para mensajes importantes.
 * - Temporizador automático (configurable)
 * - Botón "Confirmar" obligatorio si no hay temporizador
 * - Tipos: 'ok' | 'error' | 'advertencia' | 'info'
 *
 * Uso:
 *   alerta('Venta registrada', 'ok')                    → se cierra solo en 4s
 *   alerta('¿Cancelar la venta?', 'advertencia')        → exige clic en "Confirmar"
 *   alerta('Error al guardar', 'error', 0)              → exige clic, no auto-cierra
 */
export const alertaVisible = ref(false);
export const alertaConfig = reactive({
  titulo: '',
  mensaje: '',
  tipo: 'info',           // 'ok' | 'error' | 'advertencia' | 'info'
  ms: 4000,               // 0 = no auto-cierra, exige botón
  mostrarConfirmar: true, // si false y ms>0, solo aviso con temporizador
  onConfirmar: null,      // callback opcional
  onCerrar: null,         // callback opcional
});

let resolvePromesa = null;

/** Muestra una alerta modal y devuelve promesa que resuelve al confirmar/cerrar. */
export function alerta(mensaje, tipo = 'info', ms = 4000) {
  return new Promise((resolve) => {
    alertaVisible.value = true;
    alertaConfig.titulo = tipo === 'error' ? 'Error' : tipo === 'advertencia' ? 'Atención' : tipo === 'ok' ? 'Éxito' : 'Información';
    alertaConfig.mensaje = mensaje;
    alertaConfig.tipo = tipo;
    alertaConfig.ms = ms;
    alertaConfig.mostrarConfirmar = ms === 0 || tipo === 'advertencia' || tipo === 'error';
    alertaConfig.onConfirmar = null;
    alertaConfig.onCerrar = null;
    resolvePromesa = resolve;
  });
}

/** Variante con callback al confirmar. */
export function alertaConAccion(mensaje, tipo, onConfirmar, ms = 0) {
  return new Promise((resolve) => {
    alertaVisible.value = true;
    alertaConfig.titulo = tipo === 'error' ? 'Error' : tipo === 'advertencia' ? 'Atención' : tipo === 'ok' ? 'Éxito' : 'Información';
    alertaConfig.mensaje = mensaje;
    alertaConfig.tipo = tipo;
    alertaConfig.ms = ms;
    alertaConfig.mostrarConfirmar = true;
    alertaConfig.onConfirmar = () => {
      onConfirmar?.();
      resolve(true);
    };
    alertaConfig.onCerrar = () => resolve(false);
    resolvePromesa = resolve;
  });
}

export function cerrar(confirmado = false) {
  alertaVisible.value = false;
  if (resolvePromesa) {
    resolvePromesa(confirmado);
    resolvePromesa = null;
  }
  alertaConfig.onConfirmar = null;
  alertaConfig.onCerrar = null;
}