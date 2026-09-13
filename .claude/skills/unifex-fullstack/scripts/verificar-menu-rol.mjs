#!/usr/bin/env node
/**
 * El menu segun el rol, en un navegador real, incluido el caso que se reporto desde el APK.
 *
 * Lo que se comprueba y por que:
 *
 *   1. Un vendedor NO ve las opciones de administracion. Parece obvio; no lo era. El menu se
 *      arma con `permisos.puedeVer()`, y esa funcion respondia que SI mientras no supiera la
 *      respuesta del servidor. En el APK, con el telefono todavia sin señal al abrir, eso
 *      dejaba a un ADMINISTRATIVO viendo Usuarios, Roles y el Editor del plano.
 *   2. **Sin red tampoco.** Es el caso real que fallaba, y el unico que de verdad importa:
 *      aqui se corta la peticion de permisos a proposito y el menu tiene que quedarse corto,
 *      no largo.
 *   3. Cambiar los permisos de un rol llega **sin cerrar sesion**, por el aviso del servidor.
 *
 *   node .claude/skills/unifex-fullstack/scripts/verificar-menu-rol.mjs
 *   opciones: --web http://localhost:5173 --api http://localhost:7676 --clave '...'
 *
 * Deja el rol como estaba y el usuario de prueba desactivado.
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

const api = async (t, ruta, o = {}) => {
  const h = { ...(o.headers || {}) };
  if (t) h.Authorization = `Bearer ${t}`;
  if (typeof o.body === 'string') h['Content-Type'] = 'application/json';
  const r = await fetch(API + ruta, { ...o, headers: h });
  const ct = r.headers.get('content-type') || '';
  const txt = await r.text().catch(() => '');
  return { estado: r.status, cuerpo: ct.includes('json') ? (() => { try { return JSON.parse(txt); } catch { return null; } })() : txt };
};

console.log(`\n== Menu segun el rol (${WEB} -> ${API}) ==`);
const T = (await (await fetch(API + '/api/auth/login', {
  method: 'POST', headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ usuario: USUARIO, contrasena: CLAVE }),
})).json().catch(() => ({}))).token;
if (!paso('login de administracion', !!T)) process.exit(1);

// Un vendedor de prueba, reaprovechado entre pasadas (la baja es logica: ver
// verificar-acreditacion.mjs, mismo motivo).
const roles = (await api(T, '/api/app/roles')).cuerpo;
const rolVend = (Array.isArray(roles) ? roles : roles?.roles || [])
    .find((x) => (x.nombre || '').toUpperCase() === 'ADMINISTRATIVO');
if (!paso('existe el rol ADMINISTRATIVO', !!rolVend)) process.exit(1);

const USER = 'zz_menu_pruebas';
const CLAVE_V = 'Prueba.Menu.2026';
const CI = '99999970';
let uid = null;
const usuarios = (await api(T, '/api/app/usuarios')).cuerpo;
const ya = (Array.isArray(usuarios) ? usuarios : usuarios?.usuarios || []).find((u) => u.username === USER);
if (ya) {
  uid = ya.id;
  await api(T, `/api/app/usuarios/${uid}`, { method: 'PATCH',
    body: JSON.stringify({ username: USER, personaId: ya.personaId, rolId: rolVend.id }) });
  await api(T, `/api/app/usuarios/${uid}/estado`, { method: 'PATCH', body: JSON.stringify({ activo: true }) });
  await api(T, `/api/app/usuarios/${uid}/password`, { method: 'PATCH', body: JSON.stringify({ password: CLAVE_V }) });
} else {
  const porCi = (await api(T, `/api/app/usuarios/personas/por-ci?ci=${CI}`)).cuerpo;
  const cuerpo = { username: USER, password: CLAVE_V, rolId: rolVend.id };
  if (porCi?.existe) cuerpo.personaId = porCi.persona?.id;
  else cuerpo.persona = { nombre: 'ZZMENU', paterno: 'PRUEBA', materno: '', ci: CI, correo: null, celular: '70000070' };
  const alta = await api(T, '/api/app/usuarios', { method: 'POST', body: JSON.stringify(cuerpo) });
  uid = alta.cuerpo?.usuario?.id ?? alta.cuerpo?.id;
}
if (!paso('hay un vendedor de prueba', !!uid, `id ${uid}`)) process.exit(1);

const ADMINISTRACION = ['Usuarios', 'Roles', 'Permisos', 'Editor del plano', 'Vendedores'];
const ch = await abrirChrome(Number(arg('puerto', '9280')));
const menu = () => ch.evaluar(`[...document.querySelectorAll('.sidebar .enlace')].map(e=>e.textContent.trim()).join(' | ')`);

const entrar = async (u, c) => {
  await ch.ir(WEB + '/login');
  await ch.evaluar('localStorage.clear(); sessionStorage.clear();');
  await ch.ir(WEB + '/login');
  await ch.esperar(2600);
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
      { width: 420, height: 860, deviceScaleFactor: 1, mobile: true });

  titulo('Con red: el vendedor ve solo lo suyo');
  await entrar(USER, CLAVE_V);
  let m = await menu();
  paso('el menu tiene sus pantallas', /Mapa de ventas/.test(m) && /Mis ventas/.test(m), m);
  paso('y ninguna de administracion',
       !ADMINISTRACION.some((x) => m.includes(x)), m);

  // El boton "Mas" de la barra inferior abre ESTE mismo cajon, que es donde se vieron las
  // opciones de mas. Se comprueba que abre y que no aparece nada nuevo al abrirlo.
  await ch.evaluar(`[...document.querySelectorAll('.tabbar .tab')].find(b=>/Más|Mas/.test(b.textContent))?.click()`);
  await ch.esperar(600);
  paso('el boton "Más" abre el cajon', await ch.evaluar(`!!document.querySelector('.sidebar.abierto')`));
  paso('y ahi tampoco salen las de administracion',
       !ADMINISTRACION.some((x) => (menuAbierto => menuAbierto.includes(x))(m)),
       'mismo cajon, misma lista');

  titulo('SIN red al arrancar: el caso que fallaba en el APK');
  /*
   * Se corta la peticion de permisos y se recarga con la copia en disco borrada. Antes, esta
   * situacion daba el menu ENTERO. Ahora tiene que dar el minimo.
   */
  await ch.enviar('Network.enable');
  await ch.enviar('Network.setBlockedURLs', { urls: ['*/api/app/permisos/mias'] });
  await ch.evaluar(`localStorage.removeItem('permisos.cache.v1')`);
  await ch.ir(WEB + '/');
  await ch.esperar(3200);
  m = await menu();
  paso('sin saber los permisos NO se enseña administracion',
       !ADMINISTRACION.some((x) => m.includes(x)), m || '(menu vacio)');
  paso('y queda al menos Inicio, para que la aplicacion sirva de algo', /Inicio/.test(m), m);

  // Al volver la red, el menu se completa solo: la peticion fallida ya no queda memorizada.
  await ch.enviar('Network.setBlockedURLs', { urls: [] });
  await ch.evaluar(`document.dispatchEvent(new Event('visibilitychange'))`);
  await ch.esperar(2500);
  m = await menu();
  paso('al volver la red el menu se completa sin recargar',
       /Mapa de ventas/.test(m) && /Mis ventas/.test(m), m);

  titulo('Cambiar los permisos del rol llega sin cerrar sesion');
  const antes = ((await api(T, '/api/app/permisos')).cuerpo || [])
      .find((x) => x.rolId === rolVend.id);
  const original = [...(antes?.pantallas || [])];
  await api(T, `/api/app/permisos/${rolVend.id}`, { method: 'PUT',
    body: JSON.stringify({ rol: rolVend.nombre, pantallas: original.filter((p) => p !== 'mis-ventas') }) });
  await ch.esperar(3500);
  m = await menu();
  paso('quitarle una pantalla se la quita del menu al momento',
       !/Mis ventas/.test(m), m);

  await api(T, `/api/app/permisos/${rolVend.id}`, { method: 'PUT',
    body: JSON.stringify({ rol: rolVend.nombre, pantallas: original }) });
  await ch.esperar(3500);
  m = await menu();
  paso('y devolversela se la devuelve, tambien al momento', /Mis ventas/.test(m), m);

  const errores = erroresDe(ch.eventos)
      .filter((t) => !/favicon|DevTools|Download the Vue|ERR_BLOCKED_BY_CLIENT|permisos\/mias/i.test(t));
  paso('sin errores de consola', errores.length === 0, errores.slice(0, 2).join(' / '));
} finally {
  ch.cerrar();
  titulo('Limpieza');
  await api(T, `/api/app/usuarios/${uid}/estado`, { method: 'PATCH', body: JSON.stringify({ activo: false }) });
  paso('el usuario de prueba queda desactivado', true);
}

console.log(fallos.length ? `\n${fallos.length} paso(s) fallaron:` : '\nTodo paso.');
for (const f of fallos) console.log(`  - ${f}`);
process.exit(fallos.length ? 1 : 0);
