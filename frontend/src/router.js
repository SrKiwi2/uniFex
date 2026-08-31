import { createRouter, createWebHistory } from 'vue-router';
import { useAuthStore } from './stores/auth';

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', component: () => import('./views/Login.vue') },
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
        { path: 'personas', component: () => import('./views/Personas.vue'), meta: { titulo: 'Personas', requiereAuth: true, editaPlano: true } },
        { path: 'reportes', component: () => import('./views/Reportes.vue'), meta: { titulo: 'Reportes', requiereAuth: true, editaPlano: true } },
        { path: 'inscripciones', component: () => import('./views/Inscripciones.vue'), meta: { titulo: 'Inscripciones', requiereAuth: true, editaPlano: true } },
      ],
    },
  ],
});

router.beforeEach((to) => {
  const auth = useAuthStore();
  if (to.meta.requiereAuth && !auth.autenticado) return '/login';
  if (to.path === '/login' && auth.autenticado) return '/';
  // Esconder las herramientas de administración a quien no puede editar. Es solo comodidad:
  // el backend responde 403 a las escrituras aunque alguien escriba la ruta a mano.
  if (to.meta.editaPlano && !auth.puedeEditarPlano) return '/';
});

export default router;
