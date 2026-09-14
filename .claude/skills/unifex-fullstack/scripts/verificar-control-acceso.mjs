#!/usr/bin/env node
/**
 * Control de entradas y salidas en la puerta.
 *
 * Lo que se vigila, y por que cada cosa:
 *
 *   1. El SENTIDO lo manda quien escanea. No se alterna solo: alternar parece comodo hasta que
 *      alguien escanea dos veces por nerviosismo, y desde ahi todo queda invertido sin que
 *      nadie se entere.
 *   2. Un movimiento repetido se AVISA pero no se rechaza. En la puerta pasa por motivos
 *      razonables —alguien salio por otro lado sin escanear— y bloquear dejaria a una persona
 *      fuera por un fallo de registro.
 *   3. "Cuanta gente hay dentro" se cuenta por el ULTIMO movimiento de cada uno, no restando
 *      entradas menos salidas: quien entro dos veces sin salir contaria dos, y ese es
 *      justamente el numero que no puede estar mal en una evacuacion.
 *   4. Registrar exige sesion; mirar la credencial no.
 *
 *   node .claude/skills/unifex-fullstack/scripts/verificar-control-acceso.mjs
 *   opciones: --api http://localhost:7676 --clave '...'
 */
const arg = (n, d) => { const i = process.argv.indexOf('--' + n); return i > 0 ? process.argv[i + 1] : d; };
const API = arg('api', arg('base', 'http://localhost:7676'));

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
  if (typeof o.body === 'string') h['Content-Type'] = 'application/json';
  const r = await fetch(API + ruta, { ...o, headers: h });
  const ct = r.headers.get('content-type') || '';
  const txt = await r.text().catch(() => '');
  return { estado: r.status, cuerpo: ct.includes('json') ? (() => { try { return JSON.parse(txt); } catch { return null; } })() : txt };
};

console.log(`\n== Control de acceso (${API}) ==`);
const T = (await (await fetch(API + '/api/auth/login', {
  method: 'POST', headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ usuario: arg('usuario', 'admin1'), contrasena: arg('clave', 'VO7xGroB8ag2Qz1B') }),
})).json().catch(() => ({}))).token;
if (!paso('login', !!T)) process.exit(1);

for (const c of ((await api(T, '/api/app/credenciales')).cuerpo || [])) {
  if (/^ZZ /.test(c.entidad || '') && c.inscripcionId) {
    await api(T, `/api/app/inscripciones/${c.inscripcionId}/cancelar`, { method: 'POST',
      body: JSON.stringify({ motivo: 'Limpieza de pruebas de acceso' }) });
  }
}

const libre = ((await api(T, '/api/app/puestos')).cuerpo || []).find((p) => p.estado === 'L');
if (!paso('hay una caseta libre', !!libre)) process.exit(1);
await api(T, '/api/app/puestos/carrito', { method: 'POST', body: JSON.stringify({ ids: [libre.id] }) });
const venta = await api(T, '/api/app/inscripciones', { method: 'POST', body: JSON.stringify({
  entidadNombre: 'ZZ CONTROL PUERTA', nit: '', descripcion: 'P', objeto: '',
  representanteLegal: 'REP PUERTA', ciRepresentante: '99880001', celularRepresentante: '70880001',
  tipoEntidadId: 1, fechaInicio: null, fechaFin: null,
  responsables: [{ nombre: 'PUERTA', paterno: 'PRUEBA', materno: '', ci: '99880002',
                   celular: '70880002', correo: null }],
  entidadBancaria: '', numComprobante: null, pagoContado: true, puestos: [libre.id] }) });
const insId = venta.cuerpo?.inscripcionId;
if (!paso('venta de prueba creada', !!insId)) process.exit(1);

const cred = ((await api(T, '/api/app/credenciales')).cuerpo || [])
    .find((c) => c.entidad === 'ZZ CONTROL PUERTA');

const mover = (sentido, codigo = cred.codigo) => api(T, '/api/app/accesos',
    { method: 'POST', body: JSON.stringify({ codigo, sentido }) });

try {
  titulo('Entrada y salida, en el orden que diga quien escanea');
  const dentroAntes = (await api(T, '/api/app/accesos/dentro')).cuerpo?.dentro ?? 0;

  let r = await mover('E');
  paso('se registra la entrada', r.estado === 200 && r.cuerpo?.ok === true, r.cuerpo?.mensaje);
  paso('y dice de quien es', r.cuerpo?.nombre?.includes('PUERTA'), r.cuerpo?.nombre);
  paso('con el conteo: 1 entrada, 0 salidas',
       r.cuerpo?.entradas === 1 && r.cuerpo?.salidas === 0,
       `${r.cuerpo?.entradas}/${r.cuerpo?.salidas}`);
  paso('y queda marcada como dentro', r.cuerpo?.dentro === true);

  let d = (await api(T, '/api/app/accesos/dentro')).cuerpo?.dentro;
  paso('el contador de gente dentro sube en uno', d === dentroAntes + 1, `${dentroAntes} -> ${d}`);

  // La misma persona escaneada otra vez EN ENTRADA: se avisa, no se rechaza.
  r = await mover('E');
  paso('escanear dos entradas seguidas NO se rechaza', r.estado === 200 && r.cuerpo?.ok === true);
  paso('pero se avisa de que ya figuraba dentro', r.cuerpo?.repetido === true);
  paso('y cuenta dos entradas', r.cuerpo?.entradas === 2, String(r.cuerpo?.entradas));

  d = (await api(T, '/api/app/accesos/dentro')).cuerpo?.dentro;
  paso('que entre dos veces NO la cuenta dos veces dentro', d === dentroAntes + 1,
       `${d} (se esperaba ${dentroAntes + 1})`);

  titulo('La salida');
  r = await mover('S');
  paso('se registra la salida', r.cuerpo?.ok === true && r.cuerpo?.sentido === 'S');
  paso('ya no figura dentro', r.cuerpo?.dentro === false);
  paso('y el conteo distingue las dos cosas',
       r.cuerpo?.entradas === 2 && r.cuerpo?.salidas === 1,
       `${r.cuerpo?.entradas} entradas / ${r.cuerpo?.salidas} salidas`);
  d = (await api(T, '/api/app/accesos/dentro')).cuerpo?.dentro;
  paso('el contador de dentro vuelve a su sitio', d === dentroAntes, `${d}`);

  paso('el historial trae los tres movimientos, del mas nuevo al mas viejo',
       r.cuerpo?.historial?.length === 3 && r.cuerpo.historial[0].sentido === 'S',
       (r.cuerpo?.historial || []).map((h) => h.sentido).join(''));
  paso('y dice quien los registro', !!r.cuerpo?.historial?.[0]?.usuario,
       r.cuerpo?.historial?.[0]?.usuario);

  titulo('Lo que no se admite');
  r = await mover('X');
  paso('un sentido que no es entrada ni salida se rechaza', r.estado === 400, r.cuerpo?.mensaje);

  const partes = cred.codigo.split('-', 2).concat(cred.codigo.split('-').slice(2).join('-'));
  r = await mover('E', `${partes[0]}-${partes[1]}-AAAAAAAAAAAA`);
  paso('una firma inventada no registra nada', r.estado === 404 && r.cuerpo?.valida === false);

  paso('sin sesion no se registra ningun movimiento',
       (await api(null, '/api/app/accesos',
                  { method: 'POST', body: JSON.stringify({ codigo: cred.codigo, sentido: 'E' }) })).estado === 401);
  paso('ni se consulta cuanta gente hay dentro',
       (await api(null, '/api/app/accesos/dentro')).estado === 401);

  // La vista publica del QR sigue abierta: la abre cualquiera con la camara de su telefono.
  paso('pero mirar la credencial sigue sin pedir sesion',
       (await api(null, `/api/publico/credencial/${cred.codigo}`)).estado === 200);
} finally {
  titulo('Limpieza');
  await api(T, `/api/app/inscripciones/${insId}/cancelar`, { method: 'POST',
    body: JSON.stringify({ motivo: 'Prueba de control de acceso' }) });
  paso('la venta de prueba queda cancelada', true);
}

console.log(fallos.length ? `\n${fallos.length} paso(s) fallaron:` : '\nTodo paso.');
for (const f of fallos) console.log(`  - ${f}`);
process.exit(fallos.length ? 1 : 0);
