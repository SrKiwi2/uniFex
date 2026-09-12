#!/usr/bin/env node
/**
 * Permisos por rol: que ve cada uno y que NO.
 *
 * Lo que hay que vigilar aqui es que nadie se quede fuera y que nadie entre de mas:
 *  - el SUPER USUARIO ve TODO siempre y no se puede limitar. Es la salida de emergencia: la
 *    pantalla que arregla los permisos es, ella misma, una pantalla;
 *  - esconder una pantalla NO autoriza nada. El servidor sigue respondiendo 403 a quien no
 *    tiene el rol, escriba la ruta a mano o no.
 *
 *   node .claude/skills/unifex-fullstack/scripts/verificar-permisos.mjs
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
const api = async (t, ruta, o = {}) => {
  const h = { ...(o.headers || {}) };
  if (t) h.Authorization = `Bearer ${t}`;
  if (o.body) h['Content-Type'] = 'application/json';
  const r = await fetch(B + ruta, { ...o, headers: h });
  return { estado: r.status, cuerpo: await r.json().catch(() => null) };
};

console.log(`\n== Permisos por rol (${B}) ==\n`);
const login = await (await fetch(B + '/api/auth/login', {
  method: 'POST', headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ usuario: arg('usuario', 'admin1'), contrasena: arg('clave', 'VO7xGroB8ag2Qz1B') }),
})).json().catch(() => ({}));
const T = login.token;
if (!paso('login de administracion', !!T)) process.exit(1);

const catalogo = (await api(T, '/api/app/permisos/catalogo')).cuerpo || [];
paso('el catalogo de pantallas responde', catalogo.length > 0, `${catalogo.length} pantallas`);
paso('cada pantalla trae clave, titulo y grupo',
     catalogo.every((p) => p.clave && p.titulo && p.grupo));

const matriz = (await api(T, '/api/app/permisos')).cuerpo || [];
paso('la matriz trae todos los roles', matriz.length >= 5, `${matriz.length} roles`);

const superU = matriz.find((r) => /SUPER/.test(r.rol));
paso('existe el SUPER USUARIO y lo ve todo', !!superU && superU.loVeTodo);
paso('y se le devuelven TODAS las pantallas', superU?.pantallas.length === catalogo.length,
     `${superU?.pantallas.length} de ${catalogo.length}`);

// La regla que impide quedarse fuera del propio sistema.
const intento = await api(T, `/api/app/permisos/${superU.rolId}`, {
  method: 'PUT', body: JSON.stringify({ rol: superU.rol, pantallas: [] }) });
paso('NO se puede limitar al SUPER USUARIO', intento.estado === 400,
     `${intento.estado} ${intento.cuerpo?.mensaje || ''}`);

const verif = matriz.find((r) => /VERIFICADOR/.test(r.rol));
paso('existe el rol VERIFICADOR', !!verif);
if (verif) {
  paso('y ve credenciales', verif.pantallas.includes('credenciales'), verif.pantallas.join(', '));
  paso('pero no el editor del plano', !verif.pantallas.includes('editor'));
  paso('ni los usuarios', !verif.pantallas.includes('usuarios'));
}

const control = matriz.find((r) => r.rol === 'CONTROL');
if (control) paso('CONTROL ve el escaner', control.pantallas.includes('escaner'), control.pantallas.join(', '));

// Una clave inventada no se guarda: la tabla solo admite pantallas del catalogo.
if (verif) {
  const antes = [...verif.pantallas];
  const r = await api(T, `/api/app/permisos/${verif.rolId}`, {
    method: 'PUT', body: JSON.stringify({ rol: verif.rol, pantallas: [...antes, 'pantalla-inventada'] }) });
  paso('una pantalla inventada se descarta al guardar',
       r.estado === 200 && !(r.cuerpo?.pantallas || []).includes('pantalla-inventada'));
  await api(T, `/api/app/permisos/${verif.rolId}`, {
    method: 'PUT', body: JSON.stringify({ rol: verif.rol, pantallas: antes }) });
  const vuelta = ((await api(T, '/api/app/permisos')).cuerpo || []).find((x) => x.rolId === verif.rolId);
  paso('y el rol queda como estaba', vuelta.pantallas.length === antes.length);
}

paso('sin sesion no se consultan los permisos', (await api(null, '/api/app/permisos')).estado === 401);

console.log(fallos.length ? `\n${fallos.length} paso(s) fallaron:` : '\nTodo paso.');
for (const f of fallos) console.log(`  - ${f}`);
process.exit(fallos.length ? 1 : 0);
