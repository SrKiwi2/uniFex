#!/usr/bin/env node
/**
 * Credenciales: el codigo firmado del QR, los requisitos y el PDF.
 *
 * Lo que de verdad hay que vigilar aqui es la FIRMA. El QR lleva a una vista publica sin
 * sesion que enseña nombre, C.I., entidad y casetas, asi que lo unico que separa esos datos
 * de cualquiera es que no se pueda fabricar un codigo valido. Si algun dia alguien "simplifica"
 * el codigo a un id suelto, esto lo detecta.
 *
 *   node .claude/skills/unifex-fullstack/scripts/verificar-credenciales.mjs
 *   opciones: --base http://localhost:7676 --clave '...'
 */
const arg = (n, d) => { const i = process.argv.indexOf('--' + n); return i > 0 ? process.argv[i + 1] : d; };
const B = arg('base', 'http://localhost:7676');

const fallos = [];
const paso = (d, ok, det = '') => {
  console.log(`  [${ok ? 'OK   ' : 'FALLA'}] ${d}${det ? `  -> ${det}` : ''}`);
  if (!ok) fallos.push(d);
  return ok;
};
const titulo = (t) => console.log(`\n${t}`);

const api = async (t, ruta, o = {}) => {
  const h = { ...(o.headers || {}) };
  if (t) h.Authorization = `Bearer ${t}`;
  if (o.body) h['Content-Type'] = 'application/json';
  const r = await fetch(B + ruta, { ...o, headers: h });
  const ct = r.headers.get('content-type') || '';
  return {
    estado: r.status,
    tipo: ct,
    bytes: Number(r.headers.get('content-length') || 0),
    cuerpo: ct.includes('json') ? await r.json().catch(() => null) : null,
    crudo: ct.includes('pdf') ? (await r.arrayBuffer()).byteLength : 0,
  };
};

console.log(`\n== Credenciales (${B}) ==`);
const login = await (await fetch(B + '/api/auth/login', {
  method: 'POST', headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ usuario: arg('usuario', 'admin1'), contrasena: arg('clave', 'VO7xGroB8ag2Qz1B') }),
})).json().catch(() => ({}));
const T = login.token;
if (!paso('login de administracion', !!T)) process.exit(1);

/*
 * Si la base no tiene inscripciones, la prueba se creaba una propia... o mejor dicho, no: se
 * saltaba TODO y terminaba diciendo "todo paso". Una prueba que no prueba nada y sale en verde
 * es peor que una que falla, asi que aqui se monta la venta que hace falta y se cancela al
 * final. Sale COMPLETA (recibo y foto) para poder ejercer el camino del PDF.
 */
let ventaPropia = null;
const imagen = (nombre) => {
  const fd = new FormData();
  fd.append('archivo', new Blob([new Uint8Array([0xff, 0xd8, 0xff, 0xdb, 0, 1, 2, 3])],
      { type: 'image/jpeg' }), nombre);
  return fd;
};
const multipart = async (ruta, fd) => fetch(B + ruta,
    { method: 'POST', headers: { Authorization: `Bearer ${T}` }, body: fd });

async function prepararVenta() {
  const libre = ((await api(T, '/api/app/puestos')).cuerpo || []).find((p) => p.estado === 'L');
  if (!libre) return null;
  await api(T, '/api/app/puestos/carrito', { method: 'POST', body: JSON.stringify({ ids: [libre.id] }) });
  const v = await api(T, '/api/app/inscripciones', { method: 'POST', body: JSON.stringify({
    entidadNombre: 'ZZ PRUEBA CREDENCIAL', nit: '', descripcion: 'PRUEBA', objeto: '',
    representanteLegal: 'REP CRED', ciRepresentante: '99999940', celularRepresentante: '70000040',
    tipoEntidadId: 1, fechaInicio: null, fechaFin: null,
    responsables: [{ nombre: 'PRUEBA', paterno: 'CREDENCIAL', materno: '', ci: '99999941',
                     celular: '70000041', correo: null }],
    entidadBancaria: '', numComprobante: null, pagoContado: true, puestos: [libre.id] }) });
  const id = v.cuerpo?.inscripcionId;
  if (!id) return null;
  await multipart(`/api/app/inscripciones/${id}/comprobante`, imagen('comprobante.jpg'));
  const c = ((await api(T, '/api/app/credenciales')).cuerpo || [])
      .find((x) => x.entidad === 'ZZ PRUEBA CREDENCIAL');
  if (c) await multipart(`/api/app/inscripciones/${id}/responsables/${c.responsableId}/foto`,
                         imagen('foto.jpg'));
  return id;
}

titulo('Listado y requisitos');
let lista = (await api(T, '/api/app/credenciales')).cuerpo || [];
if (!lista.length) {
  ventaPropia = await prepararVenta();
  console.log(`  (la base no tenia inscripciones: se creo una de prueba${ventaPropia ? '' : ' — y no se pudo'})`);
  lista = (await api(T, '/api/app/credenciales')).cuerpo || [];
}
paso('devuelve la lista', Array.isArray(lista) && lista.length > 0, `${lista.length} credencial(es)`);
if (!lista.length) {
  console.log('  (no hay con que seguir: hace falta al menos una caseta libre)');
  process.exit(1);
}
const c = lista[0];
const PL = ['CON_ETIQUETAS', 'QR_GRANDE'];
const listo = (x, pl) => Boolean(x.listo?.[pl]);
const falta = (x, pl) => x.faltantes?.[pl] || [];

paso('cada una trae su codigo, requisitos y motivo',
     ['codigo', 'conComprobante', 'conFoto', 'listo', 'faltantes'].every((k) => k in c));
paso('lo de "listo" y lo que falta viene por plantilla, no suelto',
     lista.every((x) => PL.every((pl) => pl in (x.listo || {}) && pl in (x.faltantes || {}))));

// Las dos reglas del flujo de verificacion, en la forma en la que fallarian si alguien las
// relaja: el recibo no lo sustituye marcar "contado", y la foto solo la pide la plantilla
// que imprime los datos de la persona.
paso('el comprobante hace falta para las DOS plantillas',
     lista.filter((x) => !x.conComprobante).every((x) => PL.every((pl) => !listo(x, pl))));
paso('marcar contado NO cuenta como comprobante',
     lista.filter((x) => x.pagoContado && !x.conComprobante).every((x) => !listo(x, 'QR_GRANDE')));
paso('la foto solo la exige la plantilla con etiquetas',
     lista.filter((x) => x.conComprobante && !x.conFoto)
          .every((x) => !listo(x, 'CON_ETIQUETAS') && listo(x, 'QR_GRANDE')));
paso('con recibo y foto, las dos quedan listas',
     lista.filter((x) => x.conComprobante && x.conFoto).every((x) => PL.every((pl) => listo(x, pl))));
paso('las no listas dicen que les falta',
     lista.every((x) => PL.every((pl) => listo(x, pl) || falta(x, pl).length > 0)));
paso('y las listas no arrastran motivos viejos',
     lista.every((x) => PL.every((pl) => !listo(x, pl) || falta(x, pl).length === 0)));

paso('cada fila dice si ya se imprimio y si se imprimio incompleta',
     lista.every((x) => ['impresa', 'vecesImpresa', 'impresaIncompleta'].every((k) => k in x)));
paso('trae el id de inscripcion, que es lo que permite adjuntar sin salir de la pantalla',
     lista.every((x) => 'inscripcionId' in x));
paso('sin sesion no se lista', (await api(null, '/api/app/credenciales')).estado === 401);

titulo('El codigo del QR');
paso('lleva prefijo, id y firma', /^FXC-[0-9A-Z]+-[0-9A-Z_-]{12}$/.test(c.codigo), c.codigo);
const publica = (ruta) => api(null, `/api/publico/credencial/${ruta}`);
let r = await publica(c.codigo);
paso('el codigo autentico abre la vista publica SIN sesion', r.estado === 200 && r.cuerpo?.valida,
     // El alfabeto base64url incluye el guion, asi que una firma de cada seis lleva alguno.
     // Quien separe el codigo por TODOS los guiones rechaza esas credenciales, y no se entera
     // hasta tener la fila delante en la puerta.
     c.codigo.split('-').length > 3 ? 'esta firma lleva guiones dentro' : '');
paso('y trae los datos que se miran en la puerta',
     ['nombre', 'ci', 'entidad', 'categoria', 'casetas', 'fotoUrl'].every((k) => k in (r.cuerpo || {})));
paso('no filtra ids internos del sistema',
     !('responsableId' in (r.cuerpo || {})) && !('inscripcionId' in (r.cuerpo || {})));

// Se parte por los DOS primeros guiones, no por todos: la firma es base64url y puede llevar
// guiones dentro. Partirla mal aqui haria que la prueba de "reusar la firma con otro id"
// pasara por el motivo equivocado, con una firma truncada que tampoco valdria.
const partes = c.codigo.split('-', 2).concat(c.codigo.split('-').slice(2).join('-'));
r = await publica(`${partes[0]}-${partes[1]}-AAAAAAAAAAAA`);
paso('una firma inventada NO abre nada', r.estado === 404);
// El id va en base36, no en decimal: con Number() un id como "L" daba NaN y la prueba pasaba
// por el motivo equivocado, contra un codigo que no se parecia a ninguno real.
const otroId = (parseInt(partes[1], 36) + 1).toString(36).toUpperCase();
r = await publica(`${partes[0]}-${otroId}-${partes[2]}`);
paso('reusar la firma con otro id tampoco', r.estado === 404);
r = await publica('cualquier-cosa');
paso('un codigo con otra forma tampoco', r.estado === 404);

titulo('PDF');
const aptas = lista.filter((x) => listo(x, 'CON_ETIQUETAS'));
if (!aptas.length) {
  console.log('  (ninguna credencial cumple los requisitos en esta base)');
} else {
  for (const [nombre, cuerpo] of [
    ['plantilla con etiquetas', { plantilla: 'CON_ETIQUETAS', anchoCm: 10 }],
    ['plantilla de QR grande', { plantilla: 'QR_GRANDE', anchoCm: 12 }],
    ['una sola credencial', { responsables: [aptas[0].responsableId] }],
  ]) {
    const p = await api(T, '/api/app/credenciales/pdf', { method: 'POST', body: JSON.stringify(cuerpo) });
    paso(nombre, p.estado === 200 && p.tipo.includes('pdf') && p.crudo > 10000,
         `${p.estado}, ${Math.round(p.crudo / 1024)} KB`);
  }
  const noAptas = lista.filter((x) => !listo(x, 'CON_ETIQUETAS')).map((x) => x.responsableId);
  if (noAptas.length) {
    const cuerpo = { responsables: noAptas, plantilla: 'CON_ETIQUETAS' };
    let p = await api(T, '/api/app/credenciales/pdf', { method: 'POST', body: JSON.stringify(cuerpo) });
    paso('pedir solo credenciales incompletas se rechaza, no imprime a medias',
         p.estado === 409, `${p.estado}`);

    // Se permite a proposito, porque a veces el papel hace falta ya. Lo que NO se permite es
    // que pase sin dejar rastro: quien lo hizo y que faltaba entonces.
    const uno = noAptas[0];
    const antes = (await api(T, `/api/app/credenciales/${uno}/impresiones`)).cuerpo || [];
    p = await api(T, '/api/app/credenciales/pdf', {
      method: 'POST', body: JSON.stringify({ ...cuerpo, responsables: [uno], forzar: true }) });
    paso('con forzar si imprime, aunque falte algo',
         p.estado === 200 && p.crudo > 10000, `${p.estado}, ${Math.round(p.crudo / 1024)} KB`);

    const despues = (await api(T, `/api/app/credenciales/${uno}/impresiones`)).cuerpo || [];
    paso('y queda registrada esa impresion', despues.length === antes.length + 1);
    const ult = despues[0] || {};
    paso('el registro dice quien, cuando, con que plantilla y que faltaba',
         !!ult.usuario && !!ult.cuando && ult.plantilla === 'CON_ETIQUETAS' && !!ult.faltaba,
         JSON.stringify(ult));

    const relistado = ((await api(T, '/api/app/credenciales')).cuerpo || [])
        .find((x) => x.responsableId === uno) || {};
    paso('el listado la marca como impresa-incompleta, para que no se pierda de vista',
         relistado.impresa === true && relistado.impresaIncompleta === true);
  }
  const sin = await api(null, '/api/app/credenciales/pdf', { method: 'POST', body: '{}' });
  paso('sin sesion no se imprime', sin.estado === 401, `${sin.estado}`);
  paso('sin sesion no se ve el historial de impresiones',
       (await api(null, `/api/app/credenciales/${c.responsableId}/impresiones`)).estado === 401);
}

if (ventaPropia) {
  titulo('Limpieza');
  await api(T, `/api/app/inscripciones/${ventaPropia}/cancelar`, { method: 'POST',
    body: JSON.stringify({ motivo: 'Prueba de credenciales' }) });
  paso('la venta de prueba queda cancelada',
       !((await api(T, '/api/app/credenciales')).cuerpo || [])
           .some((x) => x.entidad === 'ZZ PRUEBA CREDENCIAL'));
}

console.log(fallos.length ? `\n${fallos.length} paso(s) fallaron:` : '\nTodo paso.');
for (const f of fallos) console.log(`  - ${f}`);
process.exit(fallos.length ? 1 : 0);
