/**
 * Reglas compartidas por el visor (Mapa.vue) y el editor (Editor.vue).
 *
 * El plano se dibuja con coordenadas normalizadas 0..1: la posicion y el TAMAÑO de una caseta
 * son fracciones del ancho de la imagen. Por eso el pin escala solo al hacer zoom, igual que
 * una caseta real sobre un plano: no hay que corregir nada a mano.
 */

/** Clase CSS por estado de caseta. */
export const CLASE_ESTADO = { L: 'libre', T: 'tramite', O: 'ocupado', X: 'bloqueado' };

export const ETIQUETA_ESTADO = {
  L: 'Libre',
  T: 'En trámite',
  O: 'Ocupado',
  X: 'Bloqueado',
};

/** Tamaño dibujado de una caseta, como fraccion del ancho del plano. */
export function tamanoDe(p) {
  return (p.tamanoMapa ?? 0.012) * (p.mapaEscala ?? 1);
}

/**
 * Estilo del pin: posicion centrada en (mapaX, mapaY) y ancho en % del plano.
 * `aspect-ratio: 1` en el CSS deriva el alto de ese ancho en pixeles, asi el pin sale
 * cuadrado aunque la imagen no lo sea.
 */
export function estiloPin(p) {
  return {
    left: `${p.mapaX * 100}%`,
    top: `${p.mapaY * 100}%`,
    width: `${tamanoDe(p) * 100}%`,
  };
}

/*
 * Nota: la funcion que aplicaba un PuestoEstadoDTO a una lista vivia aqui. Se movio a
 * `stores/puestos.js` (`aplicar`) cuando el estado de las casetas paso a ser compartido:
 * ahora hay un solo sitio que muta la lista, y ademas debe respetar los cambios locales
 * sin guardar del Editor. Tenerla duplicada en los dos lugares se prestaba a que
 * divergieran.
 */
