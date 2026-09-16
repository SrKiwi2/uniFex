/**
 * El menu agrupado y el tablero de direccion, EN EL NAVEGADOR.
 *
 * Dos cosas que una peticion HTTP no puede contestar: que el menu se lea agrupado, y que el
 * jefe —que entra con un rol de SOLO CONSULTA— vea sus cifras y NO vea lo que no le toca.
 *
 * Crea el usuario de direccion de prueba y lo borra al terminar.
 *
 *   node .claude/skills/unifex-fullstack/scripts/verificar-direccion.mjs \
 *        --spa http://localhost:5173 --usuario admin1 --clave 'usuario25$'
 */
import { abrirChrome } from './lib-navegador.mjs';

const args = process.argv.slice(2);
const opcion = (n, d) => { const i = args.indexOf(`--${n}`); return i >= 0 && args[i + 1] ? args[i + 1] : d; };

const SPA = opcion('spa', 'http://localhost:5173');
const USUARIO = opcion('usuario', 'admin1');
const CLAVE = opcion('clave', 'usuario25$');
const marca = Date.now().toString().slice(-6);

let fallos = 0;
const ok = (c, m, extra = '') => {
  console.log(`${c ? '  ok  ' : ' FALLA'} ${m}${!c && extra ? ` — ${extra}` : ''}`);
  if (!c) fallos++;
};
const esperar = (ms) => new Promise((r) => setTimeout(r, ms));
const j = async (r) => { try { return await r.json(); } catch { return null; } };

const { evaluar, cerrar } = await abrirChrome(9235);
let cab = null;
let jefeId = null;

/** Entra en la SPA con una sesion ya obtenida y va a una ruta. */
async function entrarComo(sesion, ruta) {
  await evaluar(`location.href = ${JSON.stringify(SPA + '/login')}`);
  await esperar(2200);
  await evaluar(`(() => {
    localStorage.clear();
    localStorage.setItem('token', ${JSON.stringify(sesion.token)});
    localStorage.setItem('usuario', ${JSON.stringify(String(sesion.usuario ?? ''))});
    localStorage.setItem('rol', ${JSON.stringify(String(sesion.rol ?? ''))});
    if (${JSON.stringify(sesion.id ?? null)} != null) localStorage.setItem('id', ${JSON.stringify(String(sesion.id ?? ''))});
    location.href = ${JSON.stringify(SPA + ruta)};
  })()`);
  // Se espera al menu, no un numero fijo de segundos.
  return evaluar(`(async () => {
    for (let i = 0; i < 50; i++) {
      if (document.querySelector('nav .enlace')) return true;
      await new Promise(r => setTimeout(r, 300));
    }
    return false;
  })()`);
}

try {
  console.log(`\nMenu agrupado y tablero de direccion · ${SPA}\n`);

  const admin = await j(await fetch(`${SPA}/api/auth/login`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ usuario: USUARIO, contrasena: CLAVE }),
  }));
  ok(Boolean(admin?.token), 'entra como administracion');
  if (!admin?.token) throw new Error('sin sesion no hay prueba');
  cab = { Authorization: `Bearer ${admin.token}`, 'Content-Type': 'application/json' };

  // ---- 1. el menu, agrupado ----
  console.log('Menu');
  ok(await entrarComo(admin, '/') === true, 'el menu termina de pintarse');

  const menu = JSON.parse(await evaluar(`(() => {
    const grupos = [...document.querySelectorAll('nav .grupo')].map(g => ({
      titulo: g.querySelector('.grupo-titulo')?.textContent?.trim() || '',
      enlaces: [...g.querySelectorAll('.enlace')].map(a => a.textContent.trim()),
    }));
    return JSON.stringify({ grupos, sueltos: document.querySelectorAll('nav > .enlace').length });
  })()`));

  ok(menu.grupos.length >= 4, `el menu sale agrupado (${menu.grupos.length} grupos)`,
    JSON.stringify(menu.grupos.map((g) => g.titulo)));
  ok(menu.sueltos === 0, 'no queda ningun enlace suelto fuera de su grupo', `${menu.sueltos} sueltos`);
  ok(menu.grupos.every((g) => g.titulo && g.enlaces.length > 0),
    'ningun grupo sale con titulo y sin enlaces debajo');
  ok(menu.grupos[0]?.titulo?.toLowerCase().includes('mi trabajo'),
    'el primero es el del dia a dia', menu.grupos[0]?.titulo);
  console.log(`  info  ${menu.grupos.map((g) => `${g.titulo} (${g.enlaces.length})`).join(' · ')}`);

  // ---- 2. el jefe: un rol de solo consulta ----
  console.log('\nUsuario de direccion (rol ASESORIA)');
  const roles = await j(await fetch(`${SPA}/api/app/roles`, { headers: cab }));
  const rolAsesoria = (roles || []).find((r) => r.nombre === 'ASESORIA');
  ok(Boolean(rolAsesoria), 'el rol ASESORIA existe y es de sistema',
    JSON.stringify((roles || []).map((r) => r.nombre)));

  const alta = await j(await fetch(`${SPA}/api/app/usuarios`, {
    method: 'POST', headers: cab,
    body: JSON.stringify({
      username: `jefe${marca}`, password: 'ClaveJefe2026', rolId: rolAsesoria?.id, personaId: null,
      persona: { nombre: 'DIRECCION', paterno: 'PRUEBA', materno: '', ci: `DP${marca}`, correo: '', celular: '' },
    }),
  }));
  jefeId = alta?.usuario?.id;
  ok(Boolean(jefeId), 'se crea el usuario del jefe', JSON.stringify(alta).slice(0, 150));

  const jefe = await j(await fetch(`${SPA}/api/auth/login`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ usuario: `jefe${marca}`, contrasena: 'ClaveJefe2026' }),
  }));
  ok(Boolean(jefe?.token), 'y puede entrar', `rol=${jefe?.rol}`);

  // ---- 3. lo que ve el jefe ----
  console.log('\nQue ve el jefe');
  ok(await entrarComo(jefe, '/direccion') === true, 'su menu se pinta');

  const suyo = JSON.parse(await evaluar(`(() => {
    const enlaces = [...document.querySelectorAll('nav .enlace')].map(a => a.textContent.trim());
    return JSON.stringify({ enlaces });
  })()`));
  const tiene = (t) => suyo.enlaces.some((e) => e.toLowerCase().includes(t));
  ok(tiene('dirección') || tiene('direccion'), 've el tablero de dirección', suyo.enlaces.join(' | '));
  ok(tiene('reportes'), 've los reportes');
  // Lo importante es lo que NO ve: es un rol de consulta.
  ok(!tiene('editor'), 'NO ve el editor del plano');
  ok(!tiene('usuarios'), 'NO ve la gestión de usuarios');
  ok(!tiene('registrar venta'), 'NO ve registrar venta');
  console.log(`  info  su menu: ${suyo.enlaces.join(' · ')}`);

  // ---- 4. el tablero, dibujado ----
  console.log('\nEl tablero');
  const pintado = await evaluar(`(async () => {
    for (let i = 0; i < 50; i++) {
      if (document.querySelector('.direccion .tarjetas')) return true;
      await new Promise(r => setTimeout(r, 300));
    }
    return false;
  })()`);
  ok(pintado === true, 'el tablero carga para el jefe (no le da 403)');

  const t = JSON.parse(await evaluar(`(() => JSON.stringify({
    texto: document.body.innerText,
    kpis: document.querySelectorAll('.direccion .kpi').length,
    barras: document.querySelectorAll('.direccion .barras-meta .fila-meta').length,
    linea: !!document.querySelector('.direccion svg .curva') || !!document.querySelector('.linea-tiempo'),
    descargas: document.querySelectorAll('.direccion .descargas button').length,
    filtros: document.querySelectorAll('.direccion .filtros select').length,
  }))()`));

  ok(t.kpis >= 4, 'salen las cuatro cifras de cabecera', `${t.kpis}`);
  ok(t.barras > 0, 'sale la ocupación por categoría', `${t.barras} barras`);
  ok(t.linea === true, 'sale la curva de avance');
  ok(t.descargas >= 8, 'hay PDF y Excel en los cuatro bloques', `${t.descargas} botones`);
  ok(t.filtros >= 2, 'hay filtros sobre los gráficos', `${t.filtros}`);
  ok(/vendido/i.test(t.texto) && /cobrado/i.test(t.texto), 'se leen las cifras de dinero');

  // La guia de visualizacion exige lectura alternativa al grafico: la tabla de vendedores.
  ok(/ver la tabla completa/i.test(t.texto),
    'el ranking ofrece su tabla (lectura alternativa al gráfico)');

  const desborda = await evaluar(
    'document.documentElement.scrollWidth > document.documentElement.clientWidth + 1');
  ok(desborda === false, 'el tablero no se desplaza de lado');

  console.log(`\n${fallos === 0 ? 'Todo bien.' : `${fallos} comprobacion(es) fallidas.`}\n`);
} finally {
  if (cab && jefeId) {
    await fetch(`${SPA}/api/app/usuarios/${jefeId}`, { method: 'DELETE', headers: cab }).catch(() => {});
  }
  await cerrar();
}

process.exit(fallos === 0 ? 0 : 1);
