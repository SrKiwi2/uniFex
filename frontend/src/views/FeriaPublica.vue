<script setup>
import { ref, onMounted, onUnmounted, computed, nextTick } from 'vue';
import { url as urlApi } from '../config.js';
import { alerta } from '../ui/alerta.js';

const loading = ref(true);
const error = ref(null);
const datos = ref(null);
const mostrarPlano = ref(false);
const temaOscuro = ref(false);
const menuAbierto = ref(false);
const heroLogoWrap = ref(null);
const ahora = ref(Date.now());

// Colores de las noches: los mismos rojo/morado/azul de las insignias de facultad del
// logo, reutilizados aquí como identificador de cada jornada.
const COLORES_NOCHE = ['#e31e24', '#3c1884', '#0048c0'];

// Programación nocturna: la feria aún no tiene un módulo de artistas en el backend, y el
// cartel real todavía no se revela. Ni el artista ni el tema de la noche tienen nombre
// propio todavía, así que cada jornada va como "Día N" con un artista en silueta —cuando
// haya algo confirmado, título y silueta pasan a traer el nombre real y su foto.
const nochesFexpo = [
  {
    dia: 18,
    fecha: 'Viernes 18 de septiembre',
    titulo: 'Día 1',
    descripcion: 'Inauguración oficial de la FEXPO UAP v2 2026 y encendido de la feria.'
  },
  {
    dia: 19,
    fecha: 'Sábado 19 de septiembre',
    titulo: 'Día 2',
    descripcion: 'Música y danzas de la región en el escenario central de la feria.'
  },
  {
    dia: 20,
    fecha: 'Domingo 20 de septiembre',
    titulo: 'Día 3',
    descripcion: 'Premiación a los mejores stands y concierto de clausura.'
  }
].map((n, i) => ({ ...n, color: COLORES_NOCHE[i % COLORES_NOCHE.length] }));

// Vitrina de zonas para el público visitante, curada a mano (no viene del backend).
// `invertido` alterna imagen/texto tipo revista; se anima al entrar en pantalla (ver
// onMounted).
const standsDestacados = [
  {
    imagen: '/STAND-MIPES.png',
    alt: 'Stand MYPES en la FEXPO UAP, con emprendedores atendiendo su puesto',
    titulo: 'Stand MYPES',
    descripcion: 'Las MYPES reúnen a las micro y pequeñas empresas de la región: emprendedores pandinos que muestran y venden sus productos artesanales, gastronomía y creaciones locales. Es la zona ideal para conocer el talento local, probar sabores de la tierra y llevarte algo hecho en Pando.',
    invertido: false
  },
  {
    imagen: '/STAND-EMPRESAS.png',
    alt: 'Stand Empresas en la FEXPO UAP, con marcas y cooperativas atendiendo al público',
    titulo: 'Stand Empresas',
    descripcion: 'El Stand Empresas reúne a empresas, bancos y cooperativas que apuestan por el desarrollo de Pando. Aquí presentan sus servicios y propuestas directamente a la comunidad, cara a cara con quienes visitan la feria.',
    invertido: true
  },
  {
    imagen: '/STAND-PROFESIOGRAFICA.png',
    alt: 'Stand Profesiográfico en la FEXPO UAP, con actividades de las carreras de la universidad',
    titulo: 'Stand Profesiográfico',
    descripcion: 'El Stand Profesiográfico invita a conocer, explorar y elegir tu futuro: cada facultad de la Universidad Amazónica de Pando muestra sus carreras con proyectos, laboratorios y actividades en vivo, para que quienes visitan la feria descubran su vocación y decidan qué estudiar.',
    invertido: false
  },
  {
    imagen: '/STAND-ARTESANIAS.png',
    alt: 'Stand Artesanías en la FEXPO UAP, con tallados en madera y tejidos hechos a mano',
    titulo: 'Stand Artesanías',
    descripcion: 'El Stand Artesanías reúne el trabajo hecho a mano de artesanos y artesanas de Pando: tallados en madera, tejidos, bisutería y piezas únicas que llevan la tradición local. Apoya el talento de la región y llévate contigo algo hecho con las manos de quienes lo crearon.',
    invertido: true
  },
  {
    imagen: '/AGROPECUARIA.png',
    alt: 'Stand Agropecuario en la FEXPO UAP, con ganadería y producción del campo pandino',
    titulo: 'Stand Agropecuario',
    descripcion: 'El Stand Agropecuario muestra el trabajo del campo pandino: ganadería, producción sostenible y proyectos agrícolas de la región. Apoya a los productores locales y descubre de cerca cómo se cultiva y se cría lo que llega a tu mesa.',
    invertido: false
  },
  {
    imagen: '/STAND-VIVERO.png',
    alt: 'Stand Planta Viveros en la FEXPO UAP, con plantines y proyectos de conservación de fauna',
    titulo: 'Stand Planta Viveros',
    descripcion: 'El Stand Planta Viveros impulsa el cuidado del medio ambiente: viveros de plantas nativas, estudios de fauna y proyectos de conservación que protegen los bosques y la biodiversidad de Pando. Súmate a sembrar un futuro más verde y sostenible para la región.',
    invertido: true
  },
  {
    imagen: '/STAND-VEHICULAR.png',
    alt: 'Stand Vehicular en la FEXPO UAP, con camionetas, autos y motos en exhibición',
    titulo: 'Stand Vehicular',
    descripcion: 'El Stand Vehicular reúne a las principales marcas y concesionarias de la región, con camionetas, autos y motos de último modelo en exhibición. Ven a descubrir las novedades del mercado automotor y conocer de cerca lo último en tecnología vehicular.',
    invertido: false
  },
  {
    imagen: '/STAND-COMIDA.png',
    alt: 'Stand Comida en la FEXPO UAP, con anticuchos, salchipapas y hamburguesas recién preparados',
    titulo: 'Stand Comida',
    descripcion: 'El Stand Comida invita a la familia a disfrutar de una gran variedad de platos, desde anticuchos y salchipapas típicos hasta hamburguesas y opciones para todos los gustos. Ven con hambre y descubre los sabores que se preparan al momento, listos para compartir.',
    invertido: true
  }
];

// Formulario "Quiero exponer": registro público de interesados (sin login).
// `tocado` guarda qué campos ya perdió el foco al menos una vez, para no mostrar errores
// en rojo antes de que la persona haya llegado a escribir nada (validación reactiva, pero
// silenciosa hasta que el campo se usa).
const formExponer = ref({ nombreCompleto: '', celular: '', empresa: '', rubro: '', categoriaId: '' });
const tocadoExponer = ref({ nombreCompleto: false, celular: false, empresa: false, rubro: false, categoriaId: false });
const enviandoExponer = ref(false);

const categoriasParaExponer = computed(() => datos.value?.categorias || []);

const erroresExponer = computed(() => {
  const f = formExponer.value;
  return {
    nombreCompleto: f.nombreCompleto.trim() ? '' : 'Escribe tu nombre completo',
    celular: f.celular.trim() ? '' : 'Escribe un número de celular',
    empresa: f.empresa.trim() ? '' : 'Escribe el nombre de tu empresa o emprendimiento',
    rubro: f.rubro.trim() ? '' : 'Escribe tu rubro',
    categoriaId: f.categoriaId ? '' : 'Elige una categoría'
  };
});

const formExponerValido = computed(() => Object.values(erroresExponer.value).every((m) => !m));

function tocarCampoExponer(campo) {
  tocadoExponer.value[campo] = true;
}

async function enviarFormExponer() {
  tocadoExponer.value = { nombreCompleto: true, celular: true, empresa: true, rubro: true, categoriaId: true };
  if (!formExponerValido.value || enviandoExponer.value) return;

  enviandoExponer.value = true;
  try {
    const res = await fetch(urlApi('/api/publico/interesados-stand'), {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        nombreCompleto: formExponer.value.nombreCompleto.trim(),
        celular: formExponer.value.celular.trim(),
        empresa: formExponer.value.empresa.trim(),
        rubro: formExponer.value.rubro.trim(),
        categoriaId: Number(formExponer.value.categoriaId)
      })
    });
    if (!res.ok) throw new Error('Respuesta no exitosa del servidor');

    formExponer.value = { nombreCompleto: '', celular: '', empresa: '', rubro: '', categoriaId: '' };
    tocadoExponer.value = { nombreCompleto: false, celular: false, empresa: false, rubro: false, categoriaId: false };
    await alerta('¡Registro enviado! Nos pondremos en contacto contigo muy pronto.', 'ok');
  } catch {
    await alerta('No pudimos enviar tu registro. Intenta de nuevo en unos minutos.', 'error');
  } finally {
    enviandoExponer.value = false;
  }
}

function cerrarMenu() {
  menuAbierto.value = false;
}

function onHeroMouseMove(e) {
  if (!heroLogoWrap.value) return;
  const rect = e.currentTarget.getBoundingClientRect();
  const x = (e.clientX - rect.left) / rect.width - 0.5;
  const y = (e.clientY - rect.top) / rect.height - 0.5;
  const grados = 14;
  heroLogoWrap.value.style.transform = `perspective(900px) rotateY(${x * grados}deg) rotateX(${-y * grados}deg)`;
}

function onHeroMouseLeave() {
  if (heroLogoWrap.value) heroLogoWrap.value.style.transform = '';
}

function pad(n) {
  return String(n).padStart(2, '0');
}

async function cargarDatos() {
  loading.value = true;
  error.value = null;
  try {
    const res = await fetch(urlApi('/api/publico/feria'));
    if (!res.ok) throw new Error('Error al cargar la información de la feria');
    datos.value = await res.json();
  } catch (e) {
    error.value = e.message;
  } finally {
    loading.value = false;
  }
}

function abrirPlano() {
  mostrarPlano.value = true;
  document.body.style.overflow = 'hidden';
}

function cerrarPlano() {
  mostrarPlano.value = false;
  document.body.style.overflow = '';
}

function toggleTema() {
  temaOscuro.value = !temaOscuro.value;
  document.documentElement.setAttribute('data-theme', temaOscuro.value ? 'dark' : 'light');
  localStorage.setItem('tema', temaOscuro.value ? 'dark' : 'light');
}

onMounted(() => {
  const guardado = localStorage.getItem('tema');
  if (guardado) {
    temaOscuro.value = guardado === 'dark';
    document.documentElement.setAttribute('data-theme', guardado);
  } else if (window.matchMedia('(prefers-color-scheme: dark)').matches) {
    temaOscuro.value = true;
    document.documentElement.setAttribute('data-theme', 'dark');
  }
  // Desplazamiento suave solo mientras esta vista está montada: es la única que navega
  // por anclas dentro de la misma página.
  document.documentElement.classList.add('fx-scroll-suave');
  cargarDatos();
});

let intervaloReloj = null;
onMounted(() => {
  intervaloReloj = setInterval(() => {
    ahora.value = Date.now();
  }, 1000);
});

// Entrada animada de las tarjetas de stands al hacer scroll. La clase que las oculta
// ("fx-revela-js") la pone este mismo código, no el CSS: así, si el observer no llega a
// correr por lo que sea, las tarjetas se quedan en su estado normal (visibles) en vez de
// invisibles para siempre.
let observadorStands = null;
onMounted(() => {
  nextTick(() => {
    const tarjetas = document.querySelectorAll('#stands-destacados .stand-destacado');
    if (!tarjetas.length) return;
    document.documentElement.classList.add('fx-revela-js');
    observadorStands = new IntersectionObserver(
      (entradas) => {
        entradas.forEach((entrada) => {
          if (entrada.isIntersecting) {
            entrada.target.classList.add('stand-visible');
            observadorStands.unobserve(entrada.target);
          }
        });
      },
      { threshold: 0.15, rootMargin: '0px 0px -60px 0px' }
    );
    tarjetas.forEach((el) => observadorStands.observe(el));
  });
});

onUnmounted(() => {
  document.documentElement.classList.remove('fx-scroll-suave', 'fx-revela-js');
  clearInterval(intervaloReloj);
  observadorStands?.disconnect();
});

const edicionActual = computed(() => {
  if (!datos.value?.edicion) return null;
  return datos.value.edicion;
});

const anioFeria = computed(() => edicionActual.value?.anio || new Date().getFullYear());

// Cuenta regresiva hasta el inicio: 18 de septiembre, 18:00, hora local de quien mira la
// página (no hay conversión de zona horaria — para el público de Cobija coincide).
const fechaInicioFeria = computed(() => new Date(anioFeria.value, 8, 18, 18, 0, 0).getTime());

const cuentaRegresiva = computed(() => {
  const restante = Math.max(0, fechaInicioFeria.value - ahora.value);
  return {
    dias: Math.floor(restante / 86400000),
    horas: Math.floor((restante % 86400000) / 3600000),
    minutos: Math.floor((restante % 3600000) / 60000),
    segundos: Math.floor((restante % 60000) / 1000),
    empezo: restante <= 0
  };
});
</script>

<template>
  <div class="feria-publica" :class="{ 'tema-oscuro': temaOscuro }">
    <!-- Navegación -->
    <header class="nav-fexpo">
      <div class="nav-inner">
        <a href="#inicio" class="nav-marca" @click="cerrarMenu">
          <img src="/logo-fexpo-v2.png" alt="FEXPO UAP" class="nav-logo" width="1254" height="1254" />
          <span>FEXPO UAP <em class="nav-anio">{{ anioFeria }}</em></span>
        </a>

        <nav class="nav-links" :class="{ abierto: menuAbierto }" aria-label="Secciones de la feria">
          <a href="#inicio" @click="cerrarMenu">Inicio</a>
          <a href="#artistas" @click="cerrarMenu">Artistas</a>
          <a href="#stands-destacados" @click="cerrarMenu">Stands</a>
          <a href="#stand" class="nav-cta" @click="cerrarMenu">Obtén tu stand</a>
        </nav>

        <div class="nav-acciones">
          <button class="btn-tema" @click="toggleTema" :aria-label="temaOscuro ? 'Cambiar a tema claro' : 'Cambiar a tema oscuro'">
            {{ temaOscuro ? '☀️' : '🌙' }}
          </button>
          <button
            class="nav-hamburguesa"
            @click="menuAbierto = !menuAbierto"
            :aria-expanded="menuAbierto"
            aria-label="Abrir menú de navegación"
          >
            <span></span><span></span><span></span>
          </button>
        </div>
      </div>
    </header>

    <!-- Hero: la foto pone la escena, el logo 3D y la cuenta regresiva son el centro -->
    <section id="inicio" class="hero" @mousemove="onHeroMouseMove" @mouseleave="onHeroMouseLeave">
      <div class="contenedor hero-contenido">
        <div class="hero-logo-3d" ref="heroLogoWrap">
          <img src="/FEXPO-UAP-TRASPARENTE.svg" alt="FEXPO UAP v.2.0" class="hero-logo-img" width="976" height="661" />
        </div>

        <div class="hero-cuenta">
          <template v-if="!cuentaRegresiva.empezo">
            <span class="cuenta-titulo">Comienza en</span>
            <div class="cuenta-grid">
              <div class="cuenta-item"><strong>{{ pad(cuentaRegresiva.dias) }}</strong><span>días</span></div>
              <span class="cuenta-sep">:</span>
              <div class="cuenta-item"><strong>{{ pad(cuentaRegresiva.horas) }}</strong><span>horas</span></div>
              <span class="cuenta-sep">:</span>
              <div class="cuenta-item"><strong>{{ pad(cuentaRegresiva.minutos) }}</strong><span>min</span></div>
              <span class="cuenta-sep">:</span>
              <div class="cuenta-item"><strong>{{ pad(cuentaRegresiva.segundos) }}</strong><span>seg</span></div>
            </div>
          </template>
          <span v-else class="cuenta-titulo cuenta-titulo--activa">¡La FEXPO UAP ya comenzó!</span>
          <span class="hero-lugar">18 de septiembre · Cobija, Pando</span>
        </div>

        <div class="hero-acciones">
          <button class="btn btn-primario btn-grande" @click="abrirPlano">
            Ver el plano de la feria
          </button>
          <a href="#stand" class="btn btn-hero-fantasma btn-grande">Quiero exponer</a>
        </div>
      </div>
      <svg class="hero-canopy" viewBox="0 0 1200 60" preserveAspectRatio="none" aria-hidden="true">
        <path d="M0,42 L40,14 L80,36 L120,8 L160,32 L200,16 L240,40 L280,10 L320,34 L360,6 L400,38 L440,18 L480,30 L520,4 L560,36 L600,12 L640,40 L680,18 L720,30 L760,6 L800,38 L840,14 L880,34 L920,8 L960,36 L1000,16 L1040,30 L1080,4 L1120,38 L1160,14 L1200,32 L1200,60 L0,60 Z" />
      </svg>
    </section>

    <!-- Artistas: primera sección tras el hero. Cartel aún por confirmar: cada noche
         muestra 3 espacios de artista como silueta, no nombres. -->
    <section id="artistas" class="seccion noches">
      <div class="noches-fondo" aria-hidden="true"></div>
      <div class="contenedor">
        <h2 class="titulo-seccion titulo-noches">Noches de FEXPO</h2>
        <p class="subtitulo-seccion subtitulo-noches">Cada jornada de exposición cierra con música y cultura en vivo, del 18 al 20 de septiembre. Los artistas ya están llegando y se revelarán muy pronto.</p>
        <div class="grid-noches">
          <article class="noche-card" v-for="n in nochesFexpo" :key="n.dia">
            <div class="noche-fecha" :style="{ background: n.color }"><strong>{{ n.dia }}</strong><span>set</span></div>
            <div class="noche-cuerpo">
              <h3>{{ n.titulo }}</h3>
              <p class="noche-dia-semana">{{ n.fecha }}</p>
              <p class="noche-descripcion">{{ n.descripcion }}</p>
              <div class="noche-artista">
                <div class="artista-silueta" :style="{ '--color-artista': n.color }" aria-hidden="true"></div>
                <span class="artista-etiqueta">Artista por revelar</span>
              </div>
            </div>
          </article>
        </div>
      </div>
    </section>

    <!-- Stands: qué se va a encontrar el público en la feria, con la primera zona
         destacada (MYPES). Pensada para quien viene a visitar, no para quien expone
         —esa conversación sigue más abajo, en "Zonas para exponer". -->
    <section id="stands-destacados" class="seccion stands">
      <div class="contenedor">
        <h2 class="titulo-seccion">Ven y disfruta con la familia: los stands te están esperando</h2>
        <p class="subtitulo-seccion">Recorre las distintas zonas de la feria y descubre lo que cada una tiene para ofrecer.</p>

        <article
          v-for="stand in standsDestacados"
          :key="stand.titulo"
          class="stand-destacado"
          :class="{ 'stand-destacado--invertido': stand.invertido }"
        >
          <img :src="stand.imagen" :alt="stand.alt" class="stand-destacado-img" />
          <div class="stand-destacado-texto">
            <span class="stand-destacado-tag">Zona destacada</span>
            <h3>{{ stand.titulo }}</h3>
            <p>{{ stand.descripcion }}</p>
          </div>
        </article>
      </div>
    </section>

    <!-- Quiero exponer: registro de interesados, con su propio color para distinguirse
         del resto de la página (verde de día, morado de noche, aquí rojo). -->
    <section id="stand" class="seccion exponer">
      <div class="exponer-fondo" aria-hidden="true"></div>
      <div class="contenedor">
        <h2 class="titulo-seccion titulo-exponer">Quiero exponer en la FEXPO UAP</h2>
        <p class="subtitulo-seccion subtitulo-exponer">Cuéntanos sobre ti y tu proyecto: la organización te contactará para ayudarte a reservar tu espacio.</p>

        <form class="exponer-card" @submit.prevent="enviarFormExponer" novalidate>
          <div class="campo-exponer">
            <label for="exp-nombre">Nombre completo</label>
            <input
              id="exp-nombre"
              type="text"
              v-model.trim="formExponer.nombreCompleto"
              @blur="tocarCampoExponer('nombreCompleto')"
              :class="{ invalido: tocadoExponer.nombreCompleto && erroresExponer.nombreCompleto }"
              autocomplete="name"
            />
            <span class="error-campo" v-if="tocadoExponer.nombreCompleto && erroresExponer.nombreCompleto">{{ erroresExponer.nombreCompleto }}</span>
          </div>

          <div class="campo-exponer">
            <label for="exp-celular">Número de celular</label>
            <input
              id="exp-celular"
              type="tel"
              v-model.trim="formExponer.celular"
              @blur="tocarCampoExponer('celular')"
              :class="{ invalido: tocadoExponer.celular && erroresExponer.celular }"
              autocomplete="tel"
              placeholder="Ej. 71234567"
            />
            <span class="error-campo" v-if="tocadoExponer.celular && erroresExponer.celular">{{ erroresExponer.celular }}</span>
          </div>

          <div class="campo-exponer">
            <label for="exp-empresa">Empresa o emprendimiento</label>
            <input
              id="exp-empresa"
              type="text"
              v-model.trim="formExponer.empresa"
              @blur="tocarCampoExponer('empresa')"
              :class="{ invalido: tocadoExponer.empresa && erroresExponer.empresa }"
            />
            <span class="error-campo" v-if="tocadoExponer.empresa && erroresExponer.empresa">{{ erroresExponer.empresa }}</span>
          </div>

          <div class="campo-exponer">
            <label for="exp-rubro">Rubro</label>
            <input
              id="exp-rubro"
              type="text"
              v-model.trim="formExponer.rubro"
              @blur="tocarCampoExponer('rubro')"
              :class="{ invalido: tocadoExponer.rubro && erroresExponer.rubro }"
              placeholder="Ej. gastronomía, artesanía, tecnología…"
            />
            <span class="error-campo" v-if="tocadoExponer.rubro && erroresExponer.rubro">{{ erroresExponer.rubro }}</span>
          </div>

          <div class="campo-exponer">
            <label for="exp-categoria">Categoría de stand</label>
            <select
              id="exp-categoria"
              v-model="formExponer.categoriaId"
              @blur="tocarCampoExponer('categoriaId')"
              :class="{ invalido: tocadoExponer.categoriaId && erroresExponer.categoriaId }"
            >
              <option value="" disabled>Elige una categoría</option>
              <option v-for="cat in categoriasParaExponer" :key="cat.id" :value="cat.id">{{ cat.nombre }}</option>
            </select>
            <span class="error-campo" v-if="tocadoExponer.categoriaId && erroresExponer.categoriaId">{{ erroresExponer.categoriaId }}</span>
          </div>

          <button type="submit" class="btn btn-primario btn-grande exponer-enviar" :disabled="enviandoExponer">
            {{ enviandoExponer ? 'Enviando…' : 'Enviar mi registro' }}
          </button>
        </form>
      </div>
    </section>

    <!-- Footer -->
    <footer class="footer-fexpo">
      <div class="contenedor footer-grid">
        <div class="footer-marca">
          <img src="/logo-fexpo-v2.png" alt="FEXPO UAP" class="footer-logo" width="1254" height="1254" loading="lazy" />
          <p>La feria de ciencia y tecnología de la Universidad Amazónica de Pando.</p>
        </div>
        <nav class="footer-nav" aria-label="Secciones de la feria">
          <span class="footer-titulo">Navegación</span>
          <a href="#inicio">Inicio</a>
          <a href="#artistas">Artistas</a>
          <a href="#stands-destacados">Stands</a>
          <a href="#stand">Obtén tu stand</a>
        </nav>
      </div>
      <div class="contenedor footer-legal">
        <p>© {{ anioFeria }} FEXPO UAP — Universidad Amazónica de Pando</p>
        <p class="footer-version">Equipo de Sistemas UAP</p>
      </div>
    </footer>

    <!-- Modal Plano -->
    <div v-if="mostrarPlano" class="modal-overlay" @click.self="cerrarPlano" role="dialog" aria-modal="true" aria-label="Plano de la feria">
      <div class="modal-plano">
        <button class="modal-cerrar" @click="cerrarPlano" aria-label="Cerrar plano">✕</button>
        <div class="plano-visor">
          <img src="/MAPA-WEB-PUBLICO.png" alt="Mapa de zonas de la FEXPO UAP" class="plano-imagen" />
        </div>
        <div class="modal-pie">
          <a href="/MAPA-WEB-PUBLICO.png" target="_blank" rel="noopener" class="btn btn-fantasma">Abrir en nueva pestaña</a>
          <button class="btn btn-primario" @click="cerrarPlano">Cerrar</button>
        </div>
      </div>
    </div>

    <!-- Loading / Error -->
    <div v-if="loading" class="estado-carga" aria-live="polite">
      <div class="spinner"></div>
      <p>Cargando información de la feria...</p>
    </div>
    <div v-else-if="error" class="estado-error" role="alert">
      <p>⚠️ {{ error }}</p>
      <button class="btn btn-primario" @click="cargarDatos">Reintentar</button>
    </div>
  </div>
</template>

<style>
/* Sin scope a propósito: alterna el scroll suave del documento solo mientras esta vista
   está montada (ver onMounted/onUnmounted). Un <style scoped> no puede alcanzar <html>. */
html.fx-scroll-suave {
  scroll-behavior: smooth;
}
</style>

<style scoped>
@import url('https://fonts.googleapis.com/css2?family=Anton&family=Work+Sans:wght@400;500;600;700&display=swap');

/* Paleta tomada del logotipo real (imagenes/logoFexpoUapV2.png): lima + verde selva para
   el día, y rojo/morado/azul —los mismos colores de las insignias de facultad del logo—
   como acentos. Vive solo en esta vista: el resto de la app es una herramienta interna con
   su propia identidad índigo y no necesita esta paleta. */
.feria-publica {
  --fx-lima: #a8d824;
  --fx-selva: #0c8a0c;
  --fx-selva-oscura: #063d06;
  --fx-rojo: #e31e24;
  --fx-rojo-suave: #fbe1e1;
  --fx-azul: #0048c0;
  --fx-azul-oscuro: #002b73;
  --fx-morado: #3c1884;
  --fx-morado-oscuro: #241050;
  --fx-papel: #f7f9f1;
  --fx-panel: #ffffff;
  --fx-borde: #e1e6d6;
  --fx-texto: #16210f;
  --fx-muted: #5c6b53;

  min-height: 100vh;
  background: var(--fx-papel);
  color: var(--fx-texto);
  font-family: 'Work Sans', system-ui, sans-serif;
  transition: background 0.3s ease, color 0.3s ease;
}

.feria-publica.tema-oscuro {
  --fx-papel: #0a1207;
  --fx-panel: #121d0e;
  --fx-borde: #22301a;
  --fx-texto: #edf2e6;
  --fx-muted: #9db08e;
  --fx-rojo-suave: #3a1414;
}

.feria-publica h1, .feria-publica h2, .feria-publica h3 {
  font-family: 'Anton', 'Work Sans', sans-serif;
  font-weight: 400;
  letter-spacing: 0.01em;
}

/* Secciones ancladas por el menú: que el encabezado fijo no tape el título al saltar. */
#inicio, #artistas, #stand, #stands-destacados {
  scroll-margin-top: 68px;
}

/* Navegación */
.nav-fexpo {
  position: sticky;
  top: 0;
  z-index: 40;
  background: rgba(4, 15, 4, 0.78);
  backdrop-filter: blur(10px);
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.nav-inner {
  max-width: 1180px;
  margin: 0 auto;
  padding: 0.55rem 1.5rem;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
}

.nav-marca {
  display: flex;
  align-items: center;
  gap: 0.55rem;
  text-decoration: none;
  color: #ffffff;
  font-family: 'Anton', sans-serif;
  font-size: 1.05rem;
  flex-shrink: 0;
}

.nav-logo { width: 34px; height: 34px; object-fit: contain; }
.nav-anio { color: var(--fx-lima); font-style: normal; font-size: 0.75rem; font-family: 'Work Sans', sans-serif; font-weight: 700; margin-left: 0.1rem; }

.nav-links { display: flex; align-items: center; gap: 1.4rem; }
.nav-links a {
  color: rgba(242, 247, 236, 0.88);
  text-decoration: none;
  font-weight: 600;
  font-size: 0.92rem;
  white-space: nowrap;
}
.nav-links a:hover { color: var(--fx-lima); }

.nav-cta {
  background: var(--fx-lima);
  color: #10280a !important;
  padding: 0.45rem 0.9rem;
  border-radius: 7px;
}
.nav-cta:hover { background: #93c11d; color: #10280a !important; }

.nav-acciones { display: flex; align-items: center; gap: 0.6rem; flex-shrink: 0; }

.nav-hamburguesa {
  display: none;
  flex-direction: column;
  gap: 4px;
  width: 38px;
  height: 38px;
  border-radius: 8px;
  border: 1px solid rgba(255, 255, 255, 0.25);
  background: rgba(255, 255, 255, 0.06);
  align-items: center;
  justify-content: center;
  cursor: pointer;
}
.nav-hamburguesa span { width: 18px; height: 2px; background: #f2f7ec; border-radius: 2px; }

.btn-tema {
  width: 38px;
  height: 38px;
  border-radius: 50%;
  border: 1px solid rgba(242, 247, 236, 0.4);
  background: rgba(255, 255, 255, 0.08);
  color: #f2f7ec;
  font-size: 1rem;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: background 0.2s ease;
}
.btn-tema:hover { background: rgba(255, 255, 255, 0.16); }

@media (max-width: 780px) {
  .nav-links {
    position: absolute;
    top: 100%;
    left: 0;
    right: 0;
    background: rgba(4, 15, 4, 0.97);
    backdrop-filter: blur(10px);
    flex-direction: column;
    align-items: stretch;
    gap: 0;
    padding: 0.25rem 1.25rem 1rem;
    display: none;
    border-bottom: 1px solid rgba(255, 255, 255, 0.1);
  }
  .nav-links.abierto { display: flex; }
  .nav-links a { padding: 0.7rem 0; border-bottom: 1px solid rgba(255, 255, 255, 0.08); }
  .nav-cta { margin-top: 0.7rem; text-align: center; }
  .nav-hamburguesa { display: flex; }
}

/* Hero: la fotografía de la fachada con el logo proyectado es la protagonista.
   Casi sin texto encima, a propósito: la imagen ya dice lo que hay que decir. */
.hero {
  position: relative;
  /* La foto es 16:9 (1672×941). Si la altura del hero se fija aparte del ancho (antes
     era min(78vh,620px)), "cover" tiene que ampliar la imagen para tapar la caja y en
     pantallas angostas y altas termina recortando casi todos los costados —se ve "con
     zoom". Igualando el aspecto al de la foto, cover casi no necesita recortar; el
     min/max-height es solo un piso para que quepan las fechas y los botones, y un techo
     para que en monitores muy anchos el hero no se vuelva gigante. */
  aspect-ratio: 16 / 9;
  width: 100%;
  min-height: 420px;
  max-height: 680px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 2.5rem 1.5rem 3.5rem;
  overflow: hidden;
  color: #f2f7ec;
  background-color: var(--fx-selva-oscura);
  background-image:
    linear-gradient(to top, rgba(3, 10, 3, 0.88) 0%, rgba(3, 10, 3, 0.2) 55%, rgba(3, 10, 3, 0.15) 100%),
    url('/hero-campus.png');
  background-size: cover;
  background-position: center;
  background-repeat: no-repeat;
}

.hero-contenido {
  position: relative;
  z-index: 1;
  width: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  gap: 1.4rem;
  animation: entradaHero 0.7s ease both;
}

/* Logo 3D: perspective en el envoltorio (lo mueve el mousemove del hero, ver script),
   flotación e inclinación continuas en la imagen (siempre activas, para que el efecto
   "3D" se note también en móvil, donde no hay puntero). prefers-reduced-motion las
   detiene junto con el resto de animaciones de la página (regla global al final). */
.hero-logo-3d {
  perspective: 900px;
  transition: transform 0.15s ease-out;
}

.hero-logo-img {
  display: block;
  width: min(46vw, 300px);
  height: auto;
  filter: drop-shadow(0 18px 32px rgba(0, 0, 0, 0.55));
  transform-style: preserve-3d;
  animation: flotarLogo3d 6s ease-in-out infinite;
}

@keyframes flotarLogo3d {
  0%, 100% { transform: rotateY(-8deg) rotateX(4deg) translateY(0); }
  50% { transform: rotateY(8deg) rotateX(-4deg) translateY(-10px); }
}

.hero-cuenta {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.5rem;
  color: #ffffff;
}

.cuenta-titulo {
  font-size: 0.9rem;
  font-weight: 600;
  color: #ffffff;
  text-shadow: 0 2px 10px rgba(0, 0, 0, 0.6);
}

.cuenta-titulo--activa { font-family: 'Anton', sans-serif; font-size: 1.3rem; }

.cuenta-grid { display: flex; align-items: flex-start; gap: 0.5rem; }

.cuenta-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  background: rgba(255, 255, 255, 0.12);
  border: 1px solid rgba(255, 255, 255, 0.25);
  border-radius: 10px;
  padding: 0.4rem 0.7rem;
  min-width: 48px;
}

.cuenta-item strong {
  font-family: 'Anton', sans-serif;
  font-size: clamp(1.2rem, 3.5vw, 1.7rem);
  color: #ffffff;
  line-height: 1;
  text-shadow: 0 2px 10px rgba(0, 0, 0, 0.5);
}

.cuenta-item span {
  font-size: 0.62rem;
  color: rgba(255, 255, 255, 0.85);
  margin-top: 0.15rem;
}

.cuenta-sep {
  font-family: 'Anton', sans-serif;
  font-size: 1.3rem;
  color: rgba(255, 255, 255, 0.55);
  margin-top: 0.3rem;
}

.hero-lugar {
  font-size: 0.85rem;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.8);
}

.hero-acciones { display: flex; flex-wrap: wrap; justify-content: center; gap: 1rem; }

.btn-grande { padding: 0.9rem 1.6rem; font-size: 1.05rem; }

.btn-hero-fantasma {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 1.5px solid rgba(242, 247, 236, 0.6);
  color: #f2f7ec;
  background: rgba(3, 10, 3, 0.25);
  border-radius: 7px;
  text-decoration: none;
  font-weight: 600;
  transition: background 0.15s ease;
}
.btn-hero-fantasma:hover { background: rgba(242, 247, 236, 0.16); }

@keyframes entradaHero {
  from { opacity: 0; transform: translateY(16px); }
  to { opacity: 1; transform: translateY(0); }
}

.hero-canopy {
  position: absolute;
  left: 0;
  right: 0;
  bottom: -1px;
  width: 100%;
  height: 46px;
  z-index: 1;
}

/* La sección que sigue al hero ahora es "Artistas" (fondo morado), no una clara: el
   remate combina con el morado de esa franja en vez de con el papel del tema. */
.hero-canopy path { fill: var(--fx-morado); }

/* Secciones */
.seccion { padding: 3.5rem 1.5rem; }

.contenedor { max-width: 1180px; margin: 0 auto; }

.titulo-seccion {
  font-size: clamp(1.35rem, 2.4vw, 1.8rem);
  margin: 0 0 0.5rem;
  color: var(--fx-texto);
}

.subtitulo-seccion {
  color: var(--fx-muted);
  margin: 0 0 2rem;
  font-size: 1rem;
  max-width: 62ch;
}

/* Stands (vitrina para el público visitante) */
.stand-destacado {
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(0, 1fr);
  align-items: stretch;
  gap: 0;
  margin-top: 2rem;
  background: var(--fx-panel);
  border: 1px solid var(--fx-borde);
  border-radius: 18px;
  overflow: hidden;
  transition: transform 0.2s ease, box-shadow 0.2s ease, opacity 0.6s ease;
}

.stand-destacado:hover { transform: translateY(-4px); box-shadow: var(--sombra-md); }

/* Entrada animada al hacer scroll (ver el IntersectionObserver del script). La clase que
   oculta la tarjeta la agrega el propio JS al arrancar, así que sin JS —o si algo falla—
   las tarjetas se quedan visibles de entrada en vez de invisibles para siempre. */
.fx-revela-js .stand-destacado {
  opacity: 0;
  transform: translateY(32px);
  transition: transform 0.6s ease, opacity 0.6s ease, box-shadow 0.2s ease;
}

.fx-revela-js .stand-destacado.stand-visible { opacity: 1; transform: translateY(0); }

/* La regla de arriba iguala la especificidad del :hover normal; sin esto, una vez revelada
   la tarjeta, pasar el mouse encima ya no la levantaba. */
.fx-revela-js .stand-destacado.stand-visible:hover { transform: translateY(-4px); }

/* Alterna imagen/texto en cada tarjeta siguiente, tipo revista: la de MYPES lleva la foto
   a la izquierda, esta la lleva a la derecha. Se reordena con `order` (grid), no cambiando
   el DOM, así el lector de pantalla sigue leyendo primero la foto y luego el texto. */
.stand-destacado--invertido { grid-template-columns: minmax(0, 1fr) minmax(0, 1.15fr); }
.stand-destacado--invertido .stand-destacado-img { order: 2; }
.stand-destacado--invertido .stand-destacado-texto { order: 1; }

.stand-destacado-img { width: 100%; height: 100%; min-height: 260px; object-fit: cover; display: block; }

.stand-destacado-texto { padding: 2rem; display: flex; flex-direction: column; justify-content: center; }

.stand-destacado-tag {
  display: inline-block;
  width: fit-content;
  background: rgba(12, 138, 12, 0.12);
  color: var(--fx-selva);
  padding: 0.3rem 0.75rem;
  border-radius: 999px;
  font-size: 0.78rem;
  font-weight: 700;
  margin-bottom: 0.85rem;
}

.tema-oscuro .stand-destacado-tag { background: rgba(168, 216, 36, 0.18); color: var(--fx-lima); }

.stand-destacado-texto h3 { margin: 0 0 0.75rem; font-size: 1.4rem; }
.stand-destacado-texto p { margin: 0; color: var(--fx-muted); line-height: 1.6; }


/* Artistas / Noches de FEXPO */
.noches { position: relative; overflow: hidden; color: #f2f7ec; }

.noches-fondo {
  position: absolute;
  inset: 0;
  z-index: 0;
  background: linear-gradient(150deg, var(--fx-morado) 0%, var(--fx-morado-oscuro) 55%, var(--fx-azul-oscuro) 100%);
}

.noches .contenedor { position: relative; z-index: 1; }
.titulo-noches { color: #ffffff; }
.subtitulo-noches { color: rgba(242, 247, 236, 0.78); }

.grid-noches {
  display: grid;
  gap: 1.25rem;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
}

.noche-card {
  display: flex;
  gap: 1rem;
  background: rgba(255, 255, 255, 0.07);
  border: 1px solid rgba(255, 255, 255, 0.15);
  border-radius: 14px;
  padding: 1.4rem;
  transition: transform 0.2s ease, box-shadow 0.2s ease, background 0.2s ease;
}

.noche-card:hover {
  transform: translateY(-4px);
  background: rgba(255, 255, 255, 0.1);
  box-shadow: 0 16px 32px rgba(0, 0, 0, 0.35);
}

.noche-fecha {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 58px;
  height: 58px;
  border-radius: 12px;
  color: #ffffff;
}

.noche-fecha strong { font-family: 'Anton', sans-serif; font-size: 1.4rem; line-height: 1; }
.noche-fecha span { font-size: 0.68rem; }

.noche-cuerpo h3 { margin: 0 0 0.25rem; font-size: 1.15rem; }
.noche-dia-semana { margin: 0 0 0.6rem; font-size: 0.8rem; color: rgba(242, 247, 236, 0.68); }
.noche-descripcion { margin: 0 0 0.75rem; font-size: 0.9rem; color: rgba(242, 247, 236, 0.9); }

/* Cartel aún por confirmar: un artista por noche, en silueta. El degradado que la rellena
   se desliza sin parar —un "brillo" que dice "esto se revela pronto" sin texto. */
.noche-artista {
  display: flex;
  align-items: center;
  gap: 0.9rem;
  margin-top: 1.1rem;
}

.artista-silueta {
  flex-shrink: 0;
  width: 72px;
  height: 72px;
  -webkit-mask-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 64 64'%3E%3Ccircle cx='32' cy='20' r='12'/%3E%3Cpath d='M32 36c-15 0-26 10-26 22v6h52v-6c0-12-11-22-26-22z'/%3E%3C/svg%3E");
  mask-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 64 64'%3E%3Ccircle cx='32' cy='20' r='12'/%3E%3Cpath d='M32 36c-15 0-26 10-26 22v6h52v-6c0-12-11-22-26-22z'/%3E%3C/svg%3E");
  -webkit-mask-size: contain;
  mask-size: contain;
  -webkit-mask-repeat: no-repeat;
  mask-repeat: no-repeat;
  -webkit-mask-position: center;
  mask-position: center;
  background: linear-gradient(
    115deg,
    color-mix(in srgb, var(--color-artista) 60%, black) 0%,
    var(--color-artista) 35%,
    #ffffff 50%,
    var(--color-artista) 65%,
    color-mix(in srgb, var(--color-artista) 60%, black) 100%
  );
  background-size: 260% 100%;
  animation: brillarSilueta 3.2s ease-in-out infinite;
}

@keyframes brillarSilueta {
  0% { background-position: 0% 0; }
  100% { background-position: -160% 0; }
}

.artista-etiqueta {
  font-size: 0.85rem;
  font-weight: 600;
  color: rgba(242, 247, 236, 0.9);
}


/* Quiero exponer: sección roja a propósito, para que se note que es una acción distinta
   (registrar interés) de todo lo informativo que viene antes. */
.exponer { position: relative; overflow: hidden; text-align: center; }

.exponer-fondo {
  position: absolute;
  inset: 0;
  z-index: 0;
  background: linear-gradient(160deg, var(--fx-rojo) 0%, #7a1216 60%, #430a0c 100%);
}

.exponer .contenedor { position: relative; z-index: 1; }

.titulo-exponer { color: #ffffff; }

.subtitulo-exponer {
  color: rgba(255, 255, 255, 0.85);
  max-width: 52ch;
  margin-left: auto;
  margin-right: auto;
}

.exponer-card {
  max-width: 560px;
  margin: 2rem auto 0;
  text-align: left;
  padding: 2.25rem;
  background: #fff8f7;
  border-radius: 18px;
  box-shadow: 0 24px 50px rgba(0, 0, 0, 0.35);
  display: flex;
  flex-direction: column;
  gap: 1.1rem;
}

.campo-exponer { display: flex; flex-direction: column; gap: 0.4rem; }

.campo-exponer label { font-size: 0.85rem; font-weight: 700; color: #3a1214; }

.campo-exponer input, .campo-exponer select {
  font: inherit;
  padding: 0.65rem 0.8rem;
  border-radius: 8px;
  border: 1.5px solid #e6cdcd;
  background: #ffffff;
  color: #241010;
}

.campo-exponer input:focus, .campo-exponer select:focus {
  outline: none;
  border-color: var(--fx-rojo);
  box-shadow: 0 0 0 3px rgba(227, 30, 36, 0.18);
}

.campo-exponer input.invalido, .campo-exponer select.invalido { border-color: var(--fx-rojo); }

.error-campo { font-size: 0.78rem; color: var(--fx-rojo); font-weight: 600; }

.exponer-enviar { margin-top: 0.4rem; }

/* Footer */
.footer-fexpo { background: var(--fx-panel); border-top: 1px solid var(--fx-borde); }

.footer-grid {
  display: grid;
  grid-template-columns: minmax(220px, 1.3fr) 1fr;
  gap: 2rem;
  padding: 3rem 1.5rem 2rem;
}

.footer-marca { display: flex; flex-direction: column; gap: 0.85rem; }
.footer-logo { width: 44px; height: 44px; object-fit: contain; }
.footer-marca p { margin: 0; font-size: 0.88rem; color: var(--fx-muted); max-width: 30ch; }

.footer-titulo {
  display: block;
  font-size: 0.78rem;
  font-weight: 700;
  color: var(--fx-muted);
  margin-bottom: 0.85rem;
}

.footer-nav { display: flex; flex-direction: column; gap: 0.6rem; }
.footer-nav a {
  color: var(--fx-texto);
  text-decoration: none;
  font-size: 0.92rem;
  font-weight: 500;
  width: fit-content;
}
.footer-nav a:hover { color: var(--fx-rojo); }

.footer-legal {
  border-top: 1px solid var(--fx-borde);
  padding: 1.25rem 1.5rem;
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: 0.4rem;
  font-size: 0.82rem;
  color: var(--fx-muted);
}
.footer-legal p { margin: 0; }

/* Modal Plano */
.modal-overlay {
  position: fixed; inset: 0;
  background: rgba(6, 15, 6, 0.78);
  backdrop-filter: blur(4px);
  display: flex; align-items: center; justify-content: center;
  z-index: 1000; padding: 1rem;
}

.modal-plano {
  background: var(--fx-panel);
  border: 1px solid var(--fx-borde);
  border-radius: 14px;
  width: 100%; max-width: 1100px;
  /* height, no max-height: un flex column solo reparte espacio "de sobra" con flex-grow
     cuando el propio contenedor tiene una altura definida — con max-height se ajusta al
     contenido y, ahora que la imagen del plano vive fuera del flujo (position: absolute),
     no queda contenido que la fuerce a crecer, así que el visor terminaba en 0px. */
  height: min(90vh, 760px);
  display: flex; flex-direction: column; overflow: hidden;
}

.modal-cerrar {
  position: absolute; top: 1rem; right: 1rem;
  width: 40px; height: 40px; border-radius: 50%;
  background: var(--fx-panel);
  border: 1px solid var(--fx-borde);
  font-size: 1.2rem; cursor: pointer;
  display: flex; align-items: center; justify-content: center;
  z-index: 1;
}

.modal-cerrar:hover { background: var(--fx-rojo-suave); color: var(--fx-rojo); }

/* El mapa es un plano blanco con su propia leyenda de colores por zona: se deja tal cual,
   sobre un papel blanco fijo (no el fx-papel del tema), como una lámina dentro del marco
   verde/lima de la modal — así nunca queda ilegible en modo oscuro. */
.plano-visor {
  flex: 1;
  min-height: 0;
  position: relative;
  background: var(--fx-selva-oscura);
}

/* Posicionada en vez de w/h:100% + object-fit dentro del flex: el % de alto de un hijo
   normal contra un item flex no siempre se resuelve (el navegador lo trataba como auto y
   la imagen se salía de su caja). Contra un contenedor posicionado, el % de una imagen
   absoluta sí se resuelve de forma fiable. */
.plano-imagen {
  position: absolute;
  inset: 1.25rem;
  width: calc(100% - 2.5rem);
  height: calc(100% - 2.5rem);
  object-fit: contain;
  background: #ffffff;
  border-radius: 8px;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.35);
}

.modal-pie { display: flex; justify-content: flex-end; gap: 0.75rem; padding: 1rem 1.5rem; border-top: 1px solid var(--fx-borde); }

/* Botones propios de esta vista (sobreescriben los genéricos de style.css solo aquí) */
.feria-publica .btn-primario {
  background: var(--fx-lima);
  border-color: var(--fx-lima);
  color: #10280a;
  font-weight: 700;
}

.feria-publica .btn-primario:hover { background: #93c11d; filter: none; }

.feria-publica .btn-fantasma { background: transparent; border-color: var(--fx-borde); color: var(--fx-texto); }

/* Estados */
.estado-carga, .estado-error {
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  min-height: 400px; padding: 2rem; text-align: center; gap: 1rem;
}

.spinner {
  width: 46px; height: 46px;
  border: 4px solid var(--fx-borde);
  border-top-color: var(--fx-selva);
  border-radius: 50%;
  animation: girar 1s linear infinite;
}

@keyframes girar { to { transform: rotate(360deg); } }

.estado-error p { color: var(--fx-rojo); margin: 0; }

/* Responsive */
@media (max-width: 720px) {
  .footer-grid { grid-template-columns: 1fr; gap: 1.75rem; }
  .footer-legal { flex-direction: column; text-align: center; }
}

@media (max-width: 640px) {
  .hero { padding: 1.25rem 1rem 1.75rem; }
  .hero-contenido { gap: 1rem; }
  .hero-logo-img { width: min(44vw, 190px); }
  .hero-acciones { flex-direction: column; width: 100%; }
  .btn-grande { width: 100%; }
  .stand-destacado { grid-template-columns: 1fr; }
  .stand-destacado--invertido .stand-destacado-img,
  .stand-destacado--invertido .stand-destacado-texto {
    order: initial;
  }
  .stand-destacado-img { min-height: 200px; }
  .stand-destacado-texto { padding: 1.5rem; }
  .noche-card { flex-direction: column; }
  .noche-fecha { flex-direction: row; gap: 0.35rem; width: auto; height: auto; padding: 0.4rem 0.9rem; align-self: flex-start; }
  .plano-visor { min-height: 400px; }
}

@media (prefers-reduced-motion: reduce) {
  *, *::before, *::after {
    animation-duration: 0.01ms !important;
    transition-duration: 0.01ms !important;
  }
}
</style>
