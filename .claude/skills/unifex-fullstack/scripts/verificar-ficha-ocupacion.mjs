/**
 * La ficha de una caseta, EN EL NAVEGADOR: que se lea quien la tiene.
 *
 * Lo que comprueba una peticion HTTP es que el dato viaje; lo que hace falta saber es que el
 * vendedor lo VEA. Aqui se abre el mapa de verdad, se toca una caseta vendida y otra en
 * tramite, y se lee lo que sale en pantalla.
 *
 * Necesita los dos servidores levantados y una caseta en cada estado:
 *   node .claude/skills/unifex-fullstack/scripts/verificar-ficha-ocupacion.mjs \
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

const { evaluar, cerrar } = await abrirChrome(9231);

try {
  console.log(`\nFicha de la caseta en el navegador · ${SPA}\n`);

  // ---- entrar ----
  await evaluar(`location.href = ${JSON.stringify(SPA + '/login')}`);
  await esperar(2500);
  const entro = await evaluar(`(async () => {
    const r = await fetch('/api/auth/login', { method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ usuario: ${JSON.stringify(USUARIO)}, contrasena: ${JSON.stringify(CLAVE)} }) });
    if (!r.ok) return 'HTTP ' + r.status;
    const d = await r.json();
    localStorage.setItem('token', d.token);
    localStorage.setItem('usuario', JSON.stringify(d));
    return 'ok';
  })()`);
  ok(entro === 'ok', 'entra con la sesion de prueba', String(entro));
  if (entro !== 'ok') throw new Error('sin sesion no hay nada que mirar');

  // ---- que casetas hay para mirar ----
  const datos = await evaluar(`(async () => {
    const t = localStorage.getItem('token');
    const cab = { Authorization: 'Bearer ' + t };
    const puestos = await (await fetch('/api/app/puestos', { headers: cab })).json();
    const ocup = await (await fetch('/api/app/puestos/ocupacion', { headers: cab })).json();
    const porId = new Map(puestos.map(p => [p.id, p]));
    const conNombre = ocup.filter(o => o.vendedor && porId.has(o.puestoId));
    return JSON.stringify({
      vendida: conNombre.find(o => o.estado === 'O') || null,
      tramite: conNombre.find(o => o.estado === 'T') || null,
    });
  })()`);
  const { vendida, tramite } = JSON.parse(datos);
  console.log(`  info  vendida: ${vendida ? vendida.puestoId + ' por ' + vendida.vendedor : 'ninguna'}`);
  console.log(`  info  tramite: ${tramite ? tramite.puestoId + ' por ' + tramite.vendedor : 'ninguna'}\n`);

  await evaluar(`location.href = ${JSON.stringify(SPA + '/mapa')}`);
  await esperar(4500);

  /**
   * Abre la ficha de una caseta por su id y devuelve el texto que se lee en pantalla.
   *
   * El pin no lleva el id en el DOM, asi que se localiza por el numero que muestra mas la
   * categoria de su rotulo: el numero solo se repite entre categorias.
   */
  const textoDeFicha = async (id) => {
    return evaluar(`(async () => {
      const t = localStorage.getItem('token');
      const puestos = await (await fetch('/api/app/puestos', { headers: { Authorization: 'Bearer ' + t } })).json();
      const p = puestos.find(x => x.id === ${id});
      if (!p) return 'NO EXISTE';
      const pin = [...document.querySelectorAll('.pin')]
        .find(b => (b.querySelector('.num-caseta')?.textContent || '').trim() === String(p.codigo)
                   && (b.getAttribute('title') || '').includes(p.categoria));
      if (!pin) return 'PIN NO ENCONTRADO';
      pin.click();
      await new Promise(r => setTimeout(r, 900));
      const ficha = document.querySelector('.ficha');
      const texto = ficha ? ficha.innerText : 'SIN FICHA';
      const cerrarBtn = ficha?.querySelector('header button');
      if (cerrarBtn) cerrarBtn.click();
      await new Promise(r => setTimeout(r, 400));
      return texto;
    })()`);
  };

  if (vendida) {
    const texto = await textoDeFicha(vendida.puestoId);
    ok(!String(texto).startsWith('PIN NO ENCONTRADO') && !String(texto).startsWith('SIN FICHA'),
      'la ficha de una caseta vendida se abre', String(texto).slice(0, 80));
    ok(String(texto).includes('La vendió'), 'dice "La vendió"', String(texto).slice(0, 160));
    ok(String(texto).includes(vendida.vendedor), `nombra a ${vendida.vendedor}`, String(texto).slice(0, 160));
    ok(!String(texto).includes('Ya está vendida'),
      'y ya NO muestra el "Ya está vendida" a secas, que no decia quien');
  } else {
    console.log('  info  no hay ninguna caseta vendida con vendedor: se salta esa parte');
  }

  if (tramite) {
    const texto = await textoDeFicha(tramite.puestoId);
    ok(String(texto).includes('La está registrando'), 'una en trámite dice "La está registrando"',
      String(texto).slice(0, 160));
    ok(String(texto).includes(tramite.vendedor), `nombra a ${tramite.vendedor}`, String(texto).slice(0, 160));
  } else {
    console.log('  info  no hay ninguna caseta en trámite de otro: se salta esa parte');
  }

  // El bloque nuevo mete texto y un boton de llamar dentro de la ficha: si algo de eso no
  // cupiera, la pagina empezaria a desplazarse de lado y en un telefono eso es inusable.
  const desborda = await evaluar(
    'document.documentElement.scrollWidth > document.documentElement.clientWidth + 1');
  ok(desborda === false, 'el mapa no se desplaza de lado');

  // ---- el avance, en Seguimiento en vivo ----
  //
  // Se informa un avance a mano en vez de rellenar el formulario: lo que se prueba aqui es la
  // PANTALLA que lo lee. Que el formulario calcule bien el porcentaje es otra cosa, y se
  // comprueba con el resto de la logica.
  /*
   * ---- el avance, en Seguimiento en vivo ----
   *
   * El avance lo informa OTRO usuario, y no quien mira, porque son roles distintos y mezclarlos
   * hacia que la prueba no probara nada: al abrir /seguimiento, la propia aplicacion late
   * diciendo que esta en 'seguimiento', y eso —correctamente— borra el avance del que mira.
   * Admin1 se estaba observando a si mismo y se limpiaba su propio dato.
   */
  console.log('\nSeguimiento en vivo');
  const API = SPA; // el proxy de Vite manda /api al backend
  const jsonDe = async (r) => { try { return await r.json(); } catch { return null; } };
  const comoAdmin = { Authorization: `Bearer ${await evaluar('localStorage.getItem("token")')}` };

  const marca = Date.now().toString().slice(-6);
  const roles = await jsonDe(await fetch(`${API}/api/app/roles`, { headers: comoAdmin }));
  const rolVendedor = (roles || []).find((r) => r.nombre === 'ADMINISTRATIVO');
  const alta = await jsonDe(await fetch(`${API}/api/app/usuarios`, {
    method: 'POST', headers: { ...comoAdmin, 'Content-Type': 'application/json' },
    body: JSON.stringify({
      username: `segui${marca}`, password: 'ClaveSegui9', rolId: rolVendedor?.id, personaId: null,
      persona: { nombre: 'SEGUIDO', paterno: 'PRUEBA', materno: '', ci: `SP${marca}`, correo: '', celular: '' },
    }),
  }));
  const idVendedor = alta?.usuario?.id;
  const sesionVendedor = await jsonDe(await fetch(`${API}/api/auth/login`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ usuario: `segui${marca}`, contrasena: 'ClaveSegui9' }),
  }));
  const comoVendedor = { Authorization: `Bearer ${sesionVendedor?.token}` };
  ok(Boolean(idVendedor && sesionVendedor?.token), 'se crea un vendedor de prueba que informe avance');

  // La barra solo sale con una venta EMPEZADA, y eso son casetas tomadas de verdad en la base.
  const puestosAhora = await jsonDe(await fetch(`${API}/api/app/puestos`, { headers: comoAdmin }));
  const libre = (puestosAhora || []).find((p) => p.estado === 'L');
  // Hay que HABILITARSELA antes: un ADMINISTRATIVO solo reserva lo que se le selecciono, y el
  // servidor lo comprueba en la escritura. Sin esto la reserva responde 409, que es correcto.
  await fetch(`${API}/api/app/vendedores/${idVendedor}/puestos`, {
    method: 'PUT', headers: { ...comoAdmin, 'Content-Type': 'application/json' },
    body: JSON.stringify({ puestoIds: [libre?.id] }),
  });
  const rRes = await fetch(`${API}/api/app/puestos/${libre?.id}/reservar`, { method: 'POST', headers: comoVendedor });
  const reservada = rRes.ok ? libre.id : 0;
  ok(Number(reservada) > 0, 'el vendedor de prueba reserva una caseta', `HTTP ${rRes.status}`);

  await fetch(`${API}/api/app/presencia`, {
    method: 'POST', headers: { ...comoVendedor, 'Content-Type': 'application/json' },
    body: JSON.stringify({
      pantalla: 'venta', titulo: 'Registrar venta', origen: 'WEB',
      avance: 60, avancePaso: 'Responsables', avanceFaltan: 'C.I. del Responsable 1',
    }),
  });

  await evaluar(`location.href = ${JSON.stringify(SPA + '/seguimiento')}`);
  await esperar(4000);

  const pantalla = await evaluar('document.body.innerText');
  ok(!String(pantalla).includes('No se pudo leer el seguimiento'), 'la pantalla carga');
  ok(String(pantalla).includes('casetas en trámite'), 'sale el contador de casetas en trámite');
  ok(String(pantalla).includes('casetas vendidas'), 'sale el contador de vendidas');

  // Solo se pinta la barra si quien mira tiene ademas casetas tomadas: sin venta empezada no
  // hay avance que seguir. Por eso se comprueba el ancho solo cuando el bloque existe.
  const barra = await evaluar(`(() => {
    const b = document.querySelector('.barra-avance span');
    if (!b) return 'sin barra';
    return b.style.width;
  })()`);
  ok(barra !== 'sin barra', 'con una venta empezada, se pinta la barra de avance');
  ok(barra === '60%', 'la barra refleja el porcentaje informado', String(barra));
  ok(String(pantalla).includes('60%'), 'y el porcentaje se lee en numero');
  ok(String(pantalla).includes('C.I. del Responsable 1'), 'dice lo que falta por llenar');
  ok(String(pantalla).includes('Responsables'), 'y en que paso va');

  const desbordaSeg = await evaluar(
    'document.documentElement.scrollWidth > document.documentElement.clientWidth + 1');
  ok(desbordaSeg === false, 'seguimiento tampoco se desplaza de lado');

  // Deja la base como estaba. Una caseta reservada y olvidada queda bloqueada para los demas
  // hasta que venza, y un vendedor de prueba que sobrevive a la prueba se queda con una clave
  // conocida escrita en un guion del repositorio.
  if (Number(reservada) > 0) {
    const solto = await fetch(`${API}/api/app/puestos/${Number(reservada)}/liberar`,
      { method: 'POST', headers: comoVendedor });
    ok(solto.ok, 'suelta la caseta que habia tomado para la prueba');
  }
  if (idVendedor) {
    const borrado = await fetch(`${API}/api/app/usuarios/${idVendedor}`,
      { method: 'DELETE', headers: comoAdmin });
    ok(borrado.ok, 'y borra el vendedor de prueba', `HTTP ${borrado.status}`);
  }

  console.log(`\n${fallos === 0 ? 'Todo bien.' : `${fallos} comprobacion(es) fallidas.`}\n`);
} finally {
  await cerrar();
}

process.exit(fallos === 0 ? 0 : 1);
