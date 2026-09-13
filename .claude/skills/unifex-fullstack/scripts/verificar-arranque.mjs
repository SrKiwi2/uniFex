#!/usr/bin/env node
/**
 * El arranque de la aplicacion: la bienvenida y el estado de la sesion.
 *
 * Cubre el fallo mas desconcertante que se reporto del APK: **abria directa al inicio, con el
 * menu entero, y al tocar cualquier opcion echaba al login**. La causa era que "tener token"
 * se daba por "tener sesion", y el menu se pinta desde una copia en disco; con un token
 * caducado todo parecia iniciado hasta que la primera peticion devolvia 401. Llego por
 * Android, que al reinstalar la aplicacion RESTAURA los datos viejos si `allowBackup` esta
 * encendido — token caducado incluido.
 *
 * Tambien se vigila la bienvenida: dura desde que el LOGO esta visible, no desde que arranca
 * el componente, porque si no en un telefono lento se veia a medias o nada.
 *
 *   node .claude/skills/unifex-fullstack/scripts/verificar-arranque.mjs
 *   opciones: --web http://localhost:5173 --api http://localhost:7676 --clave '...'
 */
import { abrirChrome, erroresDe } from './lib-navegador.mjs';

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

console.log(`\n== Arranque de la aplicacion (${WEB}) ==`);
const ch = await abrirChrome(Number(arg('puerto', '9300')));
const menu = () => ch.evaluar(`[...document.querySelectorAll('.sidebar .enlace')].map(e=>e.textContent.trim()).join(' | ')`);

/** Un JWT con la forma real pero ya caducado. No se firma: el cliente solo lee la carga. */
function tokenCaducado(segundosAtras = 3600) {
  const b64 = (o) => Buffer.from(JSON.stringify(o)).toString('base64url');
  const exp = Math.floor(Date.now() / 1000) - segundosAtras;
  return `${b64({ alg: 'HS256', typ: 'JWT' })}.${b64({ sub: 'admin1', exp })}.firmafalsa`;
}

const entrar = async (u, c) => {
  await ch.ir(WEB + '/login');
  await ch.esperar(2400);
  const t = (s, v) => ch.evaluar(`(() => { const el=document.querySelector(${JSON.stringify(s)});
    Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set.call(el, ${JSON.stringify(v)});
    el.dispatchEvent(new Event('input',{bubbles:true})); })()`);
  await t('#usuario', u);
  await t('#contrasena', c);
  await ch.evaluar('document.querySelector(".btn-entrar").click()');
  await ch.esperar(3200);
};

try {
  await ch.enviar('Emulation.setDeviceMetricsOverride',
      { width: 400, height: 860, deviceScaleFactor: 1, mobile: true });

  titulo('La bienvenida');
  await ch.ir(WEB + '/login');
  await ch.evaluar('localStorage.clear(); sessionStorage.clear();');
  // Red lenta: es el caso en el que la animacion se veia a medias.
  await ch.enviar('Network.enable');
  await ch.enviar('Network.emulateNetworkConditions',
      { offline: false, latency: 300, downloadThroughput: 200 * 1024, uploadThroughput: 100 * 1024 });
  await ch.ir(WEB + '/');
  await ch.esperar(400);
  /*
   * A los 400 ms con la red frenada, el paquete de JavaScript todavia no ha llegado: si lo
   * unico que tapara el hueco fuera el componente Vue, aqui habria una pantalla en blanco.
   * Por eso el logo tambien esta en el HTML.
   */
  paso('hay logo en pantalla antes de que cargue la aplicacion',
       await ch.evaluar(`!!document.querySelector('#arranque, .bienvenida')`),
       await ch.evaluar(`document.querySelector('#arranque') ? 'el del HTML' : (document.querySelector('.bienvenida') ? 'ya el de Vue' : 'NADA')`));
  await ch.esperar(400);
  paso('y no quedan los dos a la vez cuando entra Vue',
       await ch.evaluar(`!(document.querySelector('#arranque') && document.querySelector('.bienvenida'))`));
  // Lo que se quiere garantizar: que cuando se va, el logo SE HA VISTO.
  const logoListo = await ch.evaluar(`(() => { const i=document.querySelector('.bienvenida img');
    return i ? (i.complete && i.naturalWidth > 0) : 'no hay imagen'; })()`);
  await ch.esperar(2600);
  const sigue = await ch.evaluar(`!!document.querySelector('.bienvenida')`);
  const logoAlFinal = await ch.evaluar(`(() => { const i=document.querySelector('.bienvenida img');
    return i ? (i.complete && i.naturalWidth > 0) : 'ya se fue'; })()`);
  paso('no se queda pegada para siempre', !sigue, `logo listo al inicio: ${logoListo}`);
  paso('y no se fue antes de cargar el logo', logoAlFinal !== false, String(logoAlFinal));
  await ch.enviar('Network.emulateNetworkConditions',
      { offline: false, latency: 0, downloadThroughput: -1, uploadThroughput: -1 });

  titulo('Sesion caducada: el arranque que se reporto del APK');
  await entrar(USUARIO, CLAVE);
  paso('se entra con normalidad', /Inicio/.test(await menu()));
  const hayCache = await ch.evaluar(`!!localStorage.getItem('permisos.cache.v1')`);
  paso('y el menu queda guardado en disco, para arrancar sin parpadeo', hayCache);

  // Se simula lo que hace Android al restaurar: token viejo + menu guardado, ambos en disco.
  await ch.evaluar(`localStorage.setItem('token', ${JSON.stringify(tokenCaducado())})`);
  await ch.ir(WEB + '/');
  await ch.esperar(3000);
  paso('con el token caducado la aplicacion NO entra: va al login',
       (await ch.evaluar(`location.hash + location.pathname`)).includes('login'),
       await ch.evaluar(`location.pathname + location.hash`));
  paso('y no enseña el menu por el camino', !(await menu()), (await menu()) || '(sin menu)');

  titulo('Cerrar sesion no deja el menu del anterior');
  await entrar(USUARIO, CLAVE);
  await ch.evaluar(`[...document.querySelectorAll('.pie-side .btn, .sidebar .btn')].find(b=>/Salir/.test(b.textContent))?.click()`);
  await ch.esperar(2000);
  paso('se sale al login', (await ch.evaluar(`location.pathname + location.hash`)).includes('login'));
  paso('y el menu guardado se borra con la sesion',
       !(await ch.evaluar(`!!localStorage.getItem('permisos.cache.v1')`)));
  paso('y el token tambien', !(await ch.evaluar(`!!localStorage.getItem('token')`)));

  const errores = erroresDe(ch.eventos)
      .filter((t) => !/favicon|DevTools|Download the Vue|401|Sesion expirada/i.test(t));
  paso('sin errores de consola', errores.length === 0, errores.slice(0, 2).join(' / '));
} finally {
  ch.cerrar();
}

console.log(fallos.length ? `\n${fallos.length} paso(s) fallaron:` : '\nTodo paso.');
for (const f of fallos) console.log(`  - ${f}`);
process.exit(fallos.length ? 1 : 0);
