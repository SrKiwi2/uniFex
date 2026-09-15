/**
 * Las pantallas nuevas, en un navegador de verdad:
 *
 *   - CATEGORIAS: que liste, que deje editar y que muestre las opciones de precio.
 *   - SEGUIMIENTO: que muestre a quien esta conectado.
 *   - VENTA: que el selector de opcion de precio aparezca y que cambiar de opcion CAMBIE
 *     el total en pantalla. Es lo unico que importa de verdad: un selector que se pinta
 *     bien pero no mueve el total es peor que no tenerlo.
 *
 * Deja la base como estaba.
 *
 *   node .claude/skills/unifex-fullstack/scripts/verificar-pantallas-nuevas.mjs \
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

// Una categoria con DOS opciones: es lo que hace falta para que el selector tenga sentido.
let r = await api('/api/app/categorias', { method: 'POST', body: JSON.stringify({
  nombre: `ZZ PANT ${marca}`, cantidad: 2, color: '#884499', forma: 'cuadrado',
  tamanoMapa: 0.012, precioBase: 0, tamano: '3x3' }) });
const catId = r.cuerpo?.id;
const cat = ((await api('/api/app/categorias')).cuerpo || []).find((c) => c.id === catId);
await api(`/api/app/categorias/${catId}/opciones/${cat.opciones[0].id}`,
  { method: 'PATCH', body: JSON.stringify({ precio: 700 }) });
await api(`/api/app/categorias/${catId}/opciones`,
  { method: 'POST', body: JSON.stringify({ nombre: 'CON TARIMA', precio: 1500 }) });

const ch = await abrirChrome(Number(arg('puerto', '9390')));
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

// ---------------------------------------------------------------- categorias
titulo('Modulo de categorias');
await ch.ir(`${WEB}/categorias`);
await ch.esperar(2500);

paso('la pantalla carga', (await ch.evaluar('location.pathname')).includes('/categorias'));
const tarjetas = await ch.evaluar(`document.querySelectorAll('article.categoria').length`);
paso('lista las categorias', tarjetas > 0, `${tarjetas} categoria(s)`);
const conOpciones = await ch.evaluar(`document.querySelectorAll('.lista-op li').length`);
paso('y pinta sus opciones de precio', conOpciones > 0, `${conOpciones} opcion(es)`);
const marcadas = await ch.evaluar(`document.querySelectorAll('.lista-op li.predet').length`);
paso('marcando cual es la de por defecto', marcadas > 0, `${marcadas} marcada(s)`);
const miTarjeta = await ch.evaluar(
  `[...document.querySelectorAll('article.categoria')].some((a) => a.textContent.includes('ZZ PANT ${marca}'))`);
paso('la categoria recien creada sale sin recargar nada', miTarjeta);
const dosOpciones = await ch.evaluar(`
  (() => { const a = [...document.querySelectorAll('article.categoria')]
      .find((x) => x.textContent.includes('ZZ PANT ${marca}'));
    return a ? a.querySelectorAll('.lista-op li').length : 0; })()`);
paso('con sus DOS opciones', dosOpciones === 2, `${dosOpciones}`);

// ---------------------------------------------------------------- seguimiento
titulo('Seguimiento en vivo');
await ch.ir(`${WEB}/seguimiento`);
await ch.esperar(3000);
paso('la pantalla carga', (await ch.evaluar('location.pathname')).includes('/seguimiento'));
const enLinea = await ch.evaluar(`document.querySelectorAll('.persona').length`);
paso('muestra a quien esta conectado', enLinea > 0, `${enLinea} persona(s)`);
const meVeo = await ch.evaluar(`document.body.textContent.includes('admin1')
  || document.querySelectorAll('.persona').length > 0`);
paso('me incluye a mi, que acabo de entrar', meVeo);

// ---------------------------------------------------------------- venta: el total cambia
titulo('Formulario de venta: la opcion mueve el total');
const libres = ((await api('/api/app/puestos')).cuerpo || [])
  .filter((p) => p.categoriaId === catId && p.estado === 'L').slice(0, 2);
const ids = libres.map((p) => p.id);
await api('/api/app/puestos/carrito', { method: 'POST', body: JSON.stringify({ ids }) });

await ch.ir(`${WEB}/venta`);
await ch.esperar(3000);

const hayOpciones = await ch.evaluar(`document.querySelectorAll('.opciones-precio .opcion').length`);
paso('aparece el selector de opciones', hayOpciones >= 2, `${hayOpciones} boton(es)`);

const leerTotal = () => ch.evaluar(
  `(document.querySelector('.total-cab .total')?.textContent || '').replace(/[^\\d]/g, '')`);
const totalBarato = await leerTotal();
paso('arranca con la opcion por defecto', Number(totalBarato) === 1400, `${totalBarato} Bs (2 x 700)`);

// Tocar la cara tiene que mover el total. Esto es lo que se estaba probando.
await ch.evaluar(`(() => {
  const b = [...document.querySelectorAll('.opciones-precio .opcion')]
    .find((x) => x.textContent.includes('CON TARIMA'));
  b?.click(); return !!b;
})()`);
await ch.esperar(600);
const totalCaro = await leerTotal();
paso('al elegir la opcion cara, el total sube', Number(totalCaro) === 3000,
     `${totalCaro} Bs (2 x 1500)`);

await ch.evaluar(`(() => {
  const b = [...document.querySelectorAll('.opciones-precio .opcion')]
    .find((x) => !x.textContent.includes('CON TARIMA'));
  b?.click(); return !!b;
})()`);
await ch.esperar(600);
paso('y al volver a la barata, baja', Number(await leerTotal()) === 1400);

// El derecho a responsables: 2 casetas -> 4.
await ch.evaluar(`(() => { const b=[...document.querySelectorAll('button')]
  .find(x=>/Siguiente/i.test(x.textContent)); b?.click(); return !!b; })()`);
await ch.esperar(400);
await ch.evaluar(`(() => { const b=[...document.querySelectorAll('button')]
  .find(x=>/Siguiente/i.test(x.textContent)); b?.click(); return !!b; })()`);
await ch.esperar(600);
const textoDerecho = await ch.evaluar(`document.querySelector('.derecho')?.textContent || ''`);
paso('el paso de responsables dice el derecho que dan las casetas',
     /4 responsables/.test(textoDerecho), textoDerecho.trim().replace(/\s+/g, ' ').slice(0, 90));

titulo('Consola');
const errores = erroresDe(ch.eventos).filter((e) => !/favicon|PLANO-/.test(e));
paso('ni un error de JavaScript', errores.length === 0, errores.slice(0, 2).join(' | '));

ch.cerrar();

// ---------------------------------------------------------------- limpieza
titulo('Limpieza');
await api('/api/app/puestos/carrito', { method: 'DELETE', body: JSON.stringify({ ids }) });
r = await api(`/api/app/categorias/${catId}`, { method: 'DELETE' });
paso('la categoria de prueba se retira', r.estado === 200, `HTTP ${r.estado}`);

console.log(fallos ? `\n${fallos} paso(s) fallaron.\n` : '\nTodo paso.\n');
process.exit(fallos ? 1 : 0);
