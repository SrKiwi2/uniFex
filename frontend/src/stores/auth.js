import { defineStore } from 'pinia';
import { url as urlApi } from '../config.js';

/**
 * Roles que pueden rediseñar el plano. Deben coincidir con security/Roles.EDITA_PLANO
 * en el backend: aqui solo se oculta la interfaz, la autorizacion real la hace el servidor.
 * Ojo con el espacio de "SUPER USUARIO": se normaliza igual que en JwtUser.rolNormalizado().
 */
const ROLES_EDITAN_PLANO = ['SUPER_USUARIO', 'ADMINISTRADOR'];

const normalizar = (rol) => (rol || '').toUpperCase().replace(/ /g, '_');

/** Estado de autenticacion: guarda el JWT y datos del usuario (persistidos en localStorage). */
export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem('token') || '',
    // Id del usuario: lo necesita el mapa para saber cuales de las casetas en tramite
    // son suyas (se compara con `reservadoPor` de cada caseta).
    id: Number(localStorage.getItem('id')) || null,
    usuario: localStorage.getItem('usuario') || '',
    rol: localStorage.getItem('rol') || '',
  }),
  getters: {
    autenticado: (s) => !!s.token,
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
      if (!res.ok || !data.ok) throw new Error(data.mensaje || 'No se pudo iniciar sesion');
      this.token = data.token;
      this.id = data.id ?? null;
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
    },
  },
});
