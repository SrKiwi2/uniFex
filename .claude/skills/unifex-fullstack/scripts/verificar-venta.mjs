/**
 * Humo del circuito de VENTA de punta a punta (Bloque 1) + cancelacion con
 * aprobacion (V10 + V11).
 *
 *   login -> catalogo de tipos -> carrito (varias casetas) -> registrar venta
 *         -> comprobar que las casetas quedaron OCUPADAS
 *         -> reintentar con una caseta ya vendida (debe dar 409 y NO dejar nada a medias)
 *         -> pendientes de comprobante -> recibo PDF -> subir comprobante
 *         -> SOLICITAR la cancelacion con motivo (queda PENDIENTE)
 *         -> la cola del admin la ve, se aprueba, y el vendedor queda habilitado
 *         -> CANCELAR la venta: casetas LIBRES de nuevo, historico de canceladas
 *            con motivo, detalle con auditoria (solicitud/aprobacion/cancelacion)
 *
 * Complementa a verificar-api.mjs, que cubre la reserva suelta. Este cubre lo que convierte
 * el sistema en una venta: la transaccion completa.
 *
 * Deja la base como estaba: al final cancela la inscripcion (las casetas vuelven a LIBRE)
 * y queda en el historico de canceladas con motivo "prueba automatica".
 *
 *   node .claude/skills/unifex-fullstack/scripts/verificar-venta.mjs
 *   opciones: --base http://localhost:7676 --usuario admin1 --clave 'usuario25$'
 *            --admin-usuario admin1 --admin-clave 'usuario25$'  (para aprobar; si el
 *            usuario principal es vendedor ADMINISTRATIVO se necesita uno de administracion)
 */

function args() {
  const a = { base: 'http://localhost:7676', usuario: 'admin1', clave: 'usuario25$', adminUsuario: null, adminClave: null };
  const argv = process.argv.slice(2);
  for (let i = 0; i < argv.length; i++) {
    if (argv[i].startsWith('--')) {
      const k = argv[i].replace(/^--/, '');
      if (k in a) a[k] = argv[++i];
    }
  }
  return a;
}

let fallos = 0;
function paso(desc, ok, detalle = '') {
  console.log(`  [${ok ? 'OK   ' : 'FALLA'}] ${desc}${detalle ? '  -> ' + detalle : ''}`);
  if (!ok) fallos++;
  return ok;
}

async function pedir(base, ruta, { metodo = 'GET', cuerpo, token } = {}) {
  const headers = {};
  if (token) headers.Authorization = `Bearer ${token}`;
  if (cuerpo) headers['Content-Type'] = 'application/json';
  const res = await fetch(base + ruta, {
    method: metodo,
    headers,
    body: cuerpo ? JSON.stringify(cuerpo) : undefined,
    redirect: 'manual', // un 302 aqui es un sintoma, no algo a seguir
  });
  let json = null;
  try { json = await res.json(); } catch { /* puede no traer cuerpo */ }
  return { status: res.status, cuerpo: json };
}

async function main() {
  const { base, usuario, clave, adminUsuario, adminClave } = args();
  console.log(`\nVerificando el circuito de VENTA en ${base}\n`);

  // 1. Login
  let r = await pedir(base, '/api/auth/login', { metodo: 'POST', cuerpo: { usuario, contrasena: clave } });
  const token = r.cuerpo?.token;
  if (!paso('POST /api/auth/login devuelve token', r.status === 200 && !!token, `status=${r.status}`)) {
    return resumen();
  }
  paso('El login devuelve el id del usuario (lo usa el mapa)', r.cuerpo?.id != null, `id=${r.cuerpo?.id}`);
  const esAdministracion = ['SUPER USUARIO', 'ADMINISTRADOR'].includes(r.cuerpo?.rol);
  if (esAdministracion) {
    console.log('\n  AVISO: el usuario es administracion; cancelar directo siempre le funciona.');
    console.log('  Para probar el bloqueo del vendedor, pasa --usuario/--clave de un ADMINISTRATIVO.\n');
  }

  // Token de administracion para aprobar: el mismo si el usuario principal es admin,
  // o el de --admin-usuario/--admin-clave si se pasa (vendedor probando).
  let tokenAdmin = token;
  if (adminUsuario) {
    r = await pedir(base, '/api/auth/login', { metodo: 'POST', cuerpo: { usuario: adminUsuario, contrasena: adminClave } });
    tokenAdmin = r.cuerpo?.token;
    if (!paso('Login de administracion (--admin-usuario/--admin-clave) devuelve token',
        r.status === 200 && !!tokenAdmin, `status=${r.status}`)) {
      return resumen();
    }
  }

  // 2. Catalogo necesario para el formulario
  r = await pedir(base, '/api/app/catalogos/tipos-entidad', { token });
  const tipos = Array.isArray(r.cuerpo) ? r.cuerpo : [];
  if (!paso('GET /catalogos/tipos-entidad responde con opciones', r.status === 200 && tipos.length > 0,
      `status=${r.status} n=${tipos.length}`)) {
    return resumen();
  }

  // 3. Buscar casetas libres CON precio (si valen 0, la venta no prueba nada)
  r = await pedir(base, '/api/app/puestos', { token });
  const libres = (r.cuerpo || []).filter((p) => p.estado === 'L');
  const conPrecio = libres.filter((p) => Number(p.precio) > 0);
  paso('Hay casetas libres', libres.length > 0, `${libres.length} libres de ${(r.cuerpo || []).length}`);
  if (conPrecio.length < 2) {
    console.log('\n  AVISO: hacen falta 2 casetas libres con precio > 0.');
    console.log('  Ponle precio a una categoria en el Editor y vuelve a ejecutar.\n');
    return resumen();
  }
  const elegidas = conPrecio.slice(0, 2);
  const ids = elegidas.map((p) => p.id);
  const esperado = elegidas.reduce((s, p) => s + Number(p.precio), 0);
  console.log(`\n  Casetas de prueba: ${elegidas.map((p) => p.categoria + ' ' + p.codigo).join(', ')}`
    + `  (total esperado ${esperado} Bs)\n`);

  // 4. Carrito
  // Primero se vacia el carrito del usuario: una corrida anterior interrumpida
  // (fallo a mitad) pudo dejar casetas en tramite a su nombre, y el TTL aun no las libero.
  r = await pedir(base, '/api/app/puestos/mi-carrito', { token });
  const sobras = (r.cuerpo?.items || []).map((p) => p.id);
  if (sobras.length > 0) {
    await pedir(base, '/api/app/puestos/carrito', { metodo: 'DELETE', cuerpo: { ids: sobras }, token });
    console.log(`\n  Limpieza: se liberaron ${sobras.length} caseta(s) en tramite de una corrida anterior\n`);
  }

  r = await pedir(base, '/api/app/puestos/carrito', { metodo: 'POST', cuerpo: { ids }, token });
  paso('POST /puestos/carrito reserva las dos', r.status === 200 && r.cuerpo?.logradas?.length === 2,
    `logradas=${r.cuerpo?.logradas?.length}`);

  r = await pedir(base, '/api/app/puestos/mi-carrito', { token });
  paso('GET /puestos/mi-carrito las devuelve con su total',
    r.status === 200 && r.cuerpo?.cantidad === 2 && Number(r.cuerpo?.total) === esperado,
    `cantidad=${r.cuerpo?.cantidad} total=${r.cuerpo?.total}`);

  // 5. Registrar la venta
  const venta = {
    entidadNombre: 'HUMO AUTOMATICO ' + Date.now(),
    nit: '999', descripcion: 'Prueba', objeto: 'Prueba',
    representanteLegal: 'Rep Legal', ciRepresentante: '123',
    tipoEntidadId: tipos[0].id,
    fechaInicio: null, fechaFin: null,
    responsables: [
      { nombre: 'TITULAR', paterno: 'Uno', materno: 'Dos', ci: 'T1', correo: null, celular: null },
      { nombre: 'ACOMPANIANTE', paterno: 'Tres', materno: 'Cuatro', ci: 'A1', correo: null, celular: null },
    ],
    entidadBancaria: 'Banco Humo', numComprobante: 1, pagoContado: false,
    puestos: ids,
  };
  r = await pedir(base, '/api/app/inscripciones', { metodo: 'POST', cuerpo: venta, token });
  const inscripcionId = r.cuerpo?.inscripcionId;
  paso('POST /inscripciones registra la venta', r.status === 200 && r.cuerpo?.ok === true, `status=${r.status}`);
  paso('El total cobrado sale del precio de la categoria', Number(r.cuerpo?.total) === esperado,
    `total=${r.cuerpo?.total} esperado=${esperado}`);

  // 6. Las casetas quedaron ocupadas
  r = await pedir(base, '/api/app/puestos', { token });
  const ocupadas = (r.cuerpo || []).filter((p) => ids.includes(p.id) && p.estado === 'O');
  paso("Las casetas quedaron OCUPADAS ('O')", ocupadas.length === 2, `${ocupadas.length}/2`);

  // 7. Vender otra vez la MISMA caseta: 409 y sin dejar nada a medias
  const repetida = { ...venta, entidadNombre: 'HUMO REPETIDO ' + Date.now() };
  r = await pedir(base, '/api/app/inscripciones', { metodo: 'POST', cuerpo: repetida, token });
  paso('Revender una caseta ya vendida responde 409 (no 200 ni 302)', r.status === 409,
    `status=${r.status}`);

  r = await pedir(base, '/api/app/inscripciones', { token });
  const huerfana = (r.cuerpo || []).some((i) => (i.entidad || '').startsWith('HUMO REPETIDO'));
  paso('La venta rechazada NO dejo entidad a medias', !huerfana);

  // 8. Pendientes de comprobante
  r = await pedir(base, '/api/app/inscripciones/mis-pendientes', { token });
  const pendiente = (r.cuerpo || []).find((p) => p.id === inscripcionId);
  paso('GET /inscripciones/mis-pendientes incluye la venta sin comprobante',
    r.status === 200 && !!pendiente,
    `n=${Array.isArray(r.cuerpo) ? r.cuerpo.length : '-'}`);
  if (pendiente) {
    paso('Trae los dias sin comprobante (la urgencia se calcula)',
      typeof pendiente.diasSinComprobante === 'number', `dias=${pendiente.diasSinComprobante}`);
  }

  // 9. Recibo en PDF
  const resRecibo = await fetch(`${base}/api/app/inscripciones/${inscripcionId}/recibo`, {
    headers: { Authorization: `Bearer ${token}` }, redirect: 'manual',
  });
  const tipo = resRecibo.headers.get('content-type') || '';
  const bytes = resRecibo.ok ? new Uint8Array(await resRecibo.arrayBuffer()) : new Uint8Array();
  // Un PDF de verdad empieza por "%PDF". Comprobar solo el 200 dejaria pasar una pagina
  // de error servida con estado 200.
  const esPdf = bytes.length > 4 && String.fromCharCode(...bytes.slice(0, 4)) === '%PDF';
  paso('GET /inscripciones/{id}/recibo devuelve un PDF de verdad',
    resRecibo.status === 200 && tipo.includes('pdf') && esPdf,
    `status=${resRecibo.status} tipo=${tipo} bytes=${bytes.length}`);

  const ajeno = await fetch(`${base}/api/app/inscripciones/${inscripcionId}/recibo`);
  paso('Sin token, el recibo NO se descarga', ajeno.status === 401 || ajeno.status === 403,
    `status=${ajeno.status}`);

  // 10. Subir el comprobante y comprobar que sale de pendientes
  const datos = new FormData();
  // Un PNG minimo valido basta: lo que se prueba es el circuito, no la imagen.
  datos.append('archivo', new Blob([new Uint8Array([0x89, 0x50, 0x4e, 0x47])], { type: 'image/png' }),
    'comprobante.png');
  const resSubida = await fetch(`${base}/api/app/inscripciones/${inscripcionId}/comprobante`, {
    method: 'POST', headers: { Authorization: `Bearer ${token}` }, body: datos, redirect: 'manual',
  });
  const cuerpoSubida = await resSubida.json().catch(() => ({}));
  paso('POST /inscripciones/{id}/comprobante adjunta el archivo',
    resSubida.status === 200 && cuerpoSubida.ok === true,
    `status=${resSubida.status} ${cuerpoSubida.mensaje ?? ''}`);

  r = await pedir(base, '/api/app/inscripciones/mis-pendientes', { token });
  paso('Con comprobante, la venta ya NO figura como pendiente',
    !(r.cuerpo || []).some((p) => p.id === inscripcionId));

  // 11. Solicitar la cancelacion (V11): el vendedor pide con motivo y queda a la espera
  r = await pedir(base, `/api/app/inscripciones/${inscripcionId}/solicitar-cancelacion`, {
    metodo: 'POST', token, cuerpo: { motivo: 'Cancelacion de prueba automatica' },
  });
  paso('POST /inscripciones/{id}/solicitar-cancelacion con motivo', r.status === 200 && r.cuerpo?.ok === true,
    `status=${r.status} ${r.cuerpo?.mensaje ?? ''}`);
  const solicitudId = r.cuerpo?.solicitudId;
  paso('Devuelve el id de la solicitud (queda en espera)', !!solicitudId, `id=${solicitudId}`);

  // 12. Sin motivo, la solicitud se rechaza (no depende de la UI)
  r = await pedir(base, `/api/app/inscripciones/${inscripcionId}/solicitar-cancelacion`, {
    metodo: 'POST', token, cuerpo: { motivo: '' },
  });
  paso('Solicitar sin motivo responde error', r.status === 400 && r.cuerpo?.ok === false,
    `status=${r.status} ${r.cuerpo?.mensaje ?? ''}`);

  // 13. Una sola solicitud pendiente por venta
  r = await pedir(base, `/api/app/inscripciones/${inscripcionId}/solicitar-cancelacion`, {
    metodo: 'POST', token, cuerpo: { motivo: 'Segundo intento que no debe proceder' },
  });
  paso('No admite dos solicitudes pendientes a la vez', r.status === 400 && r.cuerpo?.ok === false,
    `status=${r.status} ${r.cuerpo?.mensaje ?? ''}`);

  // 14. La solicitud aparece en la cola del admin y en "mis solicitudes"
  r = await pedir(base, '/api/app/solicitudes-cancelacion/pendientes', { token: tokenAdmin });
  const enCola = (r.cuerpo || []).find((s) => s.id === solicitudId);
  paso('GET /solicitudes-cancelacion/pendientes la lista con su venta',
    r.status === 200 && !!enCola, `pendientes=${(r.cuerpo || []).length}`);
  paso('La pendiente trae quien la pidio y el motivo',
    !!enCola?.vendedor && enCola?.motivo === 'Cancelacion de prueba automatica',
    `vendedor=${enCola?.vendedor ?? '-'}`);

  r = await pedir(base, '/api/app/mis-solicitudes-cancelacion', { token });
  paso('GET /mis-solicitudes-cancelacion la incluye', r.status === 200
    && (r.cuerpo || []).some((s) => s.id === solicitudId));

  r = await pedir(base, `/api/app/inscripciones/${inscripcionId}/solicitud-cancelacion`, { token });
  paso('El estado de la venta refleja PENDIENTE', r.status === 200 && r.cuerpo?.estado === 'PENDIENTE',
    `estado=${r.cuerpo?.estado ?? '-'}`);

  // 15. Mientras este pendiente, cancelar directo NO procede (sin aprobacion).
  //     Solo aplica si el usuario NO es administracion (admin puede cancelar directo).
  if (!esAdministracion) {
    r = await pedir(base, `/api/app/inscripciones/${inscripcionId}/cancelar`, {
      metodo: 'POST', token, cuerpo: { motivo: 'Intento sin aprobacion' },
    });
    paso('Cancelar sin solicitud aprobada responde error', r.status === 400 && r.cuerpo?.ok === false,
      `status=${r.status} ${r.cuerpo?.mensaje ?? ''}`);
  }

  // 16. Administracion aprueba: el vendedor queda habilitado
  r = await pedir(base, `/api/app/solicitudes-cancelacion/${solicitudId}/aprobar`, {
    metodo: 'POST', token: tokenAdmin,
  });
  paso('POST /solicitudes-cancelacion/{id}/aprobar', r.status === 200 && r.cuerpo?.ok === true,
    `status=${r.status} ${r.cuerpo?.mensaje ?? ''}`);

  r = await pedir(base, `/api/app/inscripciones/${inscripcionId}/solicitud-cancelacion`, { token });
  paso('Tras la aprobacion el estado pasa a APROBADA', r.status === 200 && r.cuerpo?.estado === 'APROBADA',
    `estado=${r.cuerpo?.estado ?? '-'}`);

  r = await pedir(base, '/api/app/solicitudes-cancelacion/pendientes', { token: tokenAdmin });
  paso('La aprobada ya NO esta en la cola de pendientes',
    !(r.cuerpo || []).some((s) => s.id === solicitudId));

  r = await pedir(base, '/api/app/solicitudes-cancelacion/resueltas', { token: tokenAdmin });
  paso('Aparece en el historico de resueltas',
    (r.cuerpo || []).some((s) => s.id === solicitudId && s.estado === 'APROBADA'));

  // 17. Ahora si: cancelar la venta libera las casetas y queda en el historico
  r = await pedir(base, `/api/app/inscripciones/${inscripcionId}/cancelar`, {
    metodo: 'POST', token, cuerpo: { motivo: 'Cancelacion de prueba automatica' },
  });
  paso('POST /inscripciones/{id}/cancelar con la solicitud aprobada',
    r.status === 200 && r.cuerpo?.ok === true, `status=${r.status} ${r.cuerpo?.mensaje ?? ''}`);
  paso('Responde cuantas casetas libero', r.cuerpo?.puestosLiberados?.length === 2,
    `liberados=${r.cuerpo?.puestosLiberados?.length}`);

  r = await pedir(base, '/api/app/inscripciones', { token });
  paso('La cancelada ya NO figura entre las activas',
    !(r.cuerpo || []).some((i) => i.id === inscripcionId));

  r = await pedir(base, '/api/app/inscripciones?canceladas=true', { token });
  const cancelada = (r.cuerpo || []).find((i) => i.id === inscripcionId);
  paso('La venta cancelada sale en el historico con su motivo',
    r.status === 200 && !!cancelada?.motivoCancelacion,
    `motivo=${cancelada?.motivoCancelacion ?? '-'}`);

  // 18. Las casetas volvieron a LIBRE: el mapa las ofrece de nuevo
  r = await pedir(base, '/api/app/puestos', { token });
  const libresDeNuevo = (r.cuerpo || []).filter((p) => ids.includes(p.id) && p.estado === 'L');
  paso('Las casetas canceladas vuelven a estar LIBRES en el mapa',
    libresDeNuevo.length === 2, `${libresDeNuevo.length}/2`);

  // 19. Detalle con auditoria: solicitud, aprobacion y cancelacion
  r = await pedir(base, `/api/app/inscripciones/${inscripcionId}`, { token });
  const auditoria = (r.cuerpo?.auditoria || []);
  const tiene = (acc) => auditoria.some((a) => a.accion === acc);
  paso('La auditoria registra el ciclo completo (SOLICITUD, APROBACION y CANCELACION)',
    r.status === 200 && tiene('SOLICITUD_CANCELACION') && tiene('APROBACION_CANCELACION')
      && tiene('CANCELACION'),
    `eventos=${auditoria.length}`);
  const cancelEvento = auditoria.find((a) => a.accion === 'CANCELACION');
  paso('La auditoria registra quien, cuando y el origen (WEB/APK)',
    !!cancelEvento?.usuarioNombre && !!cancelEvento?.fecha && !!cancelEvento?.origen,
    `${cancelEvento?.usuarioNombre ?? '-'} · ${cancelEvento?.origen ?? '-'}`);

  console.log(`\n  Inscripcion cancelada: id=${inscripcionId} (solicitud ${solicitudId} aprobada y cancelada)`);
  return resumen();
}

function resumen() {
  if (fallos === 0) {
    console.log('\nTodo paso. El circuito de venta funciona de punta a punta.\n');
  } else {
    console.log(`\n${fallos} comprobacion(es) fallaron.\n`);
  }
  return fallos === 0 ? 0 : 1;
}

main().then((c) => process.exit(c));
