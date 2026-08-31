<script setup>
import { ref, computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useAuthStore } from '../stores/auth';
import { tema, alternarTema } from '../ui/tema';

const auth = useAuthStore();
const router = useRouter();
const route = useRoute();
const abierto = ref(false); // sidebar en móvil

// El titulo y el modo inmersivo vienen de la meta de la ruta activa:
// AppLayout es el unico que renderiza AppShell, y las vistas solo traen su contenido.
const titulo = computed(() => route.meta.titulo || '');
const inmersivo = computed(() => Boolean(route.meta.inmersivo));

// La navegación depende del rol: un vendedor no ve las herramientas de administración.
const enlaces = computed(() => {
  const base = [
    { a: '/', icono: '🏠', txt: 'Inicio' },
    { a: '/mapa', icono: '🗺️', txt: 'Mapa de ventas' },
    { a: '/venta', icono: '🛒', txt: 'Registrar venta' },
    { a: '/mis-ventas', icono: '🧾', txt: 'Mis ventas' },
  ];
  if (auth.puedeEditarPlano) {
    base.push(
      { a: '/inscripciones', icono: '📋', txt: 'Inscripciones' },
      { a: '/reportes', icono: '📊', txt: 'Reportes' },
      { a: '/editor', icono: '✏️', txt: 'Editor del plano' },
      { a: '/personas', icono: '🪪', txt: 'Personas' },
      { a: '/usuarios', icono: '👥', txt: 'Usuarios' },
    );
  }
  return base;
});

const iconoTema = computed(() => (tema.value === 'dark' ? '🌙' : tema.value === 'light' ? '☀️' : '🌗'));

function salir() {
  auth.logout();
  router.push('/login');
}
</script>

<template>
  <div class="shell">
    <aside class="sidebar" :class="{ abierto }">
      <div class="marca">
        <span class="logo">UF</span>
        <strong>UniFex</strong>
      </div>
      <nav>
        <router-link v-for="e in enlaces" :key="e.a" :to="e.a" class="enlace" @click="abierto = false">
          <span class="ico">{{ e.icono }}</span>{{ e.txt }}
        </router-link>
      </nav>
      <div class="pie-side">
        <div class="quien">
          <span class="avatar">{{ (auth.usuario || '?').charAt(0).toUpperCase() }}</span>
          <div class="datos">
            <span class="u">{{ auth.usuario }}</span>
            <span class="r">{{ auth.rol }}</span>
          </div>
        </div>
        <button class="btn btn-fantasma btn-sm" @click="salir">Salir</button>
      </div>
    </aside>

    <div v-if="abierto" class="velo" @click="abierto = false"></div>

    <div class="area">
      <header class="topbar">
        <button class="btn btn-fantasma btn-icono menu" @click="abierto = !abierto" aria-label="Menú">☰</button>
        <h1>{{ titulo }}</h1>
        <div class="crecer"></div>
        <button class="btn btn-fantasma btn-icono" :title="`Tema: ${tema}`" @click="alternarTema">{{ iconoTema }}</button>
      </header>
      <main class="contenido" :class="{ inmersivo }">
        <slot />
      </main>
    </div>
  </div>
</template>

<style scoped>
.shell { display: flex; min-height: 100vh; }

.sidebar {
  width: var(--sidebar-w); flex: none; background: var(--panel);
  border-right: 1px solid var(--border); display: flex; flex-direction: column;
  position: sticky; top: 0; height: 100vh;
}
.marca { display: flex; align-items: center; gap: 0.6rem; padding: 1.1rem 1rem; font-size: 1.1rem; }
.logo {
  display: grid; place-items: center; width: 30px; height: 30px; border-radius: 8px;
  background: var(--acento); color: var(--acento-texto); font-weight: 800; font-size: 0.8rem;
}
nav { display: flex; flex-direction: column; gap: 2px; padding: 0.4rem 0.6rem; flex: 1; overflow-y: auto; }
.enlace {
  display: flex; align-items: center; gap: 0.6rem; padding: 0.6rem 0.7rem; border-radius: var(--radio-sm);
  color: var(--text); text-decoration: none; font-weight: 600; font-size: 0.92rem;
}
.enlace:hover { background: var(--panel-2); }
.enlace.router-link-exact-active { background: var(--acento-suave); color: var(--acento); }
.ico { width: 1.3rem; text-align: center; }

.pie-side { border-top: 1px solid var(--border); padding: 0.8rem; display: flex; align-items: center; justify-content: space-between; gap: 0.5rem; }
.quien { display: flex; align-items: center; gap: 0.55rem; min-width: 0; }
.avatar { display: grid; place-items: center; width: 32px; height: 32px; border-radius: 50%; background: var(--acento-suave); color: var(--acento); font-weight: 700; flex: none; }
.datos { display: flex; flex-direction: column; min-width: 0; }
.datos .u { font-weight: 600; font-size: 0.85rem; overflow: hidden; text-overflow: ellipsis; }
.datos .r { font-size: 0.7rem; color: var(--muted); text-transform: uppercase; letter-spacing: 0.03em; }

.area { flex: 1; min-width: 0; display: flex; flex-direction: column; }
.topbar {
  display: flex; align-items: center; gap: 0.6rem; padding: 0.7rem 1.2rem;
  border-bottom: 1px solid var(--border); background: var(--panel); position: sticky; top: 0; z-index: 10;
}
.topbar h1 { margin: 0; font-size: 1.15rem; }
.menu { display: none; }
.contenido { padding: 1.4rem; max-width: 1200px; width: 100%; margin: 0 auto; }
.contenido.inmersivo { padding: 0; max-width: none; }

.velo { display: none; }

@media (max-width: 820px) {
  .sidebar {
    position: fixed; z-index: 60; transform: translateX(-100%); transition: transform 0.2s ease;
  }
  .sidebar.abierto { transform: translateX(0); box-shadow: var(--sombra-md); }
  .velo { display: block; position: fixed; inset: 0; z-index: 50; background: rgba(2, 6, 23, 0.4); }
  .menu { display: inline-flex; }
}
</style>
