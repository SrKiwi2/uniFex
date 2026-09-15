/**
 * Rotulos libres del plano: colocarlos en el Editor y que lleguen EN VIVO al mapa de los demas.
 *
 * Lo que de verdad se comprueba es el invariante 2 de la skill: toda escritura que sale bien se
 * difunde. Por eso hay dos navegadores —uno en el Editor y otro en el Mapa— y el segundo nunca
 * recarga: si el rotulo aparece ahi, es porque llego por `/topic/plano-textos`.
 *
 *   node .claude/skills/unifex-fullstack/scripts/verificar-rotulos-plano.mjs \
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

const entrar = async (ch, ruta) => {
  await ch.ir(`${WEB}/login`);
  await ch.evaluar(`(async () => {
    const r = await fetch('/api/auth/login', { method:'POST', headers:{'Content-Type':'application/json'},
      body: JSON.stringify({ usuario:'admin1', contrasena:${JSON.stringify(CLAVE)} }) });
    const d = await r.json();
    localStorage.setItem('token', d.token);
    localStorage.setItem('usuario', d.usuario || 'admin1');
    localStorage.setItem('rol', d.rol || '');
    if (d.id != null) localStorage.setItem('id', String(d.id));
  })()`);
  await ch.ir(`${WEB}${ruta}`);
  await ch.esperar(5000);
};

// ---------------------------------------------------------------- API
titulo('El API de rotulos');
let r = await api('/api/app/plano-textos');
paso('el listado responde', r.estado === 200 && Array.isArray(r.cuerpo), `HTTP ${r.estado}`);
const habia = (r.cuerpo || []).length;

r = await api('/api/app/plano-textos', { method: 'POST', body: JSON.stringify({
  contenido: `ZZ ENTRADA ${marca}`, mapaX: 0.31, mapaY: 0.22, tamano: 0.03,
  color: '#0044cc', colorBorde: '#ffff00', grosorBorde: 0.2, rotacion: 0 }) });
const id = r.cuerpo?.id;
paso('se crea un rotulo', r.estado === 200 && !!id, `id=${id}`);
paso('y conserva lo que se le pidio',
     r.cuerpo?.color === '#0044cc' && Math.abs(r.cuerpo?.tamano - 0.03) < 1e-9,
     `color=${r.cuerpo?.color} tamaño=${r.cuerpo?.tamano}`);

// Una fraccion fuera del plano dejaria el rotulo donde nadie lo ve.
r = await api(`/api/app/plano-textos/${id}`, { method: 'PATCH', body: JSON.stringify({ mapaX: 5 }) });
paso('una coordenada fuera del plano se acota a 0..1', r.cuerpo?.mapaX === 1, `mapaX=${r.cuerpo?.mapaX}`);
await api(`/api/app/plano-textos/${id}`, { method: 'PATCH', body: JSON.stringify({ mapaX: 0.31 }) });

r = await api('/api/app/plano-textos', { method: 'POST', body: JSON.stringify({ contenido: '   ' }) });
paso('un texto vacio se rechaza', r.estado === 400, `HTTP ${r.estado}`);

// ---------------------------------------------------------------- en vivo
titulo('Llega en vivo al mapa de otro usuario');
const mapa = await abrirChrome(Number(arg('puerto', '9420')));
await entrar(mapa, '/mapa');

const rotulosEnMapa = () => mapa.evaluar(`document.querySelectorAll('.rotulo').length`);
const textoEnMapa = (t) => mapa.evaluar(
  `[...document.querySelectorAll('.rotulo')].some(x => x.textContent.trim() === ${JSON.stringify(t)})`);

paso('el mapa ya pinta el rotulo creado antes de abrirlo', await textoEnMapa(`ZZ ENTRADA ${marca}`),
     `${await rotulosEnMapa()} rotulo(s)`);

// Ahora se cambia desde FUERA y el mapa NO recarga: si cambia, fue por el canal.
await api(`/api/app/plano-textos/${id}`, { method: 'PATCH',
  body: JSON.stringify({ contenido: `ZZ SALIDA ${marca}` }) });
let llego = false;
for (let i = 0; i < 20; i++) {
  await mapa.esperar(400);
  if (await textoEnMapa(`ZZ SALIDA ${marca}`)) { llego = true; break; }
}
paso('cambiar el texto se ve sin recargar', llego);

const estilo = await mapa.evaluar(`(() => {
  const el = [...document.querySelectorAll('.rotulo')].find(x => x.textContent.includes('${marca}'));
  if (!el) return null;
  const s = getComputedStyle(el);
  return { color: s.color, sombra: s.textShadow, tam: s.fontSize };
})()`);
paso('se pinta con su color', /0,\s*68,\s*204/.test(estilo?.color || ''), estilo?.color);
paso('y con el borde alrededor (cuatro sombras)',
     (estilo?.sombra || '').split('rgb').length - 1 === 4, (estilo?.sombra || '').slice(0, 60) + '…');
// Que escale con el plano y no sea un tamaño fijo en pixeles: 3% del ancho del mundo.
paso('el tamaño sale del ancho del plano, no de pixeles fijos',
     parseFloat(estilo?.tam) > 8, estilo?.tam);

// La baja tambien viaja.
await api(`/api/app/plano-textos/${id}`, { method: 'DELETE' });
let fue = false;
for (let i = 0; i < 20; i++) {
  await mapa.esperar(400);
  if (!(await textoEnMapa(`ZZ SALIDA ${marca}`))) { fue = true; break; }
}
paso('quitarlo tambien se ve sin recargar', fue);

// ---------------------------------------------------------------- el editor
titulo('El Editor coloca un rotulo');
const editor = await abrirChrome(Number(arg('puerto2', '9421')));
await entrar(editor, '/editor');

paso('hay un modo Texto en la barra',
     await editor.evaluar(`[...document.querySelectorAll('button')].some(b => /Texto/.test(b.textContent))`));

await editor.evaluar(`(() => { const b=[...document.querySelectorAll('button')]
  .find(x=>/🔤 Texto/.test(x.textContent)); b?.click(); return !!b; })()`);
await editor.esperar(500);
paso('el panel explica que hacer',
     await editor.evaluar(`!!document.querySelector('.ayuda-texto')`));

// Tocar el plano coloca un rotulo. Se simula el pointerdown sobre el lienzo.
const antes = await api('/api/app/plano-textos');
await editor.evaluar(`(() => {
  const el = document.querySelector('.plano');
  const r = el.getBoundingClientRect();
  const x = r.left + r.width * 0.4, y = r.top + r.height * 0.4;
  for (const t of ['pointerdown', 'pointerup']) {
    el.dispatchEvent(new PointerEvent(t, { bubbles: true, clientX: x, clientY: y, pointerId: 1 }));
  }
  return true;
})()`);
await editor.esperar(1500);
const despues = await api('/api/app/plano-textos');
const nuevos = (despues.cuerpo || []).length - (antes.cuerpo || []).length;
paso('tocar el plano crea un rotulo', nuevos === 1, `${nuevos} nuevo(s)`);

const creado = (despues.cuerpo || []).find((t) => !(antes.cuerpo || []).some((a) => a.id === t.id));
paso('y queda seleccionado, listo para escribirle',
     await editor.evaluar(`!!document.querySelector('.grupo-texto input.control')`));

titulo('Consola');
for (const [quien, ch] of [['mapa', mapa], ['editor', editor]]) {
  const errs = erroresDe(ch.eventos).filter((e) => !/favicon|PLANO-/.test(e));
  paso(`sin errores de JavaScript en el ${quien}`, errs.length === 0, errs.slice(0, 2).join(' | '));
}

mapa.cerrar();
editor.cerrar();

titulo('Limpieza');
if (creado) await api(`/api/app/plano-textos/${creado.id}`, { method: 'DELETE' });
r = await api('/api/app/plano-textos');
paso('el plano queda como estaba', (r.cuerpo || []).length === habia,
     `${(r.cuerpo || []).length} rotulo(s), habia ${habia}`);

console.log(fallos ? `\n${fallos} paso(s) fallaron.\n` : '\nTodo paso.\n');
process.exit(fallos ? 1 : 0);
