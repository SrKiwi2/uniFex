#!/usr/bin/env node
/**
 * Humo de extremo a extremo del API de casetas de UniFex.
 *
 * Ejercita el ciclo real que protege contra la doble venta:
 *     login -> listar -> reservar -> re-reservar (debe fallar con 409) -> liberar
 *
 * y comprueba que la cadena de seguridad responde 401 (no un 302 al login) sin
 * token, y que el endpoint WebSocket esta arriba.
 *
 * Requiere el backend corriendo con el perfil dev:
 *     mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
 *
 * Uso:
 *     node .claude/skills/unifex-fullstack/scripts/verificar-api.mjs
 *     node ... --base http://localhost:7676 --usuario admin1 --clave 'usuario25$'
 *
 * Solo usa Node (>=18) y su fetch integrado. No hace falta Python, curl ni jq.
 * Termina con codigo 0 si todo paso, 1 si algo fallo, 2 si no pudo conectar.
 */

const fallos = [];

function args() {
  const a = { base: 'http://localhost:7676', usuario: 'admin1', clave: 'usuario25$' };
  const argv = process.argv.slice(2);
  for (let i = 0; i < argv.length; i++) {
    const clave = argv[i].replace(/^--/, '');
    if (clave in a) a[clave] = argv[++i];
  }
  a.base = a.base.replace(/\/+$/, '');
  return a;
}

/** Devuelve {status, cuerpo}. No lanza ante 4xx/5xx: queremos inspeccionarlos.
 *  redirect:'manual' es deliberado: un 302 aqui es un sintoma, no un exito. */
async function pedir(base, ruta, { metodo = 'GET', cuerpo, token } = {}) {
  const headers = {};
  if (token) headers['Authorization'] = `Bearer ${token}`;
  if (cuerpo !== undefined) headers['Content-Type'] = 'application/json';
  let res;
  try {
    res = await fetch(base + ruta, {
      method: metodo,
      headers,
      redirect: 'manual',
      body: cuerpo === undefined ? undefined : JSON.stringify(cuerpo),
      signal: AbortSignal.timeout(15000),
    });
  } catch (e) {
    console.error(`\n  No se pudo conectar a ${base} -> ${e.message}`);
    console.error('  Levanta el backend:  mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"');
    process.exit(2);
  }
  const texto = await res.text();
  let parsed = texto;
  try { parsed = JSON.parse(texto); } catch { /* respuesta no-JSON: la dejamos como texto */ }
  return { status: res.status, cuerpo: parsed };
}

function paso(descripcion, condicion, detalle = '') {
  console.log(`  [${condicion ? 'OK   ' : 'FALLA'}] ${descripcion}${detalle ? `  -> ${detalle}` : ''}`);
  if (!condicion) fallos.push(descripcion);
  return condicion;
}

function resumen() {
  console.log();
  if (fallos.length) {
    console.log(`${fallos.length} paso(s) fallaron:`);
    for (const f of fallos) console.log(`  - ${f}`);
    console.log('\nNo des el cambio por bueno. Un fallo en reservar/409 significa que la');
    console.log('garantia contra la doble venta esta rota.');
    return 1;
  }
  console.log('Todo paso. El ciclo de reserva es atomico y el WebSocket responde.');
  console.log('Falta la prueba que el script no puede hacer: abre /mapa en dos pestanas,');
  console.log('reserva en una y confirma que la otra se recolorea sola.');
  return 0;
}

async function main() {
  const { base, usuario, clave } = args();
  console.log(`\nVerificando el API de casetas en ${base}\n`);

  // 1. Sin token, un endpoint protegido responde 401 con JSON (no 302 al login).
  let r = await pedir(base, '/api/app/puestos');
  paso('GET /api/app/puestos sin token responde 401', r.status === 401, `status=${r.status}`);
  if (r.status === 302) {
    console.log('       Un 302 significa que la peticion salio de la cadena JWT y la atendio la');
    console.log('       cadena web (sendError -> /error), o que un 500 se redirigio. Ver SecurityConfig.');
  }

  // 2. Login.
  r = await pedir(base, '/api/auth/login', { metodo: 'POST', cuerpo: { usuario, contrasena: clave } });
  const token = r.cuerpo?.token;
  if (!paso('POST /api/auth/login devuelve token', r.status === 200 && !!token, `status=${r.status}`)) {
    console.log(`\n  Respuesta: ${JSON.stringify(r.cuerpo)}`);
    console.log('  Revisa usuario/clave con --usuario y --clave.');
    return resumen();
  }
  console.log(`       rol=${r.cuerpo.rol}  usuario=${r.cuerpo.usuario}`);

  // 3. Listar con token.
  r = await pedir(base, '/api/app/puestos', { token });
  const puestos = r.cuerpo;
  if (!paso('GET /api/app/puestos con token responde 200', r.status === 200 && Array.isArray(puestos), `status=${r.status}`)) {
    return resumen();
  }
  const libres = puestos.filter((p) => p.estado === 'L');
  const ubicadas = puestos.filter((p) => p.mapaX != null).length;
  console.log(`       ${puestos.length} casetas, ${libres.length} libres, ${ubicadas} ubicadas en el plano`);
  if (!paso('Hay al menos una caseta libre para probar', libres.length > 0)) return resumen();

  const caseta = libres[0];
  const id = caseta.id;
  console.log(`\n  Caseta de prueba: ${caseta.categoria} ${caseta.codigo} (id=${id})\n`);

  // 4. Reservar (LIBRE -> EN_TRAMITE).
  r = await pedir(base, `/api/app/puestos/${id}/reservar`, { metodo: 'POST', token });
  paso('POST /reservar sobre una caseta libre responde 200', r.status === 200, `status=${r.status}`);

  // 5. Re-reservar: el UPDATE condicional afecta 0 filas -> 409. Este es EL invariante.
  r = await pedir(base, `/api/app/puestos/${id}/reservar`, { metodo: 'POST', token });
  paso('POST /reservar de nuevo responde 409 (no se puede vender dos veces)', r.status === 409, `status=${r.status}`);

  // 6. El estado quedo en T.
  r = await pedir(base, '/api/app/puestos', { token });
  let actual = r.cuerpo.find?.((p) => p.id === id);
  paso("La caseta figura EN_TRAMITE ('T') tras reservar", actual?.estado === 'T', `estado=${actual?.estado ?? 'no encontrada'}`);
  if (actual?.reservaExpira) console.log(`       reservaExpira=${actual.reservaExpira}`);

  // 7. Liberar (EN_TRAMITE -> LIBRE), para dejar la BD como estaba.
  r = await pedir(base, `/api/app/puestos/${id}/liberar`, { metodo: 'POST', token });
  paso('POST /liberar devuelve la caseta a libre', r.status === 200, `status=${r.status}`);

  r = await pedir(base, '/api/app/puestos', { token });
  actual = r.cuerpo.find?.((p) => p.id === id);
  paso("La caseta volvio a LIBRE ('L')", actual?.estado === 'L', `estado=${actual?.estado ?? 'no encontrada'}`);

  // 8. El WebSocket esta arriba. Se quito SockJS (y con el /ws/info), asi que se
  // comprueba lo que de verdad importa: que el endpoint acepte el upgrade a WebSocket.
  paso('El endpoint /ws acepta la conexion WebSocket', await wsResponde(base));

  return resumen();
}

/** Abre un WebSocket nativo contra /ws y resuelve si llega a abrirse. Node 22+ lo trae de serie. */
function wsResponde(base, msTiempoLimite = 5000) {
  return new Promise((resolve) => {
    let ws;
    try {
      ws = new WebSocket(base.replace(/^http/, 'ws') + '/ws');
    } catch {
      return resolve(false);
    }
    const cerrar = (ok) => { try { ws.close(); } catch {} resolve(ok); };
    const t = setTimeout(() => cerrar(false), msTiempoLimite);
    ws.onopen = () => { clearTimeout(t); cerrar(true); };
    ws.onerror = () => { clearTimeout(t); cerrar(false); };
  });
}

main().then((c) => process.exit(c));
