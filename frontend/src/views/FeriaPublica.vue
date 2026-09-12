<script setup>
import { ref, onMounted, computed } from 'vue';
import { apiFetch } from '../api.js';
import { url as urlApi } from '../config.js';

const loading = ref(true);
const error = ref(null);
const datos = ref(null);
const edicionSeleccionada = ref(null);
const mostrarPlano = ref(false);
const temaOscuro = ref(false);

const coloresFexpo = {
  primario: '#4f46e5',
  primarioOscuro: '#4338ca',
  secundario: '#06b6d4',
  acento: '#f59e0b',
  exito: '#16a34a',
  peligro: '#dc2626',
  fondo: '#f1f5f9',
  panel: '#ffffff',
  texto: '#0f172a',
  textoMuted: '#64748b'
};

const estadisticasGlobales = computed(() => {
  if (!datos.value?.estadisticas) return [];
  const s = datos.value.estadisticas;
  return [
    { label: 'Total casetas', valor: s.total, color: coloresFexpo.primario, icono: '🏪' },
    { label: 'Disponibles', valor: s.libres, color: coloresFexpo.exito, icono: '✅' },
    { label: 'En trámite', valor: s.enTramite, color: coloresFexpo.acento, icono: '⏳' },
    { label: 'Vendidas', valor: s.ocupados, color: coloresFexpo.peligro, icono: '🔴' },
    { label: 'Bloqueadas', valor: s.bloqueados, color: coloresFexpo.textoMuted, icono: '🔒' },
    { label: 'Ocupación', valor: s.porcentajeOcupacion + '%', color: s.porcentajeOcupacion > 70 ? coloresFexpo.peligro : coloresFexpo.primario, icono: '📊' }
  ];
});

const categoriasConStats = computed(() => {
  if (!datos.value?.estadisticasPorCategoria) return [];
  return datos.value.estadisticasPorCategoria.map(c => ({
    ...c,
    libres: c.libres,
    ocupados: c.ocupados,
    total: c.total,
    porcentaje: c.porcentajeOcupacion
  }));
});

async function cargarDatos() {
  loading.value = true;
  error.value = null;
  try {
    const res = await fetch(urlApi('/api/publico/feria'));
    if (!res.ok) throw new Error('Error al cargar la información de la feria');
    datos.value = await res.json();
    if (datos.value.edicion) edicionSeleccionada.value = datos.value.edicion.id;
  } catch (e) {
    error.value = e.message;
  } finally {
    loading.value = false;
  }
}

async function cargarEdicion(edicionId) {
  edicionSeleccionada.value = edicionId;
  loading.value = true;
  try {
    const res = await fetch(urlApi('/api/publico/feria'));
    if (!res.ok) throw new Error('Error al cambiar de edición');
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
  cargarDatos();
});

const urlPlano = computed(() => {
  if (!datos.value?.plano?.url) return '/files/mapa.pdf';
  return datos.value.plano.url;
});

const planoEsPropio = computed(() => datos.value?.plano?.propio ?? false);

const edicionActual = computed(() => {
  if (!datos.value?.edicion) return null;
  return datos.value.edicion;
});
</script>

<template>
  <div class="feria-publica" :class="{ 'tema-oscuro': temaOscuro }">
    <!-- Header / Hero -->
    <header class="hero" :style="{ background: temaOscuro ? 'linear-gradient(135deg, #1e1b4b 0%, #0f172a 100%)' : 'linear-gradient(135deg, #4f46e5 0%, #06b6d4 100%)' }">
      <div class="hero-contenido">
        <div class="badge-fexpo">FEXPO UAP</div>
        <h1 class="titulo-principal">Feria Exposición <span class="resaltado">Universidad Autónoma del Beni</span></h1>
        <p class="slogan" v-if="edicionActual">Edición {{ edicionActual.anio }} — {{ edicionActual.nombre }}</p>
        <p class="slogan" v-else>Próxima edición en preparación</p>
        <div class="hero-acciones">
          <button class="btn btn-primario btn-grande" @click="abrirPlano" :disabled="!urlPlano">
            <span>🗺️</span> Ver plano interactivo
          </button>
          <button class="btn btn-secundario btn-grande" @click="toggleTema">
            <span>{{ temaOscuro ? '☀️' : '🌙' }}</span> {{ temaOscuro ? 'Tema claro' : 'Tema oscuro' }}
          </button>
        </div>
      </div>
      <div class="hero-decoracion" aria-hidden="true">
        <div class="burbuja" style="--i: 0"></div>
        <div class="burbuja" style="--i: 1"></div>
        <div class="burbuja" style="--i: 2"></div>
        <div class="burbuja" style="--i: 3"></div>
      </div>
    </header>

    <!-- Selector de edición -->
    <section v-if="datos?.ediciones?.length > 1" class="seccion selector-edicion">
      <div class="contenedor">
        <label class="etiqueta-selector">Explorar ediciones anteriores</label>
        <div class="selector-wrapper">
          <select class="selector" v-model="edicionSeleccionada" @change="cargarEdicion(edicionSeleccionada)">
            <option v-for="e in datos.ediciones" :key="e.id" :value="e.id" :disabled="!e.activa && e.id !== edicionSeleccionada">
              {{ e.nombre }} ({{ e.anio }}) {{ e.activa ? '✨ Actual' : '' }}
            </option>
          </select>
        </div>
      </div>
    </section>

    <!-- Estadísticas globales -->
    <section v-if="estadisticasGlobales.length" class="seccion estadisticas">
      <div class="contenedor">
        <h2 class="titulo-seccion">Estado de la feria en tiempo real</h2>
        <div class="grid-stats" role="list">
          <article class="stat-card" v-for="stat in estadisticasGlobales" :key="stat.label" role="listitem">
            <div class="stat-icono" :style="{ background: stat.color + '20', color: stat.color }">{{ stat.icono }}</div>
            <div class="stat-info">
              <div class="stat-valor" :style="{ color: stat.color }">{{ stat.valor }}</div>
              <div class="stat-label">{{ stat.label }}</div>
            </div>
            <div class="stat-barra" v-if="stat.label === 'Ocupación'">
              <div class="barra-progreso" :style="{ width: datos.estadisticas.porcentajeOcupacion + '%', background: stat.color }"></div>
            </div>
          </article>
        </div>
      </div>
    </section>

    <!-- Categorías con disponibilidad -->
    <section v-if="categoriasConStats.length" class="seccion categorias">
      <div class="contenedor">
        <h2 class="titulo-seccion">Categorías y disponibilidad</h2>
        <p class="subtitulo-seccion">Cada categoría agrupa casetas con el mismo precio, tamaño y color en el plano</p>
        <div class="grid-categorias">
          <article class="categoria-card" v-for="cat in categoriasConStats" :key="cat.categoriaId">
            <div class="categoria-header" :style="{ borderLeftColor: cat.color }">
              <div class="categoria-forma" :style="{ background: cat.color }" :data-forma="cat.forma"></div>
              <div>
                <h3 class="categoria-nombre">{{ cat.categoriaNombre }}</h3>
                <span class="categoria-precio" v-if="cat.precioBase && cat.precioBase > 0">{{ cat.precioBase }} Bs</span>
                <span class="categoria-precio" v-else>Precio por definir</span>
              </div>
            </div>
            <div class="categoria-stats">
              <div class="mini-stat">
                <span class="mini-valor" :style="{ color: '#16a34a' }">{{ cat.libres }}</span>
                <span class="mini-label">Libres</span>
              </div>
              <div class="mini-stat">
                <span class="mini-valor" :style="{ color: '#dc2626' }">{{ cat.ocupados }}</span>
                <span class="mini-label">Vendidas</span>
              </div>
              <div class="mini-stat">
                <span class="mini-valor" :style="{ color: '#4f46e5' }">{{ cat.total }}</span>
                <span class="mini-label">Total</span>
              </div>
            </div>
            <div class="categoria-progreso">
              <div class="progreso-bar" :style="{ width: cat.porcentaje + '%', background: cat.porcentaje > 70 ? '#dc2626' : cat.color }"></div>
              <span class="progreso-texto">{{ cat.porcentaje }}% ocupado</span>
            </div>
          </article>
        </div>
      </div>
    </section>

    <!-- Información de la edición -->
    <section v-if="edicionActual" class="seccion info-edicion">
      <div class="contenedor">
        <h2 class="titulo-seccion">Información de la edición</h2>
        <div class="grid-info">
          <div class="info-card">
            <h3>📅 Detalles</h3>
            <dl class="info-lista">
              <dt>Nombre</dt><dd>{{ edicionActual.nombre }}</dd>
              <dt>Año</dt><dd>{{ edicionActual.anio }}</dd>
              <dt>Estado</dt><dd><span class="badge badge-activa">Activa</span></dd>
            </dl>
          </div>
          <div class="info-card" v-if="datos.plano">
            <h3>🗺️ Plano de la feria</h3>
            <dl class="info-lista">
              <dt>Origen</dt><dd>{{ planoEsPropio ? 'Personalizado (subido)' : 'Por defecto (empaquetado)' }}</dd>
              <dt>Dimensiones</dt><dd>{{ datos.plano.ancho }} × {{ datos.plano.alto }} px</dd>
              <dt>Versión</dt><dd>v{{ datos.plano.version }}</dd>
            </dl>
            <button class="btn btn-primario" @click="abrirPlano" style="margin-top: 1rem;">Ver plano completo</button>
          </div>
        </div>
      </div>
    </section>

    <!-- CTA Final -->
    <section class="seccion cta-final">
      <div class="contenedor">
        <div class="cta-card">
          <h2>¿Quieres exponer en la FEXPO UAP?</h2>
          <p>Contacta a la organización para reservar tu caseta y ser parte de la feria más importante de la región.</p>
          <div class="cta-contactos">
            <a href="mailto:fexpo@uap.edu.bo" class="contacto-item">
              <span class="contacto-icono">📧</span>
              <span>fexpo@uap.edu.bo</span>
            </a>
            <a href="https://wa.me/591xxxxxxxxx" target="_blank" rel="noopener" class="contacto-item">
              <span class="contacto-icono">💬</span>
              <span>WhatsApp</span>
            </a>
            <a href="https://uap.edu.bo" target="_blank" rel="noopener" class="contacto-item">
              <span class="contacto-icono">🌐</span>
              <span>uap.edu.bo</span>
            </a>
          </div>
        </div>
      </div>
    </section>

    <!-- Footer -->
    <footer class="footer">
      <div class="contenedor">
        <p>FEXPO UAP — Feria Exposición Universidad Autónoma del Beni</p>
        <p class="footer-version">Datos actualizados en tiempo real desde el sistema UniFex</p>
      </div>
    </footer>

    <!-- Modal Plano -->
    <div v-if="mostrarPlano" class="modal-overlay" @click.self="cerrarPlano" role="dialog" aria-modal="true" aria-label="Plano de la feria">
      <div class="modal-plano">
        <button class="modal-cerrar" @click="cerrarPlano" aria-label="Cerrar plano">✕</button>
        <div class="plano-visor">
          <iframe
            :src="urlPlano + '#toolbar=0&navpanes=0&scrollbar=0'"
            title="Plano de la FEXPO UAP"
            allowfullscreen
            loading="lazy">
          </iframe>
        </div>
        <div class="modal-pie">
          <a :href="urlPlano" target="_blank" rel="noopener" class="btn btn-fantasma">Abrir en nueva pestaña</a>
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

<style scoped>
/* Variables locales que respetan el tema global */
.feria-publica {
  min-height: 100vh;
  background: var(--bg);
  color: var(--text);
  transition: background 0.3s ease, color 0.3s ease;
}

.feria-publica.tema-oscuro {
  --bg: #0b1120;
  --panel: #111827;
  --border: #1f2a3c;
  --text: #e5e7eb;
  --muted: #94a3b8;
}

/* Hero */
.hero {
  position: relative;
  padding: 4rem 1.5rem 5rem;
  text-align: center;
  overflow: hidden;
}

.hero-contenido {
  position: relative;
  z-index: 1;
  max-width: 800px;
  margin: 0 auto;
}

.badge-fexpo {
  display: inline-block;
  background: rgba(255,255,255,0.2);
  backdrop-filter: blur(10px);
  border: 1px solid rgba(255,255,255,0.3);
  color: white;
  padding: 0.5rem 1.25rem;
  border-radius: 999px;
  font-size: 0.85rem;
  font-weight: 700;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  margin-bottom: 1.5rem;
  animation: fadeInUp 0.6s ease;
}

.titulo-principal {
  font-size: clamp(2rem, 5vw, 3.5rem);
  font-weight: 800;
  line-height: 1.1;
  margin: 0 0 1rem;
  color: white;
  animation: fadeInUp 0.6s ease 0.1s both;
}

.resaltado {
  background: linear-gradient(90deg, #fde047, #fbbf24, #fde047);
  background-size: 200% auto;
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
  animation: shine 3s linear infinite;
}

@keyframes shine {
  to { background-position: 200% center; }
}

.slogan {
  font-size: clamp(1.1rem, 2.5vw, 1.4rem);
  opacity: 0.9;
  margin: 0 0 2rem;
  font-weight: 400;
  animation: fadeInUp 0.6s ease 0.2s both;
}

.hero-acciones {
  display: flex;
  flex-wrap: wrap;
  gap: 1rem;
  justify-content: center;
  animation: fadeInUp 0.6s ease 0.3s both;
}

.btn-grande {
  padding: 1rem 2rem;
  font-size: 1.1rem;
  gap: 0.6rem;
}

.hero-decoracion {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.burbuja {
  position: absolute;
  border-radius: 50%;
  background: rgba(255,255,255,0.1);
  animation: flotar 8s ease-in-out infinite;
}

.burbuja[style*="--i: 0"] { width: 80px; height: 80px; top: 10%; left: 5%; animation-delay: 0s; }
.burbuja[style*="--i: 1"] { width: 120px; height: 120px; top: 60%; right: 3%; animation-delay: 2s; }
.burbuja[style*="--i: 2"] { width: 60px; height: 60px; bottom: 15%; left: 10%; animation-delay: 4s; }
.burbuja[style*="--i: 3"] { width: 100px; height: 100px; top: 20%; right: 20%; animation-delay: 6s; }

@keyframes flotar {
  0%, 100% { transform: translateY(0) translateX(0); }
  25% { transform: translateY(-20px) translateX(10px); }
  50% { transform: translateY(10px) translateX(-15px); }
  75% { transform: translateY(-15px) translateX(5px); }
}

@keyframes fadeInUp {
  from { opacity: 0; transform: translateY(20px); }
  to { opacity: 1; transform: translateY(0); }
}

/* Secciones */
.seccion {
  padding: 3rem 1.5rem;
}

.contenedor {
  max-width: 1200px;
  margin: 0 auto;
}

.titulo-seccion {
  font-size: clamp(1.5rem, 3vw, 2rem);
  font-weight: 700;
  margin: 0 0 0.5rem;
  color: var(--text);
}

.subtitulo-seccion {
  color: var(--muted);
  margin: 0 0 2rem;
  font-size: 1rem;
}

/* Selector de edición */
.selector-edicion {
  background: var(--panel);
  border-bottom: 1px solid var(--border);
}

.etiqueta-selector {
  display: block;
  font-size: 0.85rem;
  font-weight: 600;
  color: var(--muted);
  text-transform: uppercase;
  letter-spacing: 0.05em;
  margin-bottom: 0.5rem;
}

.selector-wrapper {
  position: relative;
}

.selector {
  width: 100%;
  max-width: 400px;
  padding: 0.85rem 3rem 0.85rem 1rem;
  font: inherit;
  font-size: 1rem;
  background: var(--panel);
  border: 2px solid var(--border);
  border-radius: var(--radio);
  color: var(--text);
  appearance: none;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='20' height='20' viewBox='0 0 24 24' fill='none' stroke='%2364748b' stroke-width='2'%3E%3Cpath d='M6 9l6 6 6-6'/%3E%3C/svg%3E");
  background-repeat: no-repeat;
  background-position: right 1rem center;
  transition: border-color 0.2s, box-shadow 0.2s;
}

.selector:focus {
  outline: none;
  border-color: var(--acento);
  box-shadow: 0 0 0 3px var(--acento-suave);
}

/* Estadísticas */
.grid-stats {
  display: grid;
  gap: 1rem;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
}

.stat-card {
  background: var(--panel);
  border: 1px solid var(--border);
  border-radius: var(--radio);
  padding: 1.5rem;
  display: flex;
  align-items: center;
  gap: 1rem;
  position: relative;
  overflow: hidden;
  transition: transform 0.2s, box-shadow 0.2s;
}

.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--sombra-md);
}

.stat-icono {
  width: 56px;
  height: 56px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 1.5rem;
  flex-shrink: 0;
}

.stat-info {
  flex: 1;
  text-align: left;
}

.stat-valor {
  font-size: 1.75rem;
  font-weight: 800;
  line-height: 1;
}

.stat-label {
  font-size: 0.8rem;
  color: var(--muted);
  text-transform: uppercase;
  letter-spacing: 0.05em;
  margin-top: 0.25rem;
}

.stat-barra {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  height: 4px;
  background: var(--border);
}

.barra-progreso {
  height: 100%;
  border-radius: 0 0 var(--radio) var(--radio);
  transition: width 0.5s ease;
}

/* Categorías */
.grid-categorias {
  display: grid;
  gap: 1.25rem;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
}

.categoria-card {
  background: var(--panel);
  border: 1px solid var(--border);
  border-radius: var(--radio);
  overflow: hidden;
  transition: transform 0.2s, box-shadow 0.2s;
}

.categoria-card:hover {
  transform: translateY(-4px);
  box-shadow: var(--sombra-md);
}

.categoria-header {
  display: flex;
  align-items: center;
  gap: 1rem;
  padding: 1.25rem;
  border-left: 5px solid;
}

.categoria-forma {
  width: 48px;
  height: 48px;
  border-radius: 8px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.categoria-forma[data-forma="circulo"] { border-radius: 50%; }
.categoria-forma[data-forma="triangulo"] {
  width: 0; height: 0;
  border-left: 24px solid transparent;
  border-right: 24px solid transparent;
  border-bottom: 42px solid;
  background: transparent;
  border-radius: 0;
}

.categoria-nombre {
  margin: 0 0 0.25rem;
  font-size: 1.1rem;
  font-weight: 700;
}

.categoria-precio {
  font-size: 0.85rem;
  color: var(--muted);
}

.categoria-stats {
  display: flex;
  justify-content: space-around;
  padding: 0 1.25rem 1rem;
  border-top: 1px solid var(--border);
  border-bottom: 1px solid var(--border);
  margin: 0 1.25rem;
}

.mini-stat {
  text-align: center;
}

.mini-valor {
  display: block;
  font-size: 1.5rem;
  font-weight: 800;
  line-height: 1;
}

.mini-label {
  font-size: 0.7rem;
  color: var(--muted);
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.categoria-progreso {
  padding: 0 1.25rem 1.25rem;
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.progreso-bar {
  flex: 1;
  height: 6px;
  border-radius: 3px;
  background: var(--border);
  overflow: hidden;
}

.progreso-bar::after {
  content: '';
  display: block;
  height: 100%;
  border-radius: inherit;
  transition: width 0.5s ease;
}

.progreso-texto {
  font-size: 0.8rem;
  color: var(--muted);
  white-space: nowrap;
  flex-shrink: 0;
}

/* Info edición */
.grid-info {
  display: grid;
  gap: 1.5rem;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
}

.info-card {
  background: var(--panel);
  border: 1px solid var(--border);
  border-radius: var(--radio);
  padding: 1.5rem;
}

.info-card h3 {
  margin: 0 0 1rem;
  font-size: 1.1rem;
  font-weight: 700;
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.info-lista {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 0.5rem 1rem;
  margin: 0;
  font-size: 0.9rem;
}

.info-lista dt {
  color: var(--muted);
  font-weight: 500;
}

.info-lista dd {
  margin: 0;
  font-weight: 600;
}

.badge {
  display: inline-block;
  padding: 0.15rem 0.5rem;
  border-radius: 999px;
  font-size: 0.7rem;
  font-weight: 700;
}

.badge-activa {
  background: color-mix(in srgb, var(--ok) 15%, transparent);
  color: var(--ok);
}

/* CTA Final */
.cta-final {
  background: var(--panel);
  border-top: 1px solid var(--border);
}

.cta-card {
  max-width: 600px;
  margin: 0 auto;
  text-align: center;
  padding: 2.5rem;
  background: linear-gradient(135deg, var(--acento-suave), transparent);
  border: 1px solid var(--acento-suave);
  border-radius: var(--radio);
}

.cta-card h2 {
  margin: 0 0 0.75rem;
  font-size: clamp(1.3rem, 3vw, 1.75rem);
}

.cta-card p {
  color: var(--muted);
  margin: 0 0 2rem;
}

.cta-contactos {
  display: flex;
  flex-wrap: wrap;
  gap: 1rem;
  justify-content: center;
}

.contacto-item {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.75rem 1.25rem;
  background: var(--panel);
  border: 1px solid var(--border);
  border-radius: var(--radio-sm);
  color: var(--text);
  text-decoration: none;
  font-weight: 600;
  transition: border-color 0.2s, transform 0.2s;
}

.contacto-item:hover {
  border-color: var(--acento);
  transform: translateY(-2px);
}

.contacto-icono { font-size: 1.2rem; }

/* Footer */
.footer {
  padding: 2rem 1.5rem;
  text-align: center;
  color: var(--muted);
  font-size: 0.85rem;
  border-top: 1px solid var(--border);
  background: var(--panel);
}

.footer-version {
  margin: 0.5rem 0 0;
  font-size: 0.75rem;
  opacity: 0.7;
}

/* Modal Plano */
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(2, 6, 23, 0.8);
  backdrop-filter: blur(4px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  padding: 1rem;
  animation: fadeIn 0.2s ease;
}

@keyframes fadeIn { from { opacity: 0; } to { opacity: 1; } }

.modal-plano {
  background: var(--panel);
  border: 1px solid var(--border);
  border-radius: var(--radio);
  width: 100%;
  max-width: 1000px;
  max-height: 90vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  animation: slideUp 0.3s ease;
}

@keyframes slideUp {
  from { opacity: 0; transform: translateY(20px); }
  to { opacity: 1; transform: translateY(0); }
 }

.modal-cerrar {
  position: absolute;
  top: 1rem;
  right: 1rem;
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: var(--panel-2);
  border: 1px solid var(--border);
  font-size: 1.2rem;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1;
  transition: background 0.2s;
}

.modal-cerrar:hover { background: var(--danger-suave); color: var(--danger); }

.plano-visor {
  flex: 1;
  min-height: 500px;
  position: relative;
}

.plano-visor iframe {
  width: 100%;
  height: 100%;
  min-height: 500px;
  border: none;
  background: var(--bg);
}

.modal-pie {
  display: flex;
  justify-content: flex-end;
  gap: 0.75rem;
  padding: 1rem 1.5rem;
  border-top: 1px solid var(--border);
}

/* Estados */
.estado-carga, .estado-error {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 400px;
  padding: 2rem;
  text-align: center;
  gap: 1rem;
}

.spinner {
  width: 48px;
  height: 48px;
  border: 4px solid var(--border);
  border-top-color: var(--acento);
  border-radius: 50%;
  animation: girar 1s linear infinite;
}

@keyframes girar { to { transform: rotate(360deg); } }

.estado-error p { color: var(--danger); margin: 0; }

/* Responsive */
@media (max-width: 640px) {
  .hero { padding: 3rem 1rem 4rem; }
  .btn-grande { width: 100%; justify-content: center; }
  .hero-acciones { flex-direction: column; }
  .stat-card { flex-direction: column; text-align: center; padding: 1.25rem; }
  .stat-icono { width: 48px; height: 48px; }
  .stat-barra { display: none; }
  .categoria-stats { flex-wrap: wrap; gap: 0.75rem; }
  .mini-stat { flex: 1 1 30%; }
  .cta-contactos { flex-direction: column; }
  .contacto-item { width: 100%; justify-content: center; }
  .plano-visor { min-height: 400px; }
}

@media (prefers-reduced-motion: reduce) {
  *, *::before, *::after {
    animation-duration: 0.01ms !important;
    transition-duration: 0.01ms !important;
  }
}
</style>