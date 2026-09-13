#!/usr/bin/env node
/**
 * El formulario de venta y la ficha, en un telefono, con lo que se pidio desde la feria.
 *
 * Cada comprobacion viene de un tropiezo real de un vendedor con el APK en la mano:
 *
 *   1. La cabecera decia "2 casetas · 100 Bs" y no CUALES. El numero de caseta es como se
 *      nombra lo que se esta vendiendo delante del cliente.
 *   2. "Es el mismo responsable legal" metia el nombre completo en la casilla del nombre y
 *      dejaba los apellidos vacios — y eso se imprime en la credencial. Ademas bloqueaba los
 *      campos, asi que un reparto mal hecho no habia forma de corregirlo.
 *   3. El aviso del comprobante mandaba a "Mis pendientes", que no existe como pantalla.
 *   4. La ficha de la venta no dejaba VER el comprobante subido, solo reemplazarlo.
 *
 *   node .claude/skills/unifex-fullstack/scripts/verificar-registro-venta.mjs
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

console.log(`\n== Registro de venta y ficha (${WEB}) ==`);
const T = (await (await fetch(API + '/api/auth/login', {
  method: 'POST', headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ usuario: USUARIO, contrasena: CLAVE }),
})).json().catch(() => ({}))).token;
if (!paso('login', !!T)) process.exit(1);

// Dos casetas en el carrito: hace falta mas de una para ver el "6 y 7".
const libres = ((await api(T, '/api/app/puestos')).cuerpo || []).filter((p) => p.estado === 'L').slice(0, 2);
if (!paso('hay dos casetas libres', libres.length === 2)) process.exit(1);
await api(T, '/api/app/puestos/carrito', { method: 'POST', body: JSON.stringify({ ids: libres.map((p) => p.id) }) });
const codigos = libres.map((p) => String(p.codigo));

const ch = await abrirChrome(Number(arg('puerto', '9305')));
let insId = null;

try {
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
  await ch.esperar(3200);

  titulo('La cabecera dice QUE se esta vendiendo');
  await ch.ir(WEB + '/venta');
  await ch.esperar(3000);
  const cab = (await ch.evaluar(`document.querySelector('.resumen')?.textContent.replace(/\\s+/g,' ').trim()`) || '');
  paso('sale el nombre de la categoria', new RegExp(libres[0].categoria, 'i').test(cab), cab.slice(0, 110));
  paso('y los numeros de caseta, no solo cuantas son',
       codigos.every((c) => cab.includes(c)), `esperaba ${codigos.join(' y ')}`);
  paso('con el total', /Total/.test(cab));

  titulo('Ayuda por campo');
  paso('cada campo tiene su "?"',
       (await ch.evaluar(`document.querySelectorAll('.ayuda').length`)) >= 6,
       `${await ch.evaluar(`document.querySelectorAll('.ayuda').length`)} botones`);
  await ch.evaluar(`document.querySelector('.ayuda')?.click()`);
  await ch.esperar(900);
  paso('y al tocarlo se abre la explicacion en el modal del centro',
       await ch.evaluar(`!!document.querySelector('.overlay .dialogo')`)
       && await ch.evaluar(`document.body.textContent.includes('aparecer en la feria')`));
  await ch.evaluar(`[...document.querySelectorAll('button')].find(b=>/^Ok$/i.test(b.textContent.trim()))?.click()`);
  await ch.esperar(600);

  titulo('"Es el mismo responsable legal" reparte bien el nombre');
  await teclear('.mayus', 'ZZ PRUEBA FORMULARIO');
  await ch.evaluar(`(() => { const s=document.querySelector('select.control');
    s.selectedIndex = 1; s.dispatchEvent(new Event('change',{bubbles:true})); })()`);
  // Nombre de pila doble a proposito: es el caso que la division tiene que resolver sola.
  const legal = 'MARIA LUISA QUISPE MAMANI';
  await ch.evaluar(`(() => {
    const campos=[...document.querySelectorAll('.control')];
    const poner=(el,v)=>{Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set.call(el,v);
      el.dispatchEvent(new Event('input',{bubbles:true}));};
    const etiqueta=(t)=>campos.find(c=>c.closest('.campo')?.textContent.trim().startsWith(t));
    poner(etiqueta('Nombre completo'), ${JSON.stringify(legal)});
    poner(etiqueta('C.I.'), '99123456');
    poner(etiqueta('Celular'), '70123456');
  })()`);
  await ch.esperar(500);
  await ch.evaluar(`[...document.querySelectorAll('.acciones .btn')].find(b=>/Siguiente/.test(b.textContent))?.click()`);
  await ch.esperar(1200);
  await ch.evaluar(`document.querySelector('.fila-check input[type=checkbox]')?.click()`);
  await ch.esperar(800);

  /*
   * Los tres pasos del formulario estan SIEMPRE en el DOM (`v-show`, no `v-if`), asi que
   * buscar por clase a secas encuentra tambien los campos de los pasos ocultos: "Nombre"
   * casaba con "Nombre de la entidad" del paso 1. Se mira solo dentro de la seccion visible.
   */
  const visible = `[...document.querySelectorAll('section.bloque')].find(s => s.offsetParent !== null)`;
  const leer = (t) => ch.evaluar(`(() => { const s=${visible}; if(!s) return '(sin seccion)';
    const c=[...s.querySelectorAll('.campo')]
      .find(x=>x.querySelector('span')?.textContent.trim().startsWith(${JSON.stringify(t)}));
    return c ? c.querySelector('.control')?.value : '(no hay campo)'; })()`);
  paso('el nombre de pila queda completo y sin apellidos',
       (await leer('Nombre')) === 'MARIA LUISA', await leer('Nombre'));
  paso('el apellido paterno es la penultima palabra',
       (await leer('Apellido paterno')) === 'QUISPE', await leer('Apellido paterno'));
  paso('y el materno la ultima',
       (await leer('Apellido materno')) === 'MAMANI', await leer('Apellido materno'));
  paso('los campos NO se bloquean: un reparto malo se puede corregir',
       await ch.evaluar(`(() => { const s=${visible};
         return s ? ![...s.querySelectorAll('.campo .control')].some(c=>c.disabled) : false; })()`));

  titulo('El segundo responsable no necesita C.I.');
  await ch.evaluar(`[...document.querySelectorAll('.bloque .btn')].find(b=>/Agregar Responsable 2/.test(b.textContent))?.click()`);
  await ch.esperar(700);
  await ch.evaluar(`(() => { const s=${visible};
    const campos=[...s.querySelectorAll('.campo')];
    const c=campos.filter(x=>x.querySelector('span')?.textContent.trim().startsWith('Nombre')).pop();
    const el=c.querySelector('.control');
    Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set.call(el,'SEGUNDO');
    el.dispatchEvent(new Event('input',{bubbles:true})); })()`);
  await ch.esperar(400);
  await ch.evaluar(`[...document.querySelectorAll('.acciones .btn')].find(b=>/Siguiente/.test(b.textContent))?.click()`);
  await ch.esperar(1000);
  paso('con solo el nombre del segundo responsable, deja continuar',
       await ch.evaluar(`(() => { const s=${visible};
         return s ? /Qué se está vendiendo/.test(s.textContent) : false; })()`),
       await ch.evaluar(`document.querySelector('.faltan')?.textContent.replace(/\\s+/g,' ').trim() || '(sin faltantes)'`));

  titulo('Confirmar');
  const conf = (await ch.evaluar(`(() => { const s=${visible};
    return s ? s.textContent.replace(/\\s+/g,' ').trim() : ''; })()`) || '');
  paso('agrupa por categoria con su subtotal', new RegExp(libres[0].categoria, 'i').test(conf), conf.slice(0, 100));
  paso('y dice el total a cobrar', /Total a cobrar/.test(conf));
  paso('el aviso del comprobante manda a Mis ventas, que es donde se sube',
       /Mis ventas/.test(conf) && !/Mis pendientes/.test(conf));

  titulo('Forma de pago: hay que elegir');
  paso('hay dos botones, contado y deposito',
       (await ch.evaluar(`document.querySelectorAll('.formas .forma').length`)) === 2);
  paso('ninguno viene elegido de antemano',
       !(await ch.evaluar(`!!document.querySelector('.forma.activa')`)));
  // Sin eleccion, registrar no puede seguir: antes la venta salia con "credito" por omision
  // y nadie sabia si era de verdad o un despiste.
  await ch.evaluar(`[...document.querySelectorAll('.acciones .btn')].find(b=>/Registrar/.test(b.textContent))?.click()`);
  await ch.esperar(900);
  paso('sin elegir forma de pago no deja registrar',
       /Cómo pagó/.test(await ch.evaluar(`document.querySelector('.faltan')?.textContent || ''`)),
       await ch.evaluar(`document.querySelector('.faltan')?.textContent.replace(/\\s+/g,' ').trim() || '(sin aviso)'`));
  paso('los datos del banco no salen si no toca',
       !(await ch.evaluar(`(() => { const s=${visible}; return s ? /Banco/.test(s.textContent) : false; })()`)));

  await ch.evaluar(`[...document.querySelectorAll('.forma')].find(b=>/Depósito/.test(b.textContent))?.click()`);
  await ch.esperar(700);
  paso('al elegir deposito aparecen banco y numero',
       await ch.evaluar(`(() => { const s=${visible}; return s ? /Banco/.test(s.textContent) && /depósito/i.test(s.textContent) : false; })()`));
  paso('y se puede adjuntar el comprobante ya, marcado como pendiente',
       /pendiente/.test(await ch.evaluar(`document.querySelector('.comprobante-adj')?.textContent || ''`)));

  titulo('La ficha deja VER el comprobante');
  // Se registra por API (el formulario ya se probo arriba) y se le adjunta un comprobante.
  const otra = ((await api(T, '/api/app/puestos')).cuerpo || []).find((p) => p.estado === 'L');
  const venta = await api(T, '/api/app/inscripciones', { method: 'POST', body: JSON.stringify({
    entidadNombre: 'ZZ VER COMPROBANTE', nit: '', descripcion: 'P', objeto: '',
    representanteLegal: 'REP VER', ciRepresentante: '99123457', celularRepresentante: '70123457',
    tipoEntidadId: 1, fechaInicio: null, fechaFin: null,
    responsables: [{ nombre: 'VER', paterno: 'COMP', materno: '', ci: '99123458', celular: '70123458', correo: null }],
    entidadBancaria: '', numComprobante: null, pagoContado: true,
    puestos: otra ? [otra.id] : [] }) });
  insId = venta.cuerpo?.inscripcionId;
  if (paso('venta de prueba creada', !!insId, JSON.stringify(venta.cuerpo).slice(0, 80))) {
    const fd = new FormData();
    fd.append('archivo', new Blob([new Uint8Array([0xff, 0xd8, 0xff, 0xdb, 0, 1, 2, 3])],
        { type: 'image/jpeg' }), 'comprobante.jpg');
    await fetch(`${API}/api/app/inscripciones/${insId}/comprobante`,
        { method: 'POST', headers: { Authorization: `Bearer ${T}` }, body: fd });

    await ch.ir(WEB + '/mis-ventas');
    await ch.esperar(3500);
    await ch.evaluar(`[...document.querySelectorAll('.venta')].find(f=>/ZZ VER COMPROBANTE/.test(f.textContent))?.click()`);
    await ch.esperar(2200);
    const secciones = await ch.evaluar(`[...document.querySelectorAll('.grupo h3')].map(h=>h.textContent.trim()).join(' | ')`);
    paso('la ficha separa entidad, casetas, pago y responsables', 
         ['Entidad', 'Casetas', 'Pago', 'Responsables'].every((x) => secciones.includes(x)), secciones);
    paso('y cada bloque se distingue por color', 
         (await ch.evaluar(`document.querySelectorAll('.g-entidad, .g-casetas, .g-pago, .g-responsables').length`)) === 4);
    paso('hay un boton para VER el comprobante, no solo para cambiarlo',
         /Ver comprobante/.test(await ch.evaluar(`document.querySelector('.comprobante')?.textContent || ''`)));
    await ch.evaluar(`[...document.querySelectorAll('.comprobante .btn')].find(b=>/Ver comprobante/.test(b.textContent))?.click()`);
    await ch.esperar(900);
    paso('y al tocarlo se ve la imagen',
         await ch.evaluar(`!!document.querySelector('.visor-comp img')`));
  }

  const errores = erroresDe(ch.eventos).filter((t) => !/favicon|DevTools|Download the Vue|404/i.test(t));
  paso('sin errores de consola', errores.length === 0, errores.slice(0, 2).join(' / '));
} finally {
  ch.cerrar();
  titulo('Limpieza');
  if (insId) {
    await api(T, `/api/app/inscripciones/${insId}/cancelar`, { method: 'POST',
      body: JSON.stringify({ motivo: 'Prueba del formulario de venta' }) });
  }
  await api(T, '/api/app/puestos/carrito', { method: 'DELETE',
    body: JSON.stringify({ ids: libres.map((p) => p.id) }) });
  paso('carrito y venta de prueba liberados', true);
}

console.log(fallos.length ? `\n${fallos.length} paso(s) fallaron:` : '\nTodo paso.');
for (const f of fallos) console.log(`  - ${f}`);
process.exit(fallos.length ? 1 : 0);
