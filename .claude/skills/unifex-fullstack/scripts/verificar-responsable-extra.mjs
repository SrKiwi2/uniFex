/**
 * Agregar un responsable desde la ficha de "Mis ventas", en un navegador de verdad.
 *
 * Existe por un cuelgue reportado desde la feria: al agregar un responsable la aplicacion se
 * quedaba con "Agregando al responsable… Parece que no hay señal" para siempre. Eran dos cosas
 * encadenadas, y ninguna se veia desde el servidor —el POST respondia 200—:
 *
 *   1. `textoCarga` se usaba SIN importarlo: ReferenceError justo despues de crear al
 *      responsable, asi que el flujo saltaba al `catch`.
 *   2. El `catch` hacia `await alerta(...)`, y el velo de carga (z-index 2500) tapaba al modal
 *      de aviso (2000). El dialogo quedaba invisible y sin poder pulsarse, y como
 *      `ocultarCarga()` vive en el `finally` que espera a ese await, el velo no bajaba nunca.
 *
 * Por eso esta prueba mira la PANTALLA y no la respuesta HTTP: el servidor decia que todo
 * habia ido bien mientras el vendedor tenia la aplicacion trabada.
 *
 *   node .claude/skills/unifex-fullstack/scripts/verificar-responsable-extra.mjs \
 *        --web http://localhost:5173 --api http://localhost:7676
 */
import { abrirChrome, erroresDe } from './lib-navegador.mjs';

const arg = (n, d) => { const i = process.argv.indexOf('--' + n); return i > 0 ? process.argv[i + 1] : d; };
const WEB = arg('web', 'http://localhost:5173');
const API = arg('api', 'http://localhost:7676');
const CLAVE = arg('clave', 'VO7xGroB8ag2Qz1B');
const marca = Date.now().toString().slice(-6);

let fallos = 0;
const paso = (d, ok, det = '') => {
  console.log(`  [${ok ? 'OK   ' : 'FALLA'}] ${d}${det ? `  -> ${det}` : ''}`);
  if (!ok) fallos++;
};
const titulo = (t) => console.log(`\n${t}`);
const j = async (r) => { try { return await r.json(); } catch { return null; } };

const T = (await j(await fetch(`${API}/api/auth/login`, { method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ usuario: 'admin1', contrasena: CLAVE }) })))?.token;
if (!T) { console.log('No se pudo entrar como admin1'); process.exit(1); }
const api = async (ruta, o = {}) => { const r = await fetch(API + ruta, { ...o,
  headers: { Authorization: `Bearer ${T}`, ...(o.body && typeof o.body === 'string' ? { 'Content-Type': 'application/json' } : {}) } });
  return { estado: r.status, cuerpo: await j(r) }; };

// ---------------------------------------------------------------- una venta de prueba
const tipo = ((await api('/api/app/catalogos/tipos-entidad')).cuerpo || [])[0];
const libre = ((await api('/api/app/puestos')).cuerpo || []).find((p) => p.estado === 'L');
if (!libre) { console.log('No hay casetas libres para la prueba'); process.exit(1); }
await api('/api/app/puestos/carrito', { method: 'POST', body: JSON.stringify({ ids: [libre.id] }) });
let r = await api('/api/app/inscripciones', { method: 'POST', body: JSON.stringify({
  entidadNombre: `ZZ RESP EXTRA ${marca}`, nit: '', descripcion: 'P', objeto: '',
  representanteLegal: 'REP PRUEBA', ciRepresentante: `RE${marca}`, celularRepresentante: '59170000000',
  tipoEntidadId: tipo?.id, fechaInicio: null, fechaFin: null,
  // DOS responsables: 1 caseta da derecho a 2, asi que el que se agregue desde la pantalla
  // sera el tercero — el que se cobra. Con uno solo, el alta caia dentro del derecho y la
  // prueba no ejercitaba nada del cobro.
  responsables: [
    { nombre: 'UNO', paterno: 'PRUEBA', materno: '', ci: `R1${marca}`, celular: '59170000001', correo: null },
    { nombre: 'DOS', paterno: 'PRUEBA', materno: '', ci: `R2${marca}`, celular: '59170000002', correo: null },
  ],
  entidadBancaria: '', numComprobante: null, pagoContado: true, puestos: [libre.id] }) });
const insId = r.cuerpo?.inscripcionId;
paso('hay una venta de prueba', !!insId, `id=${insId}`);

const ch = await abrirChrome(Number(arg('puerto', '9410')));
await ch.ir(`${WEB}/login`);
await ch.evaluar(`(async () => {
  const r = await fetch('/api/auth/login', { method:'POST', headers:{'Content-Type':'application/json'},
    body: JSON.stringify({ usuario:'admin1', contrasena:${JSON.stringify(CLAVE)} }) });
  const d = await r.json();
  localStorage.setItem('token', d.token);
  localStorage.setItem('usuario', d.usuario || 'admin1');
  localStorage.setItem('rol', d.rol || '');
  if (d.id != null) localStorage.setItem('id', String(d.id));
})()`);

// ---------------------------------------------------------------- el apilado
titulo('El velo de carga no puede tapar al aviso');
await ch.ir(`${WEB}/mis-ventas`);
await ch.esperar(2500);
/*
 * Se lee de los FUENTES y no del DOM: el velo solo existe mientras algo carga, y el instante en
 * que los dos coinciden en pantalla es justo el que estaba roto. La relacion entre los dos
 * numeros es la regla; medirla asi no depende de acertar el momento.
 */
{
  const { readFileSync } = await import('node:fs');
  const { fileURLToPath } = await import('node:url');
  const { dirname, resolve } = await import('node:path');
  const RAIZ = resolve(dirname(fileURLToPath(import.meta.url)), '../../../..') + '/';
  const z = (archivo, clase) => {
    const t = readFileSync(RAIZ + archivo, 'utf8');
    const i = t.indexOf(clase);
    const m = i >= 0 && t.slice(i).match(/z-index:[ ]*(\d+)/);
    return m ? Number(m[1]) : null;
  };
  const velo = z('frontend/src/components/CargandoOverlay.vue', '.velo-carga');
  const aviso = z('frontend/src/components/AlertaModal.vue', '.overlay');
  paso('el velo de carga queda por DEBAJO del modal de aviso',
       velo != null && aviso != null && velo < aviso, `velo=${velo} aviso=${aviso}`);
}

// ---------------------------------------------------------------- el flujo completo
titulo('Agregar un responsable con su foto');
await ch.evaluar(`(() => { const f=[...document.querySelectorAll('.venta')]
  .find(x=>x.textContent.includes('ZZ RESP EXTRA ${marca}')); f?.click(); return !!f; })()`);
await ch.esperar(2500);
paso('se abre la ficha de la venta',
     await ch.evaluar(`document.body.textContent.includes('Responsables')`));

await ch.evaluar(`(() => { const b=[...document.querySelectorAll('button')]
  .find(x=>/Agregar responsable/i.test(x.textContent)); b?.click(); return !!b; })()`);
await ch.esperar(900);
paso('se abre el formulario', await ch.evaluar(`!!document.querySelector('.form-resp')`));

// Rellenar. La foto se inyecta con DataTransfer: es la unica forma de poner un archivo en un
// <input type=file> desde el protocolo de depuracion.
await ch.evaluar(`(() => {
  const campos = [...document.querySelectorAll('.form-resp input.control')];
  const poner = (el, v) => { el.value = v; el.dispatchEvent(new Event('input', { bubbles: true })); };
  poner(campos[0], 'NUEVO${marca}');
  poner(campos[1], 'CI${marca}');
  const dt = new DataTransfer();
  dt.items.add(new File([new Uint8Array([0xff,0xd8,0xff,0xdb,0,1,2,3])], 'cara.jpg', { type: 'image/jpeg' }));
  const foto = document.getElementById('foto-resp-nuevo');
  foto.files = dt.files;
  foto.dispatchEvent(new Event('change', { bubbles: true }));

  // Pasado el derecho, el formulario pide ademas el comprobante del cobro.
  const comp = document.getElementById('comp-resp');
  if (comp) {
    const dt2 = new DataTransfer();
    dt2.items.add(new File([new Uint8Array([0xff,0xd8,0xff,0xdb,0,1,2,3])], 'pago.jpg', { type: 'image/jpeg' }));
    comp.files = dt2.files;
    comp.dispatchEvent(new Event('change', { bubbles: true }));
  }
  return !!comp;
})()`);
await ch.esperar(400);
paso('la foto queda adjunta antes de guardar',
     await ch.evaluar(`document.querySelector('.form-resp').textContent.includes('cara.jpg')`));
paso('y el formulario pide el comprobante porque este se cobra',
     await ch.evaluar(`document.querySelector('.form-resp').textContent.includes('pago.jpg')`));

await ch.evaluar(`(() => { const b=[...document.querySelectorAll('button')]
  .find(x=>/^\\s*Agregar\\s*$/.test(x.textContent)); b?.click(); return !!b; })()`);

/*
 * Lo que de verdad se comprueba: que el velo BAJE. El fallo reportado no era un error visible,
 * era que esto no terminaba nunca. Se espera generosamente y se mira si sigue puesto.
 */
let veloAbajo = false;
for (let i = 0; i < 40; i++) {
  await ch.esperar(500);
  if (!(await ch.evaluar(`!!document.querySelector('.velo-carga')`))) { veloAbajo = true; break; }
}
paso('el velo "Agregando al responsable…" termina bajando', veloAbajo,
     veloAbajo ? '' : 'sigue puesto tras 20 s: la aplicacion quedo trabada');

await ch.esperar(700);
// La frase exacta del aviso, no un "agregado" suelto: en la pagina hay botones de "Agregar" y
// una asercion que casa con cualquiera de ellos pasaria aunque el aviso no saliera.
const textoAviso = await ch.evaluar(`(() => {
  const d = [...document.querySelectorAll('.dialogo')].map((x) => x.textContent).join(' ');
  return d || document.body.textContent || '';
})()`);
// Se imprime el TROZO que casa, no el principio del texto: detras puede haber otro dialogo
// abierto (la ficha) y enseñar su cabecera hace dudar de que la asercion mire lo que dice.
const casa = textoAviso.match(/Responsable agregado[^]{0,90}/i);
paso('y sale el aviso de que quedo agregado', !!casa,
     (casa ? casa[0] : textoAviso.slice(0, 110)).trim().replace(/\s+/g, ' '));

// ---------------------------------------------------------------- y quedo de verdad
titulo('Quedo registrado, con su foto');
r = await api(`/api/app/inscripciones/${insId}/detalle`);
const nuevo = (r.cuerpo?.responsables || []).find((x) => (x.nombre || '').includes(`NUEVO${marca}`));
paso('el responsable esta en la venta', !!nuevo, nuevo?.nombre);
paso('y con su foto subida', !!nuevo?.tieneFoto, nuevo?.fotoUrl || '(sin foto)');

// ---------------------------------------------------------------- lo que se guardo
titulo('Lo guardado se puede ver y auditar');

r = await api(`/api/app/inscripciones/${insId}/detalle`);
const enFicha = (r.cuerpo?.responsables || []).find((x) => (x.nombre || '').includes(`NUEVO${marca}`));
paso('la ficha marca al responsable como EXTRA', enFicha?.esExtra === true,
     `esExtra=${enFicha?.esExtra} monto=${enFicha?.montoExtra}`);
paso('con el importe que se le cobro', Number(enFicha?.montoExtra) === 15, `${enFicha?.montoExtra} Bs`);
paso('y con la ruta de SU comprobante', !!enFicha?.comprobanteExtraUrl, enFicha?.comprobanteExtraUrl);

// Que el archivo se pueda ABRIR, no solo que la ruta este en el JSON: una ruta guardada cuyo
// archivo no existe es peor que no tenerla, porque parece que si esta.
if (enFicha?.comprobanteExtraUrl) {
  const img = await fetch(API + enFicha.comprobanteExtraUrl);
  paso('y el comprobante se descarga de verdad', img.ok, `HTTP ${img.status}`);
}

r = await api('/api/app/credenciales');
const enCred = (r.cuerpo || []).find((c) => (c.nombre || '').includes(`NUEVO${marca}`));
paso('el modulo de credenciales lo marca como extra', enCred?.esExtra === true,
     `esExtra=${enCred?.esExtra}`);

r = await api('/api/app/inscripciones/responsables-extra');
const enAuditoria = (r.cuerpo || []).find((x) => (x.nombre || '').includes(`NUEVO${marca}`));
paso('sale en el listado de control de extras', !!enAuditoria,
     enAuditoria ? `${enAuditoria.entidad} · ${enAuditoria.monto} Bs` : '(no aparece)');
paso('con quien lo agrego y su comprobante',
     !!enAuditoria?.agregadoPor && !!enAuditoria?.comprobanteUrl,
     `lo agrego ${enAuditoria?.agregadoPor}`);

/*
 * El comprobante adjuntado DENTRO del derecho tambien se guarda.
 *
 * Estaba dentro del `if (cobra)`, asi que el archivo se escribia en disco y la ruta se tiraba: un
 * huerfano que nadie podia encontrar, con el vendedor creyendo que lo habia adjuntado.
 *
 * Necesita su PROPIA venta, con sitio libre en el derecho. Antes se hacia sobre la de arriba, que
 * ya estaba llena, asi que el alta salia cobrada y la comprobacion se SALTABA en silencio — una
 * asercion que no corre y sale en verde es peor que no tenerla.
 */
{
  const libre2 = ((await api('/api/app/puestos')).cuerpo || []).find((p) => p.estado === 'L');
  if (!libre2) {
    paso('hay una caseta libre para la segunda venta', false, 'no quedan casetas libres');
  } else {
    await api('/api/app/puestos/carrito', { method: 'POST', body: JSON.stringify({ ids: [libre2.id] }) });
    // UN solo responsable: 1 caseta da derecho a 2, asi que queda sitio para el siguiente.
    const r2 = await api('/api/app/inscripciones', { method: 'POST', body: JSON.stringify({
      entidadNombre: `ZZ DERECHO ${marca}`, nit: '', descripcion: 'P', objeto: '',
      representanteLegal: 'REP', ciRepresentante: `RD${marca}`, celularRepresentante: '59170000000',
      tipoEntidadId: tipo?.id, fechaInicio: null, fechaFin: null,
      responsables: [{ nombre: 'SOLO', paterno: 'UNO', materno: '', ci: `S1${marca}`,
                       celular: '59170000003', correo: null }],
      entidadBancaria: '', numComprobante: null, pagoContado: true, puestos: [libre2.id] }) });
    const ins2 = r2.cuerpo?.inscripcionId;

    const fd = new FormData();
    fd.append('nombre', `GRATIS${marca}`);
    fd.append('ci', `GR${marca}`);
    fd.append('comprobante', new Blob([new Uint8Array([0xff, 0xd8, 0xff, 0xdb, 0, 1])],
      { type: 'image/jpeg' }), 'papel.jpg');
    const rr = await fetch(`${API}/api/app/inscripciones/${ins2}/responsables`,
      { method: 'POST', headers: { Authorization: `Bearer ${T}` }, body: fd });
    const dd = await rr.json().catch(() => ({}));
    paso('el segundo responsable entra SIN cobro (queda derecho)', dd?.ok && dd.cobrado === false,
         dd?.mensaje);

    const det = await api(`/api/app/inscripciones/${ins2}/detalle`);
    const gratis = (det.cuerpo?.responsables || []).find((x) => (x.nombre || '').includes(`GRATIS${marca}`));
    paso('y su comprobante NO se pierde aunque no se le cobre',
         !!gratis?.comprobanteExtraUrl, gratis?.comprobanteExtraUrl || '(se tiro la ruta)');
    if (gratis?.comprobanteExtraUrl) {
      const f = await fetch(API + gratis.comprobanteExtraUrl);
      paso('y tambien se descarga', f.ok, `HTTP ${f.status}`);
    }

    if (ins2) {
      await api(`/api/app/inscripciones/${ins2}/cancelar`, { method: 'POST',
        body: JSON.stringify({ motivo: 'Prueba de comprobante dentro del derecho' }) });
    }
  }
}

titulo('Consola');
const errores = erroresDe(ch.eventos).filter((e) => !/favicon|PLANO-|whatsapp/i.test(e));
paso('ni un ReferenceError ni excepcion sin capturar',
     !errores.some((e) => /ReferenceError|is not defined/.test(e)),
     errores.filter((e) => /ReferenceError|is not defined/.test(e)).join(' | '));

ch.cerrar();

titulo('Limpieza');
r = await api(`/api/app/inscripciones/${insId}/cancelar`, { method: 'POST',
  body: JSON.stringify({ motivo: 'Prueba de responsable extra' }) });
paso('la venta de prueba se cancela', r.estado === 200, `HTTP ${r.estado}`);

console.log(fallos ? `\n${fallos} paso(s) fallaron.\n` : '\nTodo paso.\n');
process.exit(fallos ? 1 : 0);
