<script setup>
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import { useAuthStore } from '../stores/auth';

const usuario = ref('');
const contrasena = ref('');
const error = ref('');
const cargando = ref(false);
const auth = useAuthStore();
const router = useRouter();

async function entrar() {
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
</script>

<template>
  <div class="wrap">
    <form class="card" @submit.prevent="entrar">
      <h1>UniFex</h1>
      <p class="sub">Ingreso de operadores</p>
      <input v-model="usuario" placeholder="Usuario" autocomplete="username" />
      <input v-model="contrasena" type="password" placeholder="Contraseña" autocomplete="current-password" />
      <button type="submit" :disabled="cargando">{{ cargando ? 'Ingresando…' : 'Ingresar' }}</button>
      <p v-if="error" class="error">{{ error }}</p>
    </form>
  </div>
</template>

<style scoped>
.wrap { min-height: 100%; display: grid; place-items: center; padding: 1rem; }
.card {
  width: 100%; max-width: 340px; background: var(--panel);
  border: 1px solid var(--border); border-radius: 14px; padding: 1.75rem;
  display: flex; flex-direction: column; gap: 0.75rem;
  box-shadow: 0 10px 30px rgba(2, 6, 23, 0.08);
}
h1 { margin: 0; font-size: 1.6rem; }
.sub { margin: 0 0 0.5rem; color: var(--muted); }
input {
  padding: 0.7rem 0.8rem; border: 1px solid var(--border); border-radius: 9px; font-size: 1rem;
}
button {
  margin-top: 0.25rem; padding: 0.7rem; border: none; border-radius: 9px;
  background: #2563eb; color: #fff; font-weight: 600;
}
button:disabled { opacity: 0.6; cursor: default; }
.error { color: var(--ocupado); margin: 0.25rem 0 0; font-size: 0.9rem; }
</style>
