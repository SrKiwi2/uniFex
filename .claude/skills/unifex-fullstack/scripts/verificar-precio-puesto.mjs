/*
 * Precio propio de una caseta (V37), de punta a punta.
 *
 * El caso que lo pide: se crea la categoria con su precio y se colocan todas sus casetas;
 * despues resulta que ALGUNAS valen distinto y hay que tocar esas sin alterar las demas.
 *
 * Lo que de verdad hay que demostrar no es que el campo se guarde —eso es facil— sino que el
 * precio LLEGUE AL COBRO: que la venta congele el importe propio y no el de la categoria. Un
 * precio que se ve bonito en la pantalla y se cobra mal es peor que no tenerlo.
 *
 * Tambien comprueba las dos cosas que se rompen sin querer:
 *   - poner precio a UNA caseta no debe mover a sus vecinas de la misma categoria;
 *   - vaciar el precio debe DEVOLVERLA a la categoria (no dejarla con el importe viejo, que es
 *     lo que pasaria si el UPDATE llevara COALESCE).
 *
 * Crea su propia venta de prueba y la cancela al terminar.
 *
 *   node .claude/skills/unifex-fullstack/scripts/verificar-precio-puesto.mjs
 *   opciones: --base http://localhost:7676 --usuario admin1 --clave 'usuario25$'
 */

const args = process.argv.slice(2);
const opcion = (n, d) => { const i = args.indexOf(`--${n}`); return i >= 0 && args[i + 1] ? args[i + 1] : d; };

const BASE = opcion('base', 'http://localhost:7676');
const USUARIO = opcion('usuario', 'admin1');
const CLAVE = opcion('clave', 'usuario25$');

let fallos = 0;
const ok = (c, m, extra = '') => {
  console.log(`${c ? '  ok  ' : ' FALLA'} ${m}${!c && extra ? ` — ${extra}` : ''}`);
  if (!c) fallos++;
};
const j = async (r) => { try { return await r.json(); } catch { return null; } };

let T = null;
const api = async (ruta, o = {}) => {
  const r = await fetch(BASE + ruta, {
    ...o,
    headers: { Authorization: `Bearer ${T}`, ...(o.body ? { 'Content-Type': 'application/json' } : {}) },
  });
  return { estado: r.status, cuerpo: await j(r) };
};

const precios = (cambios) => api('/api/app/puestos/precios', { method: 'PATCH', body: JSON.stringify(cambios) });
const puestoPorId = async (id) => ((await api('/api/app/puestos')).cuerpo || []).find((p) => p.id === id);

async function main() {
  console.log(`\nPrecio propio de una caseta · ${BASE}\n`);

  const rLogin = await fetch(`${BASE}/api/auth/login`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ usuario: USUARIO, contrasena: CLAVE }),
  });
  const login = await j(rLogin);
  if (!rLogin.ok || !login?.token) {
    console.error(`No se pudo entrar como ${USUARIO} (${rLogin.status}). Usa --usuario/--clave.`);
    process.exit(1);
  }
  T = login.token;

  // Dos casetas LIBRES de la MISMA categoria: una se toca y la otra es el testigo de que las
  // demas no se mueven, que es literalmente lo que se pidio.
  const todas = (await api('/api/app/puestos')).cuerpo || [];
  const porCategoria = new Map();
  for (const p of todas) {
    if (p.estado !== 'L' || p.categoriaId == null) continue;
    if (!porCategoria.has(p.categoriaId)) porCategoria.set(p.categoriaId, []);
    porCategoria.get(p.categoriaId).push(p);
  }
  const grupo = [...porCategoria.values()].find((l) => l.length >= 2);
  if (!grupo) {
    console.error('Hacen falta dos casetas libres de la misma categoria para esta prueba.');
    process.exit(1);
  }
  const [caseta, vecina] = grupo;
  const precioCategoria = Number(caseta.precio || 0);
  const ESPECIAL = precioCategoria + 777;   // inconfundible: si sale este numero, mando el propio
  console.log(`Categoria "${caseta.categoria}" a ${precioCategoria} Bs`);
  console.log(`  caseta ${caseta.codigo} (id ${caseta.id}) -> se le pone ${ESPECIAL}`);
  console.log(`  vecina ${vecina.codigo} (id ${vecina.id}) -> no se toca\n`);

  console.log('Acceso');
  const sinToken = await fetch(`${BASE}/api/app/puestos/precios`, {
    method: 'PATCH', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify([{ id: caseta.id, precio: 1 }]),
  });
  ok(sinToken.status === 401 || sinToken.status === 403,
    'sin token no se puede cambiar un precio (ni 200, ni 302 al login)', `dio ${sinToken.status}`);

  console.log('\nGuardar el precio propio');
  const r1 = await precios([{ id: caseta.id, precio: ESPECIAL }]);
  ok(r1.estado === 200 && r1.cuerpo?.ok, 'PATCH /puestos/precios responde 200', `HTTP ${r1.estado}`);
  ok(r1.cuerpo?.cambiados === 1, 'informa una caseta cambiada', `cambiados=${r1.cuerpo?.cambiados}`);

  let tras = await puestoPorId(caseta.id);
  ok(Number(tras?.precio) === ESPECIAL, 'el listado ya devuelve el precio propio', `precio=${tras?.precio}`);
  ok(tras?.precioPropio === true, 'y lo marca como propio (la pantalla lo distingue del de categoria)');

  let testigo = await puestoPorId(vecina.id);
  ok(Number(testigo?.precio) === precioCategoria,
    'la caseta vecina NO se movio (es lo que se pidio)', `precio=${testigo?.precio}`);
  ok(testigo?.precioPropio === false, 'y sigue sin precio propio');

  console.log('\nValidaciones');
  const neg = await precios([{ id: caseta.id, precio: -5 }]);
  ok(neg.estado === 409 && !neg.cuerpo?.ok, 'un precio negativo se rechaza con 409', `HTTP ${neg.estado}`);
  ok(Number((await puestoPorId(caseta.id))?.precio) === ESPECIAL,
    'y no deja el lote a medias: el precio anterior sigue intacto');

  const dup = await precios([{ id: caseta.id, precio: 10 }, { id: caseta.id, precio: 20 }]);
  ok(dup.estado === 409, 'la misma caseta dos veces en el lote se rechaza', `HTTP ${dup.estado}`);

  const vacio = await precios([]);
  ok(vacio.estado === 409, 'un lote vacio se rechaza', `HTTP ${vacio.estado}`);

  // ---- lo que de verdad importa: que se COBRE ese precio ----
  console.log('\nQue llegue al cobro');
  const marca = Date.now().toString().slice(-6);
  const tipos = (await api('/api/app/inscripciones/tipos-entidad')).cuerpo;
  const tipoId = Array.isArray(tipos) && tipos.length ? (tipos[0].id ?? tipos[0].idTipoEntidad) : 1;

  const reserva = await api(`/api/app/puestos/${caseta.id}/reservar`, { method: 'POST' });
  ok(reserva.estado === 200, 'se reserva la caseta para la venta de prueba', `HTTP ${reserva.estado}`);

  /*
   * Se manda ADEMAS la opcion predeterminada de la categoria, a proposito.
   *
   * Es el caso que de verdad importa: si el precio propio no ganara a la opcion, aqui se
   * cobraria el de la categoria y la prueba lo cazaria. Mandarlo sin opcion no distinguiria
   * una cosa de la otra.
   */
  const cat = ((await api('/api/app/categorias')).cuerpo || []).find((c) => c.id === caseta.categoriaId);
  const predeterminada = (cat?.opciones || []).find((o) => o.predeterminada);

  const venta = await api('/api/app/inscripciones', {
    method: 'POST',
    body: JSON.stringify({
      puestos: [caseta.id],
      entidadNombre: `ZZ PRECIO ${marca}`,
      nit: '', descripcion: 'P', objeto: '',
      representanteLegal: 'REP PRECIO', ciRepresentante: `RP${marca}`, celularRepresentante: '59170000000',
      tipoEntidadId: tipoId, fechaInicio: null, fechaFin: null,
      entidadBancaria: '', numComprobante: null, pagoContado: true,
      responsables: [{ nombre: 'PRUEBA', paterno: 'PRECIO', materno: '', ci: `RX${marca}`,
                       celular: '59170000001', correo: null }],
      opcionesPorCategoria: predeterminada ? { [caseta.categoriaId]: predeterminada.id } : {},
    }),
  });

  const inscripcionId = venta.cuerpo?.inscripcionId;
  if (venta.estado !== 200 || !inscripcionId) {
    ok(false, 'se registra la venta de prueba',
      `HTTP ${venta.estado}: ${JSON.stringify(venta.cuerpo).slice(0, 200)}`);
    await api(`/api/app/puestos/${caseta.id}/liberar`, { method: 'POST' });
  } else {
    ok(true, 'se registra la venta de prueba');
    ok(Number(venta.cuerpo?.total) === ESPECIAL,
      `cobra ${ESPECIAL} Bs (el propio) y no ${predeterminada?.precio ?? precioCategoria} (el de la categoria)`,
      `total=${venta.cuerpo?.total}`);

    /*
     * Y queda CONGELADO: cambiar el precio despues no puede mover una venta ya hecha.
     *
     * Se lee por `mis-ventas` y no por `/detalle`: ese devuelve la ficha (entidad, casetas,
     * responsables) pero NO el importe, asi que no sirve para comprobar un cobro. Y se lee
     * ANTES de cancelar, porque `fn_get_inscripciones` solo cuenta casetas en estado 'O' y una
     * venta cancelada desaparece de ahi.
     */
    await precios([{ id: caseta.id, precio: ESPECIAL + 1000 }]);
    const mias = (await api('/api/app/mis-ventas')).cuerpo;
    // Los nombres salen TAL CUAL de la stored function: `id_inscripcion` y `total_costo`, no
    // `id` ni `total`. `fn_get_inscripciones` devuelve su TABLE sin renombrar nada.
    const fila = (mias?.items || []).find((v) => Number(v.id_inscripcion) === Number(inscripcionId));
    const congelado = Number(fila?.total_costo ?? NaN);
    ok(Number.isFinite(congelado), 'la venta se puede consultar para comprobar el importe',
      `no se encontro la venta ${inscripcionId} en mis-ventas`);
    ok(congelado === ESPECIAL,
      'y subir el precio despues NO cambia la venta ya hecha (el costo esta congelado)',
      `la venta dice ${congelado}, deberia decir ${ESPECIAL}`);

    const cancel = await api(`/api/app/inscripciones/${inscripcionId}/cancelar`, {
      method: 'POST', body: JSON.stringify({ motivo: 'prueba automatica de precio propio' }),
    });
    ok(cancel.estado === 200, 'la venta de prueba se cancela', `HTTP ${cancel.estado}`);
  }

  // ---- quitarlo tiene que DEVOLVER la caseta a su categoria ----
  console.log('\nQuitar el precio propio');
  const quitado = await precios([{ id: caseta.id, precio: null }]);
  ok(quitado.estado === 200 && quitado.cuerpo?.ok, 'se puede vaciar el precio', `HTTP ${quitado.estado}`);
  tras = await puestoPorId(caseta.id);
  ok(tras?.precioPropio === false, 'deja de tener precio propio');
  ok(Number(tras?.precio) === precioCategoria,
    'y vuelve al de su categoria (si se queda con el viejo, al UPDATE le sobra un COALESCE)',
    `precio=${tras?.precio}, categoria=${precioCategoria}`);

  // Un 0 NO es lo mismo que vacio, y esa distincion es la razon de que la columna sea nullable.
  console.log('\nCero no es lo mismo que vacio');
  await precios([{ id: caseta.id, precio: 0 }]);
  tras = await puestoPorId(caseta.id);
  ok(tras?.precioPropio === true && Number(tras?.precio) === 0,
    'un precio propio de 0 se respeta ("esta caseta es gratis", dicho a proposito)',
    `precio=${tras?.precio}, propio=${tras?.precioPropio}`);
  await precios([{ id: caseta.id, precio: null }]);
  ok((await puestoPorId(caseta.id))?.precioPropio === false, 'y se deja como estaba al terminar');

  console.log(`\n${fallos === 0 ? 'Todo bien.' : `${fallos} comprobacion(es) fallidas.`}\n`);
  process.exit(fallos === 0 ? 0 : 1);
}

main().catch((e) => { console.error(e); process.exit(1); });
