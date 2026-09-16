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
        { path: '', component: () => import('./views/Inicio.vue'), meta: { titulo: 'Inicio', requiereAuth: true, pantalla: 'inicio'} },
        { path: 'mapa', component: () => import('./views/Mapa.vue'), meta: { titulo: 'Mapa de ventas', inmersivo: true, requiereAuth: true, pantalla: 'mapa'} },
        { path: 'venta', component: () => import('./views/Venta.vue'), meta: { titulo: 'Registrar venta', requiereAuth: true, pantalla: 'venta'} },
        { path: 'mis-ventas', component: () => import('./views/MisVentas.vue'), meta: { titulo: 'Mis ventas', requiereAuth: true, pantalla: 'mis-ventas'} },
        { path: 'catalogo', component: () => import('./views/Catalogo.vue'), meta: { titulo: 'Catalogo', requiereAuth: true, pantalla: 'catalogo'} },
        { path: 'tablero', component: () => import('./views/Board.vue'), meta: { titulo: 'Tablero', requiereAuth: true, pantalla: 'tablero'} },
        { path: 'editor', component: () => import('./views/Editor.vue'), meta: { titulo: 'Editor del plano', inmersivo: true, requiereAuth: true, pantalla: 'editor'} },
        { path: 'categorias', component: () => import('./views/Categorias.vue'), meta: { titulo: 'Categorías', requiereAuth: true, pantalla: 'categorias'} },
        { path: 'puestos', component: () => import('./views/Puestos.vue'), meta: { titulo: 'Puestos', requiereAuth: true, pantalla: 'puestos'} },
        { path: 'seguimiento', component: () => import('./views/Seguimiento.vue'), meta: { titulo: 'Seguimiento en vivo', requiereAuth: true, pantalla: 'seguimiento'} },
        { path: 'anuncios', component: () => import('./views/Anuncios.vue'), meta: { titulo: 'Anuncios', requiereAuth: true, pantalla: 'anuncios'} },
        { path: 'seguimiento-facultad', component: () => import('./views/SeguimientoFacultad.vue'), meta: { titulo: 'Seguimiento por facultad', requiereAuth: true, pantalla: 'seguimiento-facultad'} },
        { path: 'usuarios', component: () => import('./views/Usuarios.vue'), meta: { titulo: 'Usuarios', requiereAuth: true, pantalla: 'usuarios'} },
        { path: 'roles', component: () => import('./views/Roles.vue'), meta: { titulo: 'Roles', requiereAuth: true, pantalla: 'roles'} },
        { path: 'mantenimiento', component: () => import('./views/Mantenimiento.vue'), meta: { titulo: 'Mantenimiento', requiereAuth: true, pantalla: 'mantenimiento'} },
        { path: 'errores', component: () => import('./views/Errores.vue'), meta: { titulo: 'Registro de errores', requiereAuth: true, soloAdministracion: true } },
        { path: 'personas', component: () => import('./views/Personas.vue'), meta: { titulo: 'Personas', requiereAuth: true, pantalla: 'personas'} },
        { path: 'personal-apoyo', component: () => import('./views/PersonalApoyo.vue'), meta: { titulo: 'Personal de apoyo', requiereAuth: true, pantalla: 'personal-apoyo' } },
        { path: 'direccion', component: () => import('./views/Direccion.vue'), meta: { titulo: 'Tablero de dirección', requiereAuth: true, pantalla: 'direccion'} },
        { path: 'reportes', component: () => import('./views/Reportes.vue'), meta: { titulo: 'Reportes', requiereAuth: true, pantalla: 'reportes'} },
        { path: 'noches-fexpo', component: () => import('./views/NochesFexpo.vue'), meta: { titulo: 'Noches de FEXPO', requiereAuth: true, pantalla: 'noches-fexpo' } },
        { path: 'inscripciones', component: () => import('./views/Inscripciones.vue'), meta: { titulo: 'Inscripciones', requiereAuth: true, pantalla: 'inscripciones'} },
        { path: 'interesados', component: () => import('./views/Interesados.vue'), meta: { titulo: 'Interesados en exponer', requiereAuth: true, pantalla: 'interesados' } },
        { path: 'notificaciones', component: () => import('./views/Notificaciones.vue'), meta: { titulo: 'Notificaciones', requiereAuth: true, pantalla: 'notificaciones'} },
        { path: 'vendedores', component: () => import('./views/Vendedores.vue'), meta: { titulo: 'Vendedores', requiereAuth: true, pantalla: 'vendedores'} },
        { path: 'credenciales', component: () => import('./views/Credenciales.vue'), meta: { titulo: 'Credenciales', requiereAuth: true, pantalla: 'credenciales' } },
        { path: 'escaner', component: () => import('./views/Escaner.vue'), meta: { titulo: 'Escanear credencial', requiereAuth: true, pantalla: 'escaner' } },
        { path: 'permisos', component: () => import('./views/Permisos.vue'), meta: { titulo: 'Permisos por rol', requiereAuth: true, pantalla: 'permisos' } },
      ],
    },
  ],
});

router.beforeEach(async (to) => {
  const auth = useAuthStore();
  // Rutas públicas: no requieren autenticación
  if (to.meta.publico) return;
  if (to.meta.requiereAuth && !auth.autenticado) return '/login';
  if (to.meta.soloAdministracion && !auth.puedeEditarPlano) return '/';
  if (to.path === '/login' && auth.autenticado) return '/';
  /*
   * `editaPlano` ya no decide quien entra a una pantalla.
   *
   * Hubo dos porteros para la misma puerta: este, que mira el ROL, y el de abajo, que mira los
   * PERMISOS. Mientras coincidieron no se noto; en cuanto dejaron de coincidir, un VERIFICADOR
   * tenia "Inscripciones" en su menu, la tocaba y este `if` lo devolvia al inicio sin decir
   * nada. Ahora cada ruta declara su `pantalla` y manda la matriz de permisos, que es donde se
   * configura. Lo que protege de verdad sigue siendo el servidor.
   */

  /*
   * Permisos por rol. Igual que lo de arriba: es COMODIDAD, no seguridad — evita que a alguien
   * le salga una pantalla que no le toca, pero lo que protege de verdad es el servidor.
   *
   * Se ESPERA a saberlos antes de decidir. `puedeVer` ya no responde que sí mientras no se
   * sepa —eso hacía que el menú del APK saliera entero cuando la petición fallaba—, así que
   * sin esta espera un enlace directo rebotaría a Inicio solo por llegar antes que la
   * respuesta. `asegurar()` es idempotente y comparte la petición en vuelo, así que esto no
   * añade una llamada por navegación.
   */
  if (to.meta.pantalla) {
    const permisos = usePermisosStore();
    await permisos.asegurar();
    if (!permisos.puedeVer(to.meta.pantalla)) return '/';
  }
});

export default router;
