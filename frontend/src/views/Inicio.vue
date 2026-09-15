<script setup>
import { computed } from 'vue';
import { useAuthStore } from '../stores/auth';
import { usePermisosStore } from '../stores/permisos';

/*
 * Los accesos rapidos salen de los PERMISOS, no de un rol escrito aqui.
 *
 * Antes esta pantalla daba "Mapa de ventas" y "Mis ventas" a todo el mundo y solo preguntaba
 * por `puedeEditarPlano` para añadir lo de administracion. El efecto era que un VERIFICADOR o
 * alguien de CONTROL entraba y se encontraba dos tarjetas grandes invitandole a un mapa de
 * ventas que no le toca: el menu lateral ya lo escondia, pero el inicio lo ofrecia igual, que
 * es peor que no esconderlo en ningun sitio — dice dos cosas distintas a la vez.
 *
 * Ahora la lista se filtra con `puedeVer`, la misma funcion que arma el menu, asi que las dos
 * cosas no se pueden contradecir. Y cuando alguien no tiene ninguna de estas pantallas, lo que
 * queda no es una rejilla vacia: es una bienvenida que le dice para que entro.
 */
const auth = useAuthStore();
const permisos = usePermisosStore();
permisos.asegurar();

const TODOS = [
  { p: 'mapa', a: '/mapa', icono: '🗺️', titulo: 'Mapa de ventas',
    desc: 'Casetas disponibles en el plano, reserva en tiempo real.', color: 'var(--libre)' },
  { p: 'mis-ventas', a: '/mis-ventas', icono: '🧾', titulo: 'Mis ventas',
    desc: 'Tus inscripciones registradas y el total vendido.', color: 'var(--acento)' },
  { p: 'credenciales', a: '/credenciales', icono: '🪪', titulo: 'Credenciales',
    desc: 'Preparar y entregar las credenciales de los expositores.', color: '#0d9488' },
  { p: 'escaner', a: '/escaner', icono: '📷', titulo: 'Escanear credencial',
    desc: 'Control de entradas y salidas en la puerta.', color: '#ea580c' },
  { p: 'inscripciones', a: '/inscripciones', icono: '📋', titulo: 'Inscripciones',
    desc: 'Todas las ventas de la feria, con su detalle y lo adjuntado.', color: '#4f46e5' },
  { p: 'reportes', a: '/reportes', icono: '📊', titulo: 'Reportes',
    desc: 'Recaudación y ventas por categoría, entidad y vendedor.', color: '#0891b2' },
  { p: 'editor', a: '/editor', icono: '✏️', titulo: 'Editor del plano',
    desc: 'Casetas, colores y distribución sobre el plano.', color: '#7c3aed' },
  { p: 'categorias', a: '/categorias', icono: '🏷️', titulo: 'Categorías',
    desc: 'Nombre, color, tamaño y las opciones de precio de cada categoría.', color: '#c026d3' },
  { p: 'seguimiento', a: '/seguimiento', icono: '📡', titulo: 'Seguimiento en vivo',
    desc: 'Quién está conectado ahora mismo y qué está haciendo.', color: '#0284c7' },
  { p: 'usuarios', a: '/usuarios', icono: '👥', titulo: 'Usuarios',
    desc: 'Crear y gestionar los usuarios del sistema.', color: '#db2777' },
];

const accesos = computed(() => TODOS.filter((x) => permisos.puedeVer(x.p)));

/** Que decir bajo el saludo. Sale del rol, que es lo que la persona reconoce de si misma. */
const subtitulo = computed(() => {
  if (auth.puedeEditarPlano) return 'Panel de administración';
  if (auth.esVendedor) return 'Panel del vendedor';
  return auth.rol || '';
});
</script>

<template>
  <header class="hola">
      <h2>Hola, {{ auth.usuario }}</h2>
      <p class="muted">{{ subtitulo }}</p>
    </header>

    <div v-if="accesos.length" class="grid">
      <router-link v-for="a in accesos" :key="a.a" :to="a.a" class="acceso card" :style="{ '--c': a.color }">
        <span class="icono">{{ a.icono }}</span>
        <h3>{{ a.titulo }}</h3>
        <p class="muted">{{ a.desc }}</p>
      </router-link>
    </div>

    <!-- Sin accesos, una bienvenida y no una rejilla vacia: quien entra aqui tiene su trabajo
         en otra pantalla, y una pagina en blanco parece que la aplicacion se rompio. -->
    <section v-else class="bienvenida card">
      <span class="icono-grande" aria-hidden="true">🎪</span>
      <h3>Bienvenido a FEXPO UAP</h3>
      <p class="muted">
        Tu cuenta está activa. Lo que puedes hacer está en el menú
        <strong>de abajo</strong>; si no ves lo que esperabas, pídele a administración que
        revise los permisos de tu rol.
      </p>
    </section>
</template>

<style scoped>
.bienvenida {
  padding: 2rem 1.4rem; text-align: center;
  display: flex; flex-direction: column; align-items: center; gap: 0.5rem;
}
.bienvenida .icono-grande { font-size: 2.6rem; line-height: 1; }
.bienvenida h3 { margin: 0; font-size: 1.25rem; }
.bienvenida p { margin: 0; max-width: 42ch; line-height: 1.5; }

.hola { margin-bottom: 1.5rem; }
.hola h2 { margin: 0 0 0.2rem; font-size: 1.5rem; }
.grid { display: grid; gap: 1.1rem; grid-template-columns: repeat(auto-fit, minmax(240px, 1fr)); }
.acceso {
  display: block; text-decoration: none; color: inherit; padding: 1.5rem;
  border-top: 4px solid var(--c); transition: transform 0.15s ease, box-shadow 0.15s ease;
}
.acceso:hover { transform: translateY(-2px); box-shadow: var(--sombra-md); }
.icono { font-size: 2rem; }
.acceso h3 { margin: 0.7rem 0 0.35rem; font-size: 1.2rem; }
.acceso p { margin: 0; line-height: 1.5; }
@media (prefers-reduced-motion: reduce) { .acceso { transition: none; } .acceso:hover { transform: none; } }

/* En móvil cada acceso es una tarjeta grande de una sola columna: es lo que se toca con
   el pulgar para empezar a vender, así que gana tamaño sobre densidad. */
@media (max-width: 640px) {
  .hola h2 { font-size: 1.7rem; }
  .hola .muted { font-size: 1rem; }
  .grid { grid-template-columns: 1fr; gap: 0.9rem; }
  .acceso {
    display: flex; align-items: center; gap: 1rem; padding: 1.25rem 1.3rem;
    border-top: none; border-left: 5px solid var(--c); min-height: 84px;
  }
  .icono { font-size: 2.3rem; flex: none; }
  .acceso h3 { margin: 0 0 0.2rem; font-size: 1.15rem; }
  .acceso p { font-size: 0.88rem; }
}
</style>
