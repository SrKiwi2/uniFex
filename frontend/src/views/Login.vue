<script setup>
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { useAuthStore } from '../stores/auth';

const usuario = ref('');
const contrasena = ref('');
const error = ref('');
const cargando = ref(false);
const auth = useAuthStore();
const router = useRouter();
const formVisible = ref(false);
/*
 * La referencia al campo de contraseña NO puede llamarse `contrasena`.
 *
 * En <script setup>, un `ref="x"` de la plantilla ESCRIBE en la variable `x` del script si
 * existe. Como `contrasena` ya era el ref con el texto tecleado, al montarse la vista Vue le
 * metia dentro el propio <input>, y a partir de ahi `contrasena.trim()` —que usa el boton
 * para saber si habilitarse— reventaba el render: la pantalla de login se rompia y no habia
 * forma de entrar. De ahi el nombre distinto.
 */
const campoContrasena = ref(null);

// Producción sirve la SPA bajo /app/ (ver VITE_BASE en el perfil Maven "frontend"): una
// ruta fija "/logo-fexpo.png" apunta a la raíz del dominio y da 404 solo ahí.
const BASE = import.meta.env.BASE_URL;

async function entrar() {
  // El formulario ya dispara `submit` al pulsar Enter; sin esta guarda, una pulsacion que
  // ademas llegara por otro camino mandaria dos peticiones de login.
  if (cargando.value) return;
  error.value = '';
  cargando.value = true;
  try {
    await auth.login(usuario.value, contrasena.value);
    router.push('/');
  } catch (e) {
    error.value = e.message;
  } finally {
    cargando.value = false;
  }
}

onMounted(() => {
  requestAnimationFrame(() => {
    formVisible.value = true;
  });
  document.getElementById('usuario')?.focus();
});
</script>

<template>
  <div class="login-wrap">
    <div class="login-bg" aria-hidden="true">
      <div class="orb orb-1"></div>
      <div class="orb orb-2"></div>
      <div class="orb orb-3"></div>
    </div>

    <main class="login-card" :class="{ visible: formVisible }">
      <div class="logo-contenedor" aria-hidden="true">
        <img :src="`${BASE}logo-fexpo.png`" alt="FEXPO UAP" class="logo" width="640" height="433" />
      </div>

      <header class="cabecera">
        <h1>Bienvenido</h1>
        <p>Ingresa tus credenciales para continuar</p>
      </header>

      <form class="formulario" @submit.prevent="entrar">
        <div class="campo-grupo">
          <label for="usuario" class="etiqueta">Usuario</label>
          <div class="input-contenedor">
            <span class="icono" aria-hidden="true">👤</span>
            <input
              id="usuario"
              v-model="usuario"
              type="text"
              autocomplete="username"
              placeholder="Tu usuario"
              class="control"
              :disabled="cargando"
              @keydown.enter.prevent="contrasena.trim() ? entrar() : campoContrasena?.focus()"
            />
          </div>
        </div>

        <div class="campo-grupo">
          <label for="contrasena" class="etiqueta">Contraseña</label>
          <div class="input-contenedor">
            <span class="icono" aria-hidden="true">🔒</span>
            <input
              ref="campoContrasena"
              id="contrasena"
              v-model="contrasena"
              type="password"
              autocomplete="current-password"
              placeholder="Tu contraseña"
              class="control"
              :disabled="cargando"
            />
          </div>
        </div>

        <p v-if="error" class="error" role="alert">{{ error }}</p>

        <button type="submit" class="btn-entrar" :disabled="cargando || !usuario.trim() || !contrasena.trim()">
          <span v-if="cargando" class="spinner" aria-hidden="true"></span>
          <span v-else>Ingresar</span>
        </button>
      </form>

      <footer class="pie-login">
        <p>Sistema de gestión FEXPO UAP</p>
      </footer>
    </main>
  </div>
</template>

<style scoped>
.login-wrap {
  min-height: 100dvh;
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 1.5rem;
  position: relative;
  overflow: hidden;
}

/* Fondo animado con orbes suaves */
.login-bg {
  position: fixed;
  inset: 0;
  z-index: 0;
  background: var(--bg);
  overflow: hidden;
}

.orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(60px);
  opacity: 0.4;
  animation: flotar 20s ease-in-out infinite;
}

.orb-1 {
  width: 300px;
  height: 300px;
  background: var(--acento);
  top: -100px;
  right: -100px;
  animation-delay: 0s;
}

.orb-2 {
  width: 220px;
  height: 220px;
  background: #14A83A;
  bottom: -80px;
  left: -80px;
  animation-delay: -7s;
}

.orb-3 {
  width: 180px;
  height: 180px;
  background: #f59e0b;
  top: 40%;
  left: 50%;
  transform: translate(-50%, -50%);
  animation-delay: -14s;
}

@keyframes flotar {
  0%, 100% { transform: translate(0, 0) scale(1); }
  25% { transform: translate(30px, -20px) scale(1.05); }
  50% { transform: translate(-20px, 30px) scale(0.95); }
  75% { transform: translate(15px, 25px) scale(1.02); }
}

@media (prefers-reduced-motion: reduce) {
  .orb { animation: none; opacity: 0.15; }
}

/* Tarjeta de login */
.login-card {
  position: relative;
  z-index: 1;
  width: 100%;
  max-width: 400px;
  background: var(--panel);
  border: 1px solid var(--border);
  border-radius: 20px;
  padding: 2.5rem 2rem;
  box-shadow: var(--sombra-md);
  opacity: 0;
  transform: translateY(30px) scale(0.96);
  transition: opacity 0.6s cubic-bezier(0.16, 1, 0.3, 1),
              transform 0.6s cubic-bezier(0.16, 1, 0.3, 1);
}

.login-card.visible {
  opacity: 1;
  transform: translateY(0) scale(1);
}

/* Logo centrado */
.logo-contenedor {
  display: flex;
  justify-content: center;
  margin-bottom: 1.5rem;
  opacity: 0;
  transform: scale(0.9);
  animation: logoEntrada 0.7s cubic-bezier(0.16, 1, 0.3, 1) 0.15s forwards;
}

.logo {
  width: 100px;
  height: auto;
  filter: drop-shadow(0 8px 24px rgba(20, 168, 58, 0.35));
}

@keyframes logoEntrada {
  from { opacity: 0; transform: scale(0.9) translateY(10px); }
  to { opacity: 1; transform: scale(1) translateY(0); }
}

/* Cabecera */
.cabecera {
  text-align: center;
  margin-bottom: 2rem;
  opacity: 0;
  transform: translateY(10px);
  animation: entradaSuave 0.5s ease 0.3s forwards;
}

.cabecera h1 {
  margin: 0 0 0.4rem;
  font-size: 1.5rem;
  font-weight: 700;
  color: var(--text);
}

.cabecera p {
  margin: 0;
  color: var(--muted);
  font-size: 0.95rem;
}

/* Formulario */
.formulario {
  display: flex;
  flex-direction: column;
  gap: 1.15rem;
}

.campo-grupo {
  opacity: 0;
  transform: translateY(12px);
  animation: entradaSuave 0.5s ease forwards;
}

.campo-grupo:nth-child(1) { animation-delay: 0.4s; }
.campo-grupo:nth-child(2) { animation-delay: 0.5s; }

@keyframes entradaSuave {
  to { opacity: 1; transform: translateY(0); }
}

.etiqueta {
  display: block;
  font-size: 0.8rem;
  font-weight: 600;
  color: var(--muted);
  margin-bottom: 0.45rem;
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.input-contenedor {
  position: relative;
  display: flex;
  align-items: center;
}

.input-contenedor .icono {
  position: absolute;
  left: 1rem;
  font-size: 1.1rem;
  color: var(--muted);
  pointer-events: none;
  transition: color 0.2s ease;
}

.input-contenedor:focus-within .icono {
  color: var(--acento);
}

.control {
  width: 100%;
  padding: 0.85rem 1rem 0.85rem 3rem;
  font: inherit;
  font-size: 1rem;
  color: var(--text);
  background: var(--panel);
  border: 1.5px solid var(--border);
  border-radius: 12px;
  transition: border-color 0.2s ease, box-shadow 0.2s ease, background 0.2s ease;
}

.control::placeholder {
  color: var(--muted);
  opacity: 0.7;
}

.control:focus {
  outline: none;
  border-color: var(--acento);
  box-shadow: 0 0 0 3px var(--acento-suave);
}

.control:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

/* Error */
.error {
  margin: -0.5rem 0 0.25rem;
  padding: 0.7rem 1rem;
  background: var(--danger-suave);
  color: var(--danger);
  border-radius: 10px;
  font-size: 0.85rem;
  font-weight: 500;
  display: flex;
  align-items: center;
  gap: 0.5rem;
  animation: sacudida 0.4s ease;
}

.error::before {
  content: '⚠';
  font-size: 0.9rem;
}

@keyframes sacudida {
  0%, 100% { transform: translateX(0); }
  20%, 60% { transform: translateX(-6px); }
  40%, 80% { transform: translateX(6px); }
}

/* Botón entrar */
.btn-entrar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.6rem;
  width: 100%;
  padding: 0.9rem 1.5rem;
  margin-top: 0.5rem;
  font: inherit;
  font-weight: 600;
  font-size: 1rem;
  color: var(--acento-texto);
  background: var(--acento);
  border: none;
  border-radius: 12px;
  cursor: pointer;
  transition: background 0.2s ease, transform 0.1s ease, box-shadow 0.2s ease;
  box-shadow: 0 4px 14px rgba(79, 70, 229, 0.35);
}

.btn-entrar:hover:not(:disabled) {
  background: var(--acento-fuerte);
  box-shadow: 0 6px 20px rgba(79, 70, 229, 0.45);
}

.btn-entrar:active:not(:disabled) {
  transform: scale(0.98);
}

.btn-entrar:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

/* Spinner */
.spinner {
  width: 20px;
  height: 20px;
  border: 2.5px solid transparent;
  border-top-color: currentColor;
  border-radius: 50%;
  animation: girar 0.8s linear infinite;
}

@keyframes girar {
  to { transform: rotate(360deg); }
}

/* Pie */
.pie-login {
  margin-top: 2rem;
  text-align: center;
  opacity: 0;
  animation: entradaSuave 0.5s ease 0.7s forwards;
}

.pie-login p {
  margin: 0;
  font-size: 0.75rem;
  color: var(--muted);
  opacity: 0.8;
}

/* Responsive */
@media (max-width: 480px) {
  .login-wrap { padding: 1rem; }
  .login-card { padding: 2rem 1.5rem; border-radius: 16px; }
  .logo { width: 88px; }
  .cabecera h1 { font-size: 1.35rem; }
  .control { padding: 0.9rem 1rem 0.9rem 3rem; font-size: 16px; }
  .btn-entrar { padding: 1rem 1.5rem; min-height: 52px; }
}

@media (max-width: 360px) {
  .login-card { padding: 1.75rem 1.25rem; }
  .logo { width: 80px; }
  .cabecera h1 { font-size: 1.25rem; }
}

/* Modo oscuro - ajustes adicionales */
@media (prefers-color-scheme: dark) {
  .orb { opacity: 0.25; }
}
</style>