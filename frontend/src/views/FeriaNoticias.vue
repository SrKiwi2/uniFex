<script setup>
import { ref, reactive, computed, watch, onMounted, onUnmounted } from 'vue';
import { url as urlApi } from '../config.js';
import { escucharListasPublicas, SUSCRIPCION_NOTICIAS } from '../publicoEnVivo.js';
import { IDIOMAS, TEXTOS } from '../i18n/feriaPublica.js';

/*
 * Todas las noticias de la edición activa (V42), agrupadas por día de la feria: Día 1, 2 y 3.
 * Se llega desde "Ver todas las noticias" del carrusel de /feria. Pública, sin sesión.
 *
 * Cada día enseña sus 5 últimas registradas y "Ver más" despliega el resto, siempre de la última
 * registrada a la primera (el orden en que llegan del servidor). En vivo por WebSocket: una
 * noticia publicada, editada o eliminada desde el panel aparece aquí sin recargar.
 */

const DIAS = [1, 2, 3];
const VISIBLES_POR_DIA = 5;

// Idioma y tema: los mismos que eligió la persona en /feria (mismas claves de localStorage).
const CLAVE_IDIOMA = 'feria.idioma';
function idiomaInicial() {
  try {
    const guardado = localStorage.getItem(CLAVE_IDIOMA);
    if (TEXTOS[guardado]) return guardado;
  } catch { /* sin almacenamiento: se decide por el navegador */ }
  return (navigator.language || '').toLowerCase().startsWith('pt') ? 'pt' : 'es';
}
const idioma = ref(idiomaInicial());
const t = computed(() => TEXTOS[idioma.value]);
const locale = computed(() => IDIOMAS.find((i) => i.codigo === idioma.value).locale);
function cambiarIdioma(codigo) {
  idioma.value = codigo;
  try { localStorage.setItem(CLAVE_IDIOMA, codigo); } catch { /* solo se pierde el recuerdo */ }
}
const langAnterior = document.documentElement.lang;
watch(locale, (l) => { document.documentElement.lang = l; }, { immediate: true });

function temaInicialOscuro() {
  try {
    const guardado = localStorage.getItem('tema');
    if (guardado) return guardado === 'dark';
  } catch { /* sin almacenamiento */ }
  return window.matchMedia('(prefers-color-scheme: dark)').matches;
}
const temaOscuro = temaInicialOscuro();

// Producción sirve la SPA bajo /app/ (ver FeriaPublica.imgPublica).
const BASE = import.meta.env.BASE_URL;

const lista = ref(null);   // null = todavía no llegó nada
const error = ref(false);

async function cargar() {
  error.value = false;
  try {
    const r = await fetch(urlApi(SUSCRIPCION_NOTICIAS.ruta));
    if (!r.ok) throw new Error(`HTTP ${r.status}`);
    const d = await r.json();
    if (Array.isArray(d)) lista.value = d;
  } catch {
    // Si ya había algo en pantalla, se deja; el aviso solo sale cuando no hay nada que mostrar.
    if (lista.value === null) error.value = true;
  }
}

function formatearFecha(iso) {
  if (!iso) return '';
  // Mediodía fijo: new Date('2026-09-18') es medianoche UTC y en Bolivia cae el día anterior.
  return new Date(`${iso}T12:00:00`).toLocaleDateString(locale.value, { day: 'numeric', month: 'long', year: 'numeric' });
}

// El servidor ya las manda de la última registrada a la primera; se agrupan sin reordenar.
const porDia = computed(() =>
  DIAS.map((dia) => ({
    dia,
    noticias: (lista.value || [])
      .filter((n) => n.dia === dia)
      .map((n) => ({ ...n, urlMedio: urlApi(n.urlMedio), fechaTexto: formatearFecha(n.fecha) })),
  })));

const hayNoticias = computed(() => (lista.value || []).length > 0);

const desplegados = reactive({ 1: false, 2: false, 3: false });
function visibles(grupo) {
  return desplegados[grupo.dia] ? grupo.noticias : grupo.noticias.slice(0, VISIBLES_POR_DIA);
}

function cantidadTexto(n) {
  return n === 1 ? t.value.todasNoticias.unaNoticia : t.value.todasNoticias.cantidad.replace('{n}', n);
}
function textoDia(dia) {
  return t.value.todasNoticias.dia.replace('{n}', dia);
}

function irAlDia(dia) {
  document.getElementById(`dia-${dia}`)?.scrollIntoView({ behavior: 'smooth', block: 'start' });
}

// "Ver menos" deja a la persona al principio de su día, no a mitad de la página en otro sitio.
function alternarDia(dia) {
  desplegados[dia] = !desplegados[dia];
  if (!desplegados[dia]) irAlDia(dia);
}

let dejarDeEscuchar = null;
onMounted(() => {
  window.scrollTo(0, 0);
  cargar();
  dejarDeEscuchar = escucharListasPublicas([
    { ...SUSCRIPCION_NOTICIAS, onLista: (l) => { lista.value = l; error.value = false; } },
  ]);
});
onUnmounted(() => {
  dejarDeEscuchar?.();
  document.documentElement.lang = langAnterior;
});
</script>

<template>
  <div class="feria-noticias" :class="{ 'tema-oscuro': temaOscuro }">
    <header class="barra">
      <div class="barra-interior">
        <RouterLink to="/feria" class="barra-marca">
          <img :src="`${BASE}logo-fexpo-v2.png`" alt="" class="barra-logo" width="1254" height="1254" />
          <span>FEXPO UAP</span>
        </RouterLink>
        <div class="barra-acciones">
          <div class="idioma" role="group" aria-label="Idioma / Idioma">
            <button
              v-for="i in IDIOMAS"
              :key="i.codigo"
              type="button"
              class="idioma-opcion"
              :class="{ activo: idioma === i.codigo }"
              :aria-pressed="idioma === i.codigo"
              :title="i.nombre"
              @click="cambiarIdioma(i.codigo)"
            >{{ i.etiqueta }}</button>
          </div>
        </div>
      </div>
    </header>

    <main class="contenido">
      <RouterLink to="/feria" class="volver">
        <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M15 5l-7 7 7 7" /></svg>
        {{ t.todasNoticias.volver }}
      </RouterLink>

      <h1 class="titulo">{{ t.todasNoticias.titulo }}</h1>
      <p class="subtitulo">{{ t.todasNoticias.subtitulo }}</p>

      <p v-if="lista === null && !error" class="estado">{{ t.todasNoticias.cargando }}</p>
      <div v-else-if="error" class="estado">
        <p>{{ t.todasNoticias.error }}</p>
        <button type="button" class="btn btn-primario" @click="cargar">{{ t.todasNoticias.reintentar }}</button>
      </div>
      <p v-else-if="!hayNoticias" class="estado">{{ t.todasNoticias.sinNoticias }}</p>

      <template v-else>
        <!-- Atajos a cada día: fijos arriba mientras se hace scroll. -->
        <nav class="dias-nav" :aria-label="t.todasNoticias.navegacionDias">
          <button v-for="g in porDia" :key="g.dia" type="button" class="dia-chip" @click="irAlDia(g.dia)">
            {{ textoDia(g.dia) }} <span class="dia-chip-cantidad">{{ g.noticias.length }}</span>
          </button>
        </nav>

        <section v-for="g in porDia" :id="`dia-${g.dia}`" :key="g.dia" class="dia" :aria-labelledby="`titulo-dia-${g.dia}`">
          <header class="dia-cabecera">
            <h2 :id="`titulo-dia-${g.dia}`" class="dia-titulo">{{ textoDia(g.dia) }}</h2>
            <span class="dia-cantidad">{{ cantidadTexto(g.noticias.length) }}</span>
          </header>

          <p v-if="!g.noticias.length" class="dia-vacio">{{ t.todasNoticias.sinNoticiasDia }}</p>

          <div v-else class="lista">
            <article v-for="n in visibles(g)" :key="n.id" class="noticia">
              <div class="noticia-medio">
                <video v-if="n.medioTipo === 'VIDEO'" :src="n.urlMedio" muted loop autoplay playsinline controls preload="metadata" />
                <img v-else :src="n.urlMedio" :alt="n.titulo" loading="lazy" />
              </div>
              <div class="noticia-texto">
                <time class="noticia-fecha" :datetime="n.fecha">{{ n.fechaTexto }}</time>
                <h3 class="noticia-titulo">{{ n.titulo }}</h3>
                <p class="noticia-contenido">{{ n.texto }}</p>
              </div>
            </article>
          </div>

          <div v-if="g.noticias.length > VISIBLES_POR_DIA" class="dia-mas">
            <button type="button" class="btn btn-fantasma" :aria-expanded="desplegados[g.dia]" @click="alternarDia(g.dia)">
              {{ desplegados[g.dia]
                ? t.todasNoticias.verMenos
                : t.todasNoticias.verMas.replace('{n}', g.noticias.length - VISIBLES_POR_DIA) }}
            </button>
          </div>
        </section>
      </template>
    </main>
  </div>
</template>

<style scoped>
@import url('https://fonts.googleapis.com/css2?family=Anton&family=Work+Sans:wght@400;500;600;700&display=swap');

/* Misma paleta que FeriaPublica.vue: esta vista es su continuación. */
.feria-noticias {
  --fx-lima: #a8d824;
  --fx-rojo: #e31e24;
  --fx-papel: #f7f9f1;
  --fx-panel: #ffffff;
  --fx-borde: #e1e6d6;
  --fx-texto: #16210f;
  --fx-muted: #5c6b53;
  min-height: 100vh;
  background: var(--fx-papel);
  color: var(--fx-texto);
  font-family: 'Work Sans', system-ui, sans-serif;
}
.feria-noticias.tema-oscuro {
  --fx-papel: #0a1207;
  --fx-panel: #121d0e;
  --fx-borde: #22301a;
  --fx-texto: #edf2e6;
  --fx-muted: #9db08e;
}

.barra {
  position: sticky;
  top: 0;
  z-index: 20;
  background: rgba(4, 15, 4, 0.9);
  backdrop-filter: blur(10px);
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}
.barra-interior {
  max-width: 1100px;
  margin: 0 auto;
  padding: 0.6rem 1rem;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
}
.barra-marca {
  display: flex;
  align-items: center;
  gap: 0.55rem;
  color: #ffffff;
  text-decoration: none;
  font-family: 'Anton', sans-serif;
  font-size: 1.05rem;
}
.barra-logo { width: 34px; height: 34px; object-fit: contain; }
.idioma {
  display: flex;
  gap: 0.2rem;
  padding: 0.2rem;
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 999px;
}
.idioma-opcion {
  min-width: 36px;
  height: 30px;
  padding: 0 0.55rem;
  border: 0;
  border-radius: 999px;
  background: transparent;
  color: #f2f7ec;
  font: inherit;
  font-size: 0.8rem;
  font-weight: 700;
  cursor: pointer;
}
.idioma-opcion:hover { background: rgba(255, 255, 255, 0.14); }
.idioma-opcion.activo { background: var(--fx-lima); color: #10280a; }
.idioma-opcion:focus-visible { outline: 2px solid var(--fx-lima); outline-offset: 2px; }

.contenido { max-width: 1100px; margin: 0 auto; padding: 1.5rem 1rem 4rem; }

.volver {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  color: var(--fx-muted);
  text-decoration: none;
  font-weight: 600;
  font-size: 0.92rem;
}
.volver:hover { color: var(--fx-rojo); }
.volver svg { width: 18px; height: 18px; fill: none; stroke: currentColor; stroke-width: 2.5; stroke-linecap: round; stroke-linejoin: round; }

.titulo {
  margin: 1rem 0 0.4rem;
  font-family: 'Anton', sans-serif;
  font-weight: 400;
  font-size: clamp(2rem, 5vw, 3rem);
  line-height: 1.1;
}
.subtitulo { margin: 0 0 1.5rem; color: var(--fx-muted); max-width: 60ch; line-height: 1.5; }
.estado { padding: 3rem 0; text-align: center; color: var(--fx-muted); }
.estado .btn { margin-top: 0.8rem; }

.dias-nav {
  position: sticky;
  top: 52px;
  z-index: 10;
  display: flex;
  gap: 0.5rem;
  padding: 0.7rem 0;
  margin-bottom: 0.5rem;
  background: var(--fx-papel);
  overflow-x: auto;
}
.dia-chip {
  display: inline-flex;
  align-items: center;
  gap: 0.45rem;
  flex-shrink: 0;
  padding: 0.45rem 0.95rem;
  border: 1px solid var(--fx-borde);
  border-radius: 999px;
  background: var(--fx-panel);
  color: var(--fx-texto);
  font: inherit;
  font-weight: 600;
  cursor: pointer;
}
.dia-chip:hover { border-color: var(--fx-rojo); }
.dia-chip:focus-visible { outline: 2px solid var(--fx-rojo); outline-offset: 2px; }
.dia-chip-cantidad {
  min-width: 1.4rem;
  padding: 0 0.35rem;
  border-radius: 999px;
  background: var(--fx-lima);
  color: #10280a;
  font-size: 0.78rem;
  text-align: center;
}

/* scroll-margin: al saltar a un día, que no quede tapado por la barra y los atajos fijos. */
.dia { padding-top: 1.2rem; scroll-margin-top: 110px; }
.dia-cabecera {
  display: flex;
  align-items: baseline;
  gap: 0.8rem;
  padding-bottom: 0.6rem;
  margin-bottom: 1rem;
  border-bottom: 3px solid var(--fx-rojo);
}
.dia-titulo { margin: 0; font-family: 'Anton', sans-serif; font-weight: 400; font-size: 1.8rem; }
.dia-cantidad { color: var(--fx-muted); font-size: 0.9rem; font-weight: 600; }
.dia-vacio { margin: 0 0 1.5rem; color: var(--fx-muted); }

.lista { display: flex; flex-direction: column; gap: 1rem; }

/* Tarjeta horizontal: el medio a la izquierda en un 16:9 fijo (recortado con cover), el texto a
   la derecha. En celular, el medio arriba y el texto debajo. */
.noticia {
  display: grid;
  grid-template-columns: minmax(0, 42%) minmax(0, 1fr);
  overflow: hidden;
  border: 1px solid var(--fx-borde);
  border-radius: 16px;
  background: var(--fx-panel);
}
.noticia-medio { position: relative; aspect-ratio: 16 / 9; overflow: hidden; background: #0b0f08; }
.noticia-medio img, .noticia-medio video {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.noticia-texto { padding: 1.1rem 1.3rem; min-width: 0; }
.noticia-fecha {
  display: inline-block;
  margin-bottom: 0.45rem;
  padding: 0.2rem 0.65rem;
  border-radius: 999px;
  background: var(--fx-lima);
  color: #10280a;
  font-size: 0.78rem;
  font-weight: 700;
}
.noticia-titulo {
  margin: 0 0 0.4rem;
  font-family: 'Anton', sans-serif;
  font-weight: 400;
  font-size: 1.35rem;
  line-height: 1.2;
  overflow-wrap: anywhere;
}
.noticia-contenido {
  margin: 0;
  color: var(--fx-muted);
  line-height: 1.55;
  white-space: pre-line;
  overflow-wrap: anywhere;
}

.dia-mas { display: flex; justify-content: center; margin: 1.1rem 0 1.5rem; }

/* Botones con los colores de la feria, igual que en FeriaPublica.vue (los de style.css son los
   del panel). */
.feria-noticias .btn-primario { background: var(--fx-lima); border-color: var(--fx-lima); color: #10280a; font-weight: 700; }
.feria-noticias .btn-primario:hover { background: #93c11d; filter: none; }
.feria-noticias .btn-fantasma { background: var(--fx-panel); border-color: var(--fx-borde); color: var(--fx-texto); font-weight: 600; }
.feria-noticias .btn-fantasma:hover { border-color: var(--fx-rojo); }

@media (max-width: 720px) {
  .noticia { grid-template-columns: 1fr; }
  .noticia-texto { padding: 0.95rem 1.05rem 1.1rem; }
  .dia-titulo { font-size: 1.5rem; }
}
</style>
