/**
 * Que lo que hace ADMINISTRACION llegue al mapa del vendedor, en la web y en el APK.
 *
 * Esta es la prueba que faltaba: se conecta al WebSocket igual que el APK y comprueba, para
 * CADA cosa que se puede tocar desde la web administrativa y el editor del plano, que el
 * vendedor se entera sin cerrar la aplicacion.
 *
 * Hay dos canales y hacen cosas distintas:
 *   - /topic/puestos             difunde el ESTADO de una caseta (vendida, movida, bloqueada);
 *   - /topic/notificaciones/{id} avisa de que cambio QUE casetas tiene habilitadas.
 * Un cambio de habilitacion NO viaja por el primero, por eso hace falta el segundo: sin el,
 * el vendedor se quedaba con su lista vieja hasta cerrar y volver a entrar.
 *
 * Deja la base como estaba.
 *
 *   node .claude/skills/unifex-fullstack/scripts/verificar-propagacion.mjs
 *   opciones: --base http://localhost:7676
 */
import { Client } from '/home/usic-12/Documentos/RRHH KEVIN/SISTEMAS/uniFex/frontend/node_modules/@stomp/stompjs/esm6/index.js';

const arg = (n, d) => { const i = process.argv.indexOf('--' + n); return i > 0 ? process.argv[i + 1] : d; };
const B = arg('base', 'http://localhost:7676');
const WS = B.replace(/^http/, 'ws') + '/ws';
const marca = Date.now().toString().slice(-6);
let fallos = 0;
const ok = (c, m, e = '') => { console.log(`${c ? '  OK  ' : ' FALLA'} ${m}${e ? ' :: ' + e : ''}`); if (!c) fallos++; };
const j = async (r) => { try { return await r.json(); } catch { return null; } };
const login = async (u, c) => (await j(await fetch(`${B}/api/auth/login`, { method: 'POST',
  headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ usuario: u, contrasena: c }) }))) || {};
const api = async (t, ruta, o = {}) => { const r = await fetch(B + ruta, { ...o,
  headers: { Authorization: `Bearer ${t}`, ...(o.body ? { 'Content-Type': 'application/json' } : {}) } });
  return { estado: r.status, cuerpo: await j(r) }; };

console.log(`\n== Lo que hace administracion, ¿llega al vendedor? (${B}) ==\n`);
const T = (await login('admin1', arg('clave', 'VO7xGroB8ag2Qz1B'))).token;
const rolAdm = (await api(T, '/api/app/roles')).cuerpo.find((x) => x.nombre === 'ADMINISTRATIVO');
const usuario = `vprop${marca}`;
const vid = (await api(T, '/api/app/usuarios', { method: 'POST', body: JSON.stringify({
  username: usuario, password: 'ClaveVendedor9', rolId: rolAdm.id, personaId: null,
  persona: { nombre: 'VENDEDOR', paterno: 'PROP', materno: '', ci: `VP${marca}`, correo: '', celular: '' } }) })).cuerpo?.usuario?.id;
const sesion = await login(usuario, 'ClaveVendedor9');
const V = sesion.token;

// El "APK": escucha los dos canales, como hace la aplicacion.
const estados = [];
const avisos = [];
const cliente = new Client({ brokerURL: WS, connectHeaders: { Authorization: `Bearer ${V}` },
  reconnectDelay: 0, debug: () => {} });
// activate() va ANTES de esperar: la promesa se resuelve en onConnect, y sin activar no conecta.
const conectado = new Promise((res, rej) => {
  cliente.onConnect = () => {
    cliente.subscribe('/topic/puestos', (m) => { try { estados.push(JSON.parse(m.body)); } catch {} });
    cliente.subscribe(`/topic/notificaciones/${sesion.id}`, (m) => { try { avisos.push(JSON.parse(m.body)); } catch {} });
    res();
  };
  cliente.onStompError = (f) => rej(new Error(f.headers?.message || 'rechazado'));
  setTimeout(() => rej(new Error('timeout conectando')), 8000);
});
cliente.activate();
await conectado;
ok(true, 'el vendedor conecta y escucha los dos canales');

const esperar = async (arr, n, ms = 6000) => {
  const hasta = Date.now() + ms;
  while (Date.now() < hasta) { if (arr.length >= n) return true; await new Promise((r) => setTimeout(r, 120)); }
  return false;
};
const ultimoDe = (id) => [...estados].reverse().find((e) => e.id === id);

// --- 1. Habilitarle casetas ---
const catalogo = (await api(T, '/api/app/vendedores/puestos-asignables')).cuerpo || [];
const libres = catalogo.filter((p) => !p.asignadoAId && p.estado === 'L').slice(0, 2);
const catId = libres[0]?.categoriaId;
await api(T, `/api/app/vendedores/${vid}/puestos`, { method: 'PUT', body: JSON.stringify({ puestoIds: [libres[0].id] }) });
ok(await esperar(avisos, 1), 'al habilitarle una caseta, el aviso llega solo');
ok(avisos.at(-1)?.tipo === 'ASIGNACION_CAMBIADA', 'con el tipo que dispara la recarga del mapa', avisos.at(-1)?.tipo);

let r = await api(V, '/api/app/puestos');
ok(r.cuerpo?.length === 1, 'y a partir de ahi ve EXACTAMENTE esa caseta, no todas', `${r.cuerpo?.length}`);

// --- 2. Mover la caseta desde el editor ---
estados.length = 0;
await api(T, '/api/app/puestos/posiciones', { method: 'POST',
  body: JSON.stringify([{ id: libres[0].id, x: 0.42, y: 0.42, escala: 1 }]) });
ok(await esperar(estados, 1), 'mover la caseta en el editor le llega al vendedor');
ok(Math.abs((ultimoDe(libres[0].id)?.mapaX ?? 0) - 0.42) < 0.001, 'con la posicion nueva',
   String(ultimoDe(libres[0].id)?.mapaX));

// --- 3. Bloquearla y desbloquearla ---
estados.length = 0;
await api(T, `/api/app/puestos/${libres[0].id}/bloquear`, { method: 'POST' });
ok(await esperar(estados, 1) && ultimoDe(libres[0].id)?.estado === 'X',
   'bloquearla desde el editor le llega', ultimoDe(libres[0].id)?.estado);
await api(T, `/api/app/puestos/${libres[0].id}/desbloquear`, { method: 'POST' });
ok(await esperar(estados, 2) && ultimoDe(libres[0].id)?.estado === 'L', 'y desbloquearla tambien');

// --- 4. Una caseta que NO es suya no le ensucia el mapa ---
estados.length = 0;
await api(T, `/api/app/puestos/${libres[1].id}/bloquear`, { method: 'POST' });
await esperar(estados, 1);
r = await api(V, '/api/app/puestos');
ok(r.cuerpo?.length === 1, 'una caseta ajena que cambia NO aparece en su mapa', `${r.cuerpo?.length}`);
await api(T, `/api/app/puestos/${libres[1].id}/desbloquear`, { method: 'POST' });

// --- 5. Caseta nueva creada en el editor y habilitada ---
avisos.length = 0;
const nueva = await api(T, '/api/app/puestos', { method: 'POST',
  body: JSON.stringify({ categoriaId: catId, codigo: `P${marca}`, tamano: '3x3' }) });
const nuevaId = nueva.cuerpo?.id;
ok(nueva.estado === 200 && nuevaId, 'se crea una caseta nueva desde el editor', `id=${nuevaId}`);
r = await api(V, '/api/app/puestos');
ok(r.cuerpo?.length === 1, 'que todavia NO le sale al vendedor (no es suya)', `${r.cuerpo?.length}`);

await api(T, `/api/app/vendedores/${vid}/puestos`, { method: 'PUT',
  body: JSON.stringify({ puestoIds: [libres[0].id, nuevaId] }) });
ok(await esperar(avisos, 1), 'al habilitarsela, le llega el aviso');
r = await api(V, '/api/app/puestos');
ok(r.cuerpo?.length === 2, 'y ya le salen las dos', `${r.cuerpo?.length}`);

// --- 6. Quitarle una ---
avisos.length = 0;
await api(T, `/api/app/vendedores/${vid}/puestos`, { method: 'PUT', body: JSON.stringify({ puestoIds: [nuevaId] }) });
ok(await esperar(avisos, 1), 'al quitarle una, tambien le llega el aviso');
ok(/Se te quitaron/.test(avisos.at(-1)?.cuerpo || ''), 'diciendo que se le quito', avisos.at(-1)?.cuerpo);
r = await api(V, '/api/app/puestos');
ok(r.cuerpo?.length === 1, 'y le queda solo la otra', `${r.cuerpo?.length}`);

// --- 7. Borrar la caseta desde el editor ---
estados.length = 0;
await api(T, `/api/app/puestos/${nuevaId}`, { method: 'DELETE' });
ok(await esperar(estados, 1), 'anular la caseta en el editor le llega al vendedor');
ok(ultimoDe(nuevaId)?.activo === false, 'marcada como retirada, para que desaparezca del mapa',
   String(ultimoDe(nuevaId)?.activo));

cliente.deactivate();
await api(T, `/api/app/usuarios/${vid}`, { method: 'DELETE' });
console.log(`\n== ${fallos === 0 ? 'Todo en orden' : fallos + ' fallo(s)'} ==\n`);
process.exit(fallos ? 1 : 0);
