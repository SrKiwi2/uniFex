import { useAuthStore } from './stores/auth.js';
import { url as urlApi } from './config.js';

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

  const res = await fetch(urlApi(ruta), { ...options, headers });

  if (res.status === 401) {
    auth.logout();
    throw new Error('Sesion expirada');
  }
  return res;
}
