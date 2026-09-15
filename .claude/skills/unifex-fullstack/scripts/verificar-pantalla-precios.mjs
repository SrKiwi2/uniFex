/**
 * La pantalla Puestos y el formulario de venta, EN EL NAVEGADOR, con precios propios (V37).
 *
 * Lo que una peticion HTTP no puede decir: que administracion entienda cual caseta lleva
 * precio especial, y que el vendedor vea el total correcto delante del cliente. Esta es la
 * mitad del trabajo donde un fallo no da error, solo da un numero equivocado.
 *
 * Deja la caseta como estaba al terminar.
 *
 *   node .claude/skills/unifex-fullstack/scripts/verificar-pantalla-precios.mjs \
 *        --spa http://localhost:5173 --usuario admin1 --clave 'usuario25$'
 */
import { abrirChrome } from './lib-navegador.mjs';

const args = process.argv.slice(2);
const opcion = (n, d) => { const i = args.indexOf(`--${n}`); return i >= 0 && args[i + 1] ? args[i + 1] : d; };

const SPA = opcion('spa', 'http://localhost:5173');
const USUARIO = opcion('usuario', 'admin1');
const CLAVE = opcion('clave', 'usuario25$');

let fallos = 0;
const ok = (c, m, extra = '') => {
  console.log(`${c ? '  ok  ' : ' FALLA'} ${m}${!c && extra ? ` — ${extra}` : ''}`);
  if (!c) fallos++;
};
const esperar = (ms) => new Promise((r) => setTimeout(r, ms));
const jsonDe = async (r) => { try { return await r.json(); } catch { return null; } };

const { evaluar, cerrar } = await abrirChrome(9232);
let caseta = null;
let cab = null;

try {
  console.log(`\nPrecio propio en pantalla · ${SPA}\n`);

  const sesion = await jsonDe(await fetch(`${SPA}/api/auth/login`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ usuario: USUARIO, contrasena: CLAVE }),
  }));
  ok(Boolean(sesion?.token), 'entra con la sesion de prueba');
  if (!sesion?.token) throw new Error('sin sesion no hay nada que mirar');
  cab = { Authorization: `Bearer ${sesion.token}` };

  // Una caseta libre con categoria, para ponerle precio propio.
  const puestos = await jsonDe(await fetch(`${SPA}/api/app/puestos`, { headers: cab }));
  caseta = (puestos || []).find((p) => p.estado === 'L' && p.categoriaId != null && !p.precioPropio);
  ok(Boolean(caseta), 'hay una caseta libre para la prueba');
  if (!caseta) throw new Error('sin caseta no hay prueba');

  const deCategoria = Number(caseta.precio || 0);
  const ESPECIAL = deCategoria + 777;
  console.log(`  info  caseta ${caseta.codigo} de "${caseta.categoria}" · categoria ${deCategoria} Bs → propio ${ESPECIAL} Bs\n`);

  await fetch(`${SPA}/api/app/puestos/precios`, {
    method: 'PATCH', headers: { ...cab, 'Content-Type': 'application/json' },
    body: JSON.stringify([{ id: caseta.id, precio: ESPECIAL }]),
  });

  // ---- la pantalla de administracion ----
  console.log('Pantalla Puestos');
  // Hay que ESTAR en el origen de la aplicacion antes de tocar localStorage: en `about:blank`
  // el navegador lo deniega con un SecurityError que no menciona el origen por ningun lado.
  await evaluar(`location.href = ${JSON.stringify(SPA + '/login')}`);
  await esperar(2500);
  // Las claves, con la forma que espera `stores/auth.js`: `usuario` es el NOMBRE de usuario,
  // una cadena, no el objeto de la respuesta. Metiendo el objeto entero, la cabecera de la
  // aplicacion pinta el JSON —token incluido— en pantalla.
  await evaluar(`(() => {
    localStorage.setItem('token', ${JSON.stringify(sesion.token)});
    localStorage.setItem('usuario', ${JSON.stringify(String(sesion.usuario ?? USUARIO))});
    localStorage.setItem('rol', ${JSON.stringify(String(sesion.rol ?? ''))});
    if (${JSON.stringify(sesion.id ?? null)} != null) localStorage.setItem('id', ${JSON.stringify(String(sesion.id ?? ''))});
    location.href = ${JSON.stringify(SPA + '/puestos')};
  })()`);
  // Se espera a que la TABLA exista, no un numero fijo de segundos: son ~500 casetas y en
  // desarrollo Vite compila la vista al vuelo, asi que cinco segundos unas veces bastan y
  // otras no. Una espera fija es como una prueba empieza a fallar sola los martes.
  const aparecio = await evaluar(`(async () => {
    for (let i = 0; i < 40; i++) {
      if (document.querySelector('tbody tr')) return true;
      await new Promise(r => setTimeout(r, 300));
    }
    return false;
  })()`);
  ok(aparecio === true, 'la tabla de puestos termina de cargar');

  /*
   * Se compara SIN distinguir mayusculas.
   *
   * `innerText` devuelve el texto tal y como se VE, y las cabeceras de tabla llevan
   * `text-transform: uppercase`, asi que "Precio propio" llega como "PRECIO PROPIO". Buscar la
   * cadena tal cual esta en el codigo fuente falla aunque la columna este perfectamente puesta
   * —que es justo lo que paso al escribir esta prueba—.
   */
  const texto = String(await evaluar('document.body.innerText')).toLowerCase();
  ok(texto.includes('precio propio'), 'la tabla tiene columna de precio propio');
  ok(texto.includes('puestos'), 'la pantalla carga');

  // El filtro es lo que contesta "¿a cuales les puse precio especial?".
  const filtrado = await evaluar(`(async () => {
    const casillas = [...document.querySelectorAll('.solo-propio input[type=checkbox]')];
    if (!casillas.length) return 'sin filtro';
    casillas[0].click();
    casillas[0].dispatchEvent(new Event('change', { bubbles: true }));
    await new Promise(r => setTimeout(r, 600));
    return document.querySelectorAll('tbody tr').length;
  })()`);
  ok(filtrado !== 'sin filtro', 'existe el filtro "solo con precio propio"');
  ok(Number(filtrado) >= 1, 'y al activarlo quedan solo las que lo llevan', `filas: ${filtrado}`);

  const soloPropio = await evaluar('document.body.innerText');
  ok(String(soloPropio).toLowerCase().includes(String(caseta.codigo).toLowerCase()),
    `la caseta ${caseta.codigo} sale entre las de precio propio`);

  const desbordaAdmin = await evaluar(
    'document.documentElement.scrollWidth > document.documentElement.clientWidth + 1');
  ok(desbordaAdmin === false, 'la pantalla no se desplaza de lado');

  // ---- el formulario de venta ----
  //
  // Es la mitad que de verdad cuesta dinero: el vendedor canta el total delante del cliente.
  console.log('\nFormulario de venta');
  await fetch(`${SPA}/api/app/puestos/${caseta.id}/reservar`, { method: 'POST', headers: cab });

  await evaluar(`location.href = ${JSON.stringify(SPA + '/venta')}`);
  await esperar(5000);

  const venta = await evaluar('document.body.innerText');
  ok(venta.includes(ESPECIAL.toLocaleString('es-BO')) || venta.includes(String(ESPECIAL)),
    `el total lleva el precio propio (${ESPECIAL} Bs), no el de la categoria (${deCategoria})`,
    venta.slice(0, 300));
  ok(/precio propio/i.test(venta),
    'y se avisa de que esa caseta lleva precio propio (si no, parece un error de cuentas)');

  const desbordaVenta = await evaluar(
    'document.documentElement.scrollWidth > document.documentElement.clientWidth + 1');
  ok(desbordaVenta === false, 'la venta tampoco se desplaza de lado');

  console.log(`\n${fallos === 0 ? 'Todo bien.' : `${fallos} comprobacion(es) fallidas.`}\n`);
} finally {
  // Dejar la caseta como estaba: reservada y con precio especial estorbaria a la siguiente
  // prueba y, en una base de verdad, a quien intente venderla.
  if (caseta && cab) {
    await fetch(`${SPA}/api/app/puestos/${caseta.id}/liberar`, { method: 'POST', headers: cab }).catch(() => {});
    await fetch(`${SPA}/api/app/puestos/precios`, {
      method: 'PATCH', headers: { ...cab, 'Content-Type': 'application/json' },
      body: JSON.stringify([{ id: caseta.id, precio: null }]),
    }).catch(() => {});
  }
  await cerrar();
}

process.exit(fallos === 0 ? 0 : 1);
