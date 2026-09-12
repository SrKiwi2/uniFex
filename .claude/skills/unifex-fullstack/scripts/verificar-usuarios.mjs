#!/usr/bin/env node
/**
 * Alta de usuarios: lo que pasa cuando un nombre ya se uso.
 *
 * La baja de usuarios es LOGICA — la fila se queda — pero `usuario_username_key` es un indice
 * unico sobre la tabla entera. Mientras la validacion de nombre repetido ignoraba a los dados
 * de baja, crear un usuario con el nombre de uno borrado pasaba el control y reventaba contra
 * el indice de PostgreSQL: al administrador le llegaba un 500 con el nombre de una restriccion
 * dentro, en vez de "ese nombre ya esta en uso". Con 35 vendedores que van y vienen, reutilizar
 * el nombre de alguien que se fue es de lo mas normal.
 *
 *   node .claude/skills/unifex-fullstack/scripts/verificar-usuarios.mjs
 *   opciones: --base http://localhost:7676 --clave '...'
 *
 * Deja un usuario dado de baja llamado `zz_nombre_quemado`. Es a proposito: es el fixture, y
 * volver a intentarlo con ese nombre es justo lo que la prueba comprueba.
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
  if (typeof o.body === 'string') h['Content-Type'] = 'application/json';
  const r = await fetch(B + ruta, { ...o, headers: h });
  const ct = r.headers.get('content-type') || '';
  const txt = await r.text().catch(() => '');
  return { estado: r.status, cuerpo: ct.includes('json') ? (() => { try { return JSON.parse(txt); } catch { return null; } })() : txt };
};

console.log(`\n== Alta de usuarios (${B}) ==`);
const T = (await (await fetch(B + '/api/auth/login', {
  method: 'POST', headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ usuario: arg('usuario', 'admin1'), contrasena: arg('clave', 'VO7xGroB8ag2Qz1B') }),
})).json().catch(() => ({}))).token;
if (!paso('login de administracion', !!T)) process.exit(1);

const NOMBRE = 'zz_nombre_quemado';
const CI = '99999950';
const roles = (await api(T, '/api/app/roles')).cuerpo;
const rol = (Array.isArray(roles) ? roles : roles?.roles || [])
    .find((x) => (x.nombre || '').toUpperCase() === 'CONTROL');
if (!paso('hay un rol con el que dar de alta', !!rol)) process.exit(1);

titulo('Preparar el caso: un usuario dado de baja que conserva su nombre');
const usuarios = (await api(T, '/api/app/usuarios')).cuerpo;
const vivo = (Array.isArray(usuarios) ? usuarios : usuarios?.usuarios || [])
    .find((u) => u.username === NOMBRE);
if (vivo) {
  // De una pasada anterior interrumpida: se da de baja para dejar el escenario como toca.
  await api(T, `/api/app/usuarios/${vivo.id}`, { method: 'DELETE' });
  paso('se da de baja el que habia quedado vivo', true, `id ${vivo.id}`);
} else {
  const porCi = (await api(T, `/api/app/usuarios/personas/por-ci?ci=${CI}`)).cuerpo;
  const cuerpo = { username: NOMBRE, password: 'Prueba.Quemado.2026', rolId: rol.id };
  if (porCi?.existe) cuerpo.personaId = porCi.persona?.id;
  else cuerpo.persona = { nombre: 'ZZQUEMADO', paterno: 'PRUEBA', materno: '', ci: CI,
                          correo: null, celular: '70000050' };
  const alta = await api(T, '/api/app/usuarios', { method: 'POST', body: JSON.stringify(cuerpo) });
  if (alta.estado === 200 || alta.estado === 201) {
    const id = alta.cuerpo?.usuario?.id ?? alta.cuerpo?.id;
    await api(T, `/api/app/usuarios/${id}`, { method: 'DELETE' });
    paso('se crea y se da de baja un usuario para el caso', !!id, `id ${id}`);
  } else {
    // Ya existia de una pasada previa, dado de baja: el escenario ya esta montado.
    paso('el usuario de baja ya estaba de una pasada anterior',
         alta.estado === 400 && /ya esta en uso/i.test(alta.cuerpo?.mensaje || ''),
         alta.cuerpo?.mensaje || `${alta.estado}`);
  }
}
paso('y no aparece en el listado, porque la baja es logica',
     !((await api(T, '/api/app/usuarios')).cuerpo || []).some?.((u) => u.username === NOMBRE));

titulo('Reusar ese nombre');
const repetido = await api(T, '/api/app/usuarios', { method: 'POST', body: JSON.stringify({
  username: NOMBRE, password: 'Otra.Clave.2026', rolId: rol.id,
  persona: { nombre: 'ZZOTRO', paterno: 'PRUEBA', materno: '', ci: '99999951',
             correo: null, celular: '70000051' } }) });
// Un 500 aqui es el fallo que motiva esta prueba: la validacion dejo pasar y salto el indice.
paso('responde 400, no 500', repetido.estado === 400, `${repetido.estado}`);
paso('y lo explica en castellano, sin nombres de restricciones de PostgreSQL',
     /ya esta en uso/i.test(repetido.cuerpo?.mensaje || '')
     && !/constraint|violation|sql/i.test(JSON.stringify(repetido.cuerpo)),
     repetido.cuerpo?.mensaje || '');
paso('y no deja una persona suelta creada a medias',
     (await api(T, '/api/app/usuarios/personas/por-ci?ci=99999951')).cuerpo?.existe !== true);

console.log(fallos.length ? `\n${fallos.length} paso(s) fallaron:` : '\nTodo paso.');
for (const f of fallos) console.log(`  - ${f}`);
process.exit(fallos.length ? 1 : 0);
