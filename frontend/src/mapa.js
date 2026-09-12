/**
 * Reglas compartidas por el visor (Mapa.vue) y el editor (Editor.vue).
 *
 * El plano se dibuja con coordenadas normalizadas 0..1: la posicion y el TAMAÑO de una caseta
 * son fracciones del ancho de la imagen. Por eso el pin escala solo al hacer zoom, igual que
 * una caseta real sobre un plano: no hay que corregir nada a mano.
 */
import { ref, watch } from 'vue';

/** Clase CSS por estado de caseta. */
export const CLASE_ESTADO = { L: 'libre', T: 'tramite', O: 'ocupado', X: 'bloqueado' };

export const ETIQUETA_ESTADO = {
  L: 'Libre',
  T: 'En trámite',
  O: 'Ocupado',
  X: 'Bloqueado',
};

/**
 * Leyenda del mapa. No sale de ETIQUETA_ESTADO porque distingue algo que el estado del
 * servidor no distingue: una caseta 'T' es "mia" o "de otro" segun quien mire, y esa es
 * justo la diferencia que el vendedor necesita ver de un vistazo en el plano.
 */
export const LEYENDA = [
  { clase: 'libre', txt: 'Libre' },
  { clase: 'mia', txt: 'En mi venta' },
  { clase: 'tramite', txt: 'Otro vendedor' },
  { clase: 'ocupado', txt: 'Vendida' },
  { clase: 'bloqueado', txt: 'Bloqueada' },
];

/** Tamaño dibujado de una caseta, como fraccion del ancho del plano. */
export function tamanoDe(p) {
  return (p.tamanoMapa ?? 0.012) * (p.mapaEscala ?? 1);
}

/**
 * Estilo del pin: posicion centrada en (mapaX, mapaY) y ancho en % del plano.
 * `aspect-ratio: 1` en el CSS deriva el alto de ese ancho en pixeles, asi el pin sale
 * cuadrado aunque la imagen no lo sea.
 *
 * `--pin` es el ancho del pin en pixeles de maquetacion, sin unidad. Sale de `--mundo`, que
 * PanZoom publica con el ancho de diseño del plano. Con el, el CSS mide el numero y el grosor
 * del aro en fracciones de caseta, y del zoom se encarga el transform del mundo.
 *
 * El pin NO se dimensiona con `width`: su caja mide siempre 100 px y se reduce con
 * `transform: scale()` (ver `.pin` en el CSS). Con `width` en porcentaje, una caseta de
 * 0.005 del ancho salia a 1.95 px de maquetacion y el navegador redondea eso a pixel entero
 * de forma distinta segun donde caiga cada una: unas quedaban de 1 px y otras de 2, o sea
 * "unas mas anchas y otras mas pequeñas" aunque estuvieran configuradas iguales. Una caja de
 * 100 px no tiene ese problema, y la escala se aplica sin redondeos.
 *
 * Aqui han caido ya dos intentos mas, los dos por fallar EN SILENCIO:
 *   - `cqw` (container queries): exige un WebView reciente; en uno viejo la declaracion se
 *     descarta y el numero se queda al tamaño heredado.
 *   - un `font-size` diminuto en el pin (con el rotulo en `em`): con casetas de 0.005 del
 *     ancho, el pin mide ~2 px de maquetacion y su fuente saldria a ~1 px. El WebView de
 *     Android aplica un tamaño MINIMO de fuente (8 px por defecto), asi que el numero salia
 *     cuatro veces mas grande que su caseta y se recortaba: por eso "no se ven las
 *     numeraciones". El rotulo ya no usa `em` ni fuentes pequeñas — ver `.num-caseta`.
 */
export function estiloPin(p) {
  return {
    left: `${p.mapaX * 100}%`,
    top: `${p.mapaY * 100}%`,
    '--pin': `calc(var(--mundo, 1200) * ${tamanoDe(p)})`,
    '--giro': `${p.mapaRotacion || 0}deg`,
  };
}

/**
 * Preferencia compartida por el visor y el editor: ¿se rotula cada caseta con su número?
 *
 * Vive aquí y no en cada vista para que sea UNA sola preferencia: el vendedor que apaga los
 * números en el mapa no se los encuentra encendidos al abrir el editor. Se recuerda entre
 * sesiones; encendida por defecto, porque el número es como se nombra la caseta al cliente.
 */
export const numerosVisibles = ref(localStorage.getItem('mapa.numeros') !== '0');
watch(numerosVisibles, (v) => localStorage.setItem('mapa.numeros', v ? '1' : '0'));

/*
 * ---- cuánto hay que acercarse ----
 *
 * El tamaño de una caseta lo fija el administrador contra el dibujo del plano, no contra la
 * pantalla: en FEXPO vale 0.005, o sea medio punto porcentual del ancho del plano. En un
 * celular de 390 px eso son 2 px al abrir el mapa y 19 px al tope de zoom, y a ese tamaño un
 * cuadrado con la esquina redondeada se ve como un punto — de ahí lo de "se ven circulares".
 *
 * Por eso el tope de acercamiento NO puede ser un factor fijo: 10x significa cosas distintas
 * en un celular y en un monitor, y cambia solo con que el administrador redibuje el plano. Se
 * expresa en píxeles de caseta y se deja que cada pantalla saque su propio factor.
 */

/** Lado en píxeles al que una caseta ya se toca cómodo (mínimo táctil recomendado). */
export const PIN_COMODO_PX = 44;
/** Lado en píxeles a partir del cual el número de dentro se lee. */
export const PIN_LEGIBLE_PX = 22;

/** Tamaño representativo de las casetas del plano (la mediana), como fracción del ancho. */
function tamanoTipico(puestos) {
  const tams = puestos.map(tamanoDe).filter((t) => t > 0).sort((a, b) => a - b);
  return tams.length ? tams[Math.floor(tams.length / 2)] : 0.012;
}

/** La caseta más pequeña del plano: es la que decide cuánto hay que poder acercarse. */
function tamanoMinimo(puestos) {
  const tams = puestos.map(tamanoDe).filter((t) => t > 0);
  return tams.length ? Math.min(...tams) : 0.012;
}

/**
 * Ancho de plano renderizado (px) con el que la caseta más pequeña llega a tocarse cómoda.
 * Es el techo de zoom que se le pasa a PanZoom: en píxeles del contenido, no en aumentos.
 */
export function anchoParaTocar(puestos) {
  return PIN_COMODO_PX / tamanoMinimo(puestos);
}

/** Ancho de plano renderizado (px) a partir del cual el número de la caseta se lee. */
export function anchoParaLeer(puestos) {
  return PIN_LEGIBLE_PX / tamanoTipico(puestos);
}

/**
 * Ordena casetas como se leen sobre el plano: filas de arriba abajo y, dentro de cada fila,
 * de izquierda a derecha. Con `columnas` se invierten los ejes.
 *
 * No basta con ordenar por (y, x): dos casetas de la misma fila nunca tienen exactamente la
 * misma `y`, y un orden lexicográfico las intercala con las de la fila siguiente. Primero se
 * agrupan en bandas del grosor de una caseta, y solo dentro de cada banda se ordena.
 *
 * `aspecto` (alto/ancho del plano) hace falta porque `mapaX` y `mapaY` están normalizados
 * contra ejes distintos: un cuadrado de lado `t` medido en fracción de ancho ocupa
 * `t / aspecto` en fracción de alto.
 */
export function ordenLectura(lista, aspecto = 2376 / 1836, columnas = false) {
  const ubicadas = lista.filter((p) => p.mapaX != null && p.mapaY != null);
  if (!ubicadas.length) return [];

  const eje = columnas ? 'mapaX' : 'mapaY';
  const cruz = columnas ? 'mapaY' : 'mapaX';
  // Grosor de banda = la caseta más grande del grupo, para que una fila de casetas
  // desiguales no se parta en dos bandas.
  const lado = Math.max(0.004, ...ubicadas.map(tamanoDe));
  const tolerancia = columnas ? lado : lado / aspecto;

  const bandas = [];
  for (const p of [...ubicadas].sort((a, b) => a[eje] - b[eje])) {
    const ultima = bandas[bandas.length - 1];
    if (ultima && p[eje] - ultima.ref <= tolerancia) ultima.items.push(p);
    else bandas.push({ ref: p[eje], items: [p] });
  }
  return bandas.flatMap((b) => b.items.sort((a, b2) => a[cruz] - b2[cruz]));
}

/**
 * Renumeración que cierra los huecos de una categoría: 1,2,3,5,6 → 1,2,3,4,5.
 *
 * Conserva el orden numérico que ya tenían, así que solo se mueven las que van detrás del
 * hueco, y devuelve ÚNICAMENTE las que cambian. Eso segundo no es un ahorro de red: es lo que
 * permite compactar con la feria en marcha, porque una caseta vendida que esté por delante del
 * hueco no entra en el lote y no lo hace rechazar. El backend no renumera una vendida — su
 * número está impreso en un recibo ya entregado.
 *
 * Devuelve la lista lista para `PATCH /api/app/puestos/codigos`; vacía si ya está corrida.
 */
export function numeracionCompacta(puestos, categoriaId) {
  const dela = puestos
    .filter((p) => p.categoriaId === categoriaId)
    .sort((a, b) => (parseInt(a.codigo) || 0) - (parseInt(b.codigo) || 0));
  return dela
    .map((p, i) => ({ id: p.id, codigo: String(i + 1) }))
    .filter((c, i) => String(dela[i].codigo ?? '').trim() !== c.codigo);
}

/*
 * Nota: la funcion que aplicaba un PuestoEstadoDTO a una lista vivia aqui. Se movio a
 * `stores/puestos.js` (`aplicar`) cuando el estado de las casetas paso a ser compartido:
 * ahora hay un solo sitio que muta la lista, y ademas debe respetar los cambios locales
 * sin guardar del Editor. Tenerla duplicada en los dos lugares se prestaba a que
 * divergieran.
 */
