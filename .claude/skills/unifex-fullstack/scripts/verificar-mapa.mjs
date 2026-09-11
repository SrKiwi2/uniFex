#!/usr/bin/env node
/**
 * Pruebas del mapa y del formulario de venta que no necesitan ni servidor ni base de datos.
 *
 * Cubre la logica que no tiene otra red debajo y que, si se rompe, se rompe en silencio:
 *   - el orden de lectura con el que se renumera un bloque de casetas;
 *   - el cierre de huecos de numeracion;
 *   - cuanto hay que acercarse para que una caseta se toque y su numero se lea;
 *   - que el pin siga saliendo NITIDO al hacer zoom (dos fallos silenciosos ya vividos);
 *   - el almacen compartido: copia en disco, resincronizacion y —lo mas delicado— que una
 *     recarga automatica NO le pise al Editor las casetas movidas sin guardar;
 *   - que el JSON del formulario de venta encaje con el record que espera el backend;
 *   - que la app respete los margenes seguros del movil (barra de estado y de gestos).
 *
 * Uso (desde cualquier sitio):
 *     node .claude/skills/unifex-fullstack/scripts/verificar-mapa.mjs
 *
 * Para el ciclo real contra el backend (login, reserva, 409, WebSocket) usa el otro script:
 *     node .claude/skills/unifex-fullstack/scripts/verificar-api.mjs
 *
 * Solo Node (>=18). En esta maquina no hay Python.
 */

import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';

const AQUI = dirname(fileURLToPath(import.meta.url));
const SRC = resolve(AQUI, '../../../../frontend/src') + '/';

const fallos = [];
function paso(descripcion, ok, detalle = '') {
  console.log(`  [${ok ? 'OK   ' : 'FALLA'}] ${descripcion}${detalle ? `  -> ${detalle}` : ''}`);
  if (!ok) fallos.push(descripcion);
}
const titulo = (t) => console.log(`\n${t}`);

// ---------------------------------------------------------------- entorno simulado
// Se monta ANTES de importar nada de la app: mapa.js lee localStorage al cargarse y Vue
// toca document en cuanto se importa.
const disco = new Map();
globalThis.localStorage = {
  getItem: (k) => (disco.has(k) ? disco.get(k) : null),
  setItem: (k, v) => disco.set(k, String(v)),
  removeItem: (k) => disco.delete(k),
};
const oyentes = { doc: new Map(), win: new Map() };
const nodoFalso = () => ({
  content: { firstChild: null }, firstChild: null, innerHTML: '', style: {},
  setAttribute() {}, removeAttribute() {}, appendChild() {}, remove() {},
  addEventListener() {}, removeEventListener() {},
  classList: { add() {}, remove() {}, toggle() {} },
});
globalThis.document = {
  visibilityState: 'visible',
  createElement: nodoFalso, createElementNS: nodoFalso, createTextNode: nodoFalso,
  createComment: nodoFalso, querySelector: () => null, documentElement: nodoFalso(),
  addEventListener: (t, fn) => oyentes.doc.set(t, fn),
  removeEventListener: (t) => oyentes.doc.delete(t),
};
globalThis.window = {
  addEventListener: (t, fn) => oyentes.win.set(t, fn),
  removeEventListener: (t) => oyentes.win.delete(t),
  location: { protocol: 'http:', host: 'localhost' },
};
globalThis.location = globalThis.window.location;

const mapa = await import(SRC + 'mapa.js');

// ---------------------------------------------------------------- orden de lectura
titulo('Orden de lectura (con el que se numera un bloque)');
{
  const ASPECTO = 2376 / 1836;
  const T = 0.012;
  const alto = T / ASPECTO;
  const casetas = [];
  let id = 0;
  [0.20, 0.20 + alto * 3, 0.20 + alto * 6].forEach((y, fila) => {
    for (let col = 0; col < 4; col++) {
      casetas.push({
        id: ++id, fila, col,
        mapaX: 0.30 + col * T * 2.5,
        // La y "tiembla" dentro de cada fila a proposito: es lo que hace que un sort
        // por (y, x) intercale las filas y devuelva un orden inservible.
        mapaY: y + (col % 2 ? alto * 0.3 : 0),
        tamanoMapa: T, mapaEscala: 1,
      });
    }
  });
  casetas.sort(() => Math.random() - 0.5);

  const filas = mapa.ordenLectura(casetas, ASPECTO).map((p) => `${p.fila}${p.col}`).join(' ');
  const cols = mapa.ordenLectura(casetas, ASPECTO, true).map((p) => `${p.fila}${p.col}`).join(' ');
  paso('por filas: izquierda a derecha, arriba a abajo',
       filas === '00 01 02 03 10 11 12 13 20 21 22 23', filas);
  paso('por columnas: arriba a abajo, izquierda a derecha',
       cols === '00 10 20 01 11 21 02 12 22 03 13 23', cols);
  paso('las casetas sin colocar se descartan',
       mapa.ordenLectura([{ id: 9, mapaX: null, mapaY: null }], ASPECTO).length === 0);
  paso('lista vacia no revienta', mapa.ordenLectura([], ASPECTO).length === 0);
}

// ---------------------------------------------------------------- cierre de huecos
titulo('Cierre de huecos de numeracion');
{
  const casetas = (cat, cods) => cods.map((c, i) => ({ id: cat * 1000 + i, categoriaId: cat, codigo: String(c) }));
  let r = mapa.numeracionCompacta(casetas(1, [1, 2, 3, 4, 5, 6, 8, 9, 10]), 1);
  paso('borrada la 7 de 10, solo se mueven las de detras',
       JSON.stringify(r) === JSON.stringify([{ id: 1006, codigo: '7' }, { id: 1007, codigo: '8' }, { id: 1008, codigo: '9' }]),
       JSON.stringify(r));
  paso('una categoria ya corrida no genera ninguna escritura',
       mapa.numeracionCompacta(casetas(1, [1, 2, 3, 4, 5]), 1).length === 0);
  r = mapa.numeracionCompacta(casetas(1, [2, 5, 9]), 1);
  paso('huecos multiples quedan en 1,2,3',
       JSON.stringify(r.map((c) => c.codigo)) === JSON.stringify(['1', '2', '3']));
  const mezcla = [...casetas(1, [1, 3]), ...casetas(2, [1, 2, 3])];
  paso('solo afecta a la categoria pedida',
       mapa.numeracionCompacta(mezcla, 1).length === 1 && mapa.numeracionCompacta(mezcla, 2).length === 0);
  paso('un codigo con espacios no cuenta como cambio',
       mapa.numeracionCompacta([{ id: 1, categoriaId: 9, codigo: ' 1 ' }, { id: 2, categoriaId: 9, codigo: '2' }], 9).length === 0);
  paso('llega desordenado y reparte por orden numerico',
       JSON.stringify(mapa.numeracionCompacta([
         { id: 3, categoriaId: 1, codigo: '9' },
         { id: 1, categoriaId: 1, codigo: '2' },
         { id: 2, categoriaId: 1, codigo: '5' },
       ], 1)) === JSON.stringify([{ id: 1, codigo: '1' }, { id: 2, codigo: '2' }, { id: 3, codigo: '3' }]));
}

// ---------------------------------------------------------------- cuanto acercarse
titulo('Tope de zoom y umbral del numero');
{
  // Datos reales de FEXPO: la categoria PYMES tiene las casetas a 0.005 del ancho del plano.
  const fexpo = Array.from({ length: 109 }, (_, i) => ({ id: i, tamanoMapa: 0.005, mapaEscala: 1 }));
  const techo = mapa.anchoParaTocar(fexpo);
  const umbral = mapa.anchoParaLeer(fexpo);
  const lado = (visor, escala, t = 0.005) => visor * escala * t;
  const escalaTope = (visor) => Math.max(10, techo / visor);

  for (const [nombre, ancho] of [['celular 390px', 390], ['tablet 800px', 800], ['monitor 1400px', 1400]]) {
    const e = escalaTope(ancho);
    paso(`${nombre}: a tope de zoom la caseta llega a 44px o mas`,
         lado(ancho, e) >= 43.5, `zoom ${e.toFixed(1)}x -> ${lado(ancho, e).toFixed(1)}px`);
  }
  paso('con el tope viejo (10x fijo) el celular se quedaba en 19.5px',
       Math.abs(lado(390, 10) - 19.5) < 0.5);
  paso('el numero aparece cuando la caseta ronda los 22px',
       Math.abs(umbral * 0.005 - 22) < 0.01, `umbral = ${umbral.toFixed(0)}px de plano`);
  paso('con casetas mas grandes hace falta menos zoom',
       mapa.anchoParaTocar(Array.from({ length: 20 }, () => ({ tamanoMapa: 0.012, mapaEscala: 1 }))) < techo);
  const mixto = [{ tamanoMapa: 0.012, mapaEscala: 1 }, { tamanoMapa: 0.012, mapaEscala: 1 }, { tamanoMapa: 0.004, mapaEscala: 1 }];
  paso('el techo lo fija la caseta MAS PEQUEÑA', Math.abs(mapa.anchoParaTocar(mixto) - 44 / 0.004) < 1);
  paso('el umbral del rotulo lo fija la mediana', Math.abs(mapa.anchoParaLeer(mixto) - 22 / 0.012) < 1);
  paso('mapaEscala entra en la cuenta',
       Math.abs(mapa.anchoParaTocar([{ tamanoMapa: 0.005, mapaEscala: 2 }]) - 44 / 0.01) < 1);
  paso('lista vacia no revienta',
       Number.isFinite(mapa.anchoParaTocar([])) && Number.isFinite(mapa.anchoParaLeer([])));
}

// ---------------------------------------------------------------- almacen compartido
titulo('Almacen de casetas: copia en disco y resincronizacion');
{
  const { createPinia, setActivePinia } = await import(resolve(SRC, '../node_modules/pinia/dist/pinia.mjs'));
  setActivePinia(createPinia());
  const { useAuthStore } = await import(SRC + 'stores/auth.js');
  useAuthStore().token = 'falso';

  let servidor = [];
  let asignado = [];
  let peticiones = 0;
  // Cada carga son DOS peticiones: el listado de casetas y quien responde por cada una.
  const responder = (ruta) => (String(ruta).includes('asignaciones') ? asignado : servidor);
  globalThis.fetch = async (ruta) => {
    peticiones++;
    return { status: 200, ok: true, json: async () => JSON.parse(JSON.stringify(responder(ruta))) };
  };
  const caseta = (id, x, y, estado = 'L') =>
    ({ id, codigo: String(id), categoriaId: 1, estado, mapaX: x, mapaY: y, mapaEscala: 1 });

  const { usePuestosStore } = await import(SRC + 'stores/puestos.js');
  const t = usePuestosStore();

  servidor = [caseta(1, 0.1, 0.1), caseta(2, 0.2, 0.2)];
  await t.cargar();
  paso('carga inicial', t.puestos.length === 2 && t.ultimaSync > 0);
  paso('deja copia en disco', !!disco.get('puestos.cache.v1'));
  paso('desdeCache queda en false tras confirmar', t.desdeCache === false);

  // El caso que de verdad importa: ahora la lista se vuelve a pedir SOLA, y el Editor puede
  // tener casetas movidas sin guardar. Pisarlas seria deshacerle el trabajo sin avisar.
  const quitarGuardia = t.protegerLocales((id) => id === 1);
  t.puestos[0].mapaX = 0.9;
  t.puestos[0].mapaY = 0.9;
  servidor = [caseta(1, 0.1, 0.1, 'O'), caseta(2, 0.5, 0.5)];
  await t.recargar();
  const p1 = t.puestos.find((p) => p.id === 1);
  paso('una recarga automatica CONSERVA la posicion sin guardar',
       p1.mapaX === 0.9 && p1.mapaY === 0.9, `x=${p1.mapaX} y=${p1.mapaY}`);
  paso('pero si acepta el estado de venta nuevo', p1.estado === 'O');
  paso('la caseta no protegida se actualiza entera',
       t.puestos.find((p) => p.id === 2).mapaX === 0.5);
  quitarGuardia();

  servidor = [caseta(1, 0.3, 0.3), caseta(2, 0.4, 0.4)];
  await t.recargar();
  paso('sin cambios pendientes, la recarga reemplaza normal',
       t.puestos.find((p) => p.id === 1).mapaX === 0.3);

  servidor = [caseta(1, 0.3, 0.3), caseta(2, 0.4, 0.4), caseta(3, 0.7, 0.7)];
  await t.recargar();
  paso('una caseta creada desde la web aparece', t.puestos.some((p) => p.id === 3));
  servidor = [caseta(3, 0.7, 0.7)];
  await t.recargar();
  paso('las casetas eliminadas desaparecen', t.puestos.length === 1);

  asignado = [{ puestoId: 3, vendedorId: 9, vendedor: 'ANA PEREZ', celular: '70000000' }];
  await t.recargar();
  paso('el store guarda quien responde por cada caseta',
       t.asignaciones.get(3)?.vendedor === 'ANA PEREZ', JSON.stringify([...t.asignaciones]));


  t.desconectar();
  paso('cerrar sesion vacia la lista', t.puestos.length === 0);
  paso('pero no borra la copia en disco', !!disco.get('puestos.cache.v1'));

  const antes = peticiones;
  // Se retienen TODAS las respuestas, no solo una: si se resolviera la del listado y no la de
  // asignaciones, `asegurar()` no terminaria nunca y la prueba se quedaria colgada.
  const retenidas = [];
  globalThis.fetch = (ruta) => {
    peticiones++;
    return new Promise((r) => retenidas.push(
      () => r({ status: 200, ok: true, json: async () => responder(ruta) })));
  };
  const enCurso = t.asegurar();
  paso('arranque en frio: hay mapa ANTES de que conteste el servidor',
       t.puestos.length > 0, `${t.puestos.length} casetas`);
  paso('y se marca como no confirmado', t.desdeCache === true);
  paso('las peticiones salen igual (listado + asignaciones)', peticiones === antes + 2,
       `${peticiones - antes}`);
  retenidas.forEach((soltar) => soltar());
  await enCurso;
  paso('al contestar, deja de estar marcado', t.desdeCache === false);

  paso('escucha el volver al frente', oyentes.doc.has('visibilitychange'));
  paso('escucha la vuelta de la red', oyentes.win.has('online'));
  t.desconectar();
  paso('al cerrar sesion se retiran los oyentes',
       !oyentes.doc.has('visibilitychange') && !oyentes.win.has('online'));
}

// ---------------------------------------------------------------- nitidez del pin
/*
 * Dos fallos SILENCIOSOS ya han pasado por aqui, y los dos se veian igual desde el APK
 * ("al acercarme se ve borroso y no se leen los numeros"):
 *
 *   1. `will-change: transform` fijo en el mundo. Chromium congela la escala a la que
 *      rasteriza una capa que declara que su transform va a cambiar: a 20 aumentos seguia
 *      pintando la textura de 1x, estirada.
 *   2. Medir el rotulo con el font-size del pin. Con casetas de 0.005 del ancho, la fuente
 *      salia a ~1 px y el WebView de Android la sube a su minimo de 8: el numero acababa
 *      cuatro veces mas grande que su caseta y recortado.
 *
 * Ninguno de los dos da error en consola ni rompe la compilacion, asi que se vigilan aqui.
 */
titulo('Nitidez del pin al hacer zoom');
{
  const { readFileSync } = await import('node:fs');
  const RAIZ = resolve(AQUI, '../../../..') + '/';
  const leer = (p) => readFileSync(RAIZ + p, 'utf8');
  const panzoom = leer('frontend/src/components/PanZoom.vue');
  const vistas = ['frontend/src/views/Mapa.vue', 'frontend/src/views/Editor.vue'].map(leer);
  // Se quitan los comentarios antes de mirar: varios EXPLICAN lo que ya no se hace ("se quito
  // will-change"), y buscarlos en crudo daria por presente justo lo que se retiro.
  const sinComentarios = (t) => t.replace(/\/\*[\s\S]*?\*\//g, '');
  const estilos = vistas.map((v) => sinComentarios(v.slice(v.indexOf('<style'))));

  paso('el mundo NO declara will-change fijo en el CSS',
       !/\.world\s*\{[^}]*will-change/.test(sinComentarios(panzoom.slice(panzoom.indexOf('<style')))));
  paso('el will-change se pone y se quita alrededor del gesto',
       panzoom.includes('function promoverCapa') && panzoom.includes("willChange = 'auto'"));
  paso('el temporizador se limpia al desmontar',
       panzoom.indexOf('clearTimeout(temporizadorNitidez)', panzoom.indexOf('onBeforeUnmount')) > 0);
  paso('la imagen del plano tampoco lleva will-change',
       estilos.every((e) => !/\.plano img\s*\{[^}]*will-change/.test(e)));
  paso('pero conserva su capa propia (translateZ)',
       estilos.every((e) => /\.plano img\s*\{[^}]*translateZ\(0\)/.test(e)));

  paso('no quedan unidades de container query (fallan en silencio en WebView viejo)',
       estilos.every((e) => !/\dcqw|container-type/.test(e)));
  paso('el rotulo no se mide en em (lo pisaria el minimo de fuente de Android)',
       estilos.every((e) => {
         const bloque = e.slice(e.indexOf('.num-caseta'), e.indexOf('.num-caseta') + 700);
         return !/font-size:\s*[\d.]+em/.test(bloque);
       }));
  paso('el PIN es una caja fija de 100px reducida con transform',
       estilos.every((e) => /\.pin\s*\{[^}]*width:\s*100px[^}]*transform:\s*scale\(calc\(var\(--pin/.test(e)));
  paso('y el rotulo solo ocupa esa caja, sin escalarse aparte',
       estilos.every((e) => /\.num-caseta\s*\{[^}]*inset:\s*0/.test(e)));

  // La aritmetica: con los tamaños reales de FEXPO el numero tiene que acabar legible.
  const anchoVisor = 390;               // celular tipico, px CSS
  const fraccion = 0.005;               // tamano_mapa de la categoria PYMES
  const pin = anchoVisor * fraccion;    // --pin, px de maquetacion
  const CAJA = 100, FUENTE = 52;        // los de .num-caseta
  const zoomTope = mapa.anchoParaTocar([{ tamanoMapa: fraccion, mapaEscala: 1 }]) / anchoVisor;

  const alturaTexto = FUENTE * (pin / CAJA) * zoomTope;   // la caja de 100 se reduce a --pin
  paso('a tope de zoom el numero mide ~23 px en pantalla',
       Math.abs(alturaTexto - 0.52 * 44) < 1, `${alturaTexto.toFixed(1)} px`);
  paso('la fuente de maquetacion es grande (no la toca el minimo de 8 px de Android)',
       FUENTE >= 16, `${FUENTE} px`);
  paso('un codigo de 3 cifras entra en la caja del rotulo',
       3 * FUENTE * 0.62 < CAJA, `~${Math.round(3 * FUENTE * 0.62)} px de ${CAJA}`);

  const aro = pin * (12 / 100) * zoomTope;   // --aro: 12px sobre la caja de 100
  paso('el aro de categoria se ve a tope de zoom', aro >= 2, `${aro.toFixed(1)} px`);

  // estiloPin ya no debe publicar font-size: quien mide es --pin.
  const estilo = mapa.estiloPin({ mapaX: 0.5, mapaY: 0.5, tamanoMapa: fraccion, mapaEscala: 1 });
  paso('estiloPin publica --pin', typeof estilo['--pin'] === 'string' && estilo['--pin'].includes('--mundo'));
  paso('estiloPin ya no publica font-size', estilo.fontSize === undefined);
  /*
   * estiloPin ya NO devuelve `width`. Con un ancho en porcentaje, una caseta de 0.005 salia a
   * 1.95 px de maquetacion y el navegador redondea eso a pixel entero de forma distinta segun
   * donde caiga cada una: unas de 1 px y otras de 2, o sea "unas mas anchas y otras mas
   * pequeñas" estando configuradas iguales. Ahora el tamaño lo da el transform, sin redondeos.
   */
  paso('estiloPin ya no dimensiona con width (era lo que deformaba las casetas)',
       estilo.width === undefined);
  paso('--pin lleva la fraccion de la caseta', estilo['--pin'].includes(String(fraccion)));

  paso('PanZoom publica --mundo sin unidad (hace falta para dividir en calc)',
       panzoom.includes("setProperty('--mundo', String(Math.round(ancho)))"));
  /*
   * Dentro del mundo un `px` NO es un pixel: el zoom es un transform y multiplica toda medida
   * absoluta. Con casetas de ~2 px de maquetacion, `border-radius: 2px` dejaba redonda una
   * caseta cuadrada y `box-shadow: 0 0 0 1px` se convertia en un halo de 22 px. Este barrido
   * es la guarda: en las reglas que se dibujan dentro del mundo no puede quedar ni una.
   */
  // `.pin` y lo que hay dentro quedan FUERA del barrido a proposito: el pin es una caja de
  // 100 px que se reduce entera con transform, asi que los px de dentro escalan con ella y son
  // la unidad correcta. Lo que sigue siendo peligroso es lo que se dibuja en el plano SIN esa
  // caja — la guia de colocacion y el recuadro de seleccion del editor.
  const DENTRO = /(^|[\s,])(\.forma-|\.guia|\.caja|\.plano)\b/;
  const absolutas = [];
  for (const e of estilos) {
    for (const [, sel, cuerpo] of e.matchAll(/([^{}]+)\{([^}]*)\}/g)) {
      const s = sel.trim().replace(/\s+/g, ' ');
      // `.num-caseta` es la excepcion declarada: caja fija de 100 px reducida con transform.
      if (!DENTRO.test(s) || /\.pin|\.num-caseta/.test(s)) continue;
      for (const decl of cuerpo.split(';')) {
        const limpio = decl.replace(/calc\([^)]*var\(--(pin|mundo)[^)]*\)[^;]*/g, '');
        if (/[\d.]+px/.test(limpio)) absolutas.push(`${s} -> ${decl.trim()}`);
      }
    }
  }
  paso('la guia y el recuadro del editor no usan medidas absolutas',
       absolutas.length === 0, absolutas.slice(0, 3).join(' | '));

  paso('la caseta cuadrada redondea en porcentaje, no en px',
       estilos.every((e) => /\.forma-cuadrado\s*\{\s*border-radius:\s*[\d.]+%/.test(e)));
  paso('el pin no usa `border` (con box-sizing se comeria la caseta entera)',
       estilos.every((e) => !/\.pin[^{]*\{[^}]*border:\s*[\d.]+px/.test(e)));
  paso('el separador y el aro se miden en la caja de 100px del pin',
       estilos.every((e) => /--borde:\s*6px/.test(e) && /--aro:\s*12px/.test(e)));

  // Como se ve al final, con los tamaños reales de FEXPO.
  const separador = pin * (6 / 100) * zoomTope;   // --borde: 6px sobre la caja de 100
  const radio = pin * 0.10 * zoomTope;            // border-radius: 10%
  paso('el separador blanco se ve, sin comerse la caseta', separador >= 1.5 && separador <= 5,
       `${separador.toFixed(1)} px`);
  paso('la esquina redondeada es sutil: la caseta se lee cuadrada', radio < 44 / 4,
       `radio ${radio.toFixed(1)} px sobre una caseta de 44`);

}

// ---------------------------------------------------------------- formulario de venta
/*
 * El formulario manda un JSON plano contra un `record` de Java. Si se anade un campo en un
 * lado y no en el otro no falla nada visible: Jackson ignora lo que sobra y pone null en lo
 * que falta, asi que el dato simplemente NO se guarda y nadie se entera hasta que alguien lo
 * busca meses despues. Esto compara las dos listas.
 */
titulo('Contrato del formulario de venta');
{
  const { readFileSync } = await import('node:fs');
  const RAIZ = resolve(AQUI, '../../../..') + '/';
  const java = readFileSync(RAIZ + 'src/main/java/com/usic/uniFex/model/service/RegistroVentaService.java', 'utf8');

  const componentes = (nombre) => {
    const i = java.indexOf(`public record ${nombre}(`);
    const desde = i + `public record ${nombre}(`.length;
    const cuerpo = java.slice(desde, java.indexOf(')', java.indexOf('{', i) - 200));
    return cuerpo.split(',').map((t) => t.trim().split(/\s+/).pop()).filter(Boolean);
  };
  const nuevaVenta = componentes('NuevaVenta');
  const datosPersona = componentes('DatosPersona');

  const vue = readFileSync(RAIZ + 'frontend/src/views/Venta.vue', 'utf8');
  // Se ancla en el POST de la venta: el primer "/api/app/inscripciones" del archivo es el
  // GET de responsables que usa la subida de fotos, y va antes.
  const inicioPost = vue.indexOf("apiFetch('/api/app/inscripciones', {");
  const cuerpo = vue.slice(vue.indexOf('body: JSON.stringify({', inicioPost));
  const enviadas = [...cuerpo.slice(0, cuerpo.indexOf('\n      }),')).matchAll(/^\s{8}(\w+)\s*[,:]/gm)]
    .map((m) => m[1]);

  const mapaResp = vue.slice(vue.indexOf('.map((p) => ({'));
  const camposResp = [...mapaResp.slice(0, mapaResp.indexOf('}));')).matchAll(/(\w+):/g)].map((m) => m[1]);

  paso('el formulario no envia campos que el backend ignore',
       enviadas.filter((k) => !nuevaVenta.includes(k)).length === 0,
       enviadas.filter((k) => !nuevaVenta.includes(k)).join(', '));
  paso('el formulario envia todos los campos del record',
       nuevaVenta.filter((k) => !enviadas.includes(k)).length === 0,
       nuevaVenta.filter((k) => !enviadas.includes(k)).join(', '));
  paso('el celular del responsable legal viaja (V16)', enviadas.includes('celularRepresentante'));
  paso('cada responsable encaja con DatosPersona',
       camposResp.filter((k) => !datosPersona.includes(k)).length === 0,
       camposResp.filter((k) => !datosPersona.includes(k)).join(', '));

  const plantilla = vue.slice(vue.indexOf('<template>'));
  paso('los rotulos son "Responsable 1" y "Responsable 2"',
       plantilla.includes('Responsable {{ i + 1 }}') && plantilla.includes('Agregar Responsable 2'));
  paso('ya no aparecen "Titular" ni "Acompanante"', !/Titular|Acompañante/.test(plantilla));
  paso('ya no se pide el correo del responsable', !/>\s*Correo\s*</.test(plantilla));
  paso('el responsable legal pide nombre, C.I. y celular',
       /Nombre completo \*/.test(plantilla) && /C\.I\. \*/.test(plantilla) && /Celular \*/.test(plantilla));

  // La validacion tiene que decir QUE falta, no solo que falta algo.
  paso('la validacion lista los campos que faltan',
       vue.includes('const faltantes = computed(') && plantilla.includes('Falta completar:'));
  paso('el backend exige el responsable legal',
       java.includes('Falta el celular del responsable legal'));
}

// ---------------------------------------------------------------- márgenes seguros
/*
 * Desde la API 35 Android dibuja la app de BORDE A BORDE y no deja desactivarlo, asi que la
 * barra de estado (hora, señal, bateria) y la barra de gestos quedan encima de la pagina. Lo
 * unico que las esquiva es que la pagina respete los margenes seguros.
 *
 * Y aqui la trampa: sin `viewport-fit=cover` en el viewport, `env(safe-area-inset-*)` devuelve
 * 0 SIEMPRE y sin avisar. Todo el CSS que ya lo usaba parecia correcto y no hacia nada.
 */
titulo('Márgenes seguros del dispositivo (APK de borde a borde)');
{
  const { readFileSync } = await import('node:fs');
  const RAIZ = resolve(AQUI, '../../../..') + '/';
  const leer = (p) => readFileSync(RAIZ + p, 'utf8');

  const html = leer('frontend/index.html');
  paso('el viewport lleva viewport-fit=cover (sin el, env() vale 0 y nada funciona)',
       /viewport-fit\s*=\s*cover/.test(html));

  const css = leer('frontend/src/style.css');
  paso('las cuatro variables de margen seguro estan declaradas',
       ['--safe-top', '--safe-bottom', '--safe-left', '--safe-right']
         .every((v) => css.includes(`${v}: env(safe-area-inset`)));
  paso('el APK tiene un suelo por si el WebView no reporta los insets',
       /\.apk\s*\{[^}]*--safe-top:\s*max\(/.test(css));
  paso('el suelo se declara DESPUES de :root (misma especificidad, gana el orden)',
       css.indexOf('.apk {') > css.indexOf('--safe-top: env('));
  paso('la barra inferior mide contando el margen seguro',
       /--tabbar-h:\s*calc\(58px \+ var\(--safe-bottom\)\)/.test(css));

  const main = leer('frontend/src/main.js');
  paso('el APK se marca en el <html> para poder aplicarle el suelo',
       main.includes('EN_APK') && main.includes("classList.add('apk')"));

  const shell = leer('frontend/src/components/AppShell.vue');
  paso('la cabecera reserva el hueco de la barra de estado',
       /\.topbar\s*\{[^}]*padding:[^;]*var\(--safe-top\)/.test(shell));
  paso('y lo reserva como PADDING, para que su fondo pinte esa franja',
       !/\.topbar\s*\{[^}]*margin-top/.test(shell));
  paso('el cajón lateral también', /\.sidebar\s*\{[^}]*padding-top:\s*var\(--safe-top\)/.test(shell));
  paso('la barra inferior esquiva la barra de gestos',
       /\.tabbar\s*\{[^}]*padding:[^;]*var\(--safe-bottom\)/.test(shell));

  const ficha = leer('frontend/src/components/CasetaDetalle.vue');
  paso('la ficha de la caseta despega su botón de la barra de gestos',
       /\.ficha\s*\{[^}]*padding:[^;]*var\(--safe-bottom\)/.test(ficha));

  // Un `env()` suelto por ahí es justo lo que se perdió la vez anterior: se centraliza para
  // que haya UN sitio donde auditarlo.
  const sueltos = ['frontend/src/components/AppShell.vue', 'frontend/src/components/ToastHost.vue',
                   'frontend/src/components/CasetaDetalle.vue', 'frontend/src/components/AlertaModal.vue',
                   'frontend/src/components/UiModal.vue', 'frontend/src/views/Mapa.vue',
                   'frontend/src/views/Venta.vue', 'frontend/src/components/PanZoom.vue']
    .filter((p) => leer(p).includes('env(safe-area-inset'));
  paso('ningún componente usa env() suelto: todos pasan por las variables',
       sueltos.length === 0, sueltos.join(', '));
  /*
   * El teclado: desde Android 15 una ventana de borde a borde ya no se redimensiona sola al
   * abrirlo (adjustResize quedo obsoleto), asi que los ultimos campos de un formulario quedan
   * debajo del teclado sin forma de llegar a ellos.
   */
  const manifiesto = leer('frontend/android/app/src/main/AndroidManifest.xml');
  paso('el manifiesto pide adjustResize (sin el, Android DESPLAZA la ventana y mueve el menú)',
       /windowSoftInputMode="adjustResize"/.test(manifiesto));

  paso('la pagina puede crecer (min-height, no height fijo)',
       /html, body, #app \{ min-height: 100%; \}/.test(css));
  paso('el alto del teclado se publica en --kb',
       leer('frontend/src/ui/teclado.js').includes("setProperty('--kb'"));
  paso('y se observa al arrancar', main.includes('observarTeclado()'));
  paso('el contenido reserva ese hueco al final',
       /\.contenido\s*\{[^}]*var\(--kb\)/.test(shell));
  paso('con el teclado abierto, la barra inferior deja de ocupar sitio',
       /\.con-teclado\s*\{\s*--tabbar-h:\s*0px/.test(css));

  const toast = leer('frontend/src/components/ToastHost.vue');
  paso('los avisos flotantes no se tragan el arrastre',
       /\.host\s*\{[^}]*pointer-events:\s*none/.test(toast) &&
       /\.toast\s*\{[^}]*pointer-events:\s*auto/.test(toast));

  /*
   * La franja de la barra de estado sigue al tema del TELEFONO, no al de la app: los iconos
   * del sistema los colorea Android segun ese ajuste y no hay forma de cambiarlos desde la
   * pagina, asi que pintarla con el color de la app dejaba iconos blancos sobre blanco.
   */
  paso('la franja de estado tiene color propio', css.includes('--franja-estado'));
  paso('y sigue al sistema, no al tema de la app',
       /@media \(prefers-color-scheme: dark\) \{\s*:root \{ --franja-estado/.test(css));
  paso('la cabecera la pinta aparte',
       /\.topbar::before\s*\{[^}]*var\(--franja-estado\)/.test(shell));

}

// ---------------------------------------------------------------- casetas de otro vendedor
/*
 * El mapa ensena TODAS las casetas y pinta en gris las que este vendedor no lleva. Eso mueve
 * una responsabilidad de sitio: antes, "no puedes vender esa" lo garantizaba el LISTADO, que
 * simplemente no se la mandaba. Ahora se la manda, asi que la regla tiene que vivir en cada
 * ESCRITURA. Si alguien vuelve a filtrar el listado o quita un veto, esto lo delata.
 */
titulo('Casetas asignadas a otro vendedor');
{
  const { readFileSync } = await import('node:fs');
  const RAIZ = resolve(AQUI, '../../../..') + '/';
  const leer = (p) => readFileSync(RAIZ + p, 'utf8');

  const ctrl = leer('src/main/java/com/usic/uniFex/controller/puesto/PuestoApiController.java');
  const listar = ctrl.slice(ctrl.indexOf('public List<PuestoEstadoDTO> listar('),
                            ctrl.indexOf('@GetMapping("/asignaciones")'));
  paso('el listado ya NO filtra por vendedor', !/getPuestosVisiblesParaVendedor/.test(listar));
  paso('existe el endpoint de asignaciones', ctrl.includes('@GetMapping("/asignaciones")'));

  // Los tres caminos por los que un vendedor puede comprometer una caseta.
  const tramo = (nombre) => {
    const i = ctrl.indexOf(`@PostMapping("/{id}/${nombre}")`);
    return i < 0 ? '' : ctrl.slice(i, i + 700);
  };
  paso('reservar comprueba la asignacion', /vetoPorAsignacion/.test(tramo('reservar')));
  paso('confirmar comprueba la asignacion', /vetoPorAsignacion/.test(tramo('confirmar')));
  paso('el carrito ya la comprobaba', ctrl.includes('casetasNoPermitidas'));
  paso('y el registro de la venta tambien',
       leer('src/main/java/com/usic/uniFex/controller/inscripcion/InscripcionApiController.java')
         .includes('casetasNoPermitidas'));
  paso('el veto solo aplica a vendedores (administracion vende todas)',
       /if \(!esVendedor\(\) \|\| vendedorAsignacionService\.puedeVender/.test(ctrl));

  const dto = leer('src/main/java/com/usic/uniFex/model/dto/AsignacionPuestoDTO.java');
  paso('la asignacion solo expone caseta, vendedor y telefono',
       ['puestoId', 'vendedorId', 'vendedor', 'celular'].every((c) => dto.includes(c))
         && !/username|rol|\bci\b|password/.test(dto.replace(/\/\*[\s\S]*?\*\//g, '')));

  const store = leer('frontend/src/stores/puestos.js');
  paso('el store pide las asignaciones junto con el listado',
       store.includes("apiFetch('/api/app/puestos/asignaciones')") && store.includes('Promise.all'));
  paso('y las guarda en la copia en disco (si no, el arranque en frio pinta todo gris)',
       /asignaciones: \[\.\.\.asignaciones\.value\.entries\(\)\]/.test(store));

  const mapa = leer('frontend/src/views/Mapa.vue');
  paso('el mapa distingue lo que puede vender', mapa.includes('const puedoVender ='));
  paso('pinta en gris lo ajeno', /\.pin\.ajena\s*\{[^}]*var\(--bloqueado\)/.test(mapa));
  paso('y repinta cuando llegan las asignaciones (v-memo)',
       /v-memo="\[[^"]*puedoVender\(p\)/.test(mapa));
  paso('tocar una caseta ajena nunca sale a la red',
       /async function click\(p, evento\) \{\s*\/\/[\s\S]{0,220}if \(!puedoVender\(p\)\) return;/.test(mapa));

  const ficha = leer('frontend/src/components/CasetaDetalle.vue');
  paso('la ficha no ofrece vender una caseta ajena', /if \(!props\.vendible\) return null;/.test(ficha));
  paso('pero si da el contacto del companiero',
       ficha.includes('asignacion.vendedor') && ficha.includes('`tel:${telefono}`'));
}

console.log(fallos.length ? `\n${fallos.length} paso(s) fallaron:` : '\nTodo paso.');
for (const f of fallos) console.log(`  - ${f}`);
process.exit(fallos.length ? 1 : 0);
