import { useAuthStore } from './stores/auth.js';
import { url as urlApi } from './config.js';
import { registrarError } from './ui/registroErrores.js';

/**
 * fetch con el JWT en Authorization. Si el backend responde 401, cierra la sesion.
 *
 * La ruta se pasa siempre RELATIVA (`/api/app/...`); `urlApi()` decide si se queda asi
 * —web, donde el backend esta en el mismo origen— o se le antepone el servidor, que es lo
 * que necesita el APK, donde una ruta relativa apuntaria al contenedor de la app.
 */
export async function apiFetch(ruta, options = {}) {
  const auth = useAuthStore();
  const headers = { ...(options.headers || {}) };
  if (auth.token) headers['Authorization'] = `Bearer ${auth.token}`;
  // De donde llega la peticion, para la auditoria del backend (X-Origen: WEB|APK).
  // El APK (Capacitor) inyecta `window.Capacitor`; la web navegada no lo tiene.
  if (!headers['X-Origen']) {
    headers['X-Origen'] = typeof window !== 'undefined' && window.Capacitor ? 'APK' : 'WEB';
  }
  // Con FormData NO se pone Content-Type: lo tiene que poner el navegador, porque incluye
  // el `boundary` que separa las partes. Fijarlo a mano deja al servidor sin ese dato y la
  // subida del comprobante falla con un error que no apunta a nada.
  const esFormData = typeof FormData !== 'undefined' && options.body instanceof FormData;
  if (options.body && !esFormData && !headers['Content-Type']) {
    headers['Content-Type'] = 'application/json';
  }

  let res;
  try {
    res = await fetch(urlApi(ruta), { ...options, headers });
  } catch (error) {
    if (error.name !== 'AbortError') registrarError(error, `${options.method || 'GET'} ${ruta.split('?')[0]}`);
    throw error;
  }

  if (res.status === 401) {
    auth.logout();
    throw new Error('Sesion expirada');
  }
  if (res.status === 423) {
    const data = await res.clone().json().catch(() => ({}));
    if (data.codigo === 'MANTENIMIENTO') {
      auth.logout();
      window.dispatchEvent(new CustomEvent('unifex:mantenimiento', {
        detail: { mensaje: data.mensaje || 'Sistema en mantenimiento' },
      }));
      throw new Error(data.mensaje || 'Sistema en mantenimiento');
    }
  }
  return res;
}

/**
 * Mensaje para un 403: la pantalla se ve, pero el servidor no atiende a ese rol.
 *
 * Pasa con los roles creados a mano (p. ej. "ROL LIBRE"): en «Permisos por rol» se les puede
 * dar cualquier pantalla, pero los datos de cada una los protege el servidor por rol del
 * sistema (SUPER USUARIO, ADMINISTRADOR, ADMINISTRATIVO, VERIFICADOR, ASESORIA, CONTROL) y
 * no por esa lista. Decirlo así evita que parezca una avería.
 */
export const MENSAJE_SIN_PERMISO = 'Tu rol no tiene permiso en el servidor para ver estos datos. '
  + 'La pantalla está habilitada en «Permisos», pero el servidor solo la atiende para '
  + 'ciertos roles del sistema. Pide a un administrador que revise tu rol.';

/**
 * El JSON de una respuesta que tiene que ser correcta; si no lo es, un Error con un mensaje
 * que se puede enseñar tal cual.
 *
 * Existe porque `r.json()` sin mirar `r.ok` convierte un 403 en un objeto `{ok, mensaje}` que
 * la vista intenta recorrer como si fuera la lista: el usuario veía "x is not iterable".
 */
export async function jsonOError(res, porDefecto = 'No se pudo cargar la información') {
  if (res.ok) return res.json();
  if (res.status === 403) throw new Error(MENSAJE_SIN_PERMISO);
  const cuerpo = await res.json().catch(() => ({}));
  throw new Error(cuerpo.mensaje || porDefecto);
}
