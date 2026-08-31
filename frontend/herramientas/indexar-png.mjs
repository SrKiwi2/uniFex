/**
 * Convierte un PNG RGBA de colores planos (un plano CAD) a PNG indexado de 256 colores.
 *
 * Por que esto y no WebP ni JPEG:
 *  - JPEG destroza el texto diminuto y las lineas finas del plano, y ademas no habria
 *    ganancia real: el 88,5% de la imagen es blanco puro, que PNG comprime casi gratis.
 *  - En esta maquina no hay ningun codificador WebP (ni cwebp, ni magick, ni .NET).
 *  - El alfa del original es 100% opaco: sobra un canal entero.
 *
 * La imagen conserva su resolucion (1836x2376) porque el plano tiene rotulos de ~8px
 * que se volverian ilegibles al reducir. Lo que se recorta es la profundidad de color:
 * los ~69.000 tonos son antialias alrededor de una treintena de colores reales.
 *
 * Uso: node indexar-png.mjs <entrada.png> <salida.png> [nColores]
 */
import zlib from 'node:zlib';
import fs from 'node:fs';

const [entrada, salida, nColoresArg] = process.argv.slice(2);
const N_COLORES = Number(nColoresArg) || 256;

// ---------------------------------------------------------------- decodificar
function decodificar(buf) {
  let o = 8, idat = [], W = 0, H = 0, depth = 0, color = 0;
  while (o < buf.length) {
    const len = buf.readUInt32BE(o), tipo = buf.toString('ascii', o + 4, o + 8);
    if (tipo === 'IHDR') {
      W = buf.readUInt32BE(o + 8); H = buf.readUInt32BE(o + 12);
      depth = buf[o + 16]; color = buf[o + 17];
    }
    if (tipo === 'IDAT') idat.push(buf.subarray(o + 8, o + 8 + len));
    o += 12 + len;
  }
  if (depth !== 8 || (color !== 6 && color !== 2)) {
    throw new Error(`Solo soporto RGB/RGBA de 8 bits (recibi tipo ${color}, ${depth} bits)`);
  }
  const bpp = color === 6 ? 4 : 3;
  const raw = zlib.inflateSync(Buffer.concat(idat));
  const stride = W * bpp;
  const px = Buffer.alloc(H * stride);
  let p = 0;
  for (let y = 0; y < H; y++) {
    const f = raw[p++];
    const linea = raw.subarray(p, p + stride); p += stride;
    const cur = px.subarray(y * stride, (y + 1) * stride);
    const prev = y > 0 ? px.subarray((y - 1) * stride, y * stride) : null;
    for (let x = 0; x < stride; x++) {
      const a = x >= bpp ? cur[x - bpp] : 0;
      const c = prev ? prev[x] : 0;
      const d = (prev && x >= bpp) ? prev[x - bpp] : 0;
      let v = linea[x];
      if (f === 1) v += a;
      else if (f === 2) v += c;
      else if (f === 3) v += (a + c) >> 1;
      else if (f === 4) {
        const q = a + c - d, pa = Math.abs(q - a), pb = Math.abs(q - c), pc = Math.abs(q - d);
        v += (pa <= pb && pa <= pc) ? a : (pb <= pc ? c : d);
      }
      cur[x] = v & 0xff;
    }
  }
  return { W, H, px, bpp };
}

// ------------------------------------------------------------------ histograma
function histograma(px, bpp) {
  const h = new Map();
  for (let i = 0; i < px.length; i += bpp) {
    const k = (px[i] << 16) | (px[i + 1] << 8) | px[i + 2];
    h.set(k, (h.get(k) || 0) + 1);
  }
  return h;
}

// ------------------------------------------------------- median cut ponderado
// Divide repetidamente la caja con mayor volumen*poblacion por su eje mas largo,
// partiendo por la MEDIANA ponderada (no por el punto medio), que es lo que hace
// que los tonos raros no se coman entradas de paleta.
function medianCut(hist, n) {
  const entradas = [...hist.entries()].map(([k, cuenta]) => ({
    r: (k >> 16) & 255, g: (k >> 8) & 255, b: k & 255, cuenta,
  }));

  function limites(items) {
    let rmin = 255, rmax = 0, gmin = 255, gmax = 0, bmin = 255, bmax = 0, pobl = 0;
    for (const it of items) {
      if (it.r < rmin) rmin = it.r; if (it.r > rmax) rmax = it.r;
      if (it.g < gmin) gmin = it.g; if (it.g > gmax) gmax = it.g;
      if (it.b < bmin) bmin = it.b; if (it.b > bmax) bmax = it.b;
      pobl += it.cuenta;
    }
    return { rmin, rmax, gmin, gmax, bmin, bmax, pobl };
  }

  let cajas = [{ items: entradas, ...limites(entradas) }];

  while (cajas.length < n) {
    // Elegir la caja mas "cara": rango de color x poblacion.
    let mejor = -1, idx = -1;
    for (let i = 0; i < cajas.length; i++) {
      const c = cajas[i];
      if (c.items.length < 2) continue;
      const rango = Math.max(c.rmax - c.rmin, c.gmax - c.gmin, c.bmax - c.bmin);
      const puntaje = rango * Math.log2(c.pobl + 1);
      if (puntaje > mejor) { mejor = puntaje; idx = i; }
    }
    if (idx < 0) break;

    const c = cajas[idx];
    const dr = c.rmax - c.rmin, dg = c.gmax - c.gmin, db = c.bmax - c.bmin;
    const eje = dr >= dg && dr >= db ? 'r' : (dg >= db ? 'g' : 'b');
    c.items.sort((a, b) => a[eje] - b[eje]);

    // Corte por la mediana ponderada. `corte` se restringe a [0, len-2] para que
    // ambos lados queden no vacios; si la mediana cae en el ultimo item (todos los
    // colores de la caja pesan casi lo mismo), se parte por la mitad de la lista.
    const mitad = c.pobl / 2;
    let acc = 0, corte = -1;
    for (let i = 0; i < c.items.length - 1; i++) {
      acc += c.items[i].cuenta;
      if (acc >= mitad) { corte = i; break; }
    }
    if (corte < 0) corte = Math.max(0, Math.floor(c.items.length / 2) - 1);
    const izq = c.items.slice(0, corte + 1);
    const der = c.items.slice(corte + 1);

    cajas.splice(idx, 1,
      { items: izq, ...limites(izq) },
      { items: der, ...limites(der) });
  }

  // Representante de cada caja: media ponderada por poblacion.
  return cajas.map((c) => {
    let r = 0, g = 0, b = 0, t = 0;
    for (const it of c.items) { r += it.r * it.cuenta; g += it.g * it.cuenta; b += it.b * it.cuenta; t += it.cuenta; }
    return [Math.round(r / t), Math.round(g / t), Math.round(b / t)];
  });
}

// ------------------------------------------------------------------- codificar
function trozo(tipo, datos) {
  const len = Buffer.alloc(4); len.writeUInt32BE(datos.length);
  const cuerpo = Buffer.concat([Buffer.from(tipo, 'ascii'), datos]);
  const crc = Buffer.alloc(4); crc.writeUInt32BE(crc32(cuerpo) >>> 0);
  return Buffer.concat([len, cuerpo, crc]);
}

let TABLA_CRC = null;
function crc32(buf) {
  if (!TABLA_CRC) {
    TABLA_CRC = new Int32Array(256);
    for (let n = 0; n < 256; n++) {
      let c = n;
      for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
      TABLA_CRC[n] = c;
    }
  }
  let c = -1;
  for (let i = 0; i < buf.length; i++) c = TABLA_CRC[(c ^ buf[i]) & 0xff] ^ (c >>> 8);
  return c ^ -1;
}

// ------------------------------------------------------------------------ main
const original = fs.readFileSync(entrada);
const { W, H, px, bpp } = decodificar(original);
const hist = histograma(px, bpp);
console.log(`  entrada: ${W}x${H}, ${hist.size.toLocaleString()} colores unicos`);

const paleta = medianCut(hist, N_COLORES);

// El color dominante (blanco puro, 88,5%) debe quedar EXACTO: si la media ponderada
// lo desplaza a #fefefe, todo el fondo del plano cambia de tono de forma visible.
const dominante = [...hist.entries()].sort((a, b) => b[1] - a[1])[0][0];
const dom = [(dominante >> 16) & 255, (dominante >> 8) & 255, dominante & 255];
let iCerca = 0, dCerca = Infinity;
for (let i = 0; i < paleta.length; i++) {
  const d = (paleta[i][0] - dom[0]) ** 2 + (paleta[i][1] - dom[1]) ** 2 + (paleta[i][2] - dom[2]) ** 2;
  if (d < dCerca) { dCerca = d; iCerca = i; }
}
paleta[iCerca] = dom;

// Mapear pixeles al indice mas cercano, con cache por color unico.
// De paso se acumula el error para poder juzgar la calidad con un numero y no a ojo.
const cache = new Map();
const indices = Buffer.alloc(W * H);
let sumaError = 0, errorMax = 0;
for (let i = 0, j = 0; i < px.length; i += bpp, j++) {
  const k = (px[i] << 16) | (px[i + 1] << 8) | px[i + 2];
  let idx = cache.get(k);
  if (idx === undefined) {
    const r = px[i], g = px[i + 1], b = px[i + 2];
    let mejor = 0, dist = Infinity;
    for (let c = 0; c < paleta.length; c++) {
      const p = paleta[c];
      const d = (p[0] - r) ** 2 + (p[1] - g) ** 2 + (p[2] - b) ** 2;
      if (d < dist) { dist = d; mejor = c; }
    }
    idx = mejor; cache.set(k, idx);
  }
  indices[j] = idx;
  const p = paleta[idx];
  const e = (p[0] - px[i]) ** 2 + (p[1] - px[i + 1]) ** 2 + (p[2] - px[i + 2]) ** 2;
  sumaError += e;
  if (e > errorMax) errorMax = e;
}
const rmse = Math.sqrt(sumaError / (W * H * 3));
console.log(`  error: RMSE ${rmse.toFixed(2)} / 255 · peor pixel ${Math.sqrt(errorMax / 3).toFixed(1)}`);

// Scanlines con filtro por linea: en imagenes indexadas los indices no son
// numericamente continuos, asi que Ninguno (0) y Sub (1) son los unicos que ayudan.
function empaquetar(filtro) {
  const out = Buffer.alloc(H * (W + 1));
  for (let y = 0; y < H; y++) {
    const base = y * (W + 1);
    out[base] = filtro;
    const fila = indices.subarray(y * W, (y + 1) * W);
    if (filtro === 0) fila.copy(out, base + 1);
    else for (let x = 0; x < W; x++) out[base + 1 + x] = (fila[x] - (x > 0 ? fila[x - 1] : 0)) & 0xff;
  }
  return out;
}

const ihdr = Buffer.alloc(13);
ihdr.writeUInt32BE(W, 0); ihdr.writeUInt32BE(H, 4);
ihdr[8] = 8; ihdr[9] = 3; ihdr[10] = 0; ihdr[11] = 0; ihdr[12] = 0;

const plte = Buffer.alloc(paleta.length * 3);
paleta.forEach((c, i) => { plte[i * 3] = c[0]; plte[i * 3 + 1] = c[1]; plte[i * 3 + 2] = c[2]; });

// Se prueban las combinaciones de filtro y estrategia de deflate y se queda la mejor.
// Z_RLE suele ganar aqui: con 88,5% de blanco la imagen son tiradas largas del mismo indice.
const ESTRATEGIAS = [
  ['defecto', zlib.constants.Z_DEFAULT_STRATEGY],
  ['filtered', zlib.constants.Z_FILTERED],
  ['rle', zlib.constants.Z_RLE],
];
let mejorPng = null, mejorEtq = '';
for (const filtro of [0, 1]) {
  const datos = empaquetar(filtro);
  for (const [nombre, estrategia] of ESTRATEGIAS) {
    const comprimido = zlib.deflateSync(datos, { level: 9, memLevel: 9, windowBits: 15, strategy: estrategia });
    const png = Buffer.concat([
      Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
      trozo('IHDR', ihdr), trozo('PLTE', plte), trozo('IDAT', comprimido), trozo('IEND', Buffer.alloc(0)),
    ]);
    const etq = `${filtro === 0 ? 'Ninguno' : 'Sub'}+${nombre}`;
    if (!mejorPng || png.length < mejorPng.length) { mejorPng = png; mejorEtq = etq; }
  }
}
console.log(`  mejor combinacion: ${mejorEtq}`);

fs.writeFileSync(salida, mejorPng);
const antes = original.length, despues = mejorPng.length;
console.log(`  paleta: ${paleta.length} colores`);
console.log(`  ${antes.toLocaleString()} B -> ${despues.toLocaleString()} B  (-${(100 - despues / antes * 100).toFixed(1)}%)`);
