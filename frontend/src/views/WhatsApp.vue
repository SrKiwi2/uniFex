<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { apiFetch } from '../api.js';
import { toast } from '../ui/toast.js';
import UiModal from '../components/UiModal.vue';

const instancias = ref([]);
const cargando = ref(true);
const guardando = ref(false);
const fallo = ref('');
const modal = ref(false);
const activar = ref(null);
const formulario = reactive({ id: null, nombre: '', urlApi: '', instancia: '', claveApi: '' });
const activa = computed(() => instancias.value.find(i => i.activa));

async function peticion(ruta = '', opciones = {}) {
  const respuesta = await apiFetch(`/api/app/whatsapp/instancias${ruta}`, opciones);
  const datos = await respuesta.json();
  if (!respuesta.ok) throw new Error(datos.mensaje || 'No se pudo completar la operación.');
  return datos;
}

async function cargar() {
  cargando.value = true;
  fallo.value = '';
  try { instancias.value = await peticion(); }
  catch (e) { fallo.value = e.message; }
  finally { cargando.value = false; }
}

function abrir(instancia = null) {
  Object.assign(formulario, instancia || { id: null, nombre: '', urlApi: '', instancia: '' }, { claveApi: '' });
  modal.value = true;
}

function cerrar() {
  if (guardando.value) return;
  modal.value = false;
  formulario.claveApi = '';
}

async function guardar() {
  if (guardando.value) return;
  guardando.value = true;
  try {
    const { id, nombre, urlApi, instancia, claveApi } = formulario;
    const resultado = await peticion(id ? `/${id}` : '', {
      method: id ? 'PUT' : 'POST', body: JSON.stringify({ nombre, urlApi, instancia, claveApi }),
    });
    instancias.value = [...instancias.value.filter(i => i.id !== resultado.id), resultado];
    modal.value = false;
    formulario.claveApi = '';
    toast('Instancia guardada.', 'ok');
  } catch (e) { toast(e.message, 'error'); }
  finally { guardando.value = false; }
}

async function confirmarActivacion() {
  if (guardando.value || !activar.value) return;
  guardando.value = true;
  try {
    instancias.value = await peticion(`/${activar.value.id}/estado`, {
      method: 'PATCH', body: JSON.stringify({ activa: true }),
    });
    activar.value = null;
    toast('Instancia activa actualizada.', 'ok');
  } catch (e) { toast(e.message, 'error'); }
  finally { guardando.value = false; }
}

onMounted(cargar);
</script>

<template>
  <section class="whatsapp">
    <div class="encabezado">
      <p class="muted">Administra las conexiones que se usan para enviar mensajes, recibos y credenciales.</p>
      <button class="btn btn-primario" :disabled="cargando || !!fallo || guardando" @click="abrir()">＋ Nueva instancia</button>
    </div>
    <p v-if="fallo" role="alert">{{ fallo }} <button class="btn" @click="cargar">Reintentar</button></p>
    <p v-else-if="cargando" role="status">Cargando instancias…</p>
    <template v-else>
      <div class="card resumen">
        <template v-if="activa">
          <span class="badge badge-ok">Activa</span>
          <strong>{{ activa.nombre }}</strong>
          <p>Los próximos envíos usarán esta instancia. Para desactivarla, activa otra: el cambio se hace en una sola operación.</p>
        </template>
        <template v-else>
          <strong>Aún no hay una instancia configurada</strong>
          <p>Registra la primera para habilitar los envíos de WhatsApp. Quedará activa automáticamente.</p>
        </template>
      </div>
      <div v-if="instancias.length" class="card tabla-scroll">
        <table class="tabla">
          <thead><tr><th>Nombre</th><th>Instancia</th><th>URL de la API</th><th>Estado</th><th>Acciones</th></tr></thead>
          <tbody>
            <tr v-for="i in instancias" :key="i.id">
              <td><strong>{{ i.nombre }}</strong></td>
              <td>{{ i.instancia }}</td>
              <td class="direccion">{{ i.urlApi }}</td>
              <td><span class="badge" :class="i.activa ? 'badge-ok' : 'badge-muted'">{{ i.activa ? 'Activa' : 'Inactiva' }}</span></td>
              <td><div class="acciones">
                <button class="btn" :disabled="guardando" @click="abrir(i)">Editar</button>
                <button v-if="!i.activa" class="btn btn-primario" :disabled="guardando" @click="activar = i">Activar</button>
              </div></td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>

    <UiModal v-if="modal" :titulo="formulario.id ? 'Editar instancia' : 'Nueva instancia'" ancho="580px" @cerrar="cerrar">
      <form id="instancia-whatsapp" class="formulario" @submit.prevent="guardar">
        <label class="campo">Nombre descriptivo
          <input v-model="formulario.nombre" class="control" required maxlength="120" placeholder="WhatsApp FEXPO" :disabled="guardando" />
        </label>
        <label class="campo">URL de la API
          <input v-model="formulario.urlApi" class="control" type="url" required maxlength="500" placeholder="https://servidor/message/" :disabled="guardando" />
          <small class="muted">Dirección base de mensajes, sin sendText ni sendMedia.</small>
        </label>
        <label class="campo">Nombre de instancia en el proveedor
          <input v-model="formulario.instancia" class="control" required maxlength="200" placeholder="FEXPO UAP V2" :disabled="guardando" />
        </label>
        <label class="campo">Clave API
          <input v-model="formulario.claveApi" class="control" type="password" autocomplete="new-password" :required="!formulario.id" maxlength="500" :disabled="guardando" />
          <small v-if="formulario.id" class="muted">Déjala vacía para conservar la clave guardada.</small>
        </label>
        <p v-if="!formulario.id" class="muted">{{ instancias.length ? 'Se registrará inactiva. Después podrás activarla.' : 'Esta primera instancia quedará activa automáticamente.' }}</p>
      </form>
      <template #pie>
        <button class="btn" :disabled="guardando" @click="cerrar">Cancelar</button>
        <button class="btn btn-primario" form="instancia-whatsapp" type="submit" :disabled="guardando">{{ guardando ? 'Guardando…' : 'Guardar' }}</button>
      </template>
    </UiModal>

    <UiModal v-if="activar" titulo="Cambiar instancia activa" @cerrar="!guardando && (activar = null)">
      <p>Se activará <strong>{{ activar.nombre }}</strong><template v-if="activa"> y <strong>{{ activa.nombre }}</strong> quedará inactiva</template>.</p>
      <p>Los nuevos envíos usarán esta conexión. Los paquetes que ya se están enviando terminarán con la anterior.</p>
      <template #pie>
        <button class="btn" :disabled="guardando" @click="activar = null">Cancelar</button>
        <button class="btn btn-primario" :disabled="guardando" @click="confirmarActivacion">{{ guardando ? 'Activando…' : 'Activar' }}</button>
      </template>
    </UiModal>
  </section>
</template>

<style scoped>
.whatsapp, .formulario { display: flex; flex-direction: column; gap: 1rem; }
.encabezado, .acciones { display: flex; align-items: center; gap: .6rem; flex-wrap: wrap; }
.encabezado { justify-content: space-between; }
.resumen { padding: 1rem; }
.resumen strong { margin-left: .5rem; }
.resumen p { margin-bottom: 0; }
.tabla-scroll { overflow-x: auto; }
.direccion { overflow-wrap: anywhere; min-width: 180px; }
@media (max-width: 560px) { .encabezado > .btn { width: 100%; } }
</style>
