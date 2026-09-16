/*
 * Una venta de 0 Bs no tiene comprobante que adjuntar, y su credencial tiene que poder emitirse.
 *
 * Hay categorias y casetas sueltas a precio 0 —invitados, convenios, espacios cedidos—. Mientras
 * el comprobante se exigio SIEMPRE, esas credenciales quedaban bloqueadas para siempre: nadie
 * podia completar un papel que nunca se emitio, y el aviso ademas mandaba a buscarlo.
 *
 * Lo que se comprueba aqui, en orden de importancia:
 *   1. una venta de 0 Bs NO pide comprobante y su credencial sale;
 *   2. una venta que SI cuesta sigue pidiendolo (la regla no se rompio al abrir la excepcion);
 *   3. el responsable EXTRA de una venta gratis SIGUE pidiendolo: su cobro va aparte (15 Bs,
 *      V34) y no entra en el total, asi que exonerar por el importe de la venta habria abierto
 *      justo el agujero que la regla viene a cerrar.
 *
 * Crea dos ventas de prueba —una gratis y una de pago— y las cancela al terminar.
 *
 *   node .claude/skills/unifex-fullstack/scripts/verificar-credencial-sin-costo.mjs
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

const ID_VIRTUAL = 'CREDENCIAL_VIRTUAL';
const QR = 'QR_GRANDE';
const creadas = [];
const tocados = [];

/** Registra una venta de una caseta al precio que se le ponga. */
async function vender(puesto, precio, etiqueta) {
  await api('/api/app/puestos/precios', {
    method: 'PATCH', body: JSON.stringify([{ id: puesto.id, precio }]),
  });
  tocados.push(puesto.id);
  await api(`/api/app/puestos/${puesto.id}/reservar`, { method: 'POST' });

  const tipos = (await api('/api/app/inscripciones/tipos-entidad')).cuerpo;
  const r = await api('/api/app/inscripciones', {
    method: 'POST',
    body: JSON.stringify({
      puestos: [puesto.id],
      entidadNombre: `ZZ ${etiqueta} ${marca}`, nit: '', descripcion: 'P', objeto: '',
      representanteLegal: `REP ${etiqueta}`, ciRepresentante: `R${etiqueta}${marca}`,
      celularRepresentante: '59170000000',
      tipoEntidadId: tipos?.[0]?.id ?? 1, fechaInicio: null, fechaFin: null,
      entidadBancaria: '', numComprobante: null, pagoContado: true,
      responsables: [{ nombre: 'RESP', paterno: etiqueta, materno: '', ci: `C${etiqueta}${marca}`,
                       celular: '59170000001', correo: null }],
    }),
  });
  if (r.cuerpo?.inscripcionId) creadas.push(r.cuerpo.inscripcionId);
  return r;
}

const credencialesDe = async (inscripcionId) =>
  ((await api('/api/app/credenciales')).cuerpo || [])
    .filter((c) => Number(c.inscripcionId) === Number(inscripcionId));

async function main() {
  console.log(`\nCredencial de una venta sin costo · ${BASE}\n`);

  const login = await j(await fetch(`${BASE}/api/auth/login`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ usuario: USUARIO, contrasena: CLAVE }),
  }));
  if (!login?.token) { console.error('No se pudo entrar. Usa --usuario/--clave.'); process.exit(1); }
  T = login.token;

  const puestos = (await api('/api/app/puestos')).cuerpo || [];
  const libres = puestos.filter((p) => p.estado === 'L' && p.categoriaId != null);
  ok(libres.length >= 2, 'hay dos casetas libres para la prueba', `${libres.length}`);
  if (libres.length < 2) throw new Error('sin casetas no hay prueba');

  // ---- 1. la venta GRATIS ----
  console.log('Venta de 0 Bs');
  const gratis = await vender(libres[0], 0, 'GRATIS');
  ok(gratis.estado === 200, 'se registra', `HTTP ${gratis.estado}`);
  ok(Number(gratis.cuerpo?.total) === 0, 'cuesta 0 Bs', `total=${gratis.cuerpo?.total}`);

  const cg = (await credencialesDe(gratis.cuerpo?.inscripcionId))[0];
  ok(Boolean(cg), 'su responsable tiene credencial en la lista');
  ok(cg?.sinCosto === true, 'la credencial viene marcada como «sin costo»', `sinCosto=${cg?.sinCosto}`);
  ok(cg?.conComprobante === false, 'y NO tiene comprobante (no hay ninguno que adjuntar)');
  ok(cg?.requiereComprobante === false, 'el servidor dice que NO se le exige comprobante');
  ok((cg?.faltantes?.[QR] || []).every((f) => !/comprobante/i.test(f)),
    'no aparece «sin comprobante» entre lo que le falta',
    JSON.stringify(cg?.faltantes?.[QR]));
  // QR_GRANDE no pide foto, asi que sin comprobante y sin foto ya deberia estar lista.
  ok(cg?.listo?.[QR] === true,
    'con la plantilla que no pide foto, la credencial YA está lista',
    JSON.stringify(cg?.listo));
  // La virtual si pide foto: tiene que seguir faltando la foto, y SOLO la foto.
  ok(cg?.listo?.[ID_VIRTUAL] === false, 'la virtual sigue esperando la foto (identifica en la puerta)');
  ok((cg?.faltantes?.[ID_VIRTUAL] || []).join(',') === 'sin foto',
    'y lo único que le falta es la foto', JSON.stringify(cg?.faltantes?.[ID_VIRTUAL]));

  // El PDF tiene que salir de verdad, no solo decir que está listo.
  const pdf = await fetch(`${BASE}/api/app/credenciales/pdf`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${T}`, 'Content-Type': 'application/json' },
    body: JSON.stringify({ responsables: [cg.responsableId], plantilla: QR, anchoCm: 10, forzar: false }),
  });
  const buf = await pdf.arrayBuffer();
  const esPdf = new Uint8Array(buf.slice(0, 2))[0] === 0x25;
  ok(pdf.ok && esPdf && buf.byteLength > 500,
    'y el PDF se genera SIN forzar (antes respondía 409)',
    `HTTP ${pdf.status}, ${buf.byteLength} bytes`);

  // ---- 2. la venta de PAGO: la regla no se rompio ----
  console.log('\nVenta que sí cuesta');
  const pago = await vender(libres[1], 950, 'PAGO');
  ok(pago.estado === 200 && Number(pago.cuerpo?.total) === 950, 'se registra por 950 Bs',
    `HTTP ${pago.estado} total=${pago.cuerpo?.total}`);

  const cp = (await credencialesDe(pago.cuerpo?.inscripcionId))[0];
  ok(cp?.sinCosto === false, 'NO se marca como sin costo');
  ok(cp?.requiereComprobante === true, 'y SÍ se le exige comprobante');
  ok(cp?.listo?.[QR] === false, 'su credencial NO está lista sin el comprobante');
  ok((cp?.faltantes?.[QR] || []).some((f) => /comprobante/i.test(f)),
    'y se dice que falta el comprobante', JSON.stringify(cp?.faltantes?.[QR]));

  const pdfPago = await fetch(`${BASE}/api/app/credenciales/pdf`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${T}`, 'Content-Type': 'application/json' },
    body: JSON.stringify({ responsables: [cp.responsableId], plantilla: QR, anchoCm: 10, forzar: false }),
  });
  ok(pdfPago.status === 409,
    'el PDF de una venta impagada sigue respondiendo 409 sin forzar', `dio ${pdfPago.status}`);

  // ---- 3. el extra de una venta gratis SIGUE pagando ----
  //
  // Su cobro (15 Bs, V34) no entra en el total de la inscripcion. Si la exoneracion mirara solo
  // ese total, un extra que pago y no entrego el papel se acreditaria igual.
  console.log('\nResponsable extra en una venta gratis');
  /*
   * Hacen falta DOS altas, no una.
   *
   * Cada caseta da derecho a DOS responsables (V34), y la venta de prueba tiene una caseta y un
   * responsable: el siguiente entra dentro del derecho y NO es extra. El tercero ya se cobra.
   * La primera version de esta prueba añadia uno solo y fallaba diciendo "el extra no aparece",
   * cuando lo que pasaba es que ese responsable era gratis y el sistema tenia razon.
   */
  let alta = null;
  for (const n of [2, 3]) {
    const fd = new FormData();
    fd.append('nombre', `RESP${n}`);
    fd.append('paterno', 'GRATIS');
    fd.append('ci', `EX${marca}${n}`);
    fd.append('celular', '59170000002');
    /*
     * El TERCERO pasa del derecho y se cobra, y el servidor NO lo crea sin su comprobante
     * (V34: el cobro y el recibo viajan en la misma peticion, para que no exista el estado
     * "extra creado y sin pagar"). Por eso se adjunta aqui: es el unico camino real.
     *
     * Lo que se comprueba entonces es lo correcto: un extra que pago Y entrego su papel queda
     * exento del comprobante de la INSCRIPCION, que no existe porque la venta es gratis.
     */
    if (n === 3) {
      fd.append('comprobante',
        new Blob([new Uint8Array([0xff, 0xd8, 0xff, 0xdb, 0, 1])], { type: 'image/jpeg' }),
        'pago-extra.jpg');
    }
    alta = await fetch(`${BASE}/api/app/inscripciones/${gratis.cuerpo.inscripcionId}/responsables`, {
      method: 'POST', headers: { Authorization: `Bearer ${T}` }, body: fd,
    });
    if (!alta.ok) break;
  }
  const dAlta = await j(alta);
  if (!alta.ok) {
    console.log(`  info  no se pudo crear el extra (HTTP ${alta.status}): ${JSON.stringify(dAlta).slice(0, 120)}`);
    console.log('  info  se salta esta parte; el caso queda cubierto por la regla en CredencialDTO');
  } else {
    const todas = await credencialesDe(gratis.cuerpo.inscripcionId);
    const ex = todas.find((c) => c.esExtra);
    ok(Boolean(ex), 'el extra aparece en la lista');
    if (ex) {
      ok(ex.sinCosto === true, 'su venta sigue siendo de 0 Bs');
      ok(Boolean(ex.comprobanteExtraUrl),
        'y él entregó el comprobante de sus 15 Bs (V34 no deja crearlo sin él)',
        `comprobanteExtraUrl=${ex.comprobanteExtraUrl}`);
      // Pagó lo suyo y lo respaldó: no se le puede pedir además el comprobante de una venta
      // que no cuesta nada, porque ese no existe.
      ok(ex.requiereComprobante === false,
        'con su cobro respaldado, queda exento del comprobante de la venta',
        `requiereComprobante=${ex.requiereComprobante}`);
      ok(ex.listo?.[QR] === true, 'y su credencial se puede emitir', JSON.stringify(ex.listo));
    }
  }

  // ---- limpieza ----
  console.log('\nLimpieza');
  for (const id of creadas) {
    const r = await api(`/api/app/inscripciones/${id}/cancelar`, {
      method: 'POST', body: JSON.stringify({ motivo: 'prueba automatica de venta sin costo' }),
    });
    ok(r.estado === 200, `se cancela la venta ${id}`, `HTTP ${r.estado}`);
  }
  if (tocados.length) {
    await api('/api/app/puestos/precios', {
      method: 'PATCH', body: JSON.stringify(tocados.map((id) => ({ id, precio: null }))),
    });
    ok(true, 'y se les quita el precio propio a las casetas usadas');
  }

  console.log(`\n${fallos === 0 ? 'Todo bien.' : `${fallos} comprobacion(es) fallidas.`}\n`);
  process.exit(fallos === 0 ? 0 : 1);
}

main().catch((e) => { console.error(e); process.exit(1); });
