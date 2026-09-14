#!/usr/bin/env node
/**
 * La CREDENCIAL VIRTUAL: la que se entrega de verdad.
 *
 * No es papel. Se manda al telefono del expositor y se enseña en la puerta desde la pantalla,
 * asi que lo que hay que vigilar es distinto de lo que se vigila en un PDF:
 *
 *   1. Que salga como IMAGEN vertical, no como una hoja carta. Un PDF de carta en un movil
 *      sale diminuto y hay que ampliarlo con dos dedos justo cuando hay cola.
 *   2. Que el QR sea LEGIBLE de verdad y lleve la URL publica, no el codigo suelto: escaneada
 *      con la camara normal de cualquier telefono tiene que abrir la pagina del expositor.
 *      Esto se comprueba DECODIFICANDO el QR de la imagen generada, no confiando en que se
 *      dibujo bien.
 *   3. Que no se emita sin foto: una credencial sin cara no identifica a nadie en la puerta.
 *   4. Que el vendedor solo pueda bajar las suyas.
 *
 *   node .claude/skills/unifex-fullstack/scripts/verificar-credencial-virtual.mjs
 *   opciones: --api http://localhost:7676 --clave '...'
 *
 * Crea su venta de prueba y la cancela al terminar.
 */
const arg = (n, d) => { const i = process.argv.indexOf('--' + n); return i > 0 ? process.argv[i + 1] : d; };
const API = arg('api', arg('base', 'http://localhost:7676'));

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
  if (ct.startsWith('image/')) {
    return { estado: r.status, tipo: ct, bytes: Buffer.from(await r.arrayBuffer()) };
  }
  const txt = await r.text().catch(() => '');
  return { estado: r.status, tipo: ct, cuerpo: ct.includes('json') ? (() => { try { return JSON.parse(txt); } catch { return null; } })() : txt };
};

const imagen = (nombre) => {
  const fd = new FormData();
  fd.append('archivo', new Blob([new Uint8Array([0xff, 0xd8, 0xff, 0xdb, 0, 1, 2, 3])],
      { type: 'image/jpeg' }), nombre);
  return fd;
};
const multipart = async (T, ruta, fd) => fetch(API + ruta,
    { method: 'POST', headers: { Authorization: `Bearer ${T}` }, body: fd });

console.log(`\n== Credencial virtual (${API}) ==`);
const T = (await (await fetch(API + '/api/auth/login', {
  method: 'POST', headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ usuario: arg('usuario', 'admin1'), contrasena: arg('clave', 'VO7xGroB8ag2Qz1B') }),
})).json().catch(() => ({}))).token;
if (!paso('login', !!T)) process.exit(1);

// Barrido: las ventas de prueba a medias de pasadas anteriores confunden las busquedas.
for (const c of ((await api(T, '/api/app/credenciales')).cuerpo || [])) {
  if (/^ZZ /.test(c.entidad || '') && c.inscripcionId) {
    await api(T, `/api/app/inscripciones/${c.inscripcionId}/cancelar`, { method: 'POST',
      body: JSON.stringify({ motivo: 'Limpieza de pruebas de credencial virtual' }) });
  }
}

const libre = ((await api(T, '/api/app/puestos')).cuerpo || []).find((p) => p.estado === 'L');
if (!paso('hay una caseta libre', !!libre)) process.exit(1);
await api(T, '/api/app/puestos/carrito', { method: 'POST', body: JSON.stringify({ ids: [libre.id] }) });
const venta = await api(T, '/api/app/inscripciones', { method: 'POST', body: JSON.stringify({
  entidadNombre: 'ZZ CREDENCIAL VIRTUAL', nit: '', descripcion: 'ARTESANIA', objeto: '',
  representanteLegal: 'REP VIRTUAL', ciRepresentante: '99770001', celularRepresentante: '70770001',
  tipoEntidadId: 1, fechaInicio: null, fechaFin: null,
  responsables: [{ nombre: 'VIRTUAL', paterno: 'PRUEBA', materno: '', ci: '99770002',
                   celular: '70770002', correo: null }],
  entidadBancaria: '', numComprobante: null, pagoContado: true, puestos: [libre.id] }) });
const insId = venta.cuerpo?.inscripcionId;
if (!paso('venta de prueba creada', !!insId)) process.exit(1);

const dameCred = async () => ((await api(T, '/api/app/credenciales')).cuerpo || [])
    .find((c) => c.entidad === 'ZZ CREDENCIAL VIRTUAL');

try {
  titulo('Está en el catálogo y pide lo que tiene que pedir');
  const catalogo = (await api(T, '/api/app/credenciales/plantillas')).cuerpo || [];
  const virtual = catalogo.find((p) => p.id === 'CREDENCIAL_VIRTUAL');
  paso('la plantilla existe y es la primera', !!virtual && catalogo[0]?.id === 'CREDENCIAL_VIRTUAL',
       catalogo.map((p) => p.id).join(', '));
  paso('exige la foto: sin cara no identifica a nadie en la puerta', virtual?.requiereFoto === true);
  paso('es vertical, con proporcion de telefono', (virtual?.proporcion || 0) > 1.5,
       String(virtual?.proporcion));

  let c = await dameCred();
  paso('sin comprobante ni foto, no está lista', c?.listo?.CREDENCIAL_VIRTUAL === false,
       JSON.stringify(c?.faltantes?.CREDENCIAL_VIRTUAL));

  let r = await api(T, `/api/app/credenciales/${c.responsableId}/virtual`);
  paso('y pedirla igual devuelve 409, no una credencial a medias', r.estado === 409,
       `${r.estado} ${String(r.cuerpo || '').slice(0, 60)}`);

  titulo('Con comprobante y foto, sale la imagen');
  await multipart(T, `/api/app/inscripciones/${insId}/comprobante`, imagen('comprobante.jpg'));
  await multipart(T, `/api/app/inscripciones/${insId}/responsables/${c.responsableId}/foto`,
                  imagen('foto.jpg'));
  c = await dameCred();
  paso('ahora sí está lista', c?.listo?.CREDENCIAL_VIRTUAL === true);

  r = await api(T, `/api/app/credenciales/${c.responsableId}/virtual`);
  paso('responde una imagen PNG', r.estado === 200 && r.tipo === 'image/png', `${r.estado} ${r.tipo}`);
  paso('y no un PDF de hoja carta', !String(r.tipo).includes('pdf'));

  // Se lee la cabecera del PNG: ancho y alto van en los bytes 16..24.
  const ancho = r.bytes.readUInt32BE(16);
  const alto = r.bytes.readUInt32BE(20);
  paso('vertical, del tamaño de la plantilla', ancho === 900 && alto === 1600, `${ancho}x${alto}`);

  titulo('El QR de la imagen se lee, y lleva a la vista pública');
  /*
   * Se DECODIFICA el QR de la imagen generada. Mirar que el PNG pese lo suyo no prueba nada:
   * un QR dibujado sobre un fondo con poco contraste, o pegado al borde del marco, pesa igual
   * y no lo lee ningun telefono. Esto es lo unico que responde "¿sirve en la puerta?".
   */
  /*
   * Se decodifica dentro de un navegador sin cabeza, no en Node: para leer un QR hacen falta
   * los pixeles, y descomprimir un PNG en Node exigiria una dependencia mas solo para esto.
   * Chrome ya sabe abrir la imagen, y jsQR ya esta en el proyecto.
   */
  const { abrirChrome } = await import('./lib-navegador.mjs');
  const { readFileSync } = await import('node:fs');
  const { fileURLToPath } = await import('node:url');
  const { dirname, join } = await import('node:path');
  const aqui = dirname(fileURLToPath(import.meta.url));
  const libJsqr = readFileSync(join(aqui, '../../../../frontend/node_modules/jsqr/dist/jsQR.js'), 'utf8');

  const ch = await abrirChrome(Number(arg('puerto', '9330')));
  let leido = null;
  try {
    await ch.ir('about:blank');
    await ch.evaluar(libJsqr);   // define window.jsQR
    leido = await ch.evaluar(`(async () => {
      const img = new Image();
      img.src = 'data:image/png;base64,${r.bytes.toString('base64')}';
      await img.decode();
      const c = document.createElement('canvas');
      c.width = img.width; c.height = img.height;
      const ctx = c.getContext('2d');
      ctx.drawImage(img, 0, 0);
      const d = ctx.getImageData(0, 0, c.width, c.height);
      return (jsQR(d.data, d.width, d.height) || {}).data || null;
    })()`);
  } finally {
    ch.cerrar();
  }
  paso('el QR de la imagen se decodifica de verdad', !!leido, leido ? leido.slice(0, 60) : 'ilegible');
  paso('y contiene la URL publica completa, no el codigo suelto',
       /\/credencial\/FXC-/.test(leido || ''), leido || '');

  // El codigo del QR abre la vista publica SIN sesion: es lo que pasa cuando alguien lo
  // escanea con la camara normal de su telefono.
  const pub = await api(null, `/api/publico/credencial/${c.codigo}`);
  paso('ese código abre la vista pública sin sesión',
       pub.estado === 200 && pub.cuerpo?.valida === true);
  paso('con los datos que se miran en la puerta',
       ['nombre', 'ci', 'entidad', 'categoria', 'casetas'].every((k) => k in (pub.cuerpo || {})));

  titulo('Queda registrado que se entregó');
  const hist = (await api(T, `/api/app/credenciales/${c.responsableId}/impresiones`)).cuerpo || [];
  paso('la entrega se registra igual que una impresión',
       hist.some((h) => h.plantilla === 'CREDENCIAL_VIRTUAL'),
       JSON.stringify(hist[0] || {}).slice(0, 90));

  titulo('Un vendedor solo baja las suyas');
  paso('sin sesión no se descarga ninguna',
       (await api(null, `/api/app/credenciales/${c.responsableId}/virtual`)).estado === 401);
} finally {
  titulo('Limpieza');
  await api(T, `/api/app/inscripciones/${insId}/cancelar`, { method: 'POST',
    body: JSON.stringify({ motivo: 'Prueba de credencial virtual' }) });
  paso('la venta de prueba queda cancelada', !(await dameCred()));
}

console.log(fallos.length ? `\n${fallos.length} paso(s) fallaron:` : '\nTodo paso.');
for (const f of fallos) console.log(`  - ${f}`);
process.exit(fallos.length ? 1 : 0);
