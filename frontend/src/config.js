/**
 * De donde cuelga el backend, segun donde se este ejecutando la app.
 *
 * En el navegador (web) todo va en RELATIVO: en desarrollo lo proxya Vite y en produccion
 * lo sirve el propio Spring, asi que no hay CORS ni URL que mantener por entorno.
 *
 * Dentro del APK no existe ese lujo. Capacitor sirve la app desde su propio origen
 * (`https://localhost` en Android), asi que una ruta relativa como `/api/app/puestos`
 * apuntaria al contenedor de la app y no al servidor: no fallaria con un error claro,
 * simplemente no encontraria nada. Por eso ahi hace falta una URL ABSOLUTA.
 *
 * Como se decide:
 *  1. `VITE_API_BASE` si esta definida al compilar (es lo que usa la compilacion del APK).
 *  2. Si no, cadena vacia = relativo = comportamiento de siempre en la web.
 *
 * Para compilar el APK apuntando a un servidor concreto:
 *   VITE_API_BASE=http://192.168.1.50:7676 npm run build
 * (la IP de la maquina en la red local; `localhost` dentro del telefono es el telefono).
 */

/** Base del backend, sin barra final. Vacia = mismo origen que la pagina. */
export const API_BASE = (import.meta.env?.VITE_API_BASE || '').replace(/\/+$/, '');

/**
 * ¿Se esta ejecutando dentro del APK?
 *
 * Capacitor inyecta `window.Capacitor` en el WebView. Se comprueba asi, y no por el
 * user-agent, porque el WebView de Android se identifica como Chrome y seria indistinguible
 * del navegador.
 */
export const EN_APK = typeof window !== 'undefined'
  && Boolean(window.Capacitor?.isNativePlatform?.());

/** Convierte una ruta del API en la URL final que toca segun el entorno. */
export function url(ruta) {
  return API_BASE ? API_BASE + ruta : ruta;
}

/**
 * URL del WebSocket.
 *
 * En web se deriva del origen de la pagina (mismo host y protocolo). En el APK se deriva de
 * API_BASE, traduciendo http->ws y https->wss.
 */
export function urlWebSocket() {
  if (API_BASE) return API_BASE.replace(/^http/, 'ws') + '/ws';
  const protocolo = location.protocol === 'https:' ? 'wss:' : 'ws:';
  return `${protocolo}//${location.host}/ws`;
}
