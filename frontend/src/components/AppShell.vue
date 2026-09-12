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
    { a: '/notificaciones', icono: '🔔', txt: 'Notificaciones' },
  ];
  if (auth.puedeEditarPlano) {
    base.push(
      { a: '/vendedores', icono: '👥', txt: 'Vendedores' },
      { a: '/inscripciones', icono: '📋', txt: 'Inscripciones' },
      { a: '/reportes', icono: '📊', txt: 'Reportes' },
      { a: '/editor', icono: '✏️', txt: 'Editor del plano' },
      { a: '/personas', icono: '🪪', txt: 'Personas' },
      { a: '/usuarios', icono: '👥', txt: 'Usuarios' },
      { a: '/roles', icono: '🛡️', txt: 'Roles' },
    );
  }
  return base;
});

// Las 4 tareas del día a día del vendedor, para la barra inferior estilo app: los mismos
// destinos de siempre, solo que a mano del pulgar en vez de en un cajón que hay que abrir.
// El resto (herramientas de administración, Salir) queda en el cajón, detrás de "Más".
const enlacesPrincipales = computed(() => enlaces.value.slice(0, 4));

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

    <!-- Barra inferior: solo en móvil (ver media query). Es la navegación principal ahí,
         como en cualquier app nativa — el cajón lateral de arriba queda para el resto. -->
    <nav class="tabbar">
      <router-link v-for="e in enlacesPrincipales" :key="e.a" :to="e.a" class="tab" @click="abierto = false">
        <span class="ico">{{ e.icono }}</span>
        <span class="txt">{{ e.txt }}</span>
      </router-link>
      <button type="button" class="tab tab-mas" :class="{ activo: abierto }" @click="abierto = !abierto">
        <span class="ico">☰</span>
        <span class="txt">Más</span>
      </button>
    </nav>
  </div>
</template>

<style scoped>
.shell { display: flex; min-height: 100vh; min-height: 100dvh; }

.sidebar {
  width: var(--sidebar-w); flex: none; background: var(--panel);
  border-right: 1px solid var(--border); display: flex; flex-direction: column;
  /* Va a pantalla completa (fijo, en móvil), así que su primer enlace caería bajo la barra
     de estado igual que la cabecera. */
  padding-top: var(--safe-top); padding-left: var(--safe-left);
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
/* El hueco de la barra de estado va en el PADDING de la cabecera, no en un margen por encima:
   así esa franja queda pintada y no transparente. Era lo que dejaba el botón de tema pegado
   al borde superior, tapado por los iconos del sistema y sin poder pulsarse. */
.topbar {
  display: flex; align-items: center; gap: 0.6rem;
  padding: calc(0.7rem + var(--safe-top)) calc(1.2rem + var(--safe-right)) 0.7rem calc(1.2rem + var(--safe-left));
  border-bottom: 1px solid var(--border); background: var(--panel); position: sticky; top: 0; z-index: 10;
}
/* La franja de la barra de estado se pinta aparte, con un color que sigue al tema del
   TELEFONO y no al de la app (ver --franja-estado en style.css). Los iconos del sistema los
   colorea Android segun ese ajuste, así que es la única manera de que siempre se lean. */
.topbar::before {
  content: ''; position: absolute; top: 0; left: 0; right: 0;
  height: var(--safe-top); background: var(--franja-estado);
}
.topbar h1 { margin: 0; font-size: 1.15rem; }
.menu { display: none; }
/* flex: 1 0 auto — crece para ocupar lo que sobra bajo la cabecera (de eso vive el mapa,
   que se estira hasta la barra inferior en vez de dejar un hueco muerto), pero NO se encoge:
   una vista larga (una tabla de usuarios) conserva su alto natural y la pagina hace scroll
   como siempre. Con flex:1 a secas se comprimiria y quedaria cortada. */
.contenido {
  flex: 1 0 auto; max-width: 1200px; width: 100%; margin: 0 auto;
  /* El hueco del teclado se suma al final: sin el, los ultimos campos del formulario quedan
     debajo del teclado y no hay forma de deslizarse hasta ellos. */
  padding: 1.4rem calc(1.4rem + var(--safe-right))
           calc(1.4rem + var(--tabbar-h) + var(--kb)) calc(1.4rem + var(--safe-left));
}
/* Columna flex para que el plano (con la prop `llenar`) reparta con la barra de leyenda
   el alto disponible, sin tener que adivinar en CSS cuanto mide cada cosa. */
.contenido.inmersivo { display: flex; flex-direction: column; padding: 0; padding-bottom: var(--tabbar-h); max-width: none; width: 100%; }

.velo { display: none; }
.tabbar { display: none; }

@media (max-width: 820px) {
  .sidebar {
    position: fixed; z-index: 60; transform: translateX(-100%); transition: transform 0.2s ease;
  }
  .sidebar.abierto { transform: translateX(0); box-shadow: var(--sombra-md); }
  .velo { display: block; position: fixed; inset: 0; z-index: 50; background: rgba(2, 6, 23, 0.4); }

  /* El cajón lateral sigue existiendo (herramientas de admin, Salir) pero ya no se abre
     desde arriba: la barra inferior — "Más" — es la única entrada en móvil, como en
     cualquier app nativa. Un solo disparador es más intuitivo que dos botones para lo mismo. */
  .menu { display: none; }

  /* Objetivos táctiles más grandes dentro del cajón: con el pulgar hay que acertarle
     a algo mayor que con el cursor de un mouse. */
  .enlace { padding: 0.9rem 0.9rem; font-size: 1.02rem; gap: 0.8rem; }
  .ico { font-size: 1.15rem; }

  /* nav{flex-direction:column;...} de arriba es para el cajón lateral, pero por ser el
     mismo tag <nav> dentro del mismo componente también alcanza a esta barra: se
     sobrescribe cada propiedad que importa en vez de confiar en la cascada. */
  /* Con el teclado abierto la barra inferior se esconde: queda tapada por el teclado y solo
     resta sitio a un formulario que ya va justo de alto. */
  .con-teclado .tabbar { display: none; }
  .tabbar {
    display: flex; flex-direction: row; gap: 0; overflow: visible; flex: none;
    position: fixed; left: 0; right: 0; bottom: 0; z-index: 55;
    height: var(--tabbar-h);
    padding: 0 var(--safe-right) var(--safe-bottom) var(--safe-left);
    background: var(--panel); border-top: 1px solid var(--border); box-shadow: 0 -2px 10px rgba(2, 6, 23, 0.06);
  }
  .tab {
    flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center;
    gap: 0.15rem; color: var(--muted); text-decoration: none; font: inherit;
    background: none; border: none; padding: 0.3rem 0.2rem;
  }
  .tab .ico { font-size: 1.35rem; line-height: 1; }
  .tab .txt { font-size: 0.66rem; font-weight: 700; }
  .tab.router-link-exact-active, .tab-mas.activo { color: var(--acento); }
}
</style>
