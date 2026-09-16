/*
 * Los cuatro analisis de direccion, comprobados contra una venta de verdad.
 *
 * Un reporte que devuelve una lista bonita pero con los numeros mal no da ningun error: se
 * descubre cuando alguien decide algo con el. Por eso esto NO comprueba que responda 200: hace
 * una venta con importes conocidos y comprueba que cada analisis la cuente **exactamente**.
 *
 * Cubre ademas los dos errores que este repositorio ya ha cometido con los listados:
 *   - no filtrar por EDICION (mezclaria los años en cuanto exista otra edicion);
 *   - contar las ventas ANULADAS (una cancelacion tiene que desaparecer de los totales).
 *
 * Y quien puede mirarlos: un vendedor no entra (ahi esta el ranking de sus compañeros).
 *
 * Deja la base como estaba: cancela su venta y borra su vendedor.
 *
 *   node .claude/skills/unifex-fullstack/scripts/verificar-analisis.mjs
 *   opciones: --base http://localhost:7676 --usuario admin1 --clave 'usuario25$'
 */

const args = process.argv.slice(2);
const opcion = (n, d) => { const i = args.indexOf(`--${n}`); return i >= 0 && args[i + 1] ? args[i + 1] : d; };

const BASE = opcion('base', 'http://localhost:7676');
const USUARIO = opcion('usuario', 'admin1');
const CLAVE = opcion('clave', 'usuario25$');
const marca = Date.now().toString().slice(-6);

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

const num = (v) => Number(v || 0);

async function main() {
  console.log(`\nAnalisis de direccion · ${BASE}\n`);

  const login = await j(await fetch(`${BASE}/api/auth/login`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ usuario: USUARIO, contrasena: CLAVE }),
  }));
  if (!login?.token) { console.error('No se pudo entrar. Usa --usuario/--clave.'); process.exit(1); }
  T = login.token;

  // ---- foto de partida ----
  const antes = (await api('/api/app/analisis')).cuerpo;
  ok(Boolean(antes), 'GET /api/app/analisis responde');
  ok(Array.isArray(antes?.ocupacion) && Array.isArray(antes?.vendedores)
     && Array.isArray(antes?.avance) && antes?.cobros, 'trae los cuatro analisis en UNA peticion',
     JSON.stringify(Object.keys(antes || {})));

  // La foto de facultad ANTES de vender: luego se comprueba que la venta suba exactamente una.
  const facAntes = (await api('/api/app/analisis/facultad')).cuerpo || [];

  const sumaVendidas = (a) => (a.ocupacion || []).reduce((s, o) => s + o.vendidas, 0);
  const sumaBs = (a) => (a.ocupacion || []).reduce((s, o) => s + num(o.bsVendido), 0);
  const cobradoDe = (a) => num(a.cobros?.totalCobrado);
  const pendienteDe = (a) => num(a.cobros?.totalPendiente);

  // ---- coherencia interna: el total es la suma del desglose ----
  console.log('\nCoherencia');
  const totalOcup = (antes.ocupacion || []).reduce((s, o) =>
    s + o.vendidas + o.enTramite + o.libres + o.bloqueadas, 0);
  const totalDeclarado = (antes.ocupacion || []).reduce((s, o) => s + o.total, 0);
  ok(totalOcup === totalDeclarado, 'en ocupacion, los cuatro estados suman el total de cada categoria',
    `${totalOcup} vs ${totalDeclarado}`);

  const sumaCortes = (antes.cobros?.cortes || []).reduce((s, c) => s + num(c.totalBs), 0);
  ok(Math.abs(sumaCortes - (cobradoDe(antes) + pendienteDe(antes))) < 0.01,
    'en cobros, cobrado + pendiente es la suma de los cortes',
    `cortes=${sumaCortes} cobrado=${cobradoDe(antes)} pendiente=${pendienteDe(antes)}`);

  const av = antes.avance || [];
  if (av.length > 1) {
    const ultimo = av[av.length - 1];
    const sumaDias = av.reduce((s, d) => s + num(d.totalBs), 0);
    ok(Math.abs(num(ultimo.acumuladoBs) - sumaDias) < 0.01,
      'en avance, el acumulado del ultimo dia es la suma de todos',
      `${ultimo.acumuladoBs} vs ${sumaDias}`);
  }

  // ---- una venta de verdad, con importe conocido ----
  console.log('\nUna venta real entra en los cuatro analisis');
  const puestos = (await api('/api/app/puestos')).cuerpo || [];
  const libre = puestos.find((p) => p.estado === 'L' && p.categoriaId != null);
  ok(Boolean(libre), 'hay una caseta libre para la prueba');
  if (!libre) throw new Error('sin caseta no hay prueba');

  // Precio propio inconfundible: si el analisis suma otra cosa, se ve a simple vista.
  const PRECIO = 1777;
  await api('/api/app/puestos/precios', {
    method: 'PATCH', body: JSON.stringify([{ id: libre.id, precio: PRECIO }]),
  });

  const tipos = (await api('/api/app/inscripciones/tipos-entidad')).cuerpo;
  const tipoId = Array.isArray(tipos) && tipos.length ? (tipos[0].id ?? tipos[0].idTipoEntidad) : 1;
  await api(`/api/app/puestos/${libre.id}/reservar`, { method: 'POST' });

  const venta = await api('/api/app/inscripciones', {
    method: 'POST',
    body: JSON.stringify({
      puestos: [libre.id],
      entidadNombre: `ZZ ANALISIS ${marca}`, nit: '', descripcion: 'P', objeto: '',
      representanteLegal: 'REP ANALISIS', ciRepresentante: `RA${marca}`, celularRepresentante: '59170000000',
      tipoEntidadId: tipoId, fechaInicio: null, fechaFin: null,
      entidadBancaria: '', numComprobante: null, pagoContado: true,
      responsables: [{ nombre: 'PRUEBA', paterno: 'ANALISIS', materno: '', ci: `AN${marca}`,
                       celular: '59170000001', correo: null }],
    }),
  });
  const insId = venta.cuerpo?.inscripcionId;
  ok(venta.estado === 200 && !!insId, 'se registra la venta de prueba',
    `HTTP ${venta.estado} ${JSON.stringify(venta.cuerpo).slice(0, 150)}`);
  if (!insId) throw new Error('sin venta no se puede comprobar nada');
  ok(num(venta.cuerpo?.total) === PRECIO, `la venta cuesta ${PRECIO} Bs`, `total=${venta.cuerpo?.total}`);

  const despues = (await api('/api/app/analisis')).cuerpo;

  ok(sumaVendidas(despues) === sumaVendidas(antes) + 1,
    'ocupacion: una caseta vendida mas',
    `${sumaVendidas(antes)} -> ${sumaVendidas(despues)}`);
  ok(Math.abs(sumaBs(despues) - (sumaBs(antes) + PRECIO)) < 0.01,
    `ocupacion: ${PRECIO} Bs mas de vendido`,
    `${sumaBs(antes)} -> ${sumaBs(despues)}`);

  const yo = (despues.vendedores || []).find((v) => Number(v.usuarioId) === Number(login.id));
  ok(Boolean(yo), 'vendedores: quien vendio aparece en el ranking');
  const yoAntes = (antes.vendedores || []).find((v) => Number(v.usuarioId) === Number(login.id));
  ok(num(yo?.totalBs) === num(yoAntes?.totalBs) + PRECIO,
    `vendedores: su total sube ${PRECIO} Bs`, `${num(yoAntes?.totalBs)} -> ${num(yo?.totalBs)}`);
  ok(num(yo?.ventas) === num(yoAntes?.ventas) + 1, 'vendedores: una venta mas');
  ok(num(yo?.casetas) === num(yoAntes?.casetas) + 1, 'vendedores: una caseta mas');
  // Una venta de UNA caseta: ventas y casetas suben lo mismo. Si el codigo contara cada
  // caseta como una venta, el ticket medio saldria mal en las ventas de varias.
  ok(num(yo?.ticketMedio) > 0, 'vendedores: el ticket medio sale calculado');

  // Contado SIN comprobante: es el caso que estuvo contando como pagado y no lo esta.
  ok(pendienteDe(despues) === pendienteDe(antes) + PRECIO,
    'cobros: marcada "contado" pero SIN comprobante, cuenta como PENDIENTE',
    `${pendienteDe(antes)} -> ${pendienteDe(despues)}`);
  ok(cobradoDe(despues) === cobradoDe(antes),
    'cobros: y NO como cobrado (el comprobante manda sobre la forma de pago)');
  ok((despues.cobros?.pendientes || []).some((p) => Number(p.inscripcionId) === Number(insId)),
    'cobros: la venta sale en el detalle de pendientes');

  /*
   * Se mira el ULTIMO dia de la serie, no "hoy" calculado aqui.
   *
   * La primera version hacia `new Date().toISOString().slice(0,10)`, que da la fecha en UTC,
   * mientras que la base agrupa por `date(fecha_compra)` en la zona del servidor. En Bolivia
   * (UTC-4) eso las descuadra un dia entero a partir de las 20:00, y la prueba empezaba a
   * fallar sola por la tarde sin que nada estuviera roto. La venta acaba de hacerse, asi que
   * tiene que estar en el dia mas reciente: eso es cierto en cualquier zona horaria.
   */
  const dias = despues.avance || [];
  const ultimoDia = dias[dias.length - 1];
  const antesUltimo = (antes.avance || []).find((d) => d.fecha === ultimoDia?.fecha);
  ok(Boolean(ultimoDia), 'avance: hay al menos un dia con ventas',
    `dias: ${dias.map((d) => d.fecha).join(', ')}`);
  ok(num(ultimoDia?.totalBs) === num(antesUltimo?.totalBs) + PRECIO,
    `avance: el ultimo dia sube ${PRECIO} Bs`,
    `${num(antesUltimo?.totalBs)} -> ${num(ultimoDia?.totalBs)} (dia ${ultimoDia?.fecha})`);

  // ---- venta por facultad ----
  //
  // El area sale de la CARRERA del vendedor (V35), y las carreras se asignan a mano. Mientras
  // nadie lo haga, todo cae en "SIN CARRERA": eso no es un fallo del reporte, pero tiene que
  // cuadrar igual con el total de la feria, que es lo unico que no puede fallar nunca.
  console.log('\nVenta por facultad');
  const fac = (await api('/api/app/analisis/facultad')).cuerpo || [];
  ok(Array.isArray(fac) && fac.length > 0, 'devuelve al menos una fila',
    JSON.stringify(fac).slice(0, 150));

  const bsFac = fac.reduce((s, f) => s + num(f.totalBs), 0);
  ok(Math.abs(bsFac - sumaBs(despues)) < 0.01,
    'la suma por facultad cuadra con el total vendido de la feria',
    `facultad=${bsFac} vs ocupacion=${sumaBs(despues)}`);

  const pctFac = fac.reduce((s, f) => s + num(f.porcentaje), 0);
  ok(Math.abs(pctFac - 100) < 0.5, 'los porcentajes suman 100', `suman ${pctFac}`);

  /*
   * La venta tiene que caer en UNA sola facultad y subirla exactamente en PRECIO.
   *
   * Se compara contra la foto previa en vez de buscar la fila "SIN CARRERA". Esa version daba
   * por hecho que quien corre la prueba no tiene carrera asignada, y en cuanto se le asigna
   * una —que es justo lo que hay que hacer para que este reporte sirva de algo— la prueba
   * fallaba sola sin que nada estuviera roto. Asi vale en los dos casos, y ademas comprueba lo
   * que de verdad importa: que la venta se atribuya a su facultad, sin repartirse ni perderse.
   */
  const crecieron = fac.filter((f) => {
    const previo = facAntes.find((x) => x.sigla === f.sigla);
    return Math.abs(num(f.totalBs) - num(previo?.totalBs)) > 0.01;
  });
  ok(crecieron.length === 1, 'la venta sube UNA sola facultad, no varias',
    `cambiaron ${crecieron.length}: ${crecieron.map((f) => f.sigla).join(', ')}`);
  if (crecieron.length === 1) {
    const f = crecieron[0];
    const previo = facAntes.find((x) => x.sigla === f.sigla);
    ok(Math.abs(num(f.totalBs) - (num(previo?.totalBs) + PRECIO)) < 0.01,
      `y la sube exactamente ${PRECIO} Bs (facultad ${f.sigla})`,
      `${num(previo?.totalBs)} -> ${num(f.totalBs)}`);
  }

  const siglasValidas = fac.every((f) => f.sigla && f.sigla.trim().length > 0);
  ok(siglasValidas, 'ninguna fila sale sin etiqueta (quien no tiene carrera va a «SIN CARRERA»)',
    `siglas: ${fac.map((f) => f.sigla).join(', ')}`);

  const casetasFac = fac.reduce((s, f) => s + num(f.casetas), 0);
  ok(casetasFac === sumaVendidas(despues),
    'las casetas por facultad cuadran con las vendidas',
    `${casetasFac} vs ${sumaVendidas(despues)}`);

  // ---- puestos vendidos por categoria ----
  console.log('\nPuestos vendidos por categoria');
  const porCat = (await api('/api/app/analisis/vendido-categoria')).cuerpo || [];
  ok(porCat.length === (despues.ocupacion || []).length,
    'trae TODAS las categorias, tambien las que no vendieron nada',
    `${porCat.length} vs ${(despues.ocupacion || []).length}`);
  ok(porCat.reduce((s, c) => s + num(c.vendidas), 0) === sumaVendidas(despues),
    'las vendidas cuadran con la ocupacion (sale del mismo calculo)');
  ok(Math.abs(porCat.reduce((s, c) => s + num(c.totalBs), 0) - sumaBs(despues)) < 0.01,
    'y el dinero tambien');
  const ordenado = porCat.every((c, i) => i === 0 || num(porCat[i - 1].totalBs) >= num(c.totalBs));
  ok(ordenado, 'viene ordenado por importe, de mas a menos');

  // ---- quien puede mirar ----
  console.log('\nQuien puede mirarlo');
  const sinToken = await fetch(`${BASE}/api/app/analisis`);
  ok(sinToken.status === 401, 'sin token responde 401 (no un 302 al login)', `dio ${sinToken.status}`);

  const roles = (await api('/api/app/roles')).cuerpo || [];
  const rolVendedor = roles.find((r) => r.nombre === 'ADMINISTRATIVO');
  const alta = (await api('/api/app/usuarios', {
    method: 'POST',
    body: JSON.stringify({
      username: `anal${marca}`, password: 'ClaveAnalisis9', rolId: rolVendedor?.id, personaId: null,
      persona: { nombre: 'ANALISIS', paterno: 'PRUEBA', materno: '', ci: `AP${marca}`, correo: '', celular: '' },
    }),
  })).cuerpo;
  const vendedorId = alta?.usuario?.id;
  const sesionV = await j(await fetch(`${BASE}/api/auth/login`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ usuario: `anal${marca}`, contrasena: 'ClaveAnalisis9' }),
  }));
  const comoVendedor = await fetch(`${BASE}/api/app/analisis`, {
    headers: { Authorization: `Bearer ${sesionV?.token}` },
  });
  ok(comoVendedor.status === 403,
    'un ADMINISTRATIVO NO entra: aqui esta el ranking de sus compañeros', `dio ${comoVendedor.status}`);

  // ---- cancelar tiene que sacarla de los totales ----
  console.log('\nUna venta cancelada desaparece de los totales');
  const cancel = await api(`/api/app/inscripciones/${insId}/cancelar`, {
    method: 'POST', body: JSON.stringify({ motivo: 'prueba automatica de analisis' }),
  });
  ok(cancel.estado === 200, 'se cancela la venta de prueba', `HTTP ${cancel.estado}`);

  const tras = (await api('/api/app/analisis')).cuerpo;
  ok(Math.abs(sumaBs(tras) - sumaBs(antes)) < 0.01,
    'ocupacion: el importe vuelve a donde estaba', `${sumaBs(antes)} vs ${sumaBs(tras)}`);
  ok(pendienteDe(tras) === pendienteDe(antes),
    'cobros: el pendiente vuelve a donde estaba', `${pendienteDe(antes)} vs ${pendienteDe(tras)}`);
  ok(!(tras.cobros?.pendientes || []).some((p) => Number(p.inscripcionId) === Number(insId)),
    'cobros: ya no sale en el detalle de pendientes');

  // ---- descargas ----
  console.log('\nDescargas');
  for (const nombre of ['ocupacion', 'vendedores', 'cobros', 'avance', 'facultad', 'vendido-categoria']) {
    for (const formato of ['pdf', 'excel']) {
      const r = await fetch(`${BASE}/api/app/analisis/${nombre}/${formato}`, {
        headers: { Authorization: `Bearer ${T}` },
      });
      const buf = await r.arrayBuffer();
      const firma = new Uint8Array(buf.slice(0, 4));
      // Se mira la FIRMA del archivo, no solo el tamaño: un HTML de error tambien pesa.
      const esPdf = firma[0] === 0x25 && firma[1] === 0x50; // %P
      const esZip = firma[0] === 0x50 && firma[1] === 0x4b; // PK (xlsx es un zip)
      ok(r.ok && buf.byteLength > 500 && (formato === 'pdf' ? esPdf : esZip),
        `${nombre}.${formato} se descarga y es un ${formato === 'pdf' ? 'PDF' : 'XLSX'} de verdad`,
        `HTTP ${r.status}, ${buf.byteLength} bytes`);
    }
  }
  const inventado = await fetch(`${BASE}/api/app/analisis/inventado/pdf`, {
    headers: { Authorization: `Bearer ${T}` },
  });
  ok(inventado.status === 400, 'un reporte que no existe responde 400', `dio ${inventado.status}`);

  // ---- limpieza ----
  await api('/api/app/puestos/precios', {
    method: 'PATCH', body: JSON.stringify([{ id: libre.id, precio: null }]),
  });
  if (vendedorId) await api(`/api/app/usuarios/${vendedorId}`, { method: 'DELETE' });

  console.log(`\n${fallos === 0 ? 'Todo bien.' : `${fallos} comprobacion(es) fallidas.`}\n`);
  process.exit(fallos === 0 ? 0 : 1);
}

main().catch((e) => { console.error(e); process.exit(1); });
