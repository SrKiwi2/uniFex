/*
 * Quien tiene cada caseta, y cuanto lleva del formulario quien la esta registrando.
 *
 * Comprueba las dos mitades de `GET /api/app/puestos/ocupacion`, que se averiguan de forma
 * distinta y por eso se rompen por separado:
 *
 *   - 'T' sale de `puesto.reservado_por_id_usuario`.
 *   - 'O' NO esta en la caseta (al vender se pone a NULL): sale de la inscripcion.
 *
 * Y el avance del formulario, que viaja pegado al latido de presencia: se informa, se lee, y
 * un porcentaje imposible se acota en el servidor en vez de pintar una barra que se sale.
 *
 * Crea su propia reserva y la libera al terminar. NO registra ventas: la mitad 'O' se
 * comprueba sobre lo que ya haya en la base, porque una venta de prueba deja rastro en la
 * inscripcion, en las credenciales y en los totales.
 *
 *   node .claude/skills/unifex-fullstack/scripts/verificar-ocupacion.mjs
 *   opciones: --base http://localhost:7676 --usuario admin1 --clave 'usuario25$'
 */

const args = process.argv.slice(2);
const opcion = (nombre, pordefecto) => {
  const i = args.indexOf(`--${nombre}`);
  return i >= 0 && args[i + 1] ? args[i + 1] : pordefecto;
};

const BASE = opcion('base', 'http://localhost:7676');
const USUARIO = opcion('usuario', 'admin1');
const CLAVE = opcion('clave', 'usuario25$');

let fallos = 0;
// El detalle solo sale cuando FALLA: es el texto que explica por que, y pegarlo tambien a las
// que pasan producia lineas que se contradicen ("ok ... ninguna vendida tiene vendedor").
const ok = (cond, texto, detalle = '') => {
  console.log(`${cond ? '  ok  ' : ' FALLA'} ${texto}${!cond && detalle ? ` — ${detalle}` : ''}`);
  if (!cond) fallos++;
};

const json = async (r) => { try { return await r.json(); } catch { return null; } };

async function main() {
  console.log(`\nOcupacion y avance · ${BASE}\n`);

  // ---- login ----
  const rLogin = await fetch(`${BASE}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ usuario: USUARIO, contrasena: CLAVE }),
  });
  const login = await json(rLogin);
  if (!rLogin.ok || !login?.token) {
    console.error(`No se pudo entrar como ${USUARIO} (${rLogin.status}). Usa --usuario/--clave.`);
    process.exit(1);
  }
  const T = login.token;
  const conToken = (extra = {}) => ({ Authorization: `Bearer ${T}`, ...extra });
  console.log(`Sesion de ${USUARIO} (id ${login.id ?? '?'}, rol ${login.rol ?? '?'})\n`);

  // ---- la ocupacion es privada ----
  console.log('Acceso');
  const rSin = await fetch(`${BASE}/api/app/puestos/ocupacion`);
  ok(rSin.status === 401, 'sin token responde 401 (y no un 302 al login)', `dio ${rSin.status}`);

  // ---- forma de la respuesta ----
  console.log('\nGET /api/app/puestos/ocupacion');
  const rOcup = await fetch(`${BASE}/api/app/puestos/ocupacion`, { headers: conToken() });
  ok(rOcup.ok, 'responde 200', `dio ${rOcup.status}`);
  const ocupacion = (await json(rOcup)) || [];
  ok(Array.isArray(ocupacion), 'devuelve una lista');

  const malFormadas = ocupacion.filter((o) => o.puestoId == null || o.vendedorId == null);
  ok(malFormadas.length === 0, 'ninguna fila viene sin caseta o sin vendedor',
    `${malFormadas.length} mal formadas`);

  const sinNombre = ocupacion.filter((o) => !o.vendedor);
  ok(sinNombre.length === 0, 'todas traen el nombre del vendedor ya armado',
    `${sinNombre.length} sin nombre`);

  const estadosRaros = ocupacion.filter((o) => o.estado !== 'T' && o.estado !== 'O');
  ok(estadosRaros.length === 0, "solo trae casetas en 'T' u 'O'",
    `estados: ${[...new Set(ocupacion.map((o) => o.estado))].join(', ')}`);

  // Una caseta no puede aparecer dos veces: seria "la tienen dos personas".
  const repetidas = ocupacion.map((o) => o.puestoId)
    .filter((id, i, a) => a.indexOf(id) !== i);
  ok(repetidas.length === 0, 'ninguna caseta aparece dos veces', `repetidas: ${repetidas.join(', ')}`);

  // ---- coherencia con el listado de casetas ----
  console.log('\nCoherencia con GET /api/app/puestos');
  const puestos = (await json(await fetch(`${BASE}/api/app/puestos`, { headers: conToken() }))) || [];
  const porId = new Map(puestos.map((p) => [p.id, p]));

  const libresOcupadas = ocupacion.filter((o) => {
    const p = porId.get(o.puestoId);
    return p && p.estado !== o.estado;
  });
  ok(libresOcupadas.length === 0, 'el estado de cada fila coincide con el de su caseta',
    `${libresOcupadas.length} discrepan`);

  // Toda caseta en 'T' tiene dueño conocido: el id ya viaja en el listado, asi que si falta
  // aqui es que el nombre no se pudo resolver y la ficha saldria sin decir quien la tiene.
  const enTramite = puestos.filter((p) => p.estado === 'T');
  const tramiteSinFila = enTramite.filter((p) => !ocupacion.some((o) => o.puestoId === p.id));
  ok(tramiteSinFila.length === 0, `las ${enTramite.length} casetas en tramite traen quien las tiene`,
    `faltan ${tramiteSinFila.length}`);

  const tramiteMal = enTramite.filter((p) => {
    const o = ocupacion.find((x) => x.puestoId === p.id);
    return o && Number(o.vendedorId) !== Number(p.reservadoPor);
  });
  ok(tramiteMal.length === 0, "en 'T', el vendedor coincide con `reservadoPor` de la difusion",
    `${tramiteMal.length} no coinciden`);

  const vendidas = puestos.filter((p) => p.estado === 'O');
  const vendidasConDuenio = vendidas.filter((p) => ocupacion.some((o) => o.puestoId === p.id));
  console.log(`  info  ${vendidas.length} vendidas, ${vendidasConDuenio.length} con vendedor conocido`);
  if (vendidas.length) {
    // Una vendida sin fila significa inscripcion anulada o de otra edicion: es legitimo, pero
    // si son TODAS es que la mitad 'O' de la consulta no esta trayendo nada.
    ok(vendidasConDuenio.length > 0, "la mitad 'O' resuelve al menos una venta",
      'ninguna vendida tiene vendedor: revisa el filtro de edicion activa');
  }

  // ---- reservar de verdad y comprobar que aparece ----
  console.log('\nReservar una caseta y verla aparecer');
  const libre = puestos.find((p) => p.estado === 'L');
  if (!libre) {
    console.log('  info  no hay casetas libres: se salta esta parte');
  } else {
    const rRes = await fetch(`${BASE}/api/app/puestos/${libre.id}/reservar`, {
      method: 'POST', headers: conToken(),
    });
    ok(rRes.ok, `reserva la caseta ${libre.codigo}`, `dio ${rRes.status}`);
    if (rRes.ok) {
      const tras = (await json(await fetch(`${BASE}/api/app/puestos/ocupacion`, { headers: conToken() }))) || [];
      const fila = tras.find((o) => o.puestoId === libre.id);
      ok(Boolean(fila), 'la caseta recien reservada ya sale en la ocupacion');
      ok(fila?.estado === 'T', "sale en estado 'T'", `estado ${fila?.estado}`);
      ok(Number(fila?.vendedorId) === Number(login.id), 'a nombre de quien la reservo',
        `vendedorId ${fila?.vendedorId} vs ${login.id}`);
      ok(Boolean(fila?.vendedor), 'con el nombre resuelto', `vendedor: ${fila?.vendedor}`);
      ok(fila?.desde == null, "`desde` va nulo en 'T' (no hay columna que diga cuando se tomo)",
        `desde: ${fila?.desde}`);

      await fetch(`${BASE}/api/app/puestos/${libre.id}/liberar`, { method: 'POST', headers: conToken() });
      const tras2 = (await json(await fetch(`${BASE}/api/app/puestos/ocupacion`, { headers: conToken() }))) || [];
      ok(!tras2.some((o) => o.puestoId === libre.id), 'al liberarla, desaparece de la ocupacion');
    }
  }

  // ---- avance del formulario por el latido ----
  console.log('\nAvance del formulario (latido de presencia)');
  const latir = (cuerpo) => fetch(`${BASE}/api/app/presencia`, {
    method: 'POST', headers: conToken({ 'Content-Type': 'application/json' }),
    body: JSON.stringify(cuerpo),
  });
  const mio = async () => {
    const lista = (await json(await fetch(`${BASE}/api/app/presencia`, { headers: conToken() }))) || [];
    return lista.find((g) => Number(g.usuarioId) === Number(login.id)) || null;
  };

  ok((await latir({ pantalla: 'venta', titulo: 'Registrar venta', origen: 'WEB', avance: 60, avancePaso: 'Responsables', avanceFaltan: 'C.I. del Responsable 1' })).ok,
    'el latido acepta el avance');
  let yo = await mio();
  ok(yo != null, 'aparezco en el seguimiento');
  ok(yo?.avance === 60, 'devuelve el avance informado', `avance ${yo?.avance}`);
  ok(yo?.avancePaso === 'Responsables', 'devuelve el paso', `paso ${yo?.avancePaso}`);
  ok(yo?.avanceFaltan === 'C.I. del Responsable 1', 'devuelve lo que falta');
  ok(typeof yo?.vendidas === 'number', 'trae cuantas casetas lleva vendidas', `vendidas ${yo?.vendidas}`);

  // Lo manda el cliente: un numero imposible no puede llegar a pintar la barra.
  await latir({ pantalla: 'venta', origen: 'WEB', avance: 320 });
  ok((await mio())?.avance === 100, 'un avance de 320 se acota a 100');
  await latir({ pantalla: 'venta', origen: 'WEB', avance: -5 });
  ok((await mio())?.avance === 0, 'un avance negativo se acota a 0');

  // Un latido sin avance lo borra: quien salio del formulario no puede quedarse congelado
  // "al 60%" para siempre, que se leeria como un vendedor trabado.
  await latir({ pantalla: 'mapa', titulo: 'Mapa de ventas', origen: 'WEB' });
  ok((await mio())?.avance == null, 'sin avance en el latido, deja de informarlo');

  // ---- compatibilidad: el latido viejo no puede romper ----
  ok((await latir({ pantalla: 'inicio', titulo: 'Inicio', origen: 'WEB' })).ok,
    'un latido sin los campos nuevos sigue funcionando (APK sin actualizar)');

  console.log(`\n${fallos === 0 ? 'Todo bien.' : `${fallos} comprobacion(es) fallidas.`}\n`);
  process.exit(fallos === 0 ? 0 : 1);
}

main().catch((e) => { console.error(e); process.exit(1); });
