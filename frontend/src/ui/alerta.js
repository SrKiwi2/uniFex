import { ref, reactive } from 'vue';

/**
 * Modal de aviso global (lo pinta AlertaModal, montado una sola vez en App.vue): círculo con
 * ✓ / ✕ / ! / i, título en el color del tipo, mensaje y botón "Ok".
 *
 * - Se cierra con "Ok", con un clic en cualquier parte, con Esc, o solo al vencer `ms`
 *   (0 = no se cierra solo: hay que leerlo y pulsar "Ok").
 * - Tipos: 'ok' (verde) | 'error' (rojo) | 'advertencia' | 'info'.
 * - "Cancelar / Confirmar" solo aparecen con alertaConAccion(), que sí pregunta algo.
 *
 * Uso:
 *   alerta('Venta registrada', 'ok')                  → se cierra sola a los 4 s
 *   alerta('Error al guardar', 'error', 0)            → espera a que pulsen "Ok"
 *   aviso('Se guardó.', 'ok', 2000, '¡Cambios guardados!') → título propio, 2 s
 */
export const alertaVisible = ref(false);
export const alertaConfig = reactive({
  titulo: '',
  mensaje: '',
  tipo: 'info',           // 'ok' | 'error' | 'advertencia' | 'info'
  ms: 4000,               // 0 = no se cierra solo
  mostrarConfirmar: false, // true solo con alertaConAccion: Cancelar + Confirmar
  onConfirmar: null,
  onCerrar: null,
  // Sube en cada alerta mostrada. AlertaModal lo vigila para reiniciar temporizador y
  // animaciones cuando una alerta nueva reemplaza a otra que seguía abierta.
  serie: 0,
});

const TITULOS = { ok: 'Éxito', error: 'Error', advertencia: 'Atención', info: 'Información' };

let resolvePromesa = null;

function mostrar(opciones, resolve) {
  // Una alerta que reemplaza a otra abierta: la anterior se da por cerrada, no queda colgada.
  if (resolvePromesa) resolvePromesa(false);
  resolvePromesa = resolve;
  Object.assign(alertaConfig, {
    titulo: opciones.titulo || TITULOS[opciones.tipo] || TITULOS.info,
    mensaje: opciones.mensaje,
    tipo: opciones.tipo,
    ms: opciones.ms,
    mostrarConfirmar: Boolean(opciones.onConfirmar),
    onConfirmar: opciones.onConfirmar || null,
    onCerrar: null,
    serie: alertaConfig.serie + 1,
  });
  alertaVisible.value = true;
}

/** Muestra una alerta; la promesa se resuelve al cerrarla. */
export function alerta(mensaje, tipo = 'info', ms = 4000) {
  return new Promise((resolve) => mostrar({ mensaje, tipo, ms }, resolve));
}

/**
 * Aviso breve del resultado de una acción ("guardado" / "no se pudo"): se cierra solo a los
 * `ms`, con "Ok" o con un clic en cualquier parte.
 */
export function aviso(mensaje, tipo = 'ok', ms = 2000, titulo = null) {
  const porDefecto = tipo === 'ok' ? '¡Listo!' : tipo === 'error' ? 'No se pudo completar' : null;
  return new Promise((resolve) => mostrar({ mensaje, tipo, ms, titulo: titulo || porDefecto }, resolve));
}

/** Pregunta con "Cancelar / Confirmar"; resuelve true solo si se confirma. */
export function alertaConAccion(mensaje, tipo, onConfirmar, ms = 0) {
  return new Promise((resolve) =>
    mostrar({ mensaje, tipo, ms, onConfirmar: () => onConfirmar?.() }, resolve));
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
