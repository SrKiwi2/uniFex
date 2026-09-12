#!/usr/bin/env node
/**
 * La pantalla de Credenciales, en un navegador de verdad.
 *
 * Existe por un fallo concreto: se cambio el contrato del servidor —"listo" y "faltantes"
 * pasaron de ser un valor suelto a venir POR PLANTILLA— y la plantilla Vue siguio leyendo los
 * campos viejos. `npm run build` compilo sin una queja, porque `c.apto` sobre un objeto sin ese
 * campo no es un error de compilacion, es `undefined`. Solo abriendo la pagina se ve que todas
 * las filas salen bloqueadas.
 *
 * Asi que esto no comprueba estilos: comprueba que lo que el servidor manda LLEGA a la pantalla.
 *
 *   node .claude/skills/unifex-fullstack/scripts/verificar-pantalla-credenciales.mjs
 *   opciones: --web http://localhost:5173 --api http://localhost:7676 --clave '...'
 *
 * Crea una venta de prueba para tener una fila incompleta que mirar, y la cancela al terminar.
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

console.log(`\n== Pantalla de credenciales (${WEB} -> ${API}) ==`);
const T = (await (await fetch(API + '/api/auth/login', {
  method: 'POST', headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ usuario: USUARIO, contrasena: CLAVE }),
})).json().catch(() => ({}))).token;
if (!paso('login', !!T)) process.exit(1);

// Una venta al contado y sin recibo: la fila mas interesante, porque es la que el flujo de
// verificacion tiene que mostrar como incompleta y dejar completar ahi mismo.
const libre = ((await api(T, '/api/app/puestos')).cuerpo || []).find((p) => p.estado === 'L');
if (!paso('hay una caseta libre para la venta de prueba', !!libre)) process.exit(1);
await api(T, '/api/app/puestos/carrito', { method: 'POST', body: JSON.stringify({ ids: [libre.id] }) });
const venta = await api(T, '/api/app/inscripciones', { method: 'POST', body: JSON.stringify({
  entidadNombre: 'ZZ PRUEBA PANTALLA', nit: '', descripcion: 'PRUEBA', objeto: '',
  representanteLegal: 'PRUEBA LEGAL', ciRepresentante: '99999908', celularRepresentante: '70000008',
  tipoEntidadId: 1, fechaInicio: null, fechaFin: null,
  responsables: [{ nombre: 'ZZPANTALLA', paterno: 'PRUEBA', materno: '', ci: '99999909',
                   celular: '70000009', correo: null }],
  entidadBancaria: '', numComprobante: null, pagoContado: true, puestos: [libre.id] }) });
const insId = venta.cuerpo?.inscripcionId;
if (!paso('venta de prueba creada', !!insId, JSON.stringify(venta.cuerpo).slice(0, 100))) process.exit(1);

const ch = await abrirChrome(Number(arg('puerto', '9260')));
// Localiza la fila de la venta de prueba por su entidad, no por posicion.
const FILA = `[...document.querySelectorAll('.fila')].find(f => /ZZ PRUEBA PANTALLA/.test(f.textContent))`;

try {
  await ch.enviar('Emulation.setDeviceMetricsOverride',
      { width: 1400, height: 950, deviceScaleFactor: 1, mobile: false });

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

  titulo('La pantalla carga y la fila incompleta se ve como tal');
  await ch.ir(WEB + '/credenciales');
  await ch.esperar(3500);
  paso('monta la vista', await ch.evaluar('!!document.querySelector(".credenciales")'));

  // Por defecto el filtro es "listas", asi que la incompleta no deberia estar. Se pasa a
  // "pendientes", que es la pestaña donde trabaja el verificador.
  paso('la incompleta NO aparece entre las listas', !(await ch.evaluar(`!!(${FILA})`)));
  await ch.evaluar(`[...document.querySelectorAll('.resumen .tarjeta')]
      .find(b => /falta/i.test(b.textContent))?.click()`);
  await ch.esperar(800);
  paso('aparece al pasar a pendientes', await ch.evaluar(`!!(${FILA})`));

  const insignias = await ch.evaluar(`(${FILA})?.querySelector('.estado')?.textContent.trim().replace(/\\s+/g,' ')`);
  paso('dice exactamente que le falta, y no "Lista"',
       /sin comprobante/.test(insignias || '') && /sin foto/.test(insignias || '')
       && !/Lista/.test(insignias || ''), insignias);

  titulo('Lo que falta se puede completar sin salir de aqui');
  const acciones = await ch.evaluar(`[...((${FILA})?.querySelectorAll('.acciones label, .acciones button')||[])]
      .map(e => e.textContent.trim()).join(' | ')`);
  paso('ofrece adjuntar el comprobante', /Comprobante/.test(acciones || ''), acciones);
  paso('ofrece adjuntar la foto', /Foto/.test(acciones || ''));
  paso('y deja imprimirla igual, avisando', /igual/.test(acciones || ''));
  paso('los dos selectores de archivo apuntan a esa fila y no a otra',
       await ch.evaluar(`(() => { const f=${FILA}; const ids=[...f.querySelectorAll('input[type=file]')].map(i=>i.id);
         return ids.length === 2 && ids.every(i => /^(comp|foto)-\\d+$/.test(i)) && new Set(ids).size === 2; })()`));

  titulo('La plantilla elegida cambia lo que hace falta');
  // Con QR grande la foto no se pide: la misma fila debe quedarse solo con el comprobante.
  await ch.evaluar(`[...document.querySelectorAll('.opciones .opcion')]
      .find(e => /QR grande/i.test(e.textContent))?.click()`);
  await ch.esperar(700);
  const conQr = await ch.evaluar(`(${FILA})?.querySelector('.estado')?.textContent.trim().replace(/\\s+/g,' ')`);
  paso('con QR grande ya no se le exige la foto',
       /sin comprobante/.test(conQr || '') && !/sin foto/.test(conQr || ''), conQr);

  titulo('Imprimir incompleta queda registrado, y la pantalla lo enseña');
  await api(T, '/api/app/credenciales/pdf', { method: 'POST', body: JSON.stringify({
    responsables: [((await api(T, '/api/app/credenciales')).cuerpo || [])
        .find((c) => c.entidad === 'ZZ PRUEBA PANTALLA').responsableId],
    plantilla: 'QR_GRANDE', forzar: true }) });
  await ch.ir(WEB + '/credenciales');
  await ch.esperar(3500);
  await ch.evaluar(`[...document.querySelectorAll('.resumen .tarjeta')]
      .find(b => /falta/i.test(b.textContent))?.click()`);
  await ch.esperar(800);
  paso('la fila se marca como impresa incompleta',
       /impresa incompleta/i.test(await ch.evaluar(`(${FILA})?.querySelector('.estado')?.textContent || ''`)));

  await ch.evaluar(`(${FILA})?.querySelector('.estado button')?.click()`);
  await ch.esperar(1200);
  const panel = await ch.evaluar(`document.querySelector('.historial')?.textContent.replace(/\\s+/g,' ').trim()`);
  paso('y al tocarla se abre quien la imprimio y que faltaba',
       !!panel && new RegExp(USUARIO).test(panel) && /falta/i.test(panel), (panel || '').slice(0, 120));

  const errores = erroresDe(ch.eventos).filter((t) => !/favicon|DevTools|Download the Vue|409/i.test(t));
  paso('la pagina no tira errores por consola', errores.length === 0, errores.slice(0, 2).join(' / '));
} finally {
  ch.cerrar();
  titulo('Limpieza');
  await api(T, `/api/app/inscripciones/${insId}/cancelar`, { method: 'POST',
    body: JSON.stringify({ motivo: 'Prueba de pantalla de credenciales' }) });
  paso('la venta de prueba queda cancelada',
       !((await api(T, '/api/app/credenciales')).cuerpo || []).some((c) => c.entidad === 'ZZ PRUEBA PANTALLA'));
}

console.log(fallos.length ? `\n${fallos.length} paso(s) fallaron:` : '\nTodo paso.');
for (const f of fallos) console.log(`  - ${f}`);
process.exit(fallos.length ? 1 : 0);
