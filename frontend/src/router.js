import { createRouter, createWebHistory } from 'vue-router';
import { useAuthStore } from './stores/auth';
import { usePermisosStore } from './stores/permisos';

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/login', component: () => import('./views/Login.vue') },
    /*
     * La credencial que abre el QR. Fuera de AppLayout y SIN requiereAuth: la escanea quien
     * controla la puerta, con su telefono y normalmente sin cuenta en el sistema. Lo que la
     * hace de fiar es la firma del codigo, no que haya sesion.
     */
    {
      path: '/credencial/:codigo',
      component: () => import('./views/CredencialPublica.vue'),
      meta: { titulo: 'Credencial' },
    },
    {
      // Vista pública de la feria: sin autenticación, para QR, web pública, APK sin login
      path: '/feria',
      component: () => import('./views/FeriaPublica.vue'),
      meta: { titulo: 'FEXPO UAP', publico: true }
    },
    {
      // Layout global: AppShell (menu/cabecera fijos) con el router-view dentro.
      // Cada vista se carga de forma diferida y asincrona en el contenedor central.
      path: '/',
      component: () => import('./components/AppLayout.vue'),
      children: [
        { path: '', component: () => import('./views/Inicio.vue'), meta: { titulo: 'Inicio', requiereAuth: true } },
        { path: 'mapa', component: () => import('./views/Mapa.vue'), meta: { titulo: 'Mapa de ventas', inmersivo: true, requiereAuth: true } },
        { path: 'venta', component: () => import('./views/Venta.vue'), meta: { titulo: 'Registrar venta', requiereAuth: true } },
        { path: 'mis-ventas', component: () => import('./views/MisVentas.vue'), meta: { titulo: 'Mis ventas', requiereAuth: true } },
        { path: 'tablero', component: () => import('./views/Board.vue'), meta: { titulo: 'Tablero', requiereAuth: true } },
        { path: 'editor', component: () => import('./views/Editor.vue'), meta: { titulo: 'Editor del plano', inmersivo: true, requiereAuth: true, editaPlano: true } },
        { path: 'usuarios', component: () => import('./views/Usuarios.vue'), meta: { titulo: 'Usuarios', requiereAuth: true, editaPlano: true } },
        { path: 'roles', component: () => import('./views/Roles.vue'), meta: { titulo: 'Roles', requiereAuth: true, editaPlano: true } },
        { path: 'personas', component: () => import('./views/Personas.vue'), meta: { titulo: 'Personas', requiereAuth: true, editaPlano: true } },
        { path: 'reportes', component: () => import('./views/Reportes.vue'), meta: { titulo: 'Reportes', requiereAuth: true, editaPlano: true } },
        { path: 'noches-fexpo', component: () => import('./views/NochesFexpo.vue'), meta: { titulo: 'Noches de FEXPO', requiereAuth: true, editaPlano: true, pantalla: 'noches-fexpo' } },
        { path: 'inscripciones', component: () => import('./views/Inscripciones.vue'), meta: { titulo: 'Inscripciones', requiereAuth: true, editaPlano: true } },
        { path: 'notificaciones', component: () => import('./views/Notificaciones.vue'), meta: { titulo: 'Notificaciones', requiereAuth: true } },
        { path: 'vendedores', component: () => import('./views/Vendedores.vue'), meta: { titulo: 'Vendedores', requiereAuth: true, editaPlano: true } },
        { path: 'credenciales', component: () => import('./views/Credenciales.vue'), meta: { titulo: 'Credenciales', requiereAuth: true, pantalla: 'credenciales' } },
        { path: 'escaner', component: () => import('./views/Escaner.vue'), meta: { titulo: 'Escanear credencial', requiereAuth: true, pantalla: 'escaner' } },
        { path: 'permisos', component: () => import('./views/Permisos.vue'), meta: { titulo: 'Permisos por rol', requiereAuth: true, editaPlano: true, pantalla: 'permisos' } },
      ],
    },
  ],
});

router.beforeEach((to) => {
  const auth = useAuthStore();
  // Rutas públicas: no requieren autenticación
  if (to.meta.publico) return;
  if (to.meta.requiereAuth && !auth.autenticado) return '/login';
  if (to.path === '/login' && auth.autenticado) return '/';
  // Esconder las herramientas de administración a quien no puede editar. Es solo comodidad:
  // el backend responde 403 a las escrituras aunque alguien escriba la ruta a mano.
  if (to.meta.editaPlano && !auth.puedeEditarPlano) return '/';

  /*
   * Permisos por rol. Igual que lo de arriba: es COMODIDAD, no seguridad — evita que a alguien
   * le salga una pantalla que no le toca, pero lo que protege de verdad es el servidor.
   *
   * `puedeVer` responde que sí mientras no se sepa (primer arranque, aún sin respuesta), para
   * no dejar a nadie fuera de su propia aplicación por una petición lenta.
   */
  if (to.meta.pantalla) {
    const permisos = usePermisosStore();
    if (!permisos.puedeVer(to.meta.pantalla)) return '/';
  }
});

export default router;
