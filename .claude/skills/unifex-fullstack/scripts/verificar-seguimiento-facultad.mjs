/**
 * Seguimiento por facultad: quien vendio que en un area academica.
 *
 * Tres cosas que comprobar, y la primera es la razon de ser del modulo:
 *
 *   1. NO SALE NINGUN IMPORTE. Ni precios ni totales en bolivianos, en ninguna parte de la
 *      respuesta. Se comprueba sobre el JSON crudo y no campo a campo: un campo nuevo que
 *      alguien añada mañana tiene que caer aqui.
 *   2. Cada quien ve SU facultad. Si el area viajara en la peticion, cambiar un numero en la
 *      URL bastaria para mirar otra; el servidor la resuelve desde el usuario.
 *   3. Los vendedores que NO han vendido tambien salen: es justo lo que un seguimiento sirve
 *      para ver, y con un JOIN ingenuo desaparecen.
 *
 *   node .claude/skills/unifex-fullstack/scripts/verificar-seguimiento-facultad.mjs \
 *        --base http://localhost:7676
 */
const arg = (n, d) => { const i = process.argv.indexOf('--' + n); return i > 0 ? process.argv[i + 1] : d; };
const B = arg('base', 'http://localhost:7676');
const marca = Date.now().toString().slice(-6);

let fallos = 0;
const paso = (c, m, e = '') => { console.log(`  [${c ? 'OK   ' : 'FALLA'}] ${m}${e ? '  -> ' + e : ''}`); if (!c) fallos++; };
const titulo = (t) => console.log(`\n${t}`);
const j = async (r) => { try { return await r.json(); } catch { return null; } };
const login = async (u, c) => (await j(await fetch(`${B}/api/auth/login`, { method: 'POST',
  headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ usuario: u, contrasena: c }) }))) || {};
const api = async (t, ruta, o = {}) => { const r = await fetch(B + ruta, { ...o,
  headers: { Authorization: `Bearer ${t}`, ...(o.body ? { 'Content-Type': 'application/json' } : {}) } });
  const texto = await r.text();
  let cuerpo = texto; try { cuerpo = JSON.parse(texto); } catch { /* no-JSON */ }
  return { estado: r.status, cuerpo, texto };
};

console.log(`\n== Seguimiento por facultad (${B}) ==`);
const T = (await login('admin1', arg('clave', 'VO7xGroB8ag2Qz1B'))).token;
if (!T) { console.log('\nNo se pudo entrar como admin1\n'); process.exit(1); }

// ---------------------------------------------------------------- preparacion
const areas = (await api(T, '/api/app/areas')).cuerpo || [];
paso(Array.isArray(areas) && areas.length > 0, 'hay areas academicas que seguir',
     areas.map((a) => a.sigla).join(', '));
const area = areas[0];
const carrera = (area?.carreras || [])[0];
paso(!!carrera, 'y el area tiene carreras', carrera?.nombre);

// Un vendedor de esa carrera, para que el informe tenga a alguien.
const rolAdm = ((await api(T, '/api/app/roles')).cuerpo || []).find((r) => r.nombre === 'ADMINISTRATIVO');
const usuario = `zsf${marca}`;
let r = await api(T, '/api/app/usuarios', { method: 'POST', body: JSON.stringify({
  username: usuario, password: 'ClaveSeguim9', rolId: rolAdm.id, personaId: null,
  persona: { nombre: 'ZZ SEGUIM', paterno: 'PRUEBA', materno: '', ci: `ZS${marca}`,
             correo: '', celular: '', carreraId: carrera.id } }) });
const vendedorId = r.cuerpo?.usuario?.id;
paso(!!vendedorId, 'se crea un vendedor de esa carrera', `id=${vendedorId}`);

// ---------------------------------------------------------------- 1. sin importes
titulo('1. El informe NO lleva ni un importe');
r = await api(T, `/api/app/seguimiento-facultad?areaId=${area.id}`);
paso(r.estado === 200, 'el informe responde', `HTTP ${r.estado}`);

/*
 * Se busca sobre el JSON CRUDO. Comprobar campo a campo solo cubriria los que existen hoy; asi,
 * un `costo` o un `totalBs` que alguien añada mañana al DTO hace fallar esta prueba.
 */
const sospechosos = ['costo', 'precio', 'total_bs', 'totalbs', 'monto', 'importe', 'bs"'];
const encontrados = sospechosos.filter((k) => r.texto.toLowerCase().includes(k));
paso(encontrados.length === 0, 'no aparece ningun campo de dinero en la respuesta',
     encontrados.length ? `aparece: ${encontrados.join(', ')}` : 'ni costo, ni precio, ni totales');

const primer = (r.cuerpo?.vendedores || [])[0];
paso(!!r.cuerpo?.sigla && typeof r.cuerpo?.totalCasetas === 'number',
     'si lleva lo que se pidio: facultad y casetas vendidas',
     `${r.cuerpo?.sigla} · ${r.cuerpo?.totalCasetas} caseta(s)`);
if (primer) {
  paso('nombre' in primer && 'porCategoria' in primer && 'casetasVendidas' in primer,
       'y por vendedor: nombre, categorias y cantidad',
       Object.keys(primer).join(', '));
}

/*
 * Administracion NO tiene area asignada —asigna, no monitorea—, asi que al abrir el modulo sin
 * pedir ninguna tiene que entrar viendo la primera del catalogo. Antes le salia "todavia no
 * tienes una facultad asignada", que a quien puede verlas todas no le dice nada util.
 */
r = await api(T, '/api/app/seguimiento-facultad');
paso(!r.cuerpo?.sinAsignar && !!r.cuerpo?.areaId,
     'administracion entra viendo una facultad, sin tener ninguna asignada',
     r.cuerpo?.sigla || r.cuerpo?.mensaje);

// ---------------------------------------------------------------- 2. cada quien su facultad
titulo('2. Quien monitorea ve SOLO su facultad');
const V = (await login(usuario, 'ClaveSeguim9')).token;

r = await api(V, '/api/app/seguimiento-facultad');
paso(r.cuerpo?.sinAsignar === true,
     'sin facultad asignada se le dice con palabras, no con un informe vacio',
     r.cuerpo?.mensaje);

r = await api(T, `/api/app/seguimiento-facultad/asignaciones/${vendedorId}`,
  { method: 'PUT', body: JSON.stringify({ areaId: area.id }) });
paso(r.estado === 200, 'administracion le asigna una facultad', `HTTP ${r.estado}`);

r = await api(V, '/api/app/seguimiento-facultad');
paso(r.cuerpo?.areaId === area.id, 'ahora si ve la suya', r.cuerpo?.sigla);

// Lo importante: pedir OTRA por la URL no se la da.
const otra = areas.find((a) => a.id !== area.id);
if (otra) {
  r = await api(V, `/api/app/seguimiento-facultad?areaId=${otra.id}`);
  paso(r.cuerpo?.areaId === area.id,
       'y pedir otra facultad por la URL NO se la da',
       `pidio ${otra.sigla}, recibio ${r.cuerpo?.sigla}`);
}

r = await api(V, '/api/app/seguimiento-facultad/asignaciones');
paso(r.estado === 403, 'no puede tocar las asignaciones de nadie', `HTTP ${r.estado}`);

// ---------------------------------------------------------------- 3. los que no vendieron
titulo('3. Los que no han vendido tambien salen');
r = await api(T, `/api/app/seguimiento-facultad?areaId=${area.id}`);
const nuestro = (r.cuerpo?.vendedores || []).find((v) => v.usuarioId === vendedorId);
paso(!!nuestro, 'el vendedor recien creado aparece aunque no haya vendido nada',
     nuestro ? `${nuestro.nombre} · ${nuestro.casetasVendidas} caseta(s)` : '(no aparece)');
paso(nuestro?.casetasVendidas === 0 && nuestro?.porCategoria?.length === 0,
     'y aparece con cero, no inventando ventas');

// ---------------------------------------------------------------- 4. una venta de verdad
titulo('4. Una venta suya sale con su categoria y sus casetas');
const libres = ((await api(T, '/api/app/puestos')).cuerpo || []).filter((p) => p.estado === 'L');
const tipo = ((await api(T, '/api/app/catalogos/tipos-entidad')).cuerpo || [])[0];
let insId = null;
if (libres.length >= 2 && tipo) {
  const lote = libres.slice(0, 2).map((p) => p.id);
  await api(T, '/api/app/vendedores/' + vendedorId + '/puestos',
    { method: 'PUT', body: JSON.stringify({ puestoIds: lote }) });
  await api(V, '/api/app/puestos/carrito', { method: 'POST', body: JSON.stringify({ ids: lote }) });
  r = await api(V, '/api/app/inscripciones', { method: 'POST', body: JSON.stringify({
    entidadNombre: `ZZ FACULTAD ${marca}`, nit: '', descripcion: 'P', objeto: '',
    representanteLegal: 'REP', ciRepresentante: `RF${marca}`, celularRepresentante: '59170000000',
    tipoEntidadId: tipo.id, fechaInicio: null, fechaFin: null,
    responsables: [{ nombre: 'UNO', paterno: 'PRUEBA', materno: '', ci: `RU${marca}`,
                     celular: '59170000001', correo: null }],
    entidadBancaria: '', numComprobante: null, pagoContado: true, puestos: lote }) });
  insId = r.cuerpo?.inscripcionId;
  paso(!!insId, 'el vendedor registra una venta de 2 casetas', `HTTP ${r.estado}`);

  r = await api(V, '/api/app/seguimiento-facultad');
  const mio = (r.cuerpo?.vendedores || []).find((v) => v.usuarioId === vendedorId);
  paso(mio?.casetasVendidas === 2, 'el seguimiento lo cuenta', `${mio?.casetasVendidas} caseta(s)`);
  const cat = (mio?.porCategoria || [])[0];
  paso(!!cat?.categoria && cat?.casetas?.length === cat?.cantidad,
       'con su categoria, los numeros de caseta y la cantidad',
       cat ? `${cat.categoria}: ${cat.casetas.join(', ')} (${cat.cantidad})` : '(sin categoria)');

  // Y tras la venta, el informe SIGUE sin importes: es cuando mas facil seria colarlos.
  const sucios = sospechosos.filter((k) => r.texto.toLowerCase().includes(k));
  paso(sucios.length === 0, 'y con ventas de por medio tampoco aparece ningun importe',
       sucios.join(', '));
}

// ---------------------------------------------------------------- limpieza
titulo('Limpieza');
if (insId) {
  await api(T, `/api/app/inscripciones/${insId}/cancelar`, { method: 'POST',
    body: JSON.stringify({ motivo: 'Prueba de seguimiento por facultad' }) });
}
r = await api(T, `/api/app/usuarios/${vendedorId}`, { method: 'DELETE' });
paso(r.estado === 200, 'el vendedor de prueba se da de baja', `HTTP ${r.estado}`);

console.log(fallos ? `\n${fallos} paso(s) fallaron.\n` : '\nTodo paso.\n');
process.exit(fallos ? 1 : 0);
