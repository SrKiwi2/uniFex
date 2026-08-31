<script setup>
import { computed } from 'vue';
import { useAuthStore } from '../stores/auth';

const auth = useAuthStore();

// Accesos rápidos según el rol. El vendedor ve su venta; el admin, además, la gestión.
const accesos = computed(() => {
  const venta = [
    { a: '/mapa', icono: '🗺️', titulo: 'Mapa de ventas', desc: 'Casetas disponibles en el plano, reserva en tiempo real.', color: 'var(--libre)' },
    { a: '/mis-ventas', icono: '🧾', titulo: 'Mis ventas', desc: 'Tus inscripciones registradas y el total vendido.', color: 'var(--acento)' },
  ];
  if (!auth.puedeEditarPlano) return venta;
  return [
    ...venta,
    { a: '/reportes', icono: '📊', titulo: 'Reportes', desc: 'Recaudación y ventas por categoría, entidad y vendedor.', color: '#0891b2' },
    { a: '/editor', icono: '✏️', titulo: 'Editor del plano', desc: 'Categorías, casetas, colores y distribución.', color: '#7c3aed' },
    { a: '/usuarios', icono: '👥', titulo: 'Usuarios', desc: 'Crear y gestionar los usuarios del sistema.', color: '#db2777' },
  ];
});
</script>

<template>
  <header class="hola">
      <h2>Hola, {{ auth.usuario }}</h2>
      <p class="muted">{{ auth.puedeEditarPlano ? 'Panel de administración' : 'Panel del vendedor' }}</p>
    </header>

    <div class="grid">
      <router-link v-for="a in accesos" :key="a.a" :to="a.a" class="acceso card" :style="{ '--c': a.color }">
        <span class="icono">{{ a.icono }}</span>
        <h3>{{ a.titulo }}</h3>
        <p class="muted">{{ a.desc }}</p>
      </router-link>
    </div>
</template>

<style scoped>
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
</style>
