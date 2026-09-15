/**
 * Los tres modulos nuevos, de punta a punta contra el servidor:
 *
 *   1. CATEGORIAS con opciones de precio (V33). Que la opcion elegida sea la que se COBRA,
 *      y que no se pueda cobrar una caseta al precio de la opcion de otra categoria.
 *   2. RESPONSABLES por caseta (V34): dos por caseta, y los de mas con su cobro y comprobante.
 *   3. SEGUIMIENTO en vivo: el latido, y que la lista diga quien esta registrando una venta.
 *
 * Deja la base como estaba: cancela la venta de prueba y borra la categoria creada.
 *
 *   node .claude/skills/unifex-fullstack/scripts/verificar-modulos-nuevos.mjs --base http://localhost:7676
 */
const arg = (n, d) => { const i = process.argv.indexOf('--' + n); return i > 0 ? process.argv[i + 1] : d; };
const B = arg('base', 'http://localhost:7676');
const marca = Date.now().toString().slice(-6);

let fallos = 0;
const ok = (c, m, e = '') => { console.log(`${c ? '  OK  ' : ' FALLA'} ${m}${e ? ' :: ' + e : ''}`); if (!c) fallos++; };
const titulo = (t) => console.log(`\n${t}`);
const j = async (r) => { try { return await r.json(); } catch { return null; } };
const login = async (u, c) => (await j(await fetch(`${B}/api/auth/login`, { method: 'POST',
  headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ usuario: u, contrasena: c }) }))) || {};
const api = async (t, ruta, o = {}) => { const r = await fetch(B + ruta, { ...o,
  headers: { Authorization: `Bearer ${t}`, ...(o.body && typeof o.body === 'string' ? { 'Content-Type': 'application/json' } : {}) } });
  return { estado: r.status, cuerpo: await j(r) }; };

console.log(`\n== Modulos nuevos en ${B} ==`);
const sesion = await login('admin1', arg('clave', 'VO7xGroB8ag2Qz1B'));
const T = sesion.token;
if (!T) { console.log('\nNo se pudo entrar como admin1. ¿Clave correcta?\n'); process.exit(1); }

// ---------------------------------------------------------------- 1. categorias y opciones
titulo('1. Categorias con opciones de precio');

let r = await api(T, '/api/app/categorias');
ok(r.estado === 200 && Array.isArray(r.cuerpo), 'el catalogo responde', `HTTP ${r.estado}`);
const conOpciones = (r.cuerpo || []).filter((c) => Array.isArray(c.opciones) && c.opciones.length);
ok(conOpciones.length > 0,
   'cada categoria trae sus opciones', `${conOpciones.length} de ${r.cuerpo?.length}`);
ok((r.cuerpo || []).every((c) => !c.opciones?.length || c.opciones.some((o) => o.predeterminada)),
   'y todas tienen exactamente una predeterminada');

// Una categoria propia, para no tocar las de la feria.
r = await api(T, '/api/app/categorias', { method: 'POST', body: JSON.stringify({
  nombre: `ZZ PRUEBA ${marca}`, cantidad: 2, color: '#123456', forma: 'cuadrado',
  tamanoMapa: 0.012, precioBase: 0, tamano: '3x3' }) });
const catId = r.cuerpo?.id;
ok(!!catId, 'se crea una categoria de prueba', `id=${catId}`);

r = await api(T, '/api/app/categorias');
let cat = (r.cuerpo || []).find((c) => c.id === catId);
ok(cat?.opciones?.length === 1 && cat.opciones[0].predeterminada,
   'nace con UNA opcion, y es la predeterminada', cat?.opciones?.[0]?.nombre);

// La opcion basica, con precio.
const base = cat.opciones[0];
await api(T, `/api/app/categorias/${catId}/opciones/${base.id}`,
  { method: 'PATCH', body: JSON.stringify({ precio: 800 }) });

// La segunda, mas cara.
r = await api(T, `/api/app/categorias/${catId}/opciones`,
  { method: 'POST', body: JSON.stringify({ nombre: 'CON TARIMA', precio: 1200 }) });
const opCara = r.cuerpo?.opcion?.id;
ok(r.estado === 200 && !!opCara, 'se agrega una segunda opcion mas cara', `id=${opCara}`);

r = await api(T, '/api/app/categorias');
cat = (r.cuerpo || []).find((c) => c.id === catId);
ok(cat.opciones.length === 2, 'la categoria queda con dos opciones');
ok(Number(cat.precioBase) === 800,
   'el precio de la categoria sigue al de la predeterminada', `precioBase=${cat.precioBase}`);

// La predeterminada no se puede quitar sin nombrar sucesora: si no, la categoria se
// quedaria sin precio por defecto y sus ventas saldrian en cero sin avisar.
r = await api(T, `/api/app/categorias/${catId}/opciones/${base.id}`, { method: 'DELETE' });
ok(r.estado === 409, 'no se puede quitar la predeterminada sin sucesora', `HTTP ${r.estado}`);

// ---------------------------------------------------------------- 2. la venta cobra la opcion
titulo('2. La venta cobra la opcion elegida');

const puestos = (await api(T, '/api/app/puestos')).cuerpo || [];
const mias = puestos.filter((p) => p.categoriaId === catId && p.estado === 'L');
ok(mias.length >= 2, 'la categoria trajo sus casetas', `${mias.length} libres`);

const tipo = ((await api(T, '/api/app/catalogos/tipos-entidad')).cuerpo || [])[0];
const lote = mias.slice(0, 2).map((p) => p.id);
await api(T, '/api/app/puestos/carrito', { method: 'POST', body: JSON.stringify({ ids: lote }) });

const cuerpoVenta = (opciones, responsables) => JSON.stringify({
  entidadNombre: `ZZ OPCIONES ${marca}`, nit: '', descripcion: 'P', objeto: '',
  representanteLegal: 'REP PRUEBA', ciRepresentante: `RP${marca}`, celularRepresentante: '59170000000',
  tipoEntidadId: tipo?.id, fechaInicio: null, fechaFin: null,
  responsables, entidadBancaria: '', numComprobante: null, pagoContado: true,
  puestos: lote, opcionesPorCategoria: opciones,
});

const persona = (n) => ({ nombre: `RESP${n}`, paterno: 'PRUEBA', materno: '', ci: `RX${marca}${n}`,
                          celular: '59170000001', correo: null });

// Un id de opcion de OTRA categoria: el servidor tiene que rechazarlo, no cobrar ese precio.
const otraCat = (await api(T, '/api/app/categorias')).cuerpo.find((c) => c.id !== catId && c.opciones?.length);
r = await api(T, '/api/app/inscripciones', { method: 'POST',
  body: cuerpoVenta({ [catId]: otraCat.opciones[0].id }, [persona(1)]) });
// 400 y no 500: es un dato que no cuadra, no una averia. Un 500 se lleva la traza al cliente
// y no le dice al vendedor que corregir.
ok(r.estado === 400 && r.cuerpo?.ok === false,
   'una opcion de OTRA categoria se rechaza con un mensaje claro',
   `HTTP ${r.estado} · ${r.cuerpo?.mensaje || ''}`);

// Ahora bien: la opcion cara.
r = await api(T, '/api/app/inscripciones', { method: 'POST',
  body: cuerpoVenta({ [catId]: opCara }, [persona(1), persona(2)]) });
const insId = r.cuerpo?.inscripcionId;
ok(r.estado === 200 && !!insId, 'la venta se registra con la opcion cara', `HTTP ${r.estado}`);
ok(Number(r.cuerpo?.total) === 2400,
   'y el total sale de esa opcion, no del precio base', `total=${r.cuerpo?.total} (2 x 1200)`);

// ---------------------------------------------------------------- 3. responsables por caseta
titulo('3. Responsables: dos por caseta, y los de mas se cobran');

r = await api(T, `/api/app/inscripciones/${insId}/responsables/cupo`);
ok(r.cuerpo?.casetas === 2 && r.cuerpo?.derecho === 4,
   '2 casetas dan derecho a 4 responsables', `derecho=${r.cuerpo?.derecho}`);
ok(r.cuerpo?.registrados === 2 && r.cuerpo?.dentroDelDerecho === true,
   'con 2 registrados todavia queda sitio gratis');

const alta = (nombre, conComprobante) => {
  const fd = new FormData();
  fd.append('nombre', nombre);
  fd.append('paterno', 'EXTRA');
  fd.append('ci', `EX${marca}${nombre.slice(-1)}`);
  fd.append('celular', '59170000002');
  if (conComprobante) {
    fd.append('comprobante', new Blob([new Uint8Array([0xff, 0xd8, 0xff, 0xdb, 0, 1])],
      { type: 'image/jpeg' }), 'pago.jpg');
  }
  return fd;
};

// Tercero y cuarto: dentro del derecho, sin cobro y sin comprobante.
r = await api(T, `/api/app/inscripciones/${insId}/responsables`, { method: 'POST', body: alta('TERCERO3') });
ok(r.estado === 200 && r.cuerpo?.cobrado === false,
   'el 3o entra sin cobro', r.cuerpo?.mensaje);
r = await api(T, `/api/app/inscripciones/${insId}/responsables`, { method: 'POST', body: alta('CUARTO4') });
ok(r.estado === 200 && r.cuerpo?.cobrado === false, 'el 4o tambien');

r = await api(T, `/api/app/inscripciones/${insId}/responsables/cupo`);
ok(r.cuerpo?.dentroDelDerecho === false && Number(r.cuerpo?.costoSiguiente) === 15,
   'lleno el cupo, el siguiente cuesta 15 Bs', `costo=${r.cuerpo?.costoSiguiente}`);

// El quinto SIN comprobante: se rechaza. Es la regla que evita emitir credenciales de gente
// que nunca pago.
r = await api(T, `/api/app/inscripciones/${insId}/responsables`, { method: 'POST', body: alta('QUINTO5') });
ok(r.estado >= 400 && r.cuerpo?.ok === false,
   'el 5o SIN comprobante se rechaza', r.cuerpo?.mensaje);

// Con comprobante, entra y queda registrado el cobro.
r = await api(T, `/api/app/inscripciones/${insId}/responsables`, { method: 'POST', body: alta('QUINTO5', true) });
const extraId = r.cuerpo?.responsableId;
ok(r.estado === 200 && r.cuerpo?.cobrado === true && Number(r.cuerpo?.monto) === 15,
   'con comprobante entra y se anota el cobro de 15 Bs', r.cuerpo?.mensaje);

r = await api(T, `/api/app/inscripciones/${insId}/detalle`);
ok((r.cuerpo?.responsables || []).length === 5,
   'la ficha de la venta muestra los cinco', `${r.cuerpo?.responsables?.length}`);

// El registro de la venta no admite pasarse del derecho: los de mas van por la via de arriba,
// que es la unica que cobra.
r = await api(T, '/api/app/puestos/carrito', { method: 'POST', body: JSON.stringify({ ids: [] }) });
const libres2 = ((await api(T, '/api/app/puestos')).cuerpo || []).filter((p) => p.estado === 'L');
if (libres2.length) {
  const uno = [libres2[0].id];
  await api(T, '/api/app/puestos/carrito', { method: 'POST', body: JSON.stringify({ ids: uno }) });
  r = await api(T, '/api/app/inscripciones', { method: 'POST', body: JSON.stringify({
    entidadNombre: `ZZ LIMITE ${marca}`, nit: '', descripcion: 'P', objeto: '',
    representanteLegal: 'REP', ciRepresentante: `RL${marca}`, celularRepresentante: '59170000000',
    tipoEntidadId: tipo?.id, fechaInicio: null, fechaFin: null,
    responsables: [persona(7), persona(8), persona(9)],
    entidadBancaria: '', numComprobante: null, pagoContado: true, puestos: uno }) });
  ok(r.estado >= 400 || r.cuerpo?.ok === false,
     '1 caseta NO admite 3 responsables al registrar', r.cuerpo?.mensaje);
  await api(T, '/api/app/puestos/carrito', { method: 'DELETE', body: JSON.stringify({ ids: uno }) });
}

// ---------------------------------------------------------------- 4. seguimiento en vivo
titulo('4. Seguimiento en vivo');

r = await api(T, '/api/app/presencia', { method: 'POST',
  body: JSON.stringify({ pantalla: 'venta', titulo: 'Registrar venta', origen: 'WEB' }) });
ok(r.estado === 200, 'el latido se acepta', `HTTP ${r.estado}`);

r = await api(T, '/api/app/presencia');
const yo = (r.cuerpo || []).find((g) => g.usuario === 'admin1');
ok(!!yo, 'y aparezco en la lista de conectados', `${r.cuerpo?.length} conectado(s)`);
ok(yo?.enLinea === true && yo?.pantalla === 'venta',
   'con la pantalla que informe', `${yo?.titulo}`);

// "Registrando" no se cree bajo palabra: sale de las casetas reservadas de verdad.
const paraCarrito = ((await api(T, '/api/app/puestos')).cuerpo || []).filter((p) => p.estado === 'L')[0];
if (paraCarrito) {
  await api(T, '/api/app/puestos/carrito', { method: 'POST', body: JSON.stringify({ ids: [paraCarrito.id] }) });
  await api(T, '/api/app/presencia', { method: 'POST', body: JSON.stringify({ pantalla: 'mapa', titulo: 'Mapa' }) });
  r = await api(T, '/api/app/presencia');
  const conCarrito = (r.cuerpo || []).find((g) => g.usuario === 'admin1');
  ok(conCarrito?.registrando === true && conCarrito?.casetasEnCarrito === 1,
     'con una caseta tomada sale como "registrando", aunque diga estar en el mapa',
     `casetas: ${conCarrito?.casetas?.join(', ')}`);
  await api(T, '/api/app/puestos/carrito', { method: 'DELETE', body: JSON.stringify({ ids: [paraCarrito.id] }) });
}

r = await api(T, '/api/app/presencia/salir', { method: 'POST' });
r = await api(T, '/api/app/presencia');
ok(!(r.cuerpo || []).some((g) => g.usuario === 'admin1'),
   'al salir se deja de aparecer en el acto');

// ---------------------------------------------------------------- limpieza
titulo('Limpieza');
if (insId) {
  r = await api(T, `/api/app/inscripciones/${insId}/cancelar`, { method: 'POST',
    body: JSON.stringify({ motivo: 'Prueba de modulos nuevos' }) });
  ok(r.estado === 200, 'la venta de prueba se cancela', `HTTP ${r.estado}`);
}
const misVentas = (await api(T, '/api/app/mis-ventas')).cuerpo;
for (const v of (Array.isArray(misVentas) ? misVentas : misVentas?.ventas || [])) {
  if (/^ZZ (OPCIONES|LIMITE) /.test(v.entidad || '') && v.id !== insId) {
    await api(T, `/api/app/inscripciones/${v.id}/cancelar`, { method: 'POST',
      body: JSON.stringify({ motivo: 'Prueba' }) });
  }
}
if (catId) {
  r = await api(T, `/api/app/categorias/${catId}`, { method: 'DELETE' });
  ok(r.estado === 200 || r.estado === 409, 'la categoria de prueba se retira', `HTTP ${r.estado}`);
}

console.log(`\n== ${fallos === 0 ? 'Todo en orden' : fallos + ' fallo(s)'} ==\n`);
process.exit(fallos ? 1 : 0);
