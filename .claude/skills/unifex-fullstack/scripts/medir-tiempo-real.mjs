/**
 * Cuanto tarda el MAPA DEL OTRO VENDEDOR en repintar una caseta que acabo de meter al carrito.
 *
 * Existe porque hubo una queja ("tarda en remarcar a los demas") y las dos medidas obvias
 * decian que todo iba bien: el servidor difunde en 10-25 ms aunque sean 20 casetas, y en un
 * escritorio sin frenar la pantalla del otro se repinta en 13-52 ms. El retraso solo aparece
 * con la CPU frenada, que es lo que tiene un telefono: cada mensaje del WebSocket disparaba su
 * propia pasada de render sobre las ~400 casetas del plano, asi que un lote de 20 costaba 20
 * pasadas y ~243 ms. Se agrupan por fotograma (ver `stores/puestos.js`) y quedan en ~72 ms.
 *
 *   node .claude/skills/unifex-fullstack/scripts/medir-tiempo-real.mjs --cpu 6
 *   opciones: --web --api --clave --cpu (1 = sin frenar; 4 o 6 = telefono modesto) --puerto
 *
 * NO es una prueba de pasa/falla: imprime tiempos. Sirve para comparar un antes y un despues
 * en la MISMA maquina y la misma sesion; entre equipos distintos los numeros no se comparan.
 *
 * El navegador B tiene el mapa abierto. Desde fuera se mete un lote al carrito y se mide,
 * dentro de la pagina de B, cuanto pasa hasta que el pin de esa caseta cambia de clase.
 * Se mide con MutationObserver + rAF: el rAF garantiza que el fotograma ya se presento,
 * asi que es tiempo hasta VERLO, no hasta que Vue toco el DOM.
 */
import { abrirChrome } from '/home/usic-12/Documentos/RRHH KEVIN/SISTEMAS/uniFex/.claude/skills/unifex-fullstack/scripts/lib-navegador.mjs';

const arg = (n, d) => { const i = process.argv.indexOf('--' + n); return i > 0 ? process.argv[i + 1] : d; };
const WEB = arg('web', 'http://localhost:5174');
const API = arg('api', 'http://127.0.0.1:7677');
const CLAVE = arg('clave', 'VO7xGroB8ag2Qz1B');
const CPU = Number(arg('cpu', '1'));   // 4 o 6 para simular un telefono modesto

const j = async (r) => { try { return await r.json(); } catch { return null; } };
const T = (await j(await fetch(`${API}/api/auth/login`, { method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ usuario: 'admin1', contrasena: CLAVE }) }))).token;
const api = async (ruta, o = {}) => { const r = await fetch(API + ruta, { ...o,
  headers: { Authorization: `Bearer ${T}`, ...(o.body ? { 'Content-Type': 'application/json' } : {}) } });
  return { estado: r.status, cuerpo: await j(r) }; };

const ch = await abrirChrome(Number(arg('puerto', '9381')));
if (CPU > 1) await ch.enviar('Emulation.setCPUThrottlingRate', { rate: CPU });

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
await ch.ir(`${WEB}/mapa`);
await ch.esperar(4000);

const pines = await ch.evaluar(`document.querySelectorAll('.pin, [class*="pin"]').length`);
const enVivo = await ch.evaluar(`!!document.querySelector('.pin, [class*="pin"]')`);
console.log(`mapa abierto: ${pines} pines, throttle CPU x${CPU}\n`);
if (!enVivo) { console.log('no se encontraron pines; revisar el selector'); ch.cerrar(); process.exit(1); }

const todas = (await api('/api/app/puestos')).cuerpo || [];
const libres = todas.filter((p) => p.estado === 'L');

async function medir(n) {
  const lote = libres.splice(0, n).map((p) => p.id);
  if (lote.length < n) { console.log(`(no quedan ${n} libres)`); return; }

  // El pin no lleva el id en el DOM: se localiza por el numero de caseta que muestra.
  const codigos = lote.map((id) => String(todas.find((p) => p.id === id).codigo));
  await ch.evaluar(`(() => {
    const quiero = new Set(${JSON.stringify(codigos)});
    window.__m = { t0: performance.now(), vistos: {}, faltan: quiero };
    const marcar = () => requestAnimationFrame(() => {
      const t = performance.now() - window.__m.t0;
      for (const el of document.querySelectorAll('.pin.tramite')) {
        const cod = el.textContent.trim();
        if (window.__m.faltan.has(cod)) { window.__m.vistos[cod] = t; window.__m.faltan.delete(cod); }
      }
    });
    window.__obs = new MutationObserver(marcar);
    window.__obs.observe(document.body, { subtree: true, attributes: true, attributeFilter: ['class'] });
    return true;
  })()`);

  await ch.evaluar(`window.__m.t0 = performance.now()`);
  const t0 = Date.now();
  await api('/api/app/puestos/carrito', { method: 'POST', body: JSON.stringify({ ids: lote }) });

  const limite = Date.now() + 15000;
  let res = null;
  while (Date.now() < limite) {
    res = await ch.evaluar(`JSON.stringify({ v: window.__m.vistos, faltan: [...window.__m.faltan] })`);
    if (JSON.parse(res).faltan.length === 0) break;
    await ch.esperar(50);
  }
  const { v, faltan } = JSON.parse(res);
  const ts = Object.values(v);
  console.log(`${String(n).padStart(2)} caseta(s) · pintadas en el mapa del otro:`
    + (ts.length ? ` 1a ${Math.round(Math.min(...ts))} ms · ultima ${Math.round(Math.max(...ts))} ms` : ' ninguna')
    + (faltan.length ? `  <<< ${faltan.length} NUNCA se repintaron (${Date.now() - t0} ms esperando)` : ''));

  await ch.evaluar('window.__obs.disconnect()');
  await api('/api/app/puestos/carrito', { method: 'DELETE', body: JSON.stringify({ ids: lote }) });
  await ch.esperar(600);
}

// Varias repeticiones: la primera siempre sale lenta (calentamiento) y conviene verla aparte.
for (const n of [1, 1, 1, 1, 5, 10, 20, 20, 20]) await medir(n);
ch.cerrar();
process.exit(0);
