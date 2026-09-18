<script setup>
import { ref, computed, onMounted, onUnmounted, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useAuthStore } from '../stores/auth';
import { usePermisosStore } from '../stores/permisos';
import { usePuestosStore } from '../stores/puestos';
import { toast } from '../ui/toast';
import { alerta } from '../ui/alerta';
import { tema, alternarTema } from '../ui/tema';
import { iniciarPresencia, marcarPantalla, detenerPresencia } from '../ui/presencia';
import AvisoAnuncios from './AvisoAnuncios.vue';
import { useAnunciosStore } from '../stores/anuncios';

const auth = useAuthStore();
const permisos = usePermisosStore();
const tienda = usePuestosStore();
permisos.asegurar();
const router = useRouter();
const route = useRoute();
const abierto = ref(false); // sidebar en móvil

// El titulo y el modo inmersivo vienen de la meta de la ruta activa:
// AppLayout es el unico que renderiza AppShell, y las vistas solo traen su contenido.
const titulo = computed(() => route.meta.titulo || '');
const inmersivo = computed(() => Boolean(route.meta.inmersivo));

/*
 * El menu entero, en el orden en que se quiere leer. Cada entrada dice a que PANTALLA
 * corresponde, y el filtro de abajo decide cuales se enseñan.
 *
 * Antes esto era un `if (auth.puedeEditarPlano)` con la lista de administracion dentro: el
 * menu estaba cableado a un rol concreto, asi que un rol nuevo —CONTROL en la puerta,
 * VERIFICADOR— no tenia forma de ver lo suyo sin tocar este archivo.
 */
/*
 * Los grupos, en el orden en que se leen. `MI TRABAJO` va primero porque es lo que abre un
 * vendedor cincuenta veces al dia; `ADMINISTRACION` al final porque se toca una vez por
 * semana. Las claves coinciden con `PantallasSistema.grupo` en el servidor, que es lo que
 * agrupa las casillas de "Permisos por rol": si discrepan, la misma pantalla sale en un grupo
 * distinto en cada sitio y nadie sabe cual es el bueno. Hay una guarda que lo comprueba
 * (verificar-menu-agrupado.mjs).
 */
const GRUPOS = [
  { clave: 'Mi trabajo', titulo: 'Mi trabajo' },
  { clave: 'Feria', titulo: 'Feria' },
  { clave: 'Acreditacion', titulo: 'Acreditación' },
  { clave: 'Plano y precios', titulo: 'Plano y precios' },
  { clave: 'Administracion', titulo: 'Administración' },
];

const TODOS = [
  // --- Mi trabajo: el dia a dia del vendedor ---
  { a: '/', p: 'inicio', g: 'Mi trabajo', icono: '🏠', txt: 'Inicio' },
  { a: '/mapa', p: 'mapa', g: 'Mi trabajo', icono: '🗺️', txt: 'Mapa de ventas' },
  { a: '/venta', p: 'venta', g: 'Mi trabajo', icono: '🛒', txt: 'Registrar venta' },
  { a: '/mis-ventas', p: 'mis-ventas', g: 'Mi trabajo', icono: '🧾', txt: 'Mis ventas' },
  { a: '/notificaciones', p: 'notificaciones', g: 'Mi trabajo', icono: '🔔', txt: 'Notificaciones' },

  // --- Feria: lo que pasa de puertas afuera ---
  { a: '/tablero', p: 'tablero', g: 'Feria', icono: '📌', txt: 'Tablero' },
  { a: '/catalogo', p: 'catalogo', g: 'Feria', icono: '🏷️', txt: 'Catálogo' },
  { a: '/inscripciones', p: 'inscripciones', g: 'Feria', icono: '📋', txt: 'Inscripciones' },
  { a: '/interesados', p: 'interesados', g: 'Feria', icono: '🙋', txt: 'Interesados' },
  { a: '/noches-fexpo', p: 'noches-fexpo', g: 'Feria', icono: '🎤', txt: 'Noches de FEXPO' },
  { a: '/noticias', p: 'noticias', g: 'Feria', icono: '📰', txt: 'Noticias' },

  // --- Acreditacion: la puerta ---
  { a: '/credenciales', p: 'credenciales', g: 'Acreditacion', icono: '🪪', txt: 'Credenciales' },
  { a: '/credenciales-apoyo', p: 'credenciales-apoyo', g: 'Acreditacion', icono: '🎫', txt: 'Credenciales personal apoyo' },
  { a: '/impresion-masiva', p: 'impresion-masiva', g: 'Acreditacion', icono: '🖨️', txt: 'Impresión Masiva' },
  { a: '/escaner', p: 'escaner', g: 'Acreditacion', icono: '📷', txt: 'Escanear credencial' },
  { a: '/control-acceso', p: 'control-acceso', g: 'Acreditacion', icono: '📊', txt: 'Control de acceso' },

  // --- Plano y precios: como esta armada la feria ---
  { a: '/editor', p: 'editor', g: 'Plano y precios', icono: '✏️', txt: 'Editor del plano' },
  { a: '/categorias', p: 'categorias', g: 'Plano y precios', icono: '🏷️', txt: 'Categorías' },
  { a: '/puestos', p: 'puestos', g: 'Plano y precios', icono: '🔢', txt: 'Puestos' },

  // --- Administracion ---
  { a: '/direccion', p: 'direccion', g: 'Administracion', icono: '📈', txt: 'Tablero de dirección' },
  { a: '/reportes', p: 'reportes', g: 'Administracion', icono: '📊', txt: 'Reportes' },
  { a: '/seguimiento', p: 'seguimiento', g: 'Administracion', icono: '📡', txt: 'Seguimiento en vivo' },
  { a: '/vendedores', p: 'vendedores', g: 'Administracion', icono: '👥', txt: 'Vendedores' },
  { a: '/anuncios', p: 'anuncios', g: 'Administracion', icono: '📣', txt: 'Anuncios' },
  { a: '/seguimiento-facultad', p: 'seguimiento-facultad', g: 'Feria', icono: '🎓', txt: 'Seguimiento por facultad' },
  { a: '/responsables-extra', p: 'responsables-extra', g: 'Acreditacion', icono: '🧾', txt: 'Responsables extra' },
  { a: '/usuarios', p: 'usuarios', g: 'Administracion', icono: '👤', txt: 'Usuarios' },
  { a: '/roles', p: 'roles', g: 'Administracion', icono: '🛡️', txt: 'Roles' },
  { a: '/permisos', p: 'permisos', g: 'Administracion', icono: '🔐', txt: 'Permisos por rol' },
  { a: '/personas', p: 'personas', g: 'Administracion', icono: '🪪', txt: 'Personas' },
  { a: '/personal-apoyo', p: 'personal-apoyo', g: 'Administracion', icono: '👥', txt: 'Personal de apoyo' },
  { a: '/mantenimiento', p: 'mantenimiento', g: 'Administracion', icono: '⚙️', txt: 'Mantenimiento' },
  { a: '/errores', soloAdministracion: true, g: 'Administracion', icono: '⚠️', txt: 'Registro de errores' },
  { a: '/whatsapp', soloAdministracion: true, g: 'Administracion', icono: '💬', txt: 'WhatsApp' },
];

const anuncios = useAnunciosStore();

const enlaces = computed(() => TODOS.filter((e) => e.soloAdministracion
  ? auth.puedeEditarPlano : permisos.puedeVer(e.p)));

/**
 * El menu ya agrupado, sin los grupos que quedan vacios.
 *
 * Se filtra PRIMERO y se agrupa despues: a un CONTROL le toca una sola pantalla de toda
 * "Administracion", y pintarle esa cabecera sobre un grupo vacio seria enseñarle secciones
 * que no puede abrir. Con esto, cada quien ve solo los titulos que le corresponden.
 */
const grupos = computed(() => GRUPOS
  .map((g) => ({ ...g, enlaces: enlaces.value.filter((e) => e.g === g.clave) }))
  .filter((g) => g.enlaces.length > 0));

/**
 * Las 4 de la barra inferior del movil.
 *
 * Salen del grupo "Mi trabajo" y no de los primeros cuatro enlaces sueltos: al agrupar, ese
 * `slice(0, 4)` habria empezado a arrastrar lo que quedara arriba del todo —para un CONTROL,
 * pantallas de acreditacion— en vez de las tareas del dia a dia. Si el usuario no tiene ese
 * grupo (un jefe, que solo consulta), se cae a los primeros que sí puede ver.
 */
const enlacesPrincipales = computed(() => {
  const mias = enlaces.value.filter((e) => e.g === 'Mi trabajo');
  return (mias.length ? mias : enlaces.value).slice(0, 4);
});

const iconoTema = computed(() => (tema.value === 'dark' ? '🌙' : tema.value === 'light' ? '☀️' : '🌗'));

/*
 * Que un cambio de permisos llegue al telefono sin cerrar sesion. Dos vias, porque ninguna
 * basta sola:
 *
 *   - el AVISO del servidor (PERMISOS_CAMBIADOS), que es inmediato mientras la aplicacion
 *     este abierta y conectada;
 *   - VOLVER AL FRENTE, porque Android corta los sockets de una aplicacion en segundo plano.
 *     Sin esto, el aviso se pierde justo en el caso mas comun: al vendedor le cambian el rol
 *     mientras tiene el telefono en el bolsillo.
 */
function alCambiarPermisos(n) {
  if (n?.tipo === 'MANTENIMIENTO_ACTIVO') {
    expulsarPorMantenimiento(n.cuerpo || n.asunto || 'Sistema en mantenimiento');
    return;
  }
  if (n?.tipo !== 'PERMISOS_CAMBIADOS') return;
  permisos.recargar().then(() => {
    toast('Cambiaron tus opciones del menú', 'info');
    // Si estaba parado en una pantalla que ya no le toca, se le saca de ahi.
    const pantalla = route.meta?.pantalla;
    if (pantalla && !permisos.puedeVer(pantalla)) router.push('/');
  });
}

function expulsarPorMantenimiento(mensaje) {
  if (mantenimientoMostrado) return;
  mantenimientoMostrado = true;
  permisos.limpiar();
  auth.logout();
  router.push('/login');
  alerta(mensaje || 'Sistema en mantenimiento', 'advertencia', 0);
}

function alMantenimientoHttp(e) {
  expulsarPorMantenimiento(e.detail?.mensaje);
}

function alVolverAlFrente() {
  if (typeof document !== 'undefined' && document.visibilityState !== 'visible') return;
  permisos.recargar();
}

let quitarOyente = null;
let mantenimientoMostrado = false;
onMounted(() => {
  /*
   * `conectar()` y no `asegurar()`: lo unico que hace falta aqui es el canal de avisos
   * personales, no la lista de casetas. `asegurar()` ademas la DESCARGA, y para un usuario de
   * CONTROL —que solo escanea en la puerta— eso serian cientos de kilobytes por arranque que
   * no va a mirar nunca.
   *
   * Sin esta linea el aviso no llegaba: los oyentes se registran en la tienda, pero si nadie
   * ha abierto el mapa todavia no hay conexion por la que puedan llegar.
   */
  tienda.conectar();
  quitarOyente = tienda.registrarNotificaciones(alCambiarPermisos);
  window.addEventListener('unifex:mantenimiento', alMantenimientoHttp);
  document.addEventListener('visibilitychange', alVolverAlFrente);
  window.addEventListener('online', alVolverAlFrente);
  // El latido que alimenta "Seguimiento en vivo". Va aqui y no en cada vista: se late mientras
  // haya sesion abierta, sea cual sea la pantalla.
  iniciarPresencia();
  // Los anuncios: se piden los vigentes y se abre su canal. Una vez por sesion.
  anuncios.asegurar();
  marcarPantalla(route.meta?.pantalla, route.meta?.titulo);
});

// Cambiar de pantalla es el dato mas util del seguimiento, asi que se informa en el acto en vez
// de esperar al siguiente latido.
watch(() => route.fullPath, () => marcarPantalla(route.meta?.pantalla, route.meta?.titulo));
onUnmounted(() => {
  if (quitarOyente) quitarOyente();
  window.removeEventListener('unifex:mantenimiento', alMantenimientoHttp);
  document.removeEventListener('visibilitychange', alVolverAlFrente);
  window.removeEventListener('online', alVolverAlFrente);
});

function salir() {
  // Avisar de la salida ANTES de tirar el token: despues, la peticion iria sin credencial y el
  // usuario se quedaria en la lista de conectados hasta que venciera su silencio.
  detenerPresencia();
  // Los anuncios del turno anterior no pueden sobrevivir al cambio de usuario.
  anuncios.desconectar();
  // Los permisos se olvidan con la sesion: en un equipo compartido, el siguiente en entrar no
  // puede heredar el menu del anterior aunque sea de otro rol.
  permisos.limpiar();
  auth.logout();
  router.push('/login');
}
</script>

<template>
  <div class="shell">
    <!-- Los anuncios de administracion. Van AQUI, en el armazon, para que salgan en cualquier
         pantalla: el mapa, el registro de una venta, credenciales. No bloquean nada. -->
    <AvisoAnuncios />
    <aside class="sidebar" :class="{ abierto }">
      <div class="marca">
        <span class="logo">UF</span>
        <strong>UniFex</strong>
      </div>
      <nav>
        <!-- Un grupo sin enlaces visibles no se pinta (ver `grupos`), así que la cabecera
             nunca aparece sobre un hueco. -->
        <div v-for="g in grupos" :key="g.clave" class="grupo">
          <h3 class="grupo-titulo">{{ g.titulo }}</h3>
          <router-link v-for="e in g.enlaces" :key="e.a" :to="e.a" class="enlace" @click="abierto = false">
            <span class="ico">{{ e.icono }}</span>{{ e.txt }}
          </router-link>
        </div>
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
.grupo { display: flex; flex-direction: column; gap: 2px; }
/* La cabecera separa sin gritar: pequeña, en mayúsculas y en gris. Si compitiera en peso con
   los enlaces, leer el menú costaría más que antes de agruparlo. El primer grupo no lleva
   margen superior, que dejaría un hueco raro pegado al logotipo. */
.grupo-titulo {
  margin: 0.9rem 0 0.2rem; padding: 0 0.7rem;
  font-size: 0.68rem; font-weight: 800; letter-spacing: 0.08em;
  text-transform: uppercase; color: var(--muted);
}
.grupo:first-child .grupo-titulo { margin-top: 0.2rem; }
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
