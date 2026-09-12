#!/usr/bin/env node
/**
 * El lector de credenciales de la puerta, con una camara de verdad delante.
 *
 * Probar esto "a mano" no sirve: el camino que se rompe sin avisar no es el formulario, es la
 * cadena camara -> fotograma -> decodificacion -> extraer el codigo de la URL -> consulta. Asi
 * que la prueba le da a Chrome una CAMARA FALSA que reproduce un video con el QR de una
 * credencial recien generada, y comprueba que el nombre correcto acaba en pantalla.
 *
 * El QR sale del PDF real, no de un generador aparte: si algun dia el PDF deja de llevar un QR
 * legible —porque encogio, porque se le puso un fondo encima— esto se entera.
 *
 * Chrome sin cabeza no trae BarcodeDetector, asi que lo que se ejerce aqui es el camino de
 * respaldo (jsQR), que es justamente el que no prueba nadie porque en el telefono del que
 * desarrolla casi nunca entra.
 *
 *   node .claude/skills/unifex-fullstack/scripts/verificar-escaner.mjs
 *   opciones: --web http://localhost:5173 --api http://localhost:7676 --clave '...'
 *
 * Necesita `pdftoppm` (poppler-utils). Sin el se salta la parte de camara y avisa.
 */
import { abrirChrome, erroresDe } from './lib-navegador.mjs';
import { execFileSync } from 'node:child_process';
import { writeFileSync, readFileSync, mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

const arg = (n, d) => { const i = process.argv.indexOf('--' + n); return i > 0 ? process.argv[i + 1] : d; };
const WEB = arg('web', 'http://localhost:5173');
const API = arg('api', 'http://localhost:7676');
const USUARIO = arg('usuario', 'admin1');
const CLAVE = arg('clave', 'VO7xGroB8ag2Qz1B');

const fallos = [];
const paso = (d, ok, det = '') => {
  console.log(`  [${ok ? 'OK   ' : 'FALLA'}] ${d}${det ? `  -> ${det}` : ''}`);
  if (!ok) fallos.push(d);
  return ok;
};
const titulo = (t) => console.log(`\n${t}`);

const api = async (t, ruta, o = {}) => {
  const h = { ...(o.headers || {}) };
  if (t) h.Authorization = `Bearer ${t}`;
  if (typeof o.body === 'string') h['Content-Type'] = 'application/json';
  const r = await fetch(API + ruta, { ...o, headers: h });
  const ct = r.headers.get('content-type') || '';
  if (ct.includes('pdf')) return { estado: r.status, buffer: Buffer.from(await r.arrayBuffer()) };
  const txt = await r.text().catch(() => '');
  return { estado: r.status, cuerpo: ct.includes('json') ? (() => { try { return JSON.parse(txt); } catch { return null; } })() : txt };
};

// ---------------------------------------------------------------- PPM -> Y4M
/** Lee un PPM binario (P6), que es lo que escupe pdftoppm sin pedirle nada raro. */
function leerPpm(ruta) {
  const b = readFileSync(ruta);
  let i = 0;
  const campo = () => {
    while (b[i] === 32 || b[i] === 10 || b[i] === 13 || b[i] === 9) i++;
    if (b[i] === 35) { while (b[i] !== 10) i++; return campo(); }   // comentario
    let s = '';
    while (i < b.length && b[i] > 32) s += String.fromCharCode(b[i++]);
    return s;
  };
  if (campo() !== 'P6') throw new Error('no es un PPM binario');
  const w = Number(campo());
  const h = Number(campo());
  campo();          // maximo, siempre 255 aqui
  i++;              // el unico byte blanco que separa la cabecera de los datos
  return { w, h, datos: b.subarray(i, i + w * h * 3) };
}

/**
 * Compone un video Y4M de un solo fotograma repetido: el recorte del PDF centrado sobre un
 * fondo claro, como estaria el papel delante del telefono. Escalado por vecino mas cercano a
 * proposito — interpolar suavizaria los bordes del QR, que es lo ultimo que conviene.
 */
function escribirY4m(ppm, destino, { W = 640, H = 480, alto = 420 } = {}) {
  const esc = alto / ppm.h;
  const cw = Math.max(1, Math.round(ppm.w * esc));
  const ch = Math.max(1, Math.round(ppm.h * esc));
  const x0 = Math.floor((W - cw) / 2);
  const y0 = Math.floor((H - ch) / 2);

  const Y = Buffer.alloc(W * H, 219);            // fondo claro, no blanco puro
  const U = Buffer.alloc((W / 2) * (H / 2), 128);
  const V = Buffer.alloc((W / 2) * (H / 2), 128);
  for (let y = 0; y < ch; y++) {
    const sy = Math.min(ppm.h - 1, Math.floor(y / esc));
    for (let x = 0; x < cw; x++) {
      const sx = Math.min(ppm.w - 1, Math.floor(x / esc));
      const p = (sy * ppm.w + sx) * 3;
      const r = ppm.datos[p], g = ppm.datos[p + 1], bl = ppm.datos[p + 2];
      // Solo la luminancia: el QR es en blanco y negro, y el color no aporta a la lectura.
      Y[(y0 + y) * W + (x0 + x)] = Math.max(16, Math.min(235,
          Math.round(0.299 * r + 0.587 * g + 0.114 * bl)));
    }
  }
  const cuadro = Buffer.concat([Buffer.from('FRAME\n'), Y, U, V]);
  const trozos = [Buffer.from(`YUV4MPEG2 W${W} H${H} F25:1 Ip A1:1 C420jpeg\n`)];
  for (let i = 0; i < 50; i++) trozos.push(cuadro);
  writeFileSync(destino, Buffer.concat(trozos));
}

// ---------------------------------------------------------------- prueba
console.log(`\n== Lector de credenciales (${WEB} -> ${API}) ==`);
const T = (await (await fetch(API + '/api/auth/login', {
  method: 'POST', headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ usuario: USUARIO, contrasena: CLAVE }),
})).json().catch(() => ({}))).token;
if (!paso('login', !!T)) process.exit(1);

const libre = ((await api(T, '/api/app/puestos')).cuerpo || []).find((p) => p.estado === 'L');
if (!paso('hay una caseta libre para la venta de prueba', !!libre)) process.exit(1);
await api(T, '/api/app/puestos/carrito', { method: 'POST', body: JSON.stringify({ ids: [libre.id] }) });
const venta = await api(T, '/api/app/inscripciones', { method: 'POST', body: JSON.stringify({
  entidadNombre: 'ZZ QR PRUEBA', nit: '', descripcion: 'PRUEBA', objeto: '',
  representanteLegal: 'REP QR', ciRepresentante: '99999930', celularRepresentante: '70000030',
  tipoEntidadId: 1, fechaInicio: null, fechaFin: null,
  responsables: [{ nombre: 'LECTURA', paterno: 'CAMARA', materno: '', ci: '99999931',
                   celular: '70000031', correo: null }],
  entidadBancaria: '', numComprobante: null, pagoContado: true, puestos: [libre.id] }) });
const insId = venta.cuerpo?.inscripcionId;
if (!paso('venta de prueba creada', !!insId)) process.exit(1);

const cred = ((await api(T, '/api/app/credenciales')).cuerpo || [])
    .find((c) => c.entidad === 'ZZ QR PRUEBA');
const dir = mkdtempSync(join(tmpdir(), 'qr-prueba-'));
let y4m = null;
try {
  const pdf = await api(T, '/api/app/credenciales/pdf', { method: 'POST', body: JSON.stringify({
    responsables: [cred.responsableId], plantilla: 'QR_GRANDE', anchoCm: 14, forzar: true }) });
  paso('se genera la credencial de la que saldra el QR', pdf.estado === 200 && pdf.buffer?.length > 10000,
       `${Math.round((pdf.buffer?.length || 0) / 1024)} KB`);
  writeFileSync(join(dir, 'cred.pdf'), pdf.buffer);
  try {
    execFileSync('pdftoppm', ['-r', '150', '-f', '1', '-l', '1', join(dir, 'cred.pdf'), join(dir, 'p')],
                 { stdio: 'ignore' });
    // La credencial ocupa la mitad de arriba de la hoja carta; se recorta a ojo con holgura.
    const hoja = leerPpm(join(dir, 'p-1.ppm'));
    const y1 = Math.round(hoja.h * 0.135), y2 = Math.round(hoja.h * 0.83);
    const x1 = Math.round(hoja.w * 0.16), x2 = Math.round(hoja.w * 0.85);
    const w = x2 - x1, h = y2 - y1;
    const datos = Buffer.alloc(w * h * 3);
    for (let y = 0; y < h; y++) {
      hoja.datos.copy(datos, y * w * 3, ((y1 + y) * hoja.w + x1) * 3, ((y1 + y) * hoja.w + x2) * 3);
    }
    y4m = join(dir, 'qr.y4m');
    escribirY4m({ w, h, datos }, y4m);
  } catch (e) {
    console.log(`  (sin pdftoppm: se salta la parte de camara — ${e.message})`);
  }

  const extras = y4m ? ['--use-fake-ui-for-media-stream', '--use-fake-device-for-media-stream',
                        `--use-file-for-fake-video-capture=${y4m}`,
                        '--autoplay-policy=no-user-gesture-required'] : [];
  const ch = await abrirChrome(Number(arg('puerto', '9263')), extras);
  try {
    await ch.enviar('Emulation.setDeviceMetricsOverride',
        { width: 420, height: 860, deviceScaleFactor: 1, mobile: true });
    await ch.ir(WEB + '/login');
    await ch.evaluar('localStorage.clear(); sessionStorage.clear();');
    await ch.ir(WEB + '/login');
    await ch.esperar(2600);
    const teclear = (s, v) => ch.evaluar(`(() => { const el=document.querySelector(${JSON.stringify(s)});
      Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set.call(el, ${JSON.stringify(v)});
      el.dispatchEvent(new Event('input',{bubbles:true})); })()`);
    await teclear('#usuario', USUARIO);
    await teclear('#contrasena', CLAVE);
    await ch.evaluar('document.querySelector(".btn-entrar").click()');
    await ch.esperar(3000);

    await ch.ir(WEB + '/escaner');
    await ch.esperar(2500);
    paso('la pantalla monta en tamaño de telefono',
         await ch.evaluar(`!!document.querySelector('.escaner')`));
    paso('la camara NO se enciende sola al entrar',
         !(await ch.evaluar(`!!document.querySelector('.visor')`)));

    if (y4m) {
      titulo('Leer el QR con la camara');
      await ch.evaluar(`[...document.querySelectorAll('button')]
          .find(b => /Escanear con la c/i.test(b.textContent))?.click()`);

      let leido = '';
      for (let i = 0; i < 15 && !leido; i++) {
        await ch.esperar(1000);
        leido = await ch.evaluar(`document.querySelector('.resultado')?.className || ''`);
      }
      paso('lee el QR y da la credencial por valida', /valido/.test(leido), leido || 'no leyo en 15 s');

      const texto = (await ch.evaluar(
          `document.querySelector('.resultado')?.textContent.replace(/\\s+/g,' ').trim()`) || '');
      paso('y enseña a QUIEN pertenece', /LECTURA CAMARA/.test(texto), texto.slice(0, 90));
      paso('con su caseta, que es lo que se contrasta en la puerta',
           new RegExp(String(cred.casetas).split(',')[0].trim()).test(texto));
      paso('el codigo leido es el de esa credencial',
           (await ch.evaluar(`document.querySelector('.codigo')?.value`)) === cred.codigo,
           cred.codigo);
      // Dejar la camara encendida gasta bateria y deja el LED prendido, que es lo que hace
      // pensar que la aplicacion mira cuando no toca.
      paso('la camara se apaga sola en cuanto lee',
           !(await ch.evaluar(`!!document.querySelector('.visor')`)));

      titulo('La camara no se queda encendida al salir de la pantalla');
      await ch.evaluar(`[...document.querySelectorAll('button')]
          .find(b => /Escanear la siguiente/i.test(b.textContent))?.click()`);
      await ch.esperar(1200);
      await ch.ir(WEB + '/');
      await ch.esperar(1500);
      paso('al navegar a otra pantalla no queda ninguna pista de video abierta',
           await ch.evaluar(`document.querySelectorAll('video').length === 0`));
      await ch.ir(WEB + '/escaner');
      await ch.esperar(2000);
    }

    titulo('El codigo tecleado sigue siendo el respaldo');
    const escribir = async (v) => {
      await ch.evaluar(`(() => { const el=document.querySelector('.codigo');
        Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set.call(el, ${JSON.stringify(v)});
        el.dispatchEvent(new Event('input',{bubbles:true})); })()`);
      await ch.evaluar(`document.querySelector('.entrada form .btn').click()`);
      await ch.esperar(1500);
    };
    await escribir(cred.codigo);
    paso('el codigo correcto abre la credencial',
         /valido/.test(await ch.evaluar(`document.querySelector('.resultado')?.className || ''`)));

    // Pegar la URL entera es lo que pasa cuando alguien escanea con la camara del sistema.
    await ch.evaluar(`[...document.querySelectorAll('button')].find(b => /Limpiar/.test(b.textContent))?.click()`);
    await ch.esperar(400);
    await escribir(`https://feria.uap.edu.bo/credencial/${cred.codigo}`);
    paso('pegar la URL entera del QR tambien vale',
         /valido/.test(await ch.evaluar(`document.querySelector('.resultado')?.className || ''`)));

    await ch.evaluar(`[...document.querySelectorAll('button')].find(b => /Limpiar/.test(b.textContent))?.click()`);
    await ch.esperar(400);
    const partes = cred.codigo.split('-');
    await escribir(`${partes[0]}-${partes[1]}-AAAAAAAAAAAA`);
    paso('una firma inventada se rechaza en la puerta',
         /invalido/.test(await ch.evaluar(`document.querySelector('.resultado')?.className || ''`)));

    const errores = erroresDe(ch.eventos)
        .filter((t) => !/favicon|DevTools|Download the Vue|vibrate|404/i.test(t));
    paso('sin errores de consola', errores.length === 0, errores.slice(0, 2).join(' / '));
  } finally {
    ch.cerrar();
  }
} finally {
  titulo('Limpieza');
  rmSync(dir, { recursive: true, force: true });
  await api(T, `/api/app/inscripciones/${insId}/cancelar`, { method: 'POST',
    body: JSON.stringify({ motivo: 'Prueba del lector de credenciales' }) });
  paso('la venta de prueba queda cancelada',
       !((await api(T, '/api/app/credenciales')).cuerpo || []).some((c) => c.entidad === 'ZZ QR PRUEBA'));
}

console.log(fallos.length ? `\n${fallos.length} paso(s) fallaron:` : '\nTodo paso.');
for (const f of fallos) console.log(`  - ${f}`);
process.exit(fallos.length ? 1 : 0);
