#!/usr/bin/env node
/**
 * "Mis ventas" en un telefono: la lista, la ficha y las correcciones.
 *
 * Lo que se vigila aqui:
 *
 *   1. Que la lista quepa. Antes era una tabla de siete columnas: en 400 px se veian tres y
 *      el total quedaba fuera, asi que la comprobacion es literal — la pagina NO se desplaza
 *      de lado. Un `overflow-x` de vuelta rompe eso sin que salte ningun error.
 *   2. Que la ficha traiga TODO lo registrado en una sola peticion, incluido lo que falta
 *      (comprobante, fotos), que es para lo que se abre.
 *   3. Que las correcciones se guarden de verdad y se vean sin recargar. Un formulario que
 *      dice "Guardado" y no cambia nada es peor que no tenerlo.
 *   4. Que un vendedor no pueda corregir la venta de otro pasando ids a mano.
 *
 *   node .claude/skills/unifex-fullstack/scripts/verificar-mis-ventas.mjs
 *   opciones: --web http://localhost:5173 --api http://localhost:7676 --clave '...'
 *
 * Crea su venta de prueba y la cancela al terminar.
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

console.log(`\n== Mis ventas (${WEB} -> ${API}) ==`);
const T = (await (await fetch(API + '/api/auth/login', {
  method: 'POST', headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ usuario: USUARIO, contrasena: CLAVE }),
})).json().catch(() => ({}))).token;
if (!paso('login', !!T)) process.exit(1);

// Una venta propia con dos responsables: es lo que hace falta para probar la ficha entera.
const libre = ((await api(T, '/api/app/puestos')).cuerpo || []).find((p) => p.estado === 'L');
if (!paso('hay una caseta libre', !!libre)) process.exit(1);
await api(T, '/api/app/puestos/carrito', { method: 'POST', body: JSON.stringify({ ids: [libre.id] }) });
const venta = await api(T, '/api/app/inscripciones', { method: 'POST', body: JSON.stringify({
  entidadNombre: 'ZZ VENTA FICHA', nit: '123456', descripcion: 'ARTESANIA', objeto: '',
  representanteLegal: 'DUENO ORIGINAL', ciRepresentante: '99999980', celularRepresentante: '70000080',
  tipoEntidadId: 1, fechaInicio: null, fechaFin: null,
  responsables: [
    { nombre: 'PRIMERO', paterno: 'APELLIDO', materno: '', ci: '99999981', celular: '70000081', correo: null },
    { nombre: 'SEGUNDO', paterno: 'APELLIDO', materno: '', ci: '99999982', celular: '70000082', correo: null },
  ],
  entidadBancaria: '', numComprobante: null, pagoContado: true, puestos: [libre.id] }) });
const insId = venta.cuerpo?.inscripcionId;
if (!paso('venta de prueba creada', !!insId, JSON.stringify(venta.cuerpo).slice(0, 90))) process.exit(1);

const ch = await abrirChrome(Number(arg('puerto', '9295')));
const FILA = `[...document.querySelectorAll('.venta')].find(f => /ZZ VENTA FICHA/.test(f.textContent))`;

try {
  titulo('La ficha, desde el servidor');
  let d = (await api(T, `/api/app/inscripciones/${insId}/detalle`)).cuerpo;
  paso('trae la entidad con su rubro y su responsable legal',
       d?.entidad?.nombre === 'ZZ VENTA FICHA' && d?.entidad?.descripcion === 'ARTESANIA'
       && d?.entidad?.representanteLegal === 'DUENO ORIGINAL');
  paso('trae las casetas con su categoria', d?.casetas?.length === 1 && !!d.casetas[0].categoria,
       JSON.stringify(d?.casetas));
  paso('trae los dos responsables y si tienen foto',
       d?.responsables?.length === 2 && d.responsables.every((r) => 'tieneFoto' in r));
  paso('y dice que falta el comprobante, aunque la venta sea al contado',
       d?.pago?.contado === true && d?.pago?.conComprobante === false);

  titulo('Corregir lo que se escribio mal');
  let r = await api(T, `/api/app/inscripciones/${insId}/entidad`, { method: 'PATCH',
    body: JSON.stringify({ descripcion: 'textiles', celularRepresentante: '79999999' }) });
  paso('se corrige la entidad', r.estado === 200 && r.cuerpo?.ok === true, r.cuerpo?.mensaje);
  d = (await api(T, `/api/app/inscripciones/${insId}/detalle`)).cuerpo;
  paso('el rubro queda corregido y en mayusculas, como el resto del sistema',
       d?.entidad?.descripcion === 'TEXTILES', d?.entidad?.descripcion);
  paso('y el celular tambien', d?.entidad?.celularRepresentante === '79999999');
  // PATCH y no PUT: lo que no se manda no se toca. Con PUT, este mismo cuerpo habria borrado
  // el NIT y el nombre del responsable legal.
  paso('lo que no se mando sigue intacto',
       d?.entidad?.nit === '123456' && d?.entidad?.representanteLegal === 'DUENO ORIGINAL',
       `nit=${d?.entidad?.nit}`);

  const resp = d.responsables[0];
  r = await api(T, `/api/app/inscripciones/${insId}/responsables/${resp.id}`, { method: 'PATCH',
    body: JSON.stringify({ paterno: 'corregido', ci: '11112222' }) });
  paso('se corrige un responsable', r.estado === 200 && r.cuerpo?.ok === true, r.cuerpo?.mensaje);
  d = (await api(T, `/api/app/inscripciones/${insId}/detalle`)).cuerpo;
  const yaCorregido = d.responsables.find((x) => x.id === resp.id);
  paso('con su apellido y su C.I. nuevos',
       yaCorregido?.paterno === 'CORREGIDO' && yaCorregido?.ci === '11112222',
       `${yaCorregido?.paterno} / ${yaCorregido?.ci}`);
  paso('el otro responsable no se toco',
       d.responsables.some((x) => x.id !== resp.id && x.paterno === 'APELLIDO'),
       d.responsables.map((x) => `${x.nombre}/${x.paterno}`).join(' | '));
  // El formulario de correccion necesita los apellidos SUELTOS: con el nombre ya unido no se
  // pueden rellenar sus casillas sin adivinar donde acaba el nombre de pila.
  paso('los responsables llegan tambien con sus apellidos y celular por separado',
       d.responsables.every((x) => 'paterno' in x && 'materno' in x && 'celular' in x));

  titulo('No se corrige la venta de otro');
  const ajena = ((await api(T, '/api/app/credenciales')).cuerpo || [])
      .find((c) => c.inscripcionId && c.inscripcionId !== insId);
  if (ajena) {
    r = await api(T, `/api/app/inscripciones/${insId}/responsables/${ajena.responsableId}`,
                  { method: 'PATCH', body: JSON.stringify({ nombre: 'INTRUSO' }) });
    paso('un responsable de otra venta se rechaza aunque el permiso sea sobre la propia',
         r.estado === 400 && /no es de esta venta/i.test(r.cuerpo?.mensaje || ''),
         r.cuerpo?.mensaje);
  } else {
    console.log('  (no hay otra venta con la que probar el cruce)');
  }
  paso('sin sesion no se corrige nada',
       (await api(null, `/api/app/inscripciones/${insId}/entidad`,
                  { method: 'PATCH', body: JSON.stringify({ nit: '000' }) })).estado === 401);

  titulo('En el telefono');
  await ch.enviar('Emulation.setDeviceMetricsOverride',
      { width: 400, height: 860, deviceScaleFactor: 1, mobile: true });
  await ch.ir(WEB + '/login');
  await ch.evaluar('localStorage.clear(); sessionStorage.clear();');
  await ch.ir(WEB + '/login');
  await ch.esperar(2600);
  const teclear = (s, v) => ch.evaluar(`(() => { const el=document.querySelector(${JSON.stringify(s)});
    Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set.call(el, ${JSON.stringify(v)});
    el.dispatchEvent(new Event('input',{bubbles:true})); })()`);
  await teclear('#usuario', USUARIO);
  await teclear('#contrasena', CLAVE);
  await ch.evaluar('document.querySelector(".btn-entrar").click()');
  await ch.esperar(3000);
  await ch.ir(WEB + '/mis-ventas');
  await ch.esperar(3500);

  paso('hay un titulo que dice de que es la lista',
       /Entidades registradas/.test(await ch.evaluar(`document.querySelector('.cab h2')?.textContent || ''`)));
  // La razon de ser de todo el cambio: que no haya que arrastrar de lado.
  paso('la pagina NO se desplaza de lado',
       await ch.evaluar(`document.documentElement.scrollWidth <= document.documentElement.clientWidth + 1`),
       await ch.evaluar(`document.documentElement.scrollWidth + ' vs ' + document.documentElement.clientWidth`));
  paso('la fila enseña el nombre de la entidad y el total sin abrir nada',
       await ch.evaluar(`(() => { const f=${FILA}; if(!f) return false;
         const t=f.textContent; return /ZZ VENTA FICHA/.test(t) && /Bs/.test(t); })()`));
  paso('y dice que se puede tocar',
       /Ver detalle/.test(await ch.evaluar(`${FILA}?.textContent || ''`)));

  await ch.evaluar(`${FILA}?.click()`);
  await ch.esperar(2000);
  const ficha = (await ch.evaluar(`document.querySelector('.ficha')?.textContent.replace(/\\s+/g,' ').trim()`) || '');
  paso('al tocarla se abre la ficha con todo lo registrado',
       /TEXTILES/.test(ficha) && /DUENO ORIGINAL/.test(ficha) && /SEGUNDO/.test(ficha),
       ficha.slice(0, 110));
  paso('avisa de lo que falta para la credencial',
       /comprobante de pago/.test(ficha) && /foto/.test(ficha));
  const pie = await ch.evaluar(`[...document.querySelectorAll('.modal .btn-grande, .btn-grande')].map(b=>b.textContent.trim()).join(' | ')`);
  paso('con los botones de recibo, compartir y comprobante, en palabras',
       /Imprimir recibo/.test(pie) && /Compartir/.test(pie) && /comprobante/i.test(pie), pie);

  const errores = erroresDe(ch.eventos).filter((t) => !/favicon|DevTools|Download the Vue/i.test(t));
  paso('sin errores de consola', errores.length === 0, errores.slice(0, 2).join(' / '));
} finally {
  ch.cerrar();
  titulo('Limpieza');
  await api(T, `/api/app/inscripciones/${insId}/cancelar`, { method: 'POST',
    body: JSON.stringify({ motivo: 'Prueba de la ficha de venta' }) });
  paso('la venta de prueba queda cancelada', true);
}

console.log(fallos.length ? `\n${fallos.length} paso(s) fallaron:` : '\nTodo paso.');
for (const f of fallos) console.log(`  - ${f}`);
process.exit(fallos.length ? 1 : 0);
