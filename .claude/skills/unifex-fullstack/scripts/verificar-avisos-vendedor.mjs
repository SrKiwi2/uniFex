/**
 * Comprueba que el vendedor se entera EN VIVO de que le cambiaron lo que puede vender,
 * igual que lo haria el APK: se conecta al topic personal por WebSocket y espera el aviso.
 */
import { Client } from '/home/usic-12/Documentos/RRHH KEVIN/SISTEMAS/uniFex/frontend/node_modules/@stomp/stompjs/esm6/index.js';
// Node 24 ya trae WebSocket nativo: no hace falta el paquete 'ws'.


const arg = (n, d) => { const i = process.argv.indexOf('--' + n); return i > 0 ? process.argv[i + 1] : d; };
const B = arg('base', 'http://localhost:7676');
const WS = B.replace(/^http/, 'ws') + '/ws';
const marca = Date.now().toString().slice(-6);
let fallos = 0;
const ok = (c,m,e='') => { console.log(`${c?'  OK  ':' FALLA'} ${m}${e?' :: '+e:''}`); if(!c) fallos++; };
const j = async r => { try { return await r.json(); } catch { return null; } };
const login = async (u,c) => (await j(await fetch(`${B}/api/auth/login`,{method:'POST',
  headers:{'Content-Type':'application/json'},body:JSON.stringify({usuario:u,contrasena:c})}))) || {};
const api = async (t,ruta,o={}) => { const r=await fetch(B+ruta,{...o,
  headers:{Authorization:`Bearer ${t}`,...(o.body?{'Content-Type':'application/json'}:{})}});
  return { estado:r.status, cuerpo: await j(r) }; };

console.log('\n== Aviso en vivo al vendedor (lo que recibe el APK) ==\n');
const T = (await login('admin1','VO7xGroB8ag2Qz1B')).token;
const rolAdm = (await api(T,'/api/app/roles')).cuerpo.find(x=>x.nombre==='ADMINISTRATIVO');
const usuario = `vend${marca}`;
const alta = await api(T,'/api/app/usuarios',{method:'POST',body:JSON.stringify({
  username:usuario,password:'ClaveVendedor9',rolId:rolAdm.id,personaId:null,
  persona:{nombre:'VENDEDOR',paterno:'VIVO',materno:'',ci:`VV${marca}`,correo:'',celular:''}})});
const vid = alta.cuerpo?.usuario?.id;
const sesion = await login(usuario,'ClaveVendedor9');
const V = sesion.token;

// Un vendedor recien creado no ve NADA (antes veia las 119).
let r = await api(V,'/api/app/puestos');
ok(r.estado===200 && Array.isArray(r.cuerpo) && r.cuerpo.length===0,
   'un vendedor sin asignaciones NO ve ninguna caseta', `${r.cuerpo?.length} casetas`);

// El APK escuchando su topic personal.
const recibidos = [];
const cliente = new Client({
  brokerURL: WS, connectHeaders: { Authorization: `Bearer ${V}` }, reconnectDelay: 0,
  debug: () => {},
});
const conectado = new Promise((res, rej) => {
  cliente.onConnect = () => { cliente.subscribe(`/topic/notificaciones/${sesion.id}`,
    (m) => { try { recibidos.push(JSON.parse(m.body)); } catch {} }); res(); };
  cliente.onStompError = (f) => rej(new Error(f.headers?.message || 'rechazado'));
  setTimeout(() => rej(new Error('timeout conectando')), 8000);
});
cliente.activate();
await conectado;
ok(true, 'el vendedor se conecta a su topic personal por WebSocket');

const esperar = async (n, ms = 6000) => {
  const hasta = Date.now() + ms;
  while (Date.now() < hasta) { if (recibidos.length >= n) return true; await new Promise(r=>setTimeout(r,120)); }
  return false;
};

// El admin le asigna 2 casetas.
const cat = (await api(T,'/api/app/vendedores/puestos-asignables')).cuerpo;
const dos = cat.filter(p=>!p.asignadoAId).slice(0,2);
await api(T,`/api/app/vendedores/${vid}/puestos`,{method:'PUT',body:JSON.stringify({puestoIds:dos.map(p=>p.id)})});
ok(await esperar(1), 'al asignarle 2 casetas, el aviso llega solo (sin recargar nada)');
let n = recibidos.at(-1);
ok(n?.tipo === 'ASIGNACION_CAMBIADA', 'el aviso trae el tipo que dispara la recarga del mapa', n?.tipo);
ok(/Se te asignaron 2 casetas de /.test(n?.cuerpo || ''), 'y dice cuantas y de que categoria', n?.cuerpo);

// Ahora ya ve exactamente esas 2.
r = await api(V,'/api/app/puestos');
ok(r.cuerpo?.length===2, 'y a partir de ahi ve exactamente esas 2', `${r.cuerpo?.length}`);

// Le quitan una.
await api(T,`/api/app/vendedores/${vid}/puestos`,{method:'PUT',body:JSON.stringify({puestoIds:[dos[0].id]})});
ok(await esperar(2), 'al quitarle una, tambien le llega el aviso');
n = recibidos.at(-1);
ok(/Se te quitaron 1 caseta de /.test(n?.cuerpo || ''), 'y dice que se le quito y de que categoria', n?.cuerpo);


cliente.deactivate();
await api(T,`/api/app/usuarios/${vid}`,{method:'DELETE'});
console.log(`\n== ${fallos===0?'Todo en orden':fallos+' fallo(s)'} ==\n`);
process.exit(fallos?1:0);
