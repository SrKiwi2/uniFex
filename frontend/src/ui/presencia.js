import { apiFetch } from '../api';

/**
 * El latido que dice "sigo aqui, y estoy en esta pantalla".
 *
 * Es un POST pequeño cada {@link INTERVALO_MS}, mas uno inmediato al cambiar de pantalla. No va
 * por el WebSocket a proposito: la conexion STOMP se cae y se rehace sola en la feria, y una
 * presencia atada a ella marcaria como "desconectado" a quien solo perdio la señal un segundo.
 * Un POST que falla no rompe nada: al siguiente latido se pone al dia.
 *
 * **No se late con la pestaña oculta.** Un teléfono con la aplicacion en el bolsillo no es
 * alguien trabajando, y contarlo como conectado haria inutil la pantalla que esto alimenta.
 * Android ademas congela el temporizador en segundo plano, asi que el latido se pararia igual;
 * mejor que sea una decision y no un accidente.
 */
const INTERVALO_MS = 25000;

let temporizador = null;
let ultima = { pantalla: null, titulo: null };
/**
 * El avance del formulario de venta abierto: { avance, avancePaso, avanceFaltan }.
 *
 * Viaja pegado al latido y no en una peticion propia: es el mismo dato —"que estoy haciendo
 * ahora"—, con la misma vida corta y el mismo destinatario. Un POST por cada tecla en un
 * formulario de doce campos seria mucho trafico para algo que solo se mira desde
 * administracion.
 */
let avance = { avance: null, avancePaso: null, avanceFaltan: null };

/** De donde late: sirve para distinguir el APK del navegador en la pantalla de seguimiento. */
const origen = () => (window.Capacitor?.isNativePlatform?.() ? 'APK' : 'WEB');

async function latir() {
  if (document.visibilityState === 'hidden') return;
  try {
    await apiFetch('/api/app/presencia', {
      method: 'POST',
      body: JSON.stringify({ ...ultima, ...avance, origen: origen() }),
    });
  } catch {
    // Sin señal no hay latido, y no hay nada que hacer al respecto: el silencio ya significa
    // "no esta" en la pantalla que lee esto.
  }
}

/** Empieza a latir. Idempotente: llamarla dos veces no duplica el temporizador. */
export function iniciarPresencia() {
  if (temporizador) return;
  temporizador = setInterval(latir, INTERVALO_MS);
  // Al volver al frente se late en el acto: si no, el usuario apareceria como ausente hasta el
  // siguiente intervalo, justo cuando ha vuelto a trabajar.
  document.addEventListener('visibilitychange', alVolver);
  latir();
}

function alVolver() {
  if (document.visibilityState === 'visible') latir();
}

/** Anota en que pantalla esta y late ya: el cambio de pantalla es la informacion mas util. */
export function marcarPantalla(pantalla, titulo) {
  ultima = { pantalla: pantalla || null, titulo: titulo || null };
  // Salir del formulario de venta deja de decir nada de su avance. Sin esto, quien empezo una
  // venta y se fue al mapa seguiria apareciendo eternamente "al 60%", y ese numero congelado
  // parece un vendedor trabado cuando en realidad se fue a otra cosa.
  if (pantalla !== 'venta') avance = { avance: null, avancePaso: null, avanceFaltan: null };
  if (temporizador) latir();
}

/**
 * Anota cuanto lleva del formulario de venta.
 *
 * NO late al llamarla: se rellena campo a campo y latir en cada tecla serian decenas de
 * peticiones por venta. El siguiente latido del intervalo lo lleva, asi que el numero puede ir
 * hasta {@link INTERVALO_MS} atrasado —suficiente para una pantalla que se mira para saber si
 * alguien se quedo trabado, no para cronometrar a nadie—. La excepcion es cambiar de paso, que
 * si late (ver `marcarPantalla`) porque es el salto que de verdad se nota.
 */
export function marcarAvance({ porcentaje = null, paso = null, faltan = null } = {}) {
  avance = {
    avance: porcentaje == null ? null : Math.max(0, Math.min(100, Math.round(porcentaje))),
    avancePaso: paso || null,
    avanceFaltan: faltan || null,
  };
}

/** Deja de informar avance: la venta se registro, se cancelo, o se salio del formulario. */
export function limpiarAvance() {
  avance = { avance: null, avancePaso: null, avanceFaltan: null };
  if (temporizador) latir();
}

/** Cierre de sesion: se avisa para no dejarlo en la lista hasta que venza su silencio. */
export async function detenerPresencia() {
  if (temporizador) clearInterval(temporizador);
  temporizador = null;
  document.removeEventListener('visibilitychange', alVolver);
  try {
    await apiFetch('/api/app/presencia/salir', { method: 'POST' });
  } catch {
    // Si no se pudo avisar, el silencio lo resuelve en un minuto.
  }
}
