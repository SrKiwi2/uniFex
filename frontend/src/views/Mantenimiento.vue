<script setup>
import { ref, onMounted } from 'vue';
import { apiFetch } from '../api';
import { aviso, alerta } from '../ui/alerta';

const cargando = ref(true);
const guardando = ref(false);
const activo = ref(false);
const mensaje = ref('Sistema no disponible de 9:30 pm a 6 am por verificacion y conciliacion.');

async function cargar() {
  cargando.value = true;
  try {
    const res = await apiFetch('/api/app/mantenimiento');
    if (!res.ok) throw new Error('No se pudo cargar el estado de mantenimiento');
    const data = await res.json();
    activo.value = Boolean(data.activo);
    mensaje.value = data.mensaje || mensaje.value;
  } catch (e) {
    alerta(e.message, 'error', 0);
  } finally {
    cargando.value = false;
  }
}

async function guardar(nuevoEstado) {
  if (guardando.value) return;
  guardando.value = true;
  try {
    const res = await apiFetch('/api/app/mantenimiento', {
      method: 'PUT',
      body: JSON.stringify({ activo: nuevoEstado, mensaje: mensaje.value }),
    });
    const data = await res.json().catch(() => ({}));
    if (!res.ok) throw new Error(data.mensaje || 'No se pudo guardar mantenimiento');
    activo.value = Boolean(data.activo);
    mensaje.value = data.mensaje || mensaje.value;
    aviso(activo.value ? 'Modo mantenimiento activado. Los usuarios fueron expulsados.' : 'Modo mantenimiento desactivado.', 'ok');
  } catch (e) {
    alerta(e.message, 'error', 0);
  } finally {
    guardando.value = false;
  }
}

onMounted(cargar);
</script>

<template>
  <section class="mantenimiento">
    <div class="hero card" :class="{ activo }">
      <div class="halo" aria-hidden="true"></div>
      <div class="hero-texto">
        <p class="eyebrow">Control de acceso</p>
        <h2>{{ activo ? 'Modo mantenimiento activo' : 'Sistema disponible' }}</h2>
        <p>
          Activa una pausa operativa para conciliacion y verificacion. Mientras este encendida,
          solo el rol SUPER USUARIO conserva acceso al sistema.
        </p>
      </div>
      <div class="estado-pill" :class="{ activo }">
        <span class="punto"></span>
        {{ activo ? 'Acceso restringido' : 'Acceso normal' }}
      </div>
    </div>

    <div v-if="cargando" class="card cargando">
      <span class="skeleton"></span>
      <span>Cargando configuracion...</span>
    </div>

    <template v-else>
      <div class="grid-info">
        <article class="card dato">
          <span class="numero">1</span>
          <strong>SUPER USUARIO entra</strong>
          <p>Puede activar, revisar y desactivar el modo sin quedar bloqueado.</p>
        </article>
        <article class="card dato peligro">
          <span class="numero">423</span>
          <strong>Usuarios bloqueados</strong>
          <p>La API corta nuevas peticiones y el login no entrega sesion.</p>
        </article>
        <article class="card dato">
          <span class="numero">WS</span>
          <strong>Expulsion en vivo</strong>
          <p>Los clientes abiertos reciben aviso y vuelven al login.</p>
        </article>
      </div>

      <div class="paneles">
        <div class="card formulario">
          <div class="seccion-titulo">
            <div>
              <p class="eyebrow">Mensaje</p>
              <h3>Aviso para usuarios</h3>
            </div>
            <span class="badge" :class="activo ? 'badge-danger' : 'badge-ok'">
              {{ activo ? 'Publicado' : 'Preparado' }}
            </span>
          </div>

          <label class="campo">
            <span>Texto que vera quien no pueda entrar</span>
            <textarea v-model="mensaje" class="control" rows="5"></textarea>
          </label>

          <div class="acciones">
            <button class="btn btn-peligro principal" :disabled="guardando || activo" @click="guardar(true)">
              {{ guardando ? 'Guardando...' : 'Activar mantenimiento' }}
            </button>
            <button class="btn" :disabled="guardando || !activo" @click="guardar(false)">
              Desactivar mantenimiento
            </button>
          </div>
        </div>

        <aside class="card preview">
          <p class="eyebrow">Vista previa</p>
          <div class="aviso-preview">
            <strong>Sistema en mantenimiento</strong>
            <p>{{ mensaje }}</p>
          </div>
          <p class="nota">
            Este es el mensaje que acompana el cierre de sesion y el bloqueo de acceso.
          </p>
        </aside>
      </div>
    </template>
  </section>
</template>

<style scoped>
.mantenimiento { display: grid; gap: 1rem; max-width: 1080px; }
.hero {
  position: relative; overflow: hidden; display: flex; justify-content: space-between;
  align-items: flex-start; gap: 1.5rem; padding: 1.5rem;
  border: 1px solid color-mix(in srgb, var(--ok) 28%, var(--border));
  background:
    radial-gradient(circle at top right, color-mix(in srgb, var(--ok) 18%, transparent), transparent 34%),
    linear-gradient(135deg, var(--panel), color-mix(in srgb, var(--ok) 6%, var(--panel)));
}
.hero.activo {
  border-color: color-mix(in srgb, var(--danger) 40%, var(--border));
  background:
    radial-gradient(circle at top right, color-mix(in srgb, var(--danger) 22%, transparent), transparent 34%),
    linear-gradient(135deg, var(--panel), color-mix(in srgb, var(--danger) 7%, var(--panel)));
}
.halo {
  position: absolute; right: -54px; bottom: -70px; width: 220px; height: 220px;
  border-radius: 50%; border: 34px solid color-mix(in srgb, var(--ok) 12%, transparent);
}
.hero.activo .halo { border-color: color-mix(in srgb, var(--danger) 16%, transparent); }
.hero-texto { position: relative; max-width: 680px; }
.eyebrow { margin: 0 0 0.35rem; color: var(--muted); text-transform: uppercase; letter-spacing: 0.1em; font-size: 0.74rem; font-weight: 800; }
h2, h3 { margin: 0; }
h2 { font-size: clamp(1.55rem, 4vw, 2.4rem); line-height: 1.05; }
h3 { font-size: 1.05rem; }
.hero p:not(.eyebrow), .dato p, .nota { color: var(--muted); margin: 0.55rem 0 0; line-height: 1.5; }
.estado-pill {
  position: relative; display: inline-flex; align-items: center; gap: 0.45rem; padding: 0.45rem 0.75rem;
  border-radius: 999px; background: color-mix(in srgb, var(--ok) 14%, var(--panel));
  color: var(--ok); font-weight: 800; white-space: nowrap;
}
.estado-pill.activo { background: var(--danger-suave); color: var(--danger); }
.punto { width: 0.55rem; height: 0.55rem; border-radius: 50%; background: currentColor; box-shadow: 0 0 0 5px color-mix(in srgb, currentColor 14%, transparent); }
.grid-info { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 1rem; }
.dato { padding: 1rem; display: grid; gap: 0.35rem; }
.dato.peligro { border-color: color-mix(in srgb, var(--danger) 28%, var(--border)); }
.numero { width: max-content; padding: 0.25rem 0.5rem; border-radius: 0.7rem; background: var(--panel-2); color: var(--acento); font-weight: 900; font-size: 0.8rem; }
.dato.peligro .numero { color: var(--danger); background: var(--danger-suave); }
.paneles { display: grid; grid-template-columns: minmax(0, 1fr) 320px; gap: 1rem; align-items: start; }
.formulario, .preview, .cargando { padding: 1rem; }
.formulario { display: grid; gap: 1rem; }
.seccion-titulo { display: flex; justify-content: space-between; align-items: flex-start; gap: 1rem; }
.campo { display: grid; gap: 0.4rem; font-weight: 700; }
textarea.control { resize: vertical; min-height: 140px; line-height: 1.45; }
.acciones { display: flex; flex-wrap: wrap; gap: 0.7rem; padding-top: 0.25rem; }
.principal { min-width: 210px; }
.preview { position: sticky; top: 5rem; display: grid; gap: 1rem; }
.aviso-preview { padding: 1rem; border-radius: var(--radio-sm); border: 1px solid color-mix(in srgb, var(--danger) 26%, var(--border)); background: var(--danger-suave); }
.aviso-preview strong { color: var(--danger); }
.aviso-preview p { margin: 0.45rem 0 0; line-height: 1.5; white-space: pre-wrap; }
.cargando { display: flex; align-items: center; gap: 0.75rem; color: var(--muted); }
.skeleton { width: 2.1rem; height: 2.1rem; border-radius: 50%; background: linear-gradient(90deg, var(--panel-2), var(--border), var(--panel-2)); animation: pulso 1s ease-in-out infinite; }
@keyframes pulso { 50% { opacity: 0.45; } }
@media (max-width: 640px) {
  .hero { flex-direction: column; padding: 1.1rem; }
  .grid-info, .paneles { grid-template-columns: 1fr; }
  .preview { position: static; }
  .acciones .btn { width: 100%; }
  .principal { min-width: 0; }
}
</style>
