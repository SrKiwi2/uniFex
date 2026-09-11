/**
 * Cuanto ocupa el teclado en pantalla, publicado como variable CSS `--kb`.
 *
 * Hace falta porque desde Android 15 una ventana de borde a borde —y la del APK lo es a la
 * fuerza— ya no se redimensiona sola al abrirse el teclado: `adjustResize` quedo obsoleto y
 * la app tiene que apañarselas. Sin esto, los ultimos campos de un formulario quedan debajo
 * del teclado y no hay forma de llegar a ellos.
 *
 * La cuenta sirve para los dos comportamientos posibles, que es lo que la hace fiable:
 *   - si la ventana SI se redimensiona (Android 14 y anteriores con adjustResize),
 *     `innerHeight` ya encogio y la resta da 0: no hay nada que compensar;
 *   - si NO se redimensiona o se desplaza (adjustPan), `innerHeight` sigue siendo el de la
 *     pantalla completa y la resta da el alto real del teclado.
 *
 * Se usa `visualViewport`, que es el unico que informa de esto en el navegador, y no un
 * plugin nativo: asi funciona igual en la web, en el APK y en cualquier version de Android.
 */

/** Por debajo de esto no es un teclado, es la barra de gestos o un redondeo. */
const MINIMO_TECLADO = 80;

export function observarTeclado() {
  const vv = typeof window !== 'undefined' ? window.visualViewport : null;
  if (!vv) return () => {};

  const aplicar = () => {
    const alto = Math.max(0, Math.round(window.innerHeight - vv.height - vv.offsetTop));
    const raiz = document.documentElement;
    raiz.style.setProperty('--kb', alto > MINIMO_TECLADO ? `${alto}px` : '0px');
    raiz.classList.toggle('con-teclado', alto > MINIMO_TECLADO);
  };

  vv.addEventListener('resize', aplicar);
  vv.addEventListener('scroll', aplicar);
  aplicar();

  return () => {
    vv.removeEventListener('resize', aplicar);
    vv.removeEventListener('scroll', aplicar);
  };
}
