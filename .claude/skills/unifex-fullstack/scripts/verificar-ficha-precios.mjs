/**
 * La ficha de la caseta y los PRECIOS, en el navegador.
 *
 * La ficha enseñaba un precio a secas aunque su categoria se vendiera de varias formas, asi que
 * el vendedor le cantaba al cliente una cifra que no era la unica posible. Lo que se comprueba
 * aqui es que cada caso diga lo que toca:
 *
 *   - categoria con VARIAS opciones -> se listan todas, con sus precios;
 *   - categoria con UNA sola        -> el precio, directo, sin adornos;
 *   - caseta con precio propio (V37)-> ese precio manda, y NO se listan las opciones (esa
 *     caseta no las va a cobrar).
 *
 * Y que ya no se pida ni se pinte la galeria de fotos.
 *
 * Crea una opcion de precio de prueba y la borra al terminar.
 *
 *   node .claude/skills/unifex-fullstack/scripts/verificar-ficha-precios.mjs \
 *        --spa http://localhost:5173 --usuario admin1 --clave 'usuario25$'
 */
import { abrirChrome } from './lib-navegador.mjs';

const args = process.argv.slice(2);
const opcionArg = (n, d) => { const i = args.indexOf(`--${n}`); return i >= 0 && args[i + 1] ? args[i + 1] : d; };

const SPA = opcionArg('spa', 'http://localhost:5173');
const USUARIO = opcionArg('usuario', 'admin1');
const CLAVE = opcionArg('clave', 'usuario25$');

let fallos = 0;
const ok = (c, m, extra = '') => {
  console.log(`${c ? '  ok  ' : ' FALLA'} ${m}${!c && extra ? ` — ${extra}` : ''}`);
  if (!c) fallos++;
};
const esperar = (ms) => new Promise((r) => setTimeout(r, ms));
const j = async (r) => { try { return await r.json(); } catch { return null; } };

const { evaluar, cerrar } = await abrirChrome(9234);
let cab = null;
let catVarias = null;
let opcionExtra = null;
let casetaPropia = null;

try {
  console.log(`\nPrecios en la ficha de la caseta · ${SPA}\n`);

  const sesion = await j(await fetch(`${SPA}/api/auth/login`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ usuario: USUARIO, contrasena: CLAVE }),
  }));
  ok(Boolean(sesion?.token), 'entra con la sesion de prueba');
  if (!sesion?.token) throw new Error('sin sesion no hay nada que mirar');
  cab = { Authorization: `Bearer ${sesion.token}`, 'Content-Type': 'application/json' };

  const puestos = await j(await fetch(`${SPA}/api/app/puestos`, { headers: cab }));
  const cats = await j(await fetch(`${SPA}/api/app/categorias`, { headers: cab }));

  // Una categoria REAL con casetas libres, a la que se le añade una segunda forma de venta.
  const conCasetas = (cats || []).filter((c) => !/^ZZ /.test(c.nombre || '')
    && (puestos || []).some((p) => p.categoriaId === c.id && p.estado === 'L'));
  catVarias = conCasetas.find((c) => (c.opciones || []).length === 1);
  ok(Boolean(catVarias), 'hay una categoria con casetas libres para la prueba');
  if (!catVarias) throw new Error('sin categoria no hay prueba');

  const base = Number(catVarias.opciones[0].precio || 0);
  const alta = await j(await fetch(`${SPA}/api/app/categorias/${catVarias.id}/opciones`, {
    method: 'POST', headers: cab,
    body: JSON.stringify({ nombre: 'ZZ CON TARIMA', precio: base + 400, predeterminada: false }),
  }));
  opcionExtra = alta?.opcion?.id;
  ok(Boolean(opcionExtra), 'se crea una segunda forma de vender esa categoria');

  const deVarias = (puestos || []).find((p) => p.categoriaId === catVarias.id && p.estado === 'L');
  const catUna = conCasetas.find((c) => c.id !== catVarias.id && (c.opciones || []).length === 1);
  const deUna = (puestos || []).find((p) => p.categoriaId === catUna?.id && p.estado === 'L');
  console.log(`  info  "${catVarias.nombre}" pasa a 2 formas (${base} y ${base + 400}) · caseta ${deVarias?.codigo}`);
  console.log(`  info  "${catUna?.nombre}" se queda con 1 · caseta ${deUna?.codigo}\n`);

  await evaluar(`location.href = ${JSON.stringify(SPA + '/login')}`);
  await esperar(2500);
  await evaluar(`(() => {
    localStorage.setItem('token', ${JSON.stringify(sesion.token)});
    localStorage.setItem('usuario', ${JSON.stringify(String(sesion.usuario ?? USUARIO))});
    localStorage.setItem('rol', ${JSON.stringify(String(sesion.rol ?? ''))});
    if (${JSON.stringify(sesion.id ?? null)} != null) localStorage.setItem('id', ${JSON.stringify(String(sesion.id ?? ''))});
    location.href = ${JSON.stringify(SPA + '/mapa')};
  })()`);
  // Se espera al plano, no un numero fijo de segundos.
  const pintado = await evaluar(`(async () => {
    for (let i = 0; i < 50; i++) {
      if (document.querySelector('.pin')) return true;
      await new Promise(r => setTimeout(r, 300));
    }
    return false;
  })()`);
  ok(pintado === true, 'el mapa termina de pintarse');

  /** Abre la ficha de una caseta por su id y devuelve lo que se lee en ella. */
  const ficha = async (id) => evaluar(`(async () => {
    const t = localStorage.getItem('token');
    const puestos = await (await fetch('/api/app/puestos', { headers: { Authorization: 'Bearer ' + t } })).json();
    const p = puestos.find(x => x.id === ${id});
    if (!p) return 'NO EXISTE';
    const pin = [...document.querySelectorAll('.pin')]
      .find(b => (b.querySelector('.num-caseta')?.textContent || '').trim() === String(p.codigo)
                 && (b.getAttribute('title') || '').includes(p.categoria));
    if (!pin) return 'PIN NO ENCONTRADO';
    pin.click();
    await new Promise(r => setTimeout(r, 1200));
    const f = document.querySelector('.ficha');
    const texto = f ? f.innerText : 'SIN FICHA';
    const tieneLista = !!document.querySelector('.ficha .precios');
    const tieneGaleria = !!document.querySelector('.ficha .galeria');
    const cerrarBtn = f?.querySelector('header button');
    if (cerrarBtn) cerrarBtn.click();
    await new Promise(r => setTimeout(r, 400));
    return JSON.stringify({ texto, tieneLista, tieneGaleria });
  })()`);

  // ---- categoria con VARIAS formas ----
  console.log('Categoría con varias formas de venta');
  const a = JSON.parse(await ficha(deVarias.id));
  ok(a.tieneLista === true, 'la ficha lista las formas de venta en vez de un solo precio');
  ok(a.texto.includes('ZZ CON TARIMA'), 'aparece la segunda forma por su nombre', a.texto.slice(0, 200));
  ok(a.texto.includes(base.toLocaleString('es-BO')) && a.texto.includes((base + 400).toLocaleString('es-BO')),
    'y los DOS precios, no solo uno', a.texto.slice(0, 200));
  ok(/depende de cuál se elija/i.test(a.texto),
    'se avisa de que el precio depende de cuál se elija al registrar');
  ok(a.tieneGaleria === false, 'no hay galería de fotos');

  // ---- categoria con UNA sola ----
  if (deUna) {
    console.log('\nCategoría con una sola forma');
    const b = JSON.parse(await ficha(deUna.id));
    ok(b.tieneLista === false, 'no lista nada: enseña el precio directo');
    ok(/precio/i.test(b.texto), 'y ese precio se lee', b.texto.slice(0, 200));
    ok(b.tieneGaleria === false, 'tampoco hay galería');
  } else {
    console.log('  info  no hay otra categoria de una sola opcion: se salta');
  }

  // ---- caseta con precio propio: manda sobre las opciones ----
  console.log('\nCaseta con precio propio');
  casetaPropia = deVarias.id;
  const PROPIO = base + 1234;
  await fetch(`${SPA}/api/app/puestos/precios`, {
    method: 'PATCH', headers: cab, body: JSON.stringify([{ id: casetaPropia, precio: PROPIO }]),
  });
  await esperar(1200);
  const c = JSON.parse(await ficha(casetaPropia));
  ok(c.tieneLista === false,
    'con precio propio NO se listan las opciones (esa caseta no las cobra)', c.texto.slice(0, 200));
  ok(c.texto.includes(PROPIO.toLocaleString('es-BO')), 'se enseña su precio propio', c.texto.slice(0, 200));
  ok(/precio propio/i.test(c.texto), 'y se marca como propio');

  const desborda = await evaluar(
    'document.documentElement.scrollWidth > document.documentElement.clientWidth + 1');
  ok(desborda === false, 'el mapa no se desplaza de lado');

  console.log(`\n${fallos === 0 ? 'Todo bien.' : `${fallos} comprobacion(es) fallidas.`}\n`);
} finally {
  // Deja el catalogo como estaba: una opcion de prueba viva cambiaria lo que ve todo el mundo.
  if (cab && casetaPropia) {
    await fetch(`${SPA}/api/app/puestos/precios`, {
      method: 'PATCH', headers: cab, body: JSON.stringify([{ id: casetaPropia, precio: null }]),
    }).catch(() => {});
  }
  if (cab && catVarias && opcionExtra) {
    await fetch(`${SPA}/api/app/categorias/${catVarias.id}/opciones/${opcionExtra}`, {
      method: 'DELETE', headers: cab,
    }).catch(() => {});
  }
  await cerrar();
}

process.exit(fallos === 0 ? 0 : 1);
