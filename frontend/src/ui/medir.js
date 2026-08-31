/**
 * Medicion de latencia percibida, solo en desarrollo.
 *
 * Existe porque hubo un desacuerdo entre lo medido y lo vivido: el servidor responde en
 * ~20 ms y el broadcast llega en ~30 ms medido desde Node, pero la interfaz se siente
 * lenta. Reparte el tiempo de un clic en las cuatro etapas que pueden fallar por separado:
 *
 *   entrada   — del momento en que el navegador CREO el evento de clic a cuando corrio
 *               nuestro manejador. Si esto es alto, el hilo principal estaba bloqueado
 *               (por render, GC o un script): el usuario ya habia hecho clic y la pagina
 *               no reaccionaba todavia.
 *   pintado   — del clic hasta que el navegador presenta el cambio optimista en pantalla.
 *   red       — lo que tarda la peticion HTTP desde ESTE navegador.
 *   broadcast — del clic hasta que llega el aviso del WebSocket para esa caseta. Es lo
 *               que ve la pantalla del otro vendedor.
 *
 * En produccion todas las funciones son no-operativas.
 */
// `import.meta.env` solo existe bajo Vite: fuera (por ejemplo en las pruebas con Node)
// queda desactivado, que es justo lo que se quiere.
const ACTIVO = Boolean(import.meta.env?.DEV);

const enCurso = new Map();

/**
 * Marca el inicio de una interaccion.
 * @param evento el evento de clic, para medir cuanto tardo en atenderse
 */
export function iniciarMedicion(id, etiqueta, evento) {
  if (!ACTIVO) return;
  const ahora = performance.now();
  // event.timeStamp usa el mismo reloj que performance.now(): la diferencia es el
  // tiempo que el evento paso encolado esperando al hilo principal.
  const entrada = evento?.timeStamp ? ahora - evento.timeStamp : null;
  enCurso.set(id, { t0: ahora, etiqueta, entrada, pintado: null, red: null });
}

/**
 * Llamar tras `nextTick()` + doble rAF: para entonces el navegador ya presento el
 * fotograma con el cambio optimista, asi que mide el pintado real y no solo el
 * trabajo de Vue.
 */
export function marcarPintado(id) {
  const m = ACTIVO && enCurso.get(id);
  if (m) m.pintado = performance.now() - m.t0;
}

export function marcarRed(id) {
  const m = ACTIVO && enCurso.get(id);
  if (m) m.red = performance.now() - m.t0;
}

/**
 * Cierra la medicion y la vuelca a consola.
 *
 * IMPORTANTE: solo debe llamarla la llegada de un mensaje del WebSocket. Antes estaba
 * dentro de `aplicar()` del store, que es lo que usa tambien la escritura optimista, asi
 * que la medicion se cerraba a los 0 ms y no medía absolutamente nada.
 */
export function cerrarMedicion(id) {
  const m = ACTIVO && enCurso.get(id);
  if (!m) return;
  enCurso.delete(id);
  const total = performance.now() - m.t0;
  const n = (v) => (v == null ? '    —' : `${Math.round(v)}`.padStart(5) + 'ms');
  const lento = total > 300 || (m.entrada ?? 0) > 150 ? '   <<< LENTO' : '';
  console.log(
    `[medir] ${m.etiqueta} caseta ${id} · entrada ${n(m.entrada)}`
    + ` · pintado ${n(m.pintado)} · red ${n(m.red)} · broadcast ${n(total)}${lento}`,
  );
}

/** Cierra las que llevan demasiado tiempo abiertas: si el broadcast nunca llega, hay que verlo. */
export function purgarMediciones(msLimite = 6000) {
  if (!ACTIVO) return;
  const ahora = performance.now();
  for (const [id, m] of enCurso) {
    if (ahora - m.t0 > msLimite) {
      enCurso.delete(id);
      console.warn(`[medir] ${m.etiqueta} caseta ${id} · EL BROADCAST NUNCA LLEGO (>${msLimite} ms)`);
    }
  }
}
