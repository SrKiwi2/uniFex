<script setup>
import { ref, computed, onBeforeUnmount } from 'vue';
import { url as urlApi } from '../config.js';

/*
 * Verificar una credencial en la puerta.
 *
 * Dos formas de leer el codigo, y las dos hacen falta:
 *
 *   - la CAMARA, que es como se trabaja de verdad con una fila delante;
 *   - el CODIGO TECLEADO, que no es un resto de la version anterior sino el respaldo. Va
 *     impreso debajo del QR precisamente para cuando la camara no lee: papel arrugado, sol de
 *     frente, telefono sin permiso, o un equipo prestado sin camara.
 *
 * Para decodificar se prefiere BarcodeDetector, que lo resuelve el propio sistema: no gasta un
 * fotograma entero de JavaScript por lectura y es notablemente mas rapido en telefonos
 * modestos, que es lo que habra en la puerta. Donde no existe se cae a jsQR, que se carga con
 * import() DINAMICO: asi los equipos que nunca lo necesitan no arrastran su peso en el paquete
 * inicial, y el APK abre igual de rapido.
 *
 * Se consulta el endpoint publico, el mismo que abre el QR, asi que funciona con o sin sesion.
 */

const codigo = ref('');
const buscando = ref(false);
const resultado = ref(null);
const error = ref('');

// ------------------------------------------------------------------ camara
const video = ref(null);
const camara = ref(false);
const avisoCamara = ref('');
const linterna = ref(false);
let flujo = null;          // MediaStream
let lector = null;         // BarcodeDetector, si lo hay
let decodificaJs = null;   // jsQR, si hizo falta
let lienzo = null;
let animacion = 0;
let ultimoLeido = '';

const puedeLinterna = computed(() => {
  const p = flujo?.getVideoTracks?.()[0]?.getCapabilities?.();
  return !!p && 'torch' in p;
});

/**
 * El QR lleva la URL publica completa, no el codigo suelto: asi cualquier telefono que lo
 * enfoque con su camara normal abre la pagina. Aqui se extrae el codigo de esa URL, pero se
 * acepta tambien el codigo pelado por si alguien pega solo esa parte.
 */
function codigoDe(texto) {
  const t = (texto || '').trim();
  const m = t.match(/FXC-[0-9A-Z]+-[0-9A-Za-z_-]{12}/i);
  return m ? m[0].toUpperCase() : t.toUpperCase();
}

async function encender() {
  avisoCamara.value = '';
  limpiar();
  try {
    flujo = await navigator.mediaDevices.getUserMedia({
      // La trasera. `ideal` y no `exact`: en una tablet con una sola camara, `exact` falla
      // en vez de usar la que hay.
      video: { facingMode: { ideal: 'environment' }, width: { ideal: 1280 }, height: { ideal: 720 } },
      audio: false,
    });
  } catch (e) {
    avisoCamara.value = e?.name === 'NotAllowedError'
      ? 'No se dio permiso para usar la cámara. Puedes teclear el código.'
      : 'No se pudo abrir la cámara. Puedes teclear el código.';
    return;
  }

  camara.value = true;
  await new Promise((r) => setTimeout(r));   // que el <video> exista ya en el DOM
  const v = video.value;
  if (!v) { apagar(); return; }
  v.srcObject = flujo;
  v.setAttribute('playsinline', '');         // iOS abriria el reproductor a pantalla completa
  try { await v.play(); } catch { /* algunos navegadores lo rechazan y siguen funcionando */ }

  if ('BarcodeDetector' in window) {
    try {
      const formatos = await window.BarcodeDetector.getSupportedFormats();
      if (formatos.includes('qr_code')) lector = new window.BarcodeDetector({ formats: ['qr_code'] });
    } catch { lector = null; }
  }
  if (!lector && !decodificaJs) {
    decodificaJs = (await import('jsqr')).default;
  }
  animacion = requestAnimationFrame(mirar);
}

function apagar() {
  cancelAnimationFrame(animacion);
  animacion = 0;
  if (video.value) video.value.srcObject = null;
  flujo?.getTracks().forEach((t) => t.stop());
  flujo = null;
  lector = null;
  linterna.value = false;
  camara.value = false;
}

async function alternarLinterna() {
  const pista = flujo?.getVideoTracks?.()[0];
  if (!pista) return;
  try {
    await pista.applyConstraints({ advanced: [{ torch: !linterna.value }] });
    linterna.value = !linterna.value;
  } catch { /* no todos los telefonos la dejan encender desde la web */ }
}

let ultimoIntento = 0;

/**
 * Un fotograma por vuelta, pero se decodifica como mucho cada 120 ms: a 30 fps y con jsQR el
 * telefono se calienta y la vista previa se entrecorta sin leer antes por ello.
 */
async function mirar(ahora) {
  if (!camara.value) return;
  const v = video.value;
  if (v && v.readyState >= 2 && ahora - ultimoIntento > 120) {
    ultimoIntento = ahora;
    try {
      let texto = null;
      if (lector) {
        const hallado = await lector.detect(v);
        texto = hallado[0]?.rawValue || null;
      } else if (decodificaJs) {
        // Se reduce a 480 px de ancho antes de decodificar. Un fotograma de 1280x720 entero por
        // jsQR son decenas de milisegundos en un telefono de gama baja, y a esa distancia el QR
        // sigue ocupando pixeles de sobra para leerse.
        lienzo ||= document.createElement('canvas');
        const escala = Math.min(1, 480 / (v.videoWidth || 480));
        lienzo.width = Math.round(v.videoWidth * escala);
        lienzo.height = Math.round(v.videoHeight * escala);
        const ctx = lienzo.getContext('2d', { willReadFrequently: true });
        ctx.drawImage(v, 0, 0, lienzo.width, lienzo.height);
        const datos = ctx.getImageData(0, 0, lienzo.width, lienzo.height);
        texto = decodificaJs(datos.data, datos.width, datos.height,
                             { inversionAttempts: 'dontInvert' })?.data || null;
      }
      if (texto) {
        const c = codigoDe(texto);
        // El mismo codigo delante de la camara se lee muchas veces por segundo; sin esto se
        // dispararia una consulta por fotograma mientras el papel siga ahi.
        if (c !== ultimoLeido) {
          ultimoLeido = c;
          leido(c);
          return;
        }
      }
    } catch { /* un fotograma ilegible no es motivo para parar */ }
  }
  animacion = requestAnimationFrame(mirar);
}

function leido(c) {
  // Aviso corto: en la puerta se mira al expositor, no a la pantalla.
  try { navigator.vibrate?.(120); } catch { /* el escritorio no vibra */ }
  apagar();
  codigo.value = c;
  verificar();
}

// Salir de la pantalla sin esto deja la camara encendida: se nota en la bateria y el LED
// del telefono se queda prendido, que es lo que hace pensar que la aplicacion espia.
onBeforeUnmount(apagar);

// ------------------------------------------------------------------ consulta
async function verificar() {
  const c = codigoDe(codigo.value);
  if (!c || buscando.value) return;
  buscando.value = true;
  resultado.value = null;
  error.value = '';
  try {
    const r = await fetch(urlApi(`/api/publico/credencial/${encodeURIComponent(c)}`));
    const d = await r.json().catch(() => ({}));
    if (r.ok && d.valida) resultado.value = d;
    else error.value = d.mensaje || 'Esta credencial no es válida';
  } catch {
    error.value = 'No se pudo verificar. Revisa la conexión.';
  } finally {
    buscando.value = false;
  }
}

function limpiar() {
  codigo.value = '';
  resultado.value = null;
  error.value = '';
  ultimoLeido = '';
}

/** Volver a escanear: lo normal es una fila de gente, no una credencial suelta. */
function siguiente() {
  limpiar();
  encender();
}
</script>

<template>
  <div class="escaner">
    <section class="entrada card">
      <h2>Verificar credencial</h2>

      <!-- La camara ocupa el sitio principal porque es como se trabaja con una fila delante.
           Se enciende con un toque y no sola: pedir el permiso al entrar a la pantalla es lo
           que hace que la gente lo niegue "por si acaso" y luego no haya forma de volver a
           pedirlo. -->
      <div v-if="camara" class="visor">
        <video ref="video" muted playsinline></video>
        <div class="mira" aria-hidden="true"></div>
        <div class="controles">
          <button v-if="puedeLinterna" class="btn btn-fantasma btn-sm"
                  :class="{ activo: linterna }" @click="alternarLinterna">
            {{ linterna ? '🔦 Apagar luz' : '🔦 Luz' }}
          </button>
          <button class="btn btn-fantasma btn-sm" @click="apagar">Apagar cámara</button>
        </div>
      </div>
      <button v-else class="btn btn-primario btn-camara" @click="encender">
        📷 Escanear con la cámara
      </button>
      <p v-if="avisoCamara" class="aviso">{{ avisoCamara }}</p>

      <p class="ayuda">
        Si el QR no se deja leer, escribe el código impreso debajo.
      </p>
      <form class="fila" @submit.prevent="verificar">
        <input v-model="codigo" class="control codigo" placeholder="FXC-1A-XXXXXXXXXXXX"
               autocapitalize="characters" autocomplete="off" spellcheck="false" />
        <button class="btn btn-primario" :disabled="buscando || !codigo.trim()">
          {{ buscando ? 'Verificando…' : 'Verificar' }}
        </button>
      </form>
    </section>

    <section v-if="error" class="resultado invalido card">
      <span class="marca">✕</span>
      <div>
        <h3>Credencial no válida</h3>
        <p>{{ error }}</p>
        <p class="pie">No permitir el ingreso con este código.</p>
        <button class="btn btn-sm" @click="siguiente">📷 Escanear otra</button>
      </div>
    </section>

    <section v-else-if="resultado" class="resultado valido card">
      <header>
        <span class="marca">✓</span>
        <h3>Credencial válida</h3>
      </header>
      <div class="persona">
        <img v-if="resultado.fotoUrl" :src="urlApi(resultado.fotoUrl)" :alt="resultado.nombre" class="foto" />
        <div v-else class="foto sinfoto">Sin foto</div>
        <div>
          <strong>{{ resultado.nombre }}</strong>
          <p class="ci">C.I. {{ resultado.ci || '—' }}</p>
          <p class="muted">{{ resultado.entidad }}</p>
        </div>
      </div>
      <dl class="datos">
        <div><dt>Categoría</dt><dd>{{ resultado.categoria || '—' }}</dd></div>
        <div><dt>Caseta</dt><dd class="casetas">{{ resultado.casetas || '—' }}</dd></div>
      </dl>
      <div class="fila">
        <button class="btn btn-primario" @click="siguiente">📷 Escanear la siguiente</button>
        <button class="btn btn-fantasma" @click="limpiar">Limpiar</button>
      </div>
    </section>
  </div>
</template>

<style scoped>
.escaner { display: flex; flex-direction: column; gap: 1rem; max-width: 560px; margin: 0 auto; }
.entrada { padding: 1.1rem; display: flex; flex-direction: column; gap: 0.6rem; }
.entrada h2 { margin: 0; font-size: 1.1rem; }
.ayuda { margin: 0; font-size: 0.86rem; color: var(--muted); line-height: 1.5; }
.fila { display: flex; gap: 0.6rem; }
/* El codigo se teclea con prisa y en un movil: monoespaciado, grande y sin autocorrector. */
.codigo { flex: 1; font-family: ui-monospace, monospace; letter-spacing: 0.04em; text-transform: uppercase; }
.fila .btn { min-height: 48px; }
.btn-camara { min-height: 52px; font-size: 1rem; }
.aviso { margin: 0; font-size: 0.86rem; color: var(--tramite); line-height: 1.45; }

/* El visor manda en la pantalla: apuntar es la tarea, todo lo demas es respaldo. */
.visor { position: relative; border-radius: var(--radio-sm); overflow: hidden; background: #000; }
.visor video { display: block; width: 100%; max-height: 60vh; object-fit: cover; }
/* Un recuadro a donde apuntar. Sin el, la gente acerca el telefono hasta pegarlo al papel y
   el QR se sale del encuadre o queda desenfocado. */
.mira {
  position: absolute; inset: 50% auto auto 50%; transform: translate(-50%, -50%);
  width: min(62%, 240px); aspect-ratio: 1; border: 3px solid rgba(255, 255, 255, 0.9);
  border-radius: 14px; box-shadow: 0 0 0 100vmax rgba(0, 0, 0, 0.35);
}
.controles {
  position: absolute; left: 0; right: 0; bottom: 0; display: flex; gap: 0.5rem;
  justify-content: center; padding: 0.6rem;
}
.controles .btn { background: rgba(15, 23, 42, 0.72); color: #fff; border-color: transparent; }
.controles .btn.activo { background: var(--acento); }

.resultado { padding: 1.1rem; display: flex; flex-direction: column; gap: 0.9rem; }
.resultado.valido { border-top: 6px solid var(--ok); }
.resultado.invalido { border-top: 6px solid var(--danger); flex-direction: row; align-items: flex-start; gap: 0.9rem; }
.marca {
  display: grid; place-items: center; width: 42px; height: 42px; flex: none;
  border-radius: 50%; font-size: 1.4rem; font-weight: 800; color: #fff;
}
.valido .marca { background: var(--ok); }
.invalido .marca { background: var(--danger); }
.resultado header { display: flex; align-items: center; gap: 0.7rem; }
.resultado h3 { margin: 0; font-size: 1.05rem; }
.invalido h3 { color: var(--danger); }
.invalido p { margin: 0.25rem 0 0; font-size: 0.9rem; }
.invalido .pie { color: var(--muted); }

.persona { display: flex; gap: 0.9rem; align-items: center; }
.foto { width: 84px; height: 84px; flex: none; object-fit: cover; border-radius: var(--radio-sm); border: 1px solid var(--border); }
.foto.sinfoto { display: grid; place-items: center; background: var(--panel-2); color: var(--muted); font-size: 0.75rem; }
.persona strong { font-size: 1.1rem; }
.ci { margin: 0.2rem 0; font-variant-numeric: tabular-nums; }
.persona p { margin: 0; font-size: 0.88rem; }

.datos { display: grid; grid-template-columns: 1fr 1fr; gap: 0.7rem; margin: 0; }
.datos dt { font-size: 0.72rem; text-transform: uppercase; letter-spacing: 0.04em; color: var(--muted); font-weight: 700; }
.datos dd { margin: 0.15rem 0 0; font-size: 0.95rem; }
.datos .casetas { font-size: 1.4rem; font-weight: 800; color: var(--acento); font-variant-numeric: tabular-nums; }
</style>
