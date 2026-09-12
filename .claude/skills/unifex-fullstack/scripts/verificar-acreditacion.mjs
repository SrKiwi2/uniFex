#!/usr/bin/env node
/**
 * El flujo de acreditacion completo, sobre una venta creada y cancelada por la propia prueba.
 *
 * Esto no comprueba endpoints sueltos: recorre el camino real de un expositor, que es donde
 * estan las dos reglas faciles de romper sin que nada falle a la vista.
 *
 *   1. El COMPROBANTE hace falta SIEMPRE, tambien al contado. Marcar "contado" dice como se
 *      pago, no que exista el recibo. Cuando el listado daba por buenas las ventas al contado,
 *      se imprimian credenciales de gente que nunca entrego el papel.
 *   2. La FOTO solo la exige la plantilla CON_ETIQUETAS, que es la que imprime los datos de la
 *      persona. La de QR grande no lleva ni nombre ni C.I., asi que pedirla ahi solo frena la
 *      cola sin proteger nada.
 *
 * Y dos permisos que son el sentido del rol VERIFICADOR: puede adjuntar el comprobante de una
 * venta que NO es suya (el expositor llega al mostrador con el papel en la mano), y puede
 * imprimir algo incompleto a proposito, pero eso queda registrado con su nombre y con lo que
 * faltaba en ese momento.
 *
 *   node .claude/skills/unifex-fullstack/scripts/verificar-acreditacion.mjs
 *   opciones: --base http://localhost:7676 --usuario admin1 --clave '...'
 *
 * Deja la base como la encontro: cancela la venta y da de baja el usuario de prueba.
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
  // FormData pone su propio Content-Type CON el boundary: fijarlo a mano rompe el multipart.
  if (typeof o.body === 'string') h['Content-Type'] = 'application/json';
  const r = await fetch(B + ruta, { ...o, headers: h });
  // El cuerpo se lee UNA sola vez: leerlo dos veces lo deja inservible.
  const ct = r.headers.get('content-type') || '';
  if (ct.includes('pdf')) return { estado: r.status, cuerpo: null, pdf: (await r.arrayBuffer()).byteLength };
  const txt = await r.text().catch(() => '');
  return { estado: r.status, cuerpo: ct.includes('json') ? (() => { try { return JSON.parse(txt); } catch { return null; } })() : txt, pdf: 0 };
};

const entrar = async (usuario, contrasena) => (await (await fetch(B + '/api/auth/login', {
  method: 'POST', headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ usuario, contrasena }),
})).json().catch(() => ({}))).token;

const imagen = (nombre) => {
  const fd = new FormData();
  fd.append('archivo', new Blob([new Uint8Array([0xff, 0xd8, 0xff, 0xdb, 0, 1, 2, 3])],
      { type: 'image/jpeg' }), nombre);
  return fd;
};

console.log(`\n== Acreditacion, flujo completo (${B}) ==`);
const T = await entrar(arg('usuario', 'admin1'), arg('clave', 'VO7xGroB8ag2Qz1B'));
if (!paso('login de administracion', !!T)) process.exit(1);

// --------------------------------------------------------------- venta de prueba
/*
 * Barrido de arranque. Cada pasada crea sus ventas de prueba y las cancela al final, pero si
 * una se corta a la mitad quedan vivas — y entonces la siguiente encuentra DOS entidades con
 * el mismo nombre y se lia. Antes de empezar se limpia lo que dejaron las pasadas anteriores.
 */
for (const c of ((await api(T, '/api/app/credenciales')).cuerpo || [])) {
  if (/^ZZ /.test(c.entidad || '') && c.inscripcionId) {
    await api(T, `/api/app/inscripciones/${c.inscripcionId}/cancelar`, { method: 'POST',
      body: JSON.stringify({ motivo: 'Limpieza de pruebas de acreditacion' }) });
  }
}

titulo('Preparar una venta de prueba, AL CONTADO');
const puestos = (await api(T, '/api/app/puestos')).cuerpo || [];
const libre = puestos.find((p) => p.estado === 'L');
if (!paso('hay alguna caseta libre para la prueba', !!libre)) process.exit(1);

await api(T, '/api/app/puestos/carrito', { method: 'POST', body: JSON.stringify({ ids: [libre.id] }) });
const venta = await api(T, '/api/app/inscripciones', { method: 'POST', body: JSON.stringify({
  entidadNombre: 'ZZ PRUEBA ACREDITACION', nit: '', descripcion: 'PRUEBA', objeto: '',
  representanteLegal: 'PRUEBA LEGAL', ciRepresentante: '99999905', celularRepresentante: '70000005',
  tipoEntidadId: 1, fechaInicio: null, fechaFin: null,
  responsables: [{ nombre: 'ZZPRUEBA', paterno: 'ACREDITA', materno: '', ci: '99999906',
                   celular: '70000006', correo: null }],
  entidadBancaria: '', numComprobante: null,
  pagoContado: true,            // <- a proposito: es la mitad de lo que se esta probando
  puestos: [libre.id] }) });
const insId = venta.cuerpo?.inscripcionId;
if (!paso('venta creada al contado y sin comprobante', !!insId,
          JSON.stringify(venta.cuerpo).slice(0, 120))) process.exit(1);

const dameCred = async (t = T) => ((await api(t, '/api/app/credenciales')).cuerpo || [])
    .find((c) => c.entidad === 'ZZ PRUEBA ACREDITACION');

const limpiar = async () => {
  await api(T, `/api/app/inscripciones/${insId}/cancelar`, { method: 'POST',
    body: JSON.stringify({ motivo: 'Venta de prueba de acreditacion' }) });
};

try {
  // ------------------------------------------------------------- el recibo, siempre
  titulo('El comprobante hace falta AUNQUE la venta sea al contado');
  let c = await dameCred();
  if (!paso('la credencial aparece en la lista', !!c)) throw new Error('sin credencial');
  paso('marcada como SIN comprobante, pese al contado', c.conComprobante === false);
  paso('no esta lista para ninguna plantilla',
       c.listo.CON_ETIQUETAS === false && c.listo.QR_GRANDE === false);
  paso('y el motivo es el comprobante', c.faltantes.CON_ETIQUETAS.includes('sin comprobante'),
       JSON.stringify(c.faltantes));

  // La misma regla, vista desde el vendedor: si su venta al contado no le sale como pendiente,
  // nunca sabra que tiene que subir el recibo.
  const mias = (await api(T, '/api/app/inscripciones/mis-pendientes')).cuerpo || [];
  paso('al vendedor le sale como pendiente de comprobante, aun siendo al contado',
       mias.some((p) => p.id === insId), `${mias.length} pendiente(s)`);

  // ------------------------------------------------------------- imprimir incompleta
  titulo('Imprimir sin cumplir: se rechaza, salvo que se fuerce a proposito');
  let r = await api(T, '/api/app/credenciales/pdf', { method: 'POST',
    body: JSON.stringify({ responsables: [c.responsableId], plantilla: 'CON_ETIQUETAS' }) });
  paso('sin forzar -> 409', r.estado === 409, `${r.estado}`);

  r = await api(T, '/api/app/credenciales/pdf', { method: 'POST',
    body: JSON.stringify({ responsables: [c.responsableId], plantilla: 'CON_ETIQUETAS', forzar: true }) });
  paso('forzando -> 200 y sale el PDF', r.estado === 200 && r.pdf > 10000,
       `${r.estado}, ${Math.round(r.pdf / 1024)} KB`);

  const hist = (await api(T, `/api/app/credenciales/${c.responsableId}/impresiones`)).cuerpo || [];
  paso('queda registrado quien la imprimio', hist.length === 1 && !!hist[0]?.usuario,
       JSON.stringify(hist[0] || {}));
  paso('y QUE le faltaba en ese momento', /sin comprobante/.test(hist[0]?.faltaba || ''),
       hist[0]?.faltaba);

  c = await dameCred();
  paso('el listado la marca impresa-incompleta, para que no se pierda de vista',
       c.impresa === true && c.impresaIncompleta === true && c.vecesImpresa === 1);

  // ------------------------------------------------------------- el verificador
  titulo('Un VERIFICADOR puede completar una venta que no es suya');
  const roles = (await api(T, '/api/app/roles')).cuerpo || [];
  const rolVerif = (Array.isArray(roles) ? roles : roles.roles || [])
      .find((x) => (x.nombre || '').toUpperCase() === 'VERIFICADOR');
  if (!paso('existe el rol VERIFICADOR (migracion V29)', !!rolVerif)) throw new Error('sin rol');

  /*
   * Aqui no se puede "crear y borrar" y ya esta, por dos motivos que solo se ven a la segunda
   * pasada: la baja de usuarios es LOGICA, asi que el nombre sigue ocupado despues de borrar,
   * y la persona tambien queda, con su C.I., que tampoco se puede repetir. Un usuario borrado
   * ademas no sale en ningun listado, o sea que queda fuera de alcance para siempre.
   *
   * Asi que la prueba reaprovecha SIEMPRE el mismo usuario: lo reactiva y le pone la clave al
   * empezar, y lo deja DESACTIVADO al terminar. Desactivado no puede entrar, que es lo que
   * importa cuando la clave esta escrita en un archivo del repositorio.
   *
   * No lo borres "para limpiar": el nombre quedaria ocupado por una fila que ya no sale en
   * ningun listado, y la siguiente pasada no tendria forma de recuperarlo ni de crear otro.
   */
  const USER = 'zz_verificador_pruebas';
  const CLAVE_V = 'Prueba.Verif.2026';
  const CI_V = '99999907';
  let usuarioId = null;

  const usuarios = (await api(T, '/api/app/usuarios')).cuerpo;
  const ya = (Array.isArray(usuarios) ? usuarios : usuarios?.usuarios || [])
      .find((u) => u.username === USER);
  if (ya) {
    usuarioId = ya.id;
    await api(T, `/api/app/usuarios/${usuarioId}`, { method: 'PATCH',
      body: JSON.stringify({ username: USER, personaId: ya.personaId, rolId: rolVerif.id }) });
    await api(T, `/api/app/usuarios/${usuarioId}/estado`, { method: 'PATCH',
      body: JSON.stringify({ activo: true }) });
    await api(T, `/api/app/usuarios/${usuarioId}/password`, { method: 'PATCH',
      body: JSON.stringify({ password: CLAVE_V }) });
    paso('se reaprovecha el usuario de prueba de la vez anterior', true, `id ${usuarioId}`);
  } else {
    // La persona puede haber sobrevivido a un usuario borrado: si existe se enlaza por id, y si
    // no, se crea entera. Mandar los datos de una persona que ya esta da "C.I. duplicado".
    const porCi = (await api(T, `/api/app/usuarios/personas/por-ci?ci=${CI_V}`)).cuerpo;
    const cuerpo = { username: USER, password: CLAVE_V, rolId: rolVerif.id };
    if (porCi?.existe) cuerpo.personaId = porCi.persona?.id;
    else cuerpo.persona = { nombre: 'ZZVERIF', paterno: 'PRUEBA', materno: '', ci: CI_V,
                            correo: null, celular: '70000007' };
    const nuevo = await api(T, '/api/app/usuarios', { method: 'POST', body: JSON.stringify(cuerpo) });
    usuarioId = nuevo.cuerpo?.usuario?.id ?? nuevo.cuerpo?.id;
    paso('se crea un usuario con ese rol', !!usuarioId,
         `${nuevo.estado} ${JSON.stringify(nuevo.cuerpo).slice(0, 90)}`);
  }

  const V = await entrar(USER, CLAVE_V);
  if (paso('el verificador entra', !!V)) {
    paso('y ve TODAS las inscripciones en el listado de credenciales', !!(await dameCred(V)));

    // Lo que justifica el rol: el papel llega al mostrador, no al vendedor.
    r = await api(V, `/api/app/inscripciones/${insId}/comprobante`,
                  { method: 'POST', body: imagen('comprobante.jpg') });
    paso('adjunta el comprobante de una venta ajena', r.estado === 200 && r.cuerpo?.ok === true,
         `${r.estado} ${JSON.stringify(r.cuerpo).slice(0, 80)}`);

    c = await dameCred(V);
    paso('ahora consta el comprobante', c?.conComprobante === true);
    paso('sigue sin foto', c?.conFoto === false);
    paso('el QR grande YA se puede imprimir: no pide foto', c?.listo.QR_GRANDE === true);
    paso('la de etiquetas NO, porque imprime los datos de la persona',
         c?.listo.CON_ETIQUETAS === false);
    paso('y lo unico que le falta es la foto',
         JSON.stringify(c?.faltantes.CON_ETIQUETAS) === '["sin foto"]',
         JSON.stringify(c?.faltantes.CON_ETIQUETAS));

    r = await api(V, '/api/app/credenciales/pdf', { method: 'POST',
      body: JSON.stringify({ responsables: [c.responsableId], plantilla: 'QR_GRANDE' }) });
    paso('imprime el QR grande sin tener que forzar', r.estado === 200 && r.pdf > 10000, `${r.estado}`);

    // ----------------------------------------------------------- la foto, en el momento
    titulo('Adjuntar la foto cierra el caso');
    r = await api(V, `/api/app/inscripciones/${insId}/responsables/${c.responsableId}/foto`,
                  { method: 'POST', body: imagen('foto.jpg') });
    paso('el verificador sube la foto del responsable', r.estado === 200 && r.cuerpo?.ok !== false,
         `${r.estado} ${JSON.stringify(r.cuerpo).slice(0, 80)}`);

    c = await dameCred(V);
    paso('la credencial queda lista para las dos plantillas',
         c?.listo.CON_ETIQUETAS === true && c?.listo.QR_GRANDE === true);
    paso('y ya no le falta nada',
         c?.faltantes.CON_ETIQUETAS.length === 0 && c?.faltantes.QR_GRANDE.length === 0);

    r = await api(V, '/api/app/credenciales/pdf', { method: 'POST',
      body: JSON.stringify({ responsables: [c.responsableId], plantilla: 'CON_ETIQUETAS' }) });
    paso('se imprime la de etiquetas sin forzar', r.estado === 200 && r.pdf > 10000, `${r.estado}`);

    // Tres impresiones: la forzada de antes, el QR grande y esta. Completarla despues no borra
    // el historial —seria justo lo que no interesa perder— pero si deja de faltarle nada.
    c = await dameCred();
    paso('el historial conserva la impresion incompleta de antes',
         c?.vecesImpresa === 3 && c?.impresaIncompleta === true, `${c?.vecesImpresa} impresion(es)`);
    paso('pero ya no le falta nada por completar',
         c?.faltantes.CON_ETIQUETAS.length === 0);
  }

  // ------------------------------------------------------------- el vendedor
  /*
   * Un ADMINISTRATIVO acredita a SUS expositores y a nadie mas.
   *
   * Es el caso que motiva todo esto: quien tiene delante al expositor, con el recibo en la mano
   * y la foto por tomar, es el vendedor que le vendio la caseta, no el mostrador. Pero la lista
   * completa lleva nombres, C.I. y telefonos de los clientes de TODOS los vendedores, asi que
   * darle la pantalla sin acotarla habria sido peor que no darsela.
   */
  titulo('Un ADMINISTRATIVO solo acredita sus propias ventas');
  const rolVend = (Array.isArray(roles) ? roles : roles.roles || [])
      .find((x) => (x.nombre || '').toUpperCase() === 'ADMINISTRATIVO');
  const USER_V = 'zz_vendedor_pruebas';
  const CLAVE_VEND = 'Prueba.Vendedor.2026';
  const CI_VEND = '99999960';
  let vendedorId = null;

  const usuariosV = (await api(T, '/api/app/usuarios')).cuerpo;
  const yaVend = (Array.isArray(usuariosV) ? usuariosV : usuariosV?.usuarios || [])
      .find((u) => u.username === USER_V);
  if (yaVend) {
    vendedorId = yaVend.id;
    await api(T, `/api/app/usuarios/${vendedorId}/estado`, { method: 'PATCH',
      body: JSON.stringify({ activo: true }) });
    await api(T, `/api/app/usuarios/${vendedorId}/password`, { method: 'PATCH',
      body: JSON.stringify({ password: CLAVE_VEND }) });
  } else {
    const porCi = (await api(T, `/api/app/usuarios/personas/por-ci?ci=${CI_VEND}`)).cuerpo;
    const cuerpo = { username: USER_V, password: CLAVE_VEND, rolId: rolVend.id };
    if (porCi?.existe) cuerpo.personaId = porCi.persona?.id;
    else cuerpo.persona = { nombre: 'ZZVENDEDOR', paterno: 'PRUEBA', materno: '', ci: CI_VEND,
                            correo: null, celular: '70000060' };
    const alta = await api(T, '/api/app/usuarios', { method: 'POST', body: JSON.stringify(cuerpo) });
    vendedorId = alta.cuerpo?.usuario?.id ?? alta.cuerpo?.id;
  }
  if (!paso('hay un vendedor de prueba', !!vendedorId, `id ${vendedorId}`)) throw new Error('sin vendedor');

  // Sin caseta habilitada no puede vender, asi que no habria nada que acreditar.
  const libres = ((await api(T, '/api/app/vendedores/puestos-asignables')).cuerpo || [])
      .filter((p) => !p.asignadoAId).map((p) => p.id);
  const suya = ((await api(T, '/api/app/puestos')).cuerpo || [])
      .find((p) => p.estado === 'L' && libres.includes(p.id));
  await api(T, `/api/app/vendedores/${vendedorId}/puestos`, { method: 'PUT',
    body: JSON.stringify({ puestoIds: suya ? [suya.id] : [] }) });
  if (!paso('con una caseta habilitada para vender', !!suya, suya?.codigo)) throw new Error('sin caseta');

  const VE = await entrar(USER_V, CLAVE_VEND);
  if (!paso('el vendedor entra', !!VE)) throw new Error('el vendedor no entra');

  await api(VE, '/api/app/puestos/carrito', { method: 'POST', body: JSON.stringify({ ids: [suya.id] }) });
  const ventaV = await api(VE, '/api/app/inscripciones', { method: 'POST', body: JSON.stringify({
    entidadNombre: 'ZZ CLIENTE DEL VENDEDOR', nit: '', descripcion: 'PRUEBA', objeto: '',
    representanteLegal: 'REP VEND', ciRepresentante: '99999961', celularRepresentante: '70000061',
    tipoEntidadId: 1, fechaInicio: null, fechaFin: null,
    responsables: [{ nombre: 'CLIENTE', paterno: 'DELVENDEDOR', materno: '', ci: '99999962',
                     celular: '70000062', correo: null }],
    entidadBancaria: '', numComprobante: null, pagoContado: true, puestos: [suya.id] }) });
  const insV = ventaV.cuerpo?.inscripcionId;
  if (!paso('registra una venta suya', !!insV, JSON.stringify(ventaV.cuerpo).slice(0, 90))) {
    throw new Error('el vendedor no pudo vender');
  }

  const listaV = (await api(VE, '/api/app/credenciales')).cuerpo || [];
  // Se busca por ID DE INSCRIPCION y no por el nombre de la entidad: si una pasada anterior
  // dejo una venta de prueba a medias, el nombre coincide con dos y se acaba subiendo la foto
  // de un responsable que pertenece a otra venta. El sintoma era un 400 sin relacion aparente.
  const suyaCred = listaV.find((x) => x.inscripcionId === insV);
  paso('ve la credencial de su propia venta', !!suyaCred);
  paso('y NO ve las ventas de los demas',
       !listaV.some((x) => x.entidad === 'ZZ PRUEBA ACREDITACION'),
       `${listaV.length} credencial(es) en su lista`);

  // Lo que justifica darle la pantalla: completar en el momento, sin mandar a nadie a una cola.
  r = await api(VE, `/api/app/inscripciones/${insV}/comprobante`,
                { method: 'POST', body: imagen('comprobante.jpg') });
  paso('adjunta el comprobante de su venta', r.estado === 200 && r.cuerpo?.ok === true,
       `${r.estado}`);
  r = await api(VE, `/api/app/inscripciones/${insV}/responsables/${suyaCred.responsableId}/foto`,
                { method: 'POST', body: imagen('foto.jpg') });
  paso('y la foto de su responsable', r.estado === 200 && r.cuerpo?.ok !== false, `${r.estado}`);

  const suyaLista = ((await api(VE, '/api/app/credenciales')).cuerpo || [])
      .find((x) => x.inscripcionId === insV);
  paso('con eso su credencial queda lista', suyaLista?.listo?.CON_ETIQUETAS === true);

  r = await api(VE, '/api/app/credenciales/pdf', { method: 'POST',
    body: JSON.stringify({ responsables: [suyaCred.responsableId], plantilla: 'CON_ETIQUETAS' }) });
  paso('y la imprime', r.estado === 200 && r.pdf > 10000, `${r.estado}, ${Math.round(r.pdf / 1024)} KB`);

  // Esconder no es impedir: los ids los escribe quien llama, asi que el corte tiene que estar
  // en el servidor. `c` sigue siendo la credencial de la venta que registro admin1.
  r = await api(VE, '/api/app/credenciales/pdf', { method: 'POST',
    body: JSON.stringify({ responsables: [c.responsableId], plantilla: 'QR_GRANDE' }) });
  paso('NO puede imprimir la credencial de una venta ajena, ni pasando el id a mano',
       r.estado === 403, `${r.estado}`);

  r = await api(VE, `/api/app/credenciales/${c.responsableId}/impresiones`);
  paso('ni ver quien imprimio una credencial ajena',
       r.estado === 200 && Array.isArray(r.cuerpo) && r.cuerpo.length === 0,
       `${r.estado}, ${r.cuerpo?.length} fila(s)`);

  // "Imprimir todas" para un vendedor son todas LAS SUYAS, no la feria entera.
  const antesAjena = ((await api(T, `/api/app/credenciales/${c.responsableId}/impresiones`)).cuerpo || []).length;
  r = await api(VE, '/api/app/credenciales/pdf', { method: 'POST',
    body: JSON.stringify({ plantilla: 'CON_ETIQUETAS' }) });
  paso('la generacion masiva le sale solo con lo suyo', r.estado === 200, `${r.estado}`);
  const despuesAjena = ((await api(T, `/api/app/credenciales/${c.responsableId}/impresiones`)).cuerpo || []).length;
  paso('y no ha tocado ninguna credencial ajena', despuesAjena === antesAjena,
       `${antesAjena} -> ${despuesAjena}`);

  titulo('Limpieza del vendedor');
  await api(T, `/api/app/inscripciones/${insV}/cancelar`, { method: 'POST',
    body: JSON.stringify({ motivo: 'Venta de prueba del vendedor' }) });
  await api(T, `/api/app/vendedores/${vendedorId}/puestos`, { method: 'PUT',
    body: JSON.stringify({ puestoIds: [] }) });
  await api(T, `/api/app/usuarios/${vendedorId}/estado`, { method: 'PATCH',
    body: JSON.stringify({ activo: false }) });
  paso('su venta de prueba queda cancelada',
       !((await api(T, '/api/app/credenciales')).cuerpo || [])
           .some((x) => x.entidad === 'ZZ CLIENTE DEL VENDEDOR'));

  // Desactivar, no borrar: borrado quedaria inalcanzable y la proxima pasada no podria entrar.
  if (usuarioId) await api(T, `/api/app/usuarios/${usuarioId}/estado`, { method: 'PATCH',
    body: JSON.stringify({ activo: false }) });
} finally {
  titulo('Limpieza');
  await limpiar();
  paso('la venta de prueba queda cancelada', !(await dameCred()));
}

console.log(fallos.length ? `\n${fallos.length} paso(s) fallaron:` : '\nTodo paso.');
for (const f of fallos) console.log(`  - ${f}`);
process.exit(fallos.length ? 1 : 0);
