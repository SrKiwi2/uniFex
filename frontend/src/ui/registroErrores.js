import { url, EN_APK } from '../config.js';

const recientes = new Map();
const pendientes = [];
let enviando = false;

function limpiar(valor, limite) {
  return String(valor || '')
    .replace(/eyJ[\w-]+\.[\w-]+\.[\w-]+/g, '[oculto]')
    .replace(/Bearer\s+[^\s,;"']+/gi, 'Bearer [oculto]')
    .replace(/(["']?(?:password|contrasena|contraseña|token|secret|authorization|api[_-]?key)["']?\s*[:=]\s*)(?:"[^"]*"|'[^']*'|[^\s,;}]+)/gi, '$1[oculto]')
    .slice(0, limite);
}

/** Nunca interrumpe la accion original. Cola en memoria, acotada y ligada a la sesion. */
export function registrarError(error, detalle = '') {
  try {
    const token = localStorage.getItem('token');
    if (!token) return;
    const mensaje = limpiar(error?.message || error, 2000);
    if (!mensaje) return;
    const ruta = window.location.pathname;
    const clave = `${token}:${ruta}:${mensaje}`;
    const ahora = Date.now();
    for (const [k, fecha] of recientes) if (ahora - fecha > 60_000) recientes.delete(k);
    if (recientes.has(clave) || recientes.size >= 20) return;
    recientes.set(clave, ahora);
    if (pendientes.length >= 20) pendientes.shift();
    pendientes.push({ token, cuerpo: {
      mensaje, detalle: limpiar([detalle, error?.stack].filter(Boolean).join('\n'), 6000),
      ruta: ruta.slice(0, 500), origen: EN_APK ? 'APK' : 'WEB',
    } });
    void enviarPendientes();
  } catch { /* registrar no debe causar otro error */ }
}

export async function enviarPendientes() {
  if (enviando) return;
  enviando = true;
  try {
    while (pendientes.length) {
      const entrada = pendientes[0];
      if (entrada.token !== localStorage.getItem('token')) { pendientes.shift(); continue; }
      const respuesta = await fetch(url('/api/app/errores/cliente'), {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${entrada.token}` },
        body: JSON.stringify(entrada.cuerpo),
        signal: AbortSignal.timeout(5000),
      });
      if (respuesta.status >= 500) break;
      pendientes.shift();
    }
  } catch { /* sin red: se reintenta al recuperar conexion, mientras siga abierta la app */ }
  finally { enviando = false; }
}

export function instalarRegistroErrores(app, router) {
  const anterior = app.config.errorHandler;
  app.config.errorHandler = (error, instancia, informacion) => {
    registrarError(error, informacion);
    if (anterior) anterior(error, instancia, informacion);
    else console.error(error);
  };
  window.addEventListener('error', (evento) => registrarError(evento.error || evento.message));
  window.addEventListener('unhandledrejection', (evento) => registrarError(evento.reason));
  window.addEventListener('online', enviarPendientes);
  router.onError((error) => registrarError(error));
}
