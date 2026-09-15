import { defineStore } from 'pinia';
import { url as urlApi } from '../config.js';

/**
 * Roles que pueden rediseñar el plano. Deben coincidir con security/Roles.EDITA_PLANO
 * en el backend: aqui solo se oculta la interfaz, la autorizacion real la hace el servidor.
 * Ojo con el espacio de "SUPER USUARIO": se normaliza igual que en JwtUser.rolNormalizado().
 */
const ROLES_EDITAN_PLANO = ['SUPER_USUARIO', 'ADMINISTRADOR'];

const normalizar = (rol) => (rol || '').toUpperCase().replace(/ /g, '_');

/** La copia en disco del menu. Se borra junto con la sesion; ver `logout`. */
const CACHE_PERMISOS = 'permisos.cache.v1';

/**
 * Cuando caduca este token, en milisegundos. 0 = no se pudo leer.
 *
 * Solo se LEE la carga del JWT; no se verifica la firma, que es cosa del servidor. Sirve para
 * una decision local: si ya caduco, no vale la pena entrar a la aplicacion como si nada para
 * que la primera peticion devuelva 401 y expulse al usuario.
 */
function caducaEn(token) {
  const exp = delToken(token, 'exp');
  return typeof exp === 'number' ? exp * 1000 : 0;   // 0 = ilegible: que decida el servidor
}

/** Margen para no dar por buena una sesion que caduca mientras se pinta la pantalla. */
const MARGEN_MS = 10_000;

/** Una reclamacion cualquiera de la carga del token, sin verificar la firma. */
function delToken(token, clave) {
  try {
    const carga = String(token).split('.')[1];
    if (!carga) return null;
    return JSON.parse(atob(carga.replace(/-/g, '+').replace(/_/g, '/')))[clave] ?? null;
  } catch {
    return null;
  }
}

/**
 * El id del usuario, con el TOKEN como respaldo.
 *
 * Se guarda aparte en disco, pero puede faltar: una sesion creada por una version anterior, o
 * un almacenamiento restaurado a medias. Y sin id el mapa deja de reconocer las casetas
 * propias —`reservadoPor === auth.id` no casa nunca—, asi que una caseta recien agregada al
 * carrito no se marcaba como tuya en el APK aunque el servidor la tuviera bien reservada.
 * El token ya lleva `uid`, asi que no hace falta pedirlo ni volver a entrar.
 */
function idInicial(token) {
  const guardado = Number(localStorage.getItem('id'));
  if (guardado) return guardado;
  const delJwt = Number(delToken(token, 'uid'));
  return delJwt || null;
}

/** Estado de autenticacion: guarda el JWT y datos del usuario (persistidos en localStorage). */
export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem('token') || '',
    // Id del usuario: lo necesita el mapa para saber cuales de las casetas en tramite
    // son suyas (se compara con `reservadoPor` de cada caseta). Si no esta en disco se saca
    // del propio token, que lo lleva como `uid`.
    id: idInicial(localStorage.getItem('token') || ''),
    usuario: localStorage.getItem('usuario') || '',
    rol: localStorage.getItem('rol') || '',
  }),
  getters: {
    /*
     * Hay token Y todavia sirve.
     *
     * Antes bastaba con que hubiera token, y eso producia el arranque mas desconcertante que
     * se reporto del APK: la aplicacion abria directa al inicio, con el menu completo —que se
     * pinta desde la copia en disco— y al tocar cualquier opcion te echaba al login. Con un
     * token caducado todo parece iniciado hasta que la primera peticion devuelve 401.
     *
     * El caso llego por Android: el APK tenia `allowBackup`, asi que al reinstalar la
     * aplicacion el sistema RESTAURO los datos viejos, token incluido, ya caducado.
     *
     * Un token ilegible se da por bueno a proposito: que decida el servidor. Fallar cerrado
     * ahi echaria a todo el mundo si algun dia cambia el formato, y el 401 ya cubre ese caso.
     */
    autenticado: (s) => {
      if (!s.token) return false;
      const caduca = caducaEn(s.token);
      return caduca === 0 || caduca - MARGEN_MS > Date.now();
    },
    /** Solo para mostrar u ocultar el editor; el backend vuelve a comprobarlo con 403. */
    puedeEditarPlano: (s) => ROLES_EDITAN_PLANO.includes(normalizar(s.rol)),
    /**
     * Los 35 usuarios ADMINISTRATIVO son los vendedores, y son los unicos a quienes se les
     * limita el mapa a sus casetas asignadas. Administracion vende cualquiera.
     * Igual que arriba: aqui solo se pinta, el permiso real lo comprueba el servidor.
     */
    esVendedor: (s) => normalizar(s.rol) === 'ADMINISTRATIVO',
  },
  actions: {
    async login(usuario, contrasena) {
      // Ruta ABSOLUTA en el APK (urlApi la resuelve): una relativa aqui apuntaria al
      // contenedor de Capacitor (https://localhost), no al servidor. Es la causa de que
      // el login funcione en la web y no en el APK.
      const res = await fetch(urlApi('/api/auth/login'), {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ usuario, contrasena }),
      });
      const data = await res.json().catch(() => ({}));
      if (!res.ok || !data.ok) {
        const error = new Error(data.mensaje || 'No se pudo iniciar sesion');
        error.codigo = data.codigo || '';
        throw error;
      }
      this.token = data.token;
      this.id = Number(data.id) || Number(delToken(data.token, 'uid')) || null;
      this.usuario = data.usuario;
      this.rol = data.rol;
      localStorage.setItem('token', this.token);
      if (this.id != null) localStorage.setItem('id', String(this.id));
      localStorage.setItem('usuario', this.usuario);
      localStorage.setItem('rol', this.rol);
    },
    logout() {
      this.token = '';
      this.id = null;
      this.usuario = '';
      this.rol = '';
      localStorage.removeItem('token');
      localStorage.removeItem('id');
      localStorage.removeItem('usuario');
      localStorage.removeItem('rol');
      /*
       * Y el menu guardado, que es parte de la sesion aunque viva en otro sitio.
       *
       * Se borra la clave a mano en vez de llamar al store de permisos: ese store importa
       * `api.js`, que importa este, y el ciclo deja uno de los dos a medio construir. La clave
       * esta declarada arriba, junto a la del store, para que se vea que van juntas.
       *
       * Sin esto, cerrar sesion —o que el servidor te expulse con un 401— dejaba el menu del
       * usuario anterior en disco, y al volver a abrir la aplicacion se pintaba entero antes
       * de que nadie hubiera entrado.
       */
      try { localStorage.removeItem(CACHE_PERMISOS); } catch { /* modo privado */ }
    },
  },
});
