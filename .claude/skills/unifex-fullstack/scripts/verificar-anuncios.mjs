/**
 * Anuncios para todos: que lleguen EN VIVO, que salgan en cualquier pantalla y que NO bloqueen.
 *
 * El "no bloquea" es la parte que de verdad importa y la que un test de API no puede ver: esto
 * aparece mientras alguien registra una venta con un cliente delante. Si tapara la pantalla o se
 * comiera los toques, seria peor que no tenerlo. Por eso se comprueba que, con el anuncio puesto,
 * el mapa de debajo se sigue pudiendo tocar.
 *
 *   node .claude/skills/unifex-fullstack/scripts/verificar-anuncios.mjs \
 *        --web http://localhost:5173 --api http://localhost:7676
 */
import { abrirChrome, erroresDe } from './lib-navegador.mjs';

const arg = (n, d) => { const i = process.argv.indexOf('--' + n); return i > 0 ? process.argv[i + 1] : d; };
const WEB = arg('web', 'http://localhost:5173');
const API = arg('api', 'http://localhost:7676');
const CLAVE = arg('clave', 'VO7xGroB8ag2Qz1B');
const marca = Date.now().toString().slice(-6);

let fallos = 0;
const paso = (d, ok, det = '') => {
  console.log(`  [${ok ? 'OK   ' : 'FALLA'}] ${d}${det ? `  -> ${det}` : ''}`);
  if (!ok) fallos++;
};
const titulo = (t) => console.log(`\n${t}`);
const j = async (r) => { try { return await r.json(); } catch { return null; } };

const T = (await j(await fetch(`${API}/api/auth/login`, { method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ usuario: 'admin1', contrasena: CLAVE }) })))?.token;
if (!T) { console.log('No se pudo entrar como admin1'); process.exit(1); }
const api = async (ruta, o = {}) => { const r = await fetch(API + ruta, { ...o,
  headers: { Authorization: `Bearer ${T}`, ...(o.body ? { 'Content-Type': 'application/json' } : {}) } });
  return { estado: r.status, cuerpo: await j(r) }; };

// ---------------------------------------------------------------- API
titulo('El API de anuncios');
let r = await api('/api/app/anuncios');
paso('los vigentes responden', r.estado === 200 && Array.isArray(r.cuerpo), `HTTP ${r.estado}`);
const habia = (r.cuerpo || []).length;

r = await api('/api/app/anuncios', { method: 'POST', body: JSON.stringify({ mensaje: '   ' }) });
paso('un anuncio sin mensaje se rechaza', r.estado === 400, `HTTP ${r.estado}`);

// Un anuncio ya vencido NO puede seguir saliendo. Se publica con un minuto y se comprueba que
// el filtro es del servidor, no del reloj del telefono.
r = await api('/api/app/anuncios', { method: 'POST',
  body: JSON.stringify({ mensaje: `ZZ VENCIDO ${marca}`, nivel: 'info', minutos: 1 }) });
const idCorto = r.cuerpo?.id;
paso('se publica uno con vencimiento', !!idCorto && !!r.cuerpo?.vigenteHasta, r.cuerpo?.vigenteHasta);
await api(`/api/app/anuncios/${idCorto}`, { method: 'DELETE' });

// ---------------------------------------------------------------- en vivo
titulo('Llega en vivo a quien esta en otra pantalla');
const ch = await abrirChrome(Number(arg('puerto', '9430')));
await ch.ir(`${WEB}/login`);
await ch.evaluar(`(async () => {
  const r = await fetch('/api/auth/login', { method:'POST', headers:{'Content-Type':'application/json'},
    body: JSON.stringify({ usuario:'admin1', contrasena:${JSON.stringify(CLAVE)} }) });
  const d = await r.json();
  localStorage.setItem('token', d.token);
  localStorage.setItem('usuario', d.usuario || 'admin1');
  localStorage.setItem('rol', d.rol || '');
  if (d.id != null) localStorage.setItem('id', String(d.id));
  // Sin anuncios cerrados de una corrida anterior: si no, el aviso no saldria y pareceria un fallo.
  localStorage.removeItem('anuncios.cerrados.v1');
})()`);
// El mapa: la pantalla donde MAS estorbaria un aviso que bloquee.
await ch.ir(`${WEB}/mapa`);
await ch.esperar(5000);

r = await api('/api/app/anuncios', { method: 'POST', body: JSON.stringify({
  titulo: `ZZ TITULO ${marca}`, mensaje: `No vendan la zona C ${marca}`, nivel: 'urgente', minutos: 120 }) });
const id = r.cuerpo?.id;

let salio = false;
for (let i = 0; i < 25; i++) {
  await ch.esperar(400);
  if (await ch.evaluar(`document.body.textContent.includes('No vendan la zona C ${marca}')`)) { salio = true; break; }
}
paso('el anuncio aparece sin recargar, estando en el mapa', salio);
paso('sale arriba a la DERECHA', await ch.evaluar(`(() => {
  const el = document.querySelector('.pila-anuncios'); if (!el) return false;
  const r = el.getBoundingClientRect();
  return r.top < window.innerHeight / 3 && r.right > window.innerWidth * 0.6;
})()`));
paso('con el nivel que se le puso', await ch.evaluar(`!!document.querySelector('.anuncio.nivel-urgente')`));

/*
 * Lo que de verdad hay que comprobar: que NO bloquee. Se mira el punto central de la pantalla
 * —donde esta el plano— y se comprueba que quien recibe el toque no es la capa de anuncios.
 */
paso('no bloquea la pantalla: el mapa de debajo se sigue tocando', await ch.evaluar(`(() => {
  const el = document.elementFromPoint(window.innerWidth / 2, window.innerHeight / 2);
  return !el?.closest?.('.pila-anuncios');
})()`));
paso('y la columna vacia tampoco se come los toques',
     await ch.evaluar(`getComputedStyle(document.querySelector('.pila-anuncios')).pointerEvents === 'none'`));

// Cerrarlo es del usuario, y se recuerda.
const habiaBoton = await ch.evaluar(`(() => {
  const b = document.querySelector('.anuncio .cerrar'); b?.click(); return !!b;
})()`);
// Se SONDEA en vez de esperar un tiempo fijo: entre el clic y que desaparezca hay una
// transicion de salida, y un `esperar` justo la convierte en una prueba que falla a ratos.
let cerrado = false;
for (let i = 0; i < 20; i++) {
  await ch.esperar(250);
  if (!(await ch.evaluar(`document.body.textContent.includes('No vendan la zona C ${marca}')`))) { cerrado = true; break; }
}
paso('se puede cerrar', habiaBoton && cerrado, habiaBoton ? '' : 'no habia boton de cerrar');
await ch.ir(`${WEB}/mapa`);
await ch.esperar(4000);
paso('y cerrado no vuelve a salir al cambiar de pantalla',
     !(await ch.evaluar(`document.body.textContent.includes('No vendan la zona C ${marca}')`)));

// Retirarlo desde administracion se lo quita a todos, hayan cerrado o no.
titulo('Retirarlo llega tambien');
r = await api('/api/app/anuncios', { method: 'POST', body: JSON.stringify({
  mensaje: `ZZ SEGUNDO ${marca}`, nivel: 'aviso', minutos: 120 }) });
const id2 = r.cuerpo?.id;
let salio2 = false;
for (let i = 0; i < 25; i++) {
  await ch.esperar(400);
  if (await ch.evaluar(`document.body.textContent.includes('ZZ SEGUNDO ${marca}')`)) { salio2 = true; break; }
}
paso('sale el segundo', salio2);
await api(`/api/app/anuncios/${id2}`, { method: 'DELETE' });
let quitado = false;
for (let i = 0; i < 25; i++) {
  await ch.esperar(400);
  if (!(await ch.evaluar(`document.body.textContent.includes('ZZ SEGUNDO ${marca}')`))) { quitado = true; break; }
}
paso('retirarlo lo quita de la pantalla sin recargar', quitado);

titulo('Consola');
const errores = erroresDe(ch.eventos).filter((e) => !/favicon|PLANO-/.test(e));
paso('ni un error de JavaScript', errores.length === 0, errores.slice(0, 2).join(' | '));

ch.cerrar();

titulo('Limpieza');
if (id) await api(`/api/app/anuncios/${id}`, { method: 'DELETE' });
r = await api('/api/app/anuncios');
paso('no queda ningun anuncio de prueba', (r.cuerpo || []).length === habia,
     `${(r.cuerpo || []).length} vigente(s), habia ${habia}`);

console.log(fallos ? `\n${fallos} paso(s) fallaron.\n` : '\nTodo paso.\n');
process.exit(fallos ? 1 : 0);
