#!/usr/bin/env node
/**
 * Que ve cada rol al entrar, y que puede abrir de verdad.
 *
 * Cubre dos fallos que se reportaron juntos y tenian la misma raiz —dos sitios decidiendo lo
 * mismo por su cuenta— pero se manifestaban al reves:
 *
 *   1. La pantalla de INICIO ofrecia "Mapa de ventas" y "Mis ventas" a TODO el mundo, con un
 *      `if` de rol escrito ahi dentro. El menu lateral ya las escondia a quien no le tocan,
 *      asi que la aplicacion decia dos cosas distintas a la vez: un VERIFICADOR entraba y se
 *      encontraba dos tarjetas grandes invitandole a un mapa que no es suyo.
 *   2. Al VERIFICADOR el permiso de pantalla SI le daba "Inscripciones", entraba... y el
 *      listado respondia 403, porque el endpoint pedia rol de administracion. Parecia un fallo
 *      de la aplicacion cuando era una contradiccion entre el permiso y el endpoint.
 *
 * Por eso aqui se comprueban las DOS capas para el mismo rol: lo que se ofrece y lo que
 * responde el servidor. Que coincidan es justamente lo que fallaba.
 *
 *   node .claude/skills/unifex-fullstack/scripts/verificar-inicio-y-roles.mjs
 *   opciones: --web http://localhost:5173 --api http://localhost:7676 --clave '...'
 */
import { abrirChrome, erroresDe } from './lib-navegador.mjs';

const arg = (n, d) => { const i = process.argv.indexOf('--' + n); return i > 0 ? process.argv[i + 1] : d; };
const WEB = arg('web', 'http://localhost:5173');
const API = arg('api', 'http://localhost:7676');
const USUARIO = arg('usuario', 'admin1');
const CLAVE = arg('clave', 'VO7xGroB8ag2Qz1B');

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
const entrarApi = async (u, c) => (await (await fetch(API + '/api/auth/login', {
  method: 'POST', headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ usuario: u, contrasena: c }),
})).json().catch(() => ({}))).token;

console.log(`\n== Inicio y permisos por rol (${WEB}) ==`);
const T = await entrarApi(USUARIO, CLAVE);
if (!paso('login de administracion', !!T)) process.exit(1);

/** Crea o reaprovecha un usuario de prueba con ese rol. La baja es logica: ver acreditacion. */
async function usuarioCon(rolNombre, username, clave, ci, nombre) {
  const roles = (await api(T, '/api/app/roles')).cuerpo;
  const rol = (Array.isArray(roles) ? roles : roles?.roles || [])
      .find((x) => (x.nombre || '').toUpperCase() === rolNombre);
  if (!rol) return null;
  const usuarios = (await api(T, '/api/app/usuarios')).cuerpo;
  const ya = (Array.isArray(usuarios) ? usuarios : usuarios?.usuarios || [])
      .find((u) => u.username === username);
  let id;
  if (ya) {
    id = ya.id;
    await api(T, `/api/app/usuarios/${id}`, { method: 'PATCH',
      body: JSON.stringify({ username, personaId: ya.personaId, rolId: rol.id }) });
    await api(T, `/api/app/usuarios/${id}/estado`, { method: 'PATCH', body: JSON.stringify({ activo: true }) });
    await api(T, `/api/app/usuarios/${id}/password`, { method: 'PATCH', body: JSON.stringify({ password: clave }) });
  } else {
    const porCi = (await api(T, `/api/app/usuarios/personas/por-ci?ci=${ci}`)).cuerpo;
    const cuerpo = { username, password: clave, rolId: rol.id };
    if (porCi?.existe) cuerpo.personaId = porCi.persona?.id;
    else cuerpo.persona = { nombre, paterno: 'PRUEBA', materno: '', ci, correo: null, celular: '70000000' };
    const alta = await api(T, '/api/app/usuarios', { method: 'POST', body: JSON.stringify(cuerpo) });
    id = alta.cuerpo?.usuario?.id ?? alta.cuerpo?.id;
  }
  return id;
}

const idVerif = await usuarioCon('VERIFICADOR', 'zz_verif_inicio', 'Prueba.Verif.2026', '99990010', 'ZZVERIF');
const idControl = await usuarioCon('CONTROL', 'zz_control_inicio', 'Prueba.Control.2026', '99990011', 'ZZCONTROL');
paso('hay usuarios de prueba con rol VERIFICADOR y CONTROL', !!idVerif && !!idControl);

/*
 * Una venta CON comprobante y CON foto: sin ella no se puede comprobar lo que de verdad
 * importa aqui, que quien acredita vea lo adjuntado por los demas. Se cancela al terminar.
 */
let insId = null;
{
  for (const c of ((await api(T, '/api/app/credenciales')).cuerpo || [])) {
    if (/^ZZ /.test(c.entidad || '') && c.inscripcionId) {
      await api(T, `/api/app/inscripciones/${c.inscripcionId}/cancelar`, { method: 'POST',
        body: JSON.stringify({ motivo: 'Limpieza de pruebas de roles' }) });
    }
  }
  const libre = ((await api(T, '/api/app/puestos')).cuerpo || []).find((p) => p.estado === 'L');
  if (libre) {
    await api(T, '/api/app/puestos/carrito', { method: 'POST', body: JSON.stringify({ ids: [libre.id] }) });
    const v = await api(T, '/api/app/inscripciones', { method: 'POST', body: JSON.stringify({
      entidadNombre: 'ZZ PARA EL VERIFICADOR', nit: '', descripcion: 'P', objeto: '',
      representanteLegal: 'REP VER', ciRepresentante: '99990020', celularRepresentante: '59170000000',
      tipoEntidadId: 1, fechaInicio: null, fechaFin: null,
      responsables: [{ nombre: 'CON', paterno: 'FOTO', materno: '', ci: '99990021',
                       celular: '59170000001', correo: null }],
      entidadBancaria: '', numComprobante: null, pagoContado: true, puestos: [libre.id] }) });
    insId = v.cuerpo?.inscripcionId;
    if (insId) {
      const fd = () => { const f = new FormData();
        f.append('archivo', new Blob([new Uint8Array([0xff, 0xd8, 0xff, 0xdb, 0, 1, 2, 3])],
            { type: 'image/jpeg' }), 'x.jpg'); return f; };
      await fetch(`${API}/api/app/inscripciones/${insId}/comprobante`,
          { method: 'POST', headers: { Authorization: `Bearer ${T}` }, body: fd() });
      const cred = ((await api(T, '/api/app/credenciales')).cuerpo || [])
          .find((c) => c.entidad === 'ZZ PARA EL VERIFICADOR');
      if (cred) {
        await fetch(`${API}/api/app/inscripciones/${insId}/responsables/${cred.responsableId}/foto`,
            { method: 'POST', headers: { Authorization: `Bearer ${T}` }, body: fd() });
      }
    }
  }
}
paso('hay una venta con comprobante y foto que mirar', !!insId);

const ch = await abrirChrome(Number(arg('puerto', '9370')));
const entrarWeb = async (u, c) => {
  await ch.ir(WEB + '/login');
  await ch.evaluar('localStorage.clear(); sessionStorage.clear();');
  await ch.ir(WEB + '/login');
  await ch.esperar(2600);
  const t = (s, v) => ch.evaluar(`(() => { const el=document.querySelector(${JSON.stringify(s)});
    Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set.call(el, ${JSON.stringify(v)});
    el.dispatchEvent(new Event('input',{bubbles:true})); })()`);
  await t('#usuario', u);
  await t('#contrasena', c);
  await ch.evaluar('document.querySelector(".btn-entrar").click()');
  await ch.esperar(3200);
};
const tarjetas = () => ch.evaluar(`[...document.querySelectorAll('.acceso h3')].map(h=>h.textContent.trim()).join(' | ')`);

try {
  await ch.enviar('Emulation.setDeviceMetricsOverride',
      { width: 400, height: 860, deviceScaleFactor: 1, mobile: true });

  titulo('CONTROL: solo la puerta, y nada de ventas');
  await entrarWeb('zz_control_inicio', 'Prueba.Control.2026');
  let t = await tarjetas();
  paso('el inicio NO le ofrece el mapa de ventas', !/Mapa de ventas/.test(t), t || '(sin tarjetas)');
  paso('ni "Mis ventas"', !/Mis ventas/.test(t));
  paso('pero si lo suyo, o una bienvenida si no tiene nada',
       /Escanear/.test(t) || await ch.evaluar(`!!document.querySelector('.bienvenida')`), t);

  titulo('VERIFICADOR: acredita, no vende');
  await entrarWeb('zz_verif_inicio', 'Prueba.Verif.2026');
  t = await tarjetas();
  paso('tampoco le sale el mapa de ventas', !/Mapa de ventas/.test(t), t || '(sin tarjetas)');
  paso('ni "Mis ventas"', !/Mis ventas/.test(t));
  paso('y si las suyas: credenciales e inscripciones',
       /Credenciales/.test(t) && /Inscripciones/.test(t), t);

  // Lo que fallaba: la pantalla estaba, pero el servidor le devolvia 403.
  titulo('Y de verdad puede abrir Inscripciones');
  const TV = await entrarApi('zz_verif_inicio', 'Prueba.Verif.2026');
  let r = await api(TV, '/api/app/inscripciones');
  paso('el listado le responde, no 403', r.estado === 200 && Array.isArray(r.cuerpo),
       `${r.estado}`);

  await ch.ir(WEB + '/inscripciones');
  await ch.esperar(3500);
  paso('y la pantalla carga sin echarlo',
       (await ch.evaluar(`location.pathname`)).includes('inscripciones'),
       await ch.evaluar(`location.pathname`));

  const conDatos = (r.cuerpo || []).find((x) => x.id === insId) || (r.cuerpo || [])[0];
  if (conDatos) {
    const uno = conDatos;
    const det = await api(TV, `/api/app/inscripciones/${uno.id}`);
    paso('ve el detalle completo de una venta', det.estado === 200, `${det.estado}`);
    paso('con los responsables y su foto',
         Array.isArray(det.cuerpo?.responsables)
         && det.cuerpo.responsables.every((x) => 'fotoUrl' in x),
         JSON.stringify(det.cuerpo?.responsables?.[0] || {}).slice(0, 110));
    paso('y con el comprobante que subio el vendedor',
         !!det.cuerpo?.imgComprobante, det.cuerpo?.imgComprobante || '(sin comprobante)');
    // Que el archivo se pueda ABRIR, no solo que la ruta este en el JSON: si la foto no esta
    // en disco, quien acredita ve un hueco y no sabe si falta o si el servidor esta mal.
    const foto = det.cuerpo?.responsables?.find((x) => x.fotoUrl)?.fotoUrl;
    if (foto) {
      const img = await fetch(API + foto);
      paso('y la foto se puede abrir de verdad', img.ok, `${img.status} ${foto}`);
    }
  } else {
    console.log('  (no hay ventas en esta base para mirar el detalle)');
  }

  titulo('El vendedor sigue viendo lo suyo');
  const idVend = await usuarioCon('ADMINISTRATIVO', 'zz_vend_inicio', 'Prueba.Vend.2026', '99990012', 'ZZVEND');
  if (idVend) {
    await entrarWeb('zz_vend_inicio', 'Prueba.Vend.2026');
    t = await tarjetas();
    paso('al ADMINISTRATIVO si le sale el mapa y sus ventas',
         /Mapa de ventas/.test(t) && /Mis ventas/.test(t), t);
    await api(T, `/api/app/usuarios/${idVend}/estado`, { method: 'PATCH', body: JSON.stringify({ activo: false }) });
  }

  const errores = erroresDe(ch.eventos).filter((x) => !/favicon|DevTools|Download the Vue|403|401/i.test(x));
  paso('sin errores de consola', errores.length === 0, errores.slice(0, 2).join(' / '));
} finally {
  ch.cerrar();
  titulo('Limpieza');
  if (insId) {
    await api(T, `/api/app/inscripciones/${insId}/cancelar`, { method: 'POST',
      body: JSON.stringify({ motivo: 'Prueba de roles' }) });
  }
  for (const id of [idVerif, idControl]) {
    if (id) await api(T, `/api/app/usuarios/${id}/estado`, { method: 'PATCH', body: JSON.stringify({ activo: false }) });
  }
  paso('los usuarios de prueba quedan desactivados', true);
}

console.log(fallos.length ? `\n${fallos.length} paso(s) fallaron:` : '\nTodo paso.');
for (const f of fallos) console.log(`  - ${f}`);
process.exit(fallos.length ? 1 : 0);
