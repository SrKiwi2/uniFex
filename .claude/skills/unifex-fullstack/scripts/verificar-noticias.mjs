#!/usr/bin/env node
/**
 * Verifica el modulo de noticias (V42) contra un backend en marcha:
 *
 *  - el panel exige sesion; alta y edicion validan fecha, dia (1..3), titulo, texto y foto/video;
 *  - la foto o el video es obligatorio y solo se aceptan imagenes o videos (no PDF ni MP3);
 *  - editar sin archivo conserva el medio;
 *  - /api/publico/feria trae las 10 ultimas registradas (carrusel) y /api/publico/feria/noticias
 *    todas (vista por dias), siempre de la ultima registrada a la primera y sin las eliminadas.
 *
 * Crea sus propias noticias y las da de baja al terminar (baja logica: las filas quedan en 'X').
 * No toca las noticias que ya hubiera.
 *
 *   node .claude/skills/unifex-fullstack/scripts/verificar-noticias.mjs
 *   opciones: --base http://localhost:7676 --usuario admin1 --clave 'usuario25$'
 */

function opcion(nombre, defecto) {
  const i = process.argv.indexOf(`--${nombre}`);
  return i >= 0 && process.argv[i + 1] ? process.argv[i + 1] : defecto;
}
const BASE = opcion('base', 'http://localhost:7676');
const USUARIO = opcion('usuario', 'admin1');
const CLAVE = opcion('clave', 'usuario25$');

let fallos = 0;
function comprobar(ok, texto, detalle = '') {
  if (!ok) fallos++;
  console.log(`  [${ok ? 'OK   ' : 'FALLA'}] ${texto}${detalle ? `  -> ${detalle}` : ''}`);
}

// PNG de 1x1 valido: el servidor lo guarda como cualquier foto.
const PNG = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==',
  'base64');

let token = null;
async function pedir(ruta, { metodo = 'GET', cuerpo, conToken = true } = {}) {
  const headers = {};
  if (conToken && token) headers.Authorization = `Bearer ${token}`;
  if (cuerpo && !(cuerpo instanceof FormData)) headers['Content-Type'] = 'application/json';
  const r = await fetch(BASE + ruta, {
    method: metodo, headers, redirect: 'manual',
    body: cuerpo instanceof FormData ? cuerpo : cuerpo ? JSON.stringify(cuerpo) : undefined,
  });
  const datos = await r.json().catch(() => null);
  return { status: r.status, datos };
}

/** `dia: null` no manda el campo; sin indicarlo va Dia 1. */
function formulario({ fecha, dia = 1, titulo, texto, archivo, nombre = 'foto.png', tipo = 'image/png' }) {
  const f = new FormData();
  if (fecha !== undefined) f.append('fecha', fecha);
  if (dia !== null) f.append('dia', dia);
  if (titulo !== undefined) f.append('titulo', titulo);
  if (texto !== undefined) f.append('texto', texto);
  if (archivo) f.append('archivo', new Blob([archivo], { type: tipo }), nombre);
  return f;
}

const crear = (datos) => pedir('/api/app/noticias', { metodo: 'POST', cuerpo: formulario(datos) });

const marca = Date.now().toString(36);
const creadas = [];

console.log(`\nNoticias · ${BASE}\n`);

const sinSesion = await pedir('/api/app/noticias', { conToken: false });
comprobar(sinSesion.status === 401, 'el panel exige sesion', `HTTP ${sinSesion.status}`);

const login = await pedir('/api/auth/login', { metodo: 'POST', cuerpo: { usuario: USUARIO, contrasena: CLAVE }, conToken: false });
token = login.datos?.token;
if (!token) {
  console.error('No se pudo entrar. Usa --usuario/--clave.');
  process.exit(1);
}

console.log('\nValidaciones');
let r = await crear({ fecha: '2026-09-18', titulo: `Sin medio ${marca}`, texto: 'x' });
comprobar(r.status === 400 && /obligatori/i.test(r.datos?.mensaje || ''), 'sin foto ni video se rechaza', r.datos?.mensaje);

r = await crear({ fecha: '2026-09-18', titulo: `Musica ${marca}`, texto: 'x', archivo: PNG, nombre: 'cancion.mp3', tipo: 'audio/mpeg' });
comprobar(r.status === 400 && /formato/i.test(r.datos?.mensaje || ''), 'un MP3 no vale como medio', r.datos?.mensaje);

r = await crear({ fecha: '2026-09-18', titulo: `Sin texto ${marca}`, texto: '  ', archivo: PNG });
comprobar(r.status === 400 && /texto/i.test(r.datos?.mensaje || ''), 'sin texto se rechaza', r.datos?.mensaje);

r = await crear({ titulo: `Sin fecha ${marca}`, texto: 'x', archivo: PNG });
comprobar(r.status === 400 && /fecha/i.test(r.datos?.mensaje || ''), 'sin fecha se rechaza', r.datos?.mensaje);

for (const dia of [null, 0, 4]) {
  r = await crear({ fecha: '2026-09-18', dia, titulo: `Dia ${dia} ${marca}`, texto: 'x', archivo: PNG });
  comprobar(r.status === 400 && /dia/i.test(r.datos?.mensaje || ''), `dia ${dia ?? 'vacio'} se rechaza`, r.datos?.mensaje);
}

console.log('\nAlta y edicion');
// 11 noticias repartidas en los 3 dias. Las fechas van AL REVES del registro: el orden publico es
// el de registro, no el de la fecha, y asi se nota si alguien lo cambia.
for (let i = 0; i < 11; i++) {
  const fecha = `2099-01-${String(20 - i).padStart(2, '0')}`;
  r = await crear({ fecha, dia: (i % 3) + 1, titulo: `Prueba ${marca} #${i + 1}`, texto: `Contexto de la foto ${i + 1}`, archivo: PNG });
  if (r.datos?.ok) creadas.push(r.datos.noticia);
}
comprobar(creadas.length === 11, 'se crean 11 noticias con foto', `${creadas.length} creadas`);
const primera = creadas[0];
comprobar(primera?.medioTipo === 'FOTO' && /^\/files\/noticias\//.test(primera?.urlMedio || '') && primera?.dia === 1,
  'queda como FOTO bajo /files/noticias/, con su dia', primera?.urlMedio);

if (primera) {
  const img = await fetch(BASE + primera.urlMedio);
  comprobar(img.status === 200, 'el archivo subido se sirve', `HTTP ${img.status}`);

  r = await pedir(`/api/app/noticias/${primera.id}`, { metodo: 'POST',
    cuerpo: formulario({ fecha: primera.fecha, dia: 3, titulo: `${primera.titulo} (editada)`, texto: 'Texto editado' }) });
  comprobar(r.datos?.ok && r.datos.noticia.urlMedio === primera.urlMedio && r.datos.noticia.texto === 'Texto editado'
    && r.datos.noticia.dia === 3, 'editar sin archivo conserva la foto y cambia el dia', r.datos?.mensaje);
}

console.log('\nVista publica');
const todas = await pedir('/api/publico/feria/noticias', { conToken: false });
const lista = Array.isArray(todas.datos) ? todas.datos : [];
const idsCreados = new Set(creadas.map((n) => n.id));
const nuestras = lista.filter((n) => idsCreados.has(n.id));
comprobar(todas.status === 200 && nuestras.length === 11, 'GET publico sin sesion trae TODAS (las 11 de prueba)', `${lista.length} en total`);
const ids = lista.map((n) => n.id);
comprobar(ids.every((id, i) => i === 0 || ids[i - 1] > id), 'de la ultima registrada a la primera (no por fecha)', ids.slice(0, 3).join(', '));
comprobar([1, 2, 3].every((d) => nuestras.some((n) => n.dia === d)), 'cada noticia trae su dia (1, 2 y 3)');

const feria = await pedir('/api/publico/feria', { conToken: false });
const carrusel = feria.datos?.noticias || [];
comprobar(carrusel.length === Math.min(10, lista.length), '/api/publico/feria trae como mucho 10 (carrusel)', `${carrusel.length}`);
comprobar(carrusel[0]?.id === creadas[10]?.id, 'la primera del carrusel es la ultima registrada');
comprobar(primera && !carrusel.some((n) => n.id === primera.id), 'la primera registrada de las 11 queda fuera del carrusel');

console.log('\nBaja');
const ultima = creadas[10];
if (ultima) {
  r = await pedir(`/api/app/noticias/${ultima.id}`, { metodo: 'DELETE' });
  comprobar(r.datos?.ok, 'se da de baja', r.datos?.mensaje);
  const tras = await pedir('/api/publico/feria/noticias', { conToken: false });
  comprobar(!tras.datos.some((n) => n.id === ultima.id), 'la eliminada ya no sale en la vista publica');
  const trasCarrusel = (await pedir('/api/publico/feria', { conToken: false })).datos?.noticias || [];
  comprobar(primera && trasCarrusel.some((n) => n.id === primera.id), 'y la que estaba fuera del carrusel entra en su lugar');
  r = await pedir(`/api/app/noticias/${ultima.id}`, { metodo: 'DELETE' });
  comprobar(r.status === 400, 'eliminar dos veces responde 400', r.datos?.mensaje);
}

console.log('\nLimpieza');
let limpias = 0;
for (const n of creadas.slice(0, 10)) {
  const d = await pedir(`/api/app/noticias/${n.id}`, { metodo: 'DELETE' });
  if (d.datos?.ok) limpias++;
}
comprobar(limpias === Math.min(10, creadas.length), 'las noticias de prueba se dan de baja', `${limpias}`);

console.log(fallos ? `\n${fallos} paso(s) fallaron.\n` : '\nTodo bien.\n');
process.exit(fallos ? 1 : 0);
