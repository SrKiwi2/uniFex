<script setup>
import { onMounted, ref, watch } from 'vue';
import { apiFetch } from '../api.js';
import { descargarArchivo } from '../ui/descargas.js';
import UiModal from '../components/UiModal.vue';

const archivos = ref([]);
const archivo = ref('errores.txt');
const buscar = ref('');
const usuario = ref('');
const errores = ref([]);
const anterior = ref(0);
const cargando = ref(false);
const fallo = ref('');
const descargando = ref(false);
const vaciando = ref(false);
const confirmarArchivo = ref(null);
const aviso = ref('');
watch([archivo, buscar, usuario], () => { errores.value = []; anterior.value = 0; });

async function cargar(mas = false) {
  if (cargando.value) return;
  cargando.value = true;
  fallo.value = '';
  try {
    if (!mas) {
      const lista = await apiFetch('/api/app/errores/archivos');
      if (!lista.ok) throw new Error('No se pudo consultar los archivos de errores.');
      archivos.value = await lista.json();
    }
    const parametros = new URLSearchParams({ archivo: archivo.value, buscar: buscar.value, usuario: usuario.value });
    if (mas) parametros.set('antes', String(anterior.value));
    const respuesta = await apiFetch(`/api/app/errores?${parametros}`);
    if (!respuesta.ok) throw new Error('No se pudo leer el registro de errores.');
    const datos = await respuesta.json();
    errores.value = mas ? [...errores.value, ...datos.errores] : datos.errores;
    anterior.value = datos.anterior;
  } catch (error) {
    fallo.value = error.message || 'No se pudo cargar el registro.';
  } finally {
    cargando.value = false;
  }
}

function fecha(valor) { return new Date(valor).toLocaleString('es-BO'); }

async function descargar() {
  if (descargando.value) return;
  descargando.value = true;
  fallo.value = '';
  aviso.value = '';
  try {
    const resultado = await descargarArchivo(`/api/app/errores/descargar?${new URLSearchParams({ archivo: archivo.value })}`, archivo.value);
    aviso.value = resultado.destino === 'telefono' ? 'Texto guardado en Documentos.' : 'Texto descargado.';
  } catch (error) { fallo.value = error.message || 'No se pudo descargar el texto.'; }
  finally { descargando.value = false; }
}

async function vaciar() {
  if (vaciando.value || !confirmarArchivo.value) return;
  vaciando.value = true;
  fallo.value = '';
  aviso.value = '';
  try {
    const parametros = new URLSearchParams({ archivo: confirmarArchivo.value, confirmar: 'true' });
    const respuesta = await apiFetch(`/api/app/errores?${parametros}`, { method: 'DELETE' });
    if (!respuesta.ok) throw new Error('No se pudo vaciar el archivo.');
    confirmarArchivo.value = null;
    errores.value = [];
    anterior.value = 0;
    aviso.value = 'Archivo vaciado. Los nuevos errores se seguirán registrando.';
    await cargar();
  } catch (error) { fallo.value = error.message || 'No se pudo vaciar el archivo.'; confirmarArchivo.value = null; }
  finally { vaciando.value = false; }
}
onMounted(() => cargar());
</script>

<template>
  <section class="registro-errores">
    <header class="encabezado">
      <div><h2>Registro de errores</h2><p class="muted">Errores del servidor y de los usuarios, del más reciente al más antiguo.</p></div>
      <button class="btn btn-primario" :disabled="cargando" @click="cargar()">{{ cargando ? 'Cargando…' : 'Actualizar' }}</button>
    </header>
    <form class="card filtros" @submit.prevent="cargar()">
      <label class="campo"><span>Archivo / fecha</span>
        <select v-model="archivo" class="control" :disabled="cargando">
          <option value="errores.txt">Actual — errores.txt</option>
          <option v-for="nombre in archivos.filter(a => a !== 'errores.txt')" :key="nombre" :value="nombre">{{ nombre }}</option>
        </select>
      </label>
      <label class="campo"><span>Usuario</span><input v-model="usuario" class="control" :disabled="cargando" placeholder="Nombre de usuario" maxlength="150" /></label>
      <label class="campo"><span>Buscar</span><input v-model="buscar" class="control" :disabled="cargando" placeholder="Mensaje, ruta o módulo" maxlength="200" /></label>
      <button class="btn" :disabled="cargando">Filtrar</button>
    </form>
    <div class="acciones-archivo">
      <button class="btn" :disabled="cargando || descargando || vaciando || !archivos.includes(archivo)" @click="descargar">{{ descargando ? 'Descargando…' : 'Descargar texto' }}</button>
      <button class="btn vaciar" :disabled="cargando || descargando || vaciando || !archivos.includes(archivo)" @click="confirmarArchivo = archivo">Vaciar archivo</button>
      <span class="muted">Se aplica al archivo completo seleccionado, incluidos los errores ocultos por los filtros.</span>
    </div>
    <p v-if="aviso" role="status">{{ aviso }}</p>
    <p v-if="fallo" class="fallo" role="alert">{{ fallo }}</p>
    <p v-if="!cargando && !errores.length && !fallo" class="card vacio">No se encontraron errores en el tramo consultado.</p>
    <p class="muted">{{ errores.length }} errores mostrados. «Sistema» indica un proceso sin usuario; «Sin autenticar», una petición sin sesión identificada.</p>
    <article v-for="(error, indice) in errores" :key="`${error.peticionId}-${error.fecha}-${indice}`" class="card entrada">
      <header>
        <strong>{{ error.usuario }}<small v-if="error.usuarioId"> · ID {{ error.usuarioId }}</small></strong>
        <span class="badge badge-danger">{{ error.origen }}</span>
        <time :datetime="error.fecha">{{ fecha(error.fecha) }}</time>
      </header>
      <p class="mensaje">{{ error.mensaje }}</p>
      <p v-if="error.ruta" class="ruta">{{ error.ruta }}</p>
      <details>
        <summary>Ver detalle técnico</summary>
        <p class="muted">{{ error.modulo }}</p>
        <p v-if="error.peticionId" class="muted">Petición: {{ error.peticionId }}</p>
        <pre v-if="error.detalle">{{ error.detalle }}</pre>
        <p v-else class="muted">Sin traza adicional.</p>
      </details>
    </article>
    <button v-if="anterior > 0" class="btn anteriores" :disabled="cargando" @click="cargar(true)">{{ cargando ? 'Cargando…' : 'Buscar errores anteriores' }}</button>
    <UiModal v-if="confirmarArchivo" titulo="¿Vaciar el archivo de errores?" @cerrar="!vaciando && (confirmarArchivo = null)">
      <p>Se borrará todo el contenido de <strong>{{ confirmarArchivo }}</strong>. Esta acción no se puede deshacer.</p>
      <p>Descarga el texto antes si quieres conservar una copia. Los demás archivos no se vaciarán y los nuevos errores se seguirán registrando.</p>
      <template #pie>
        <button class="btn btn-fantasma" :disabled="vaciando" @click="confirmarArchivo = null">Cancelar</button>
        <button class="btn vaciar" :disabled="vaciando" @click="vaciar">{{ vaciando ? 'Vaciando…' : 'Sí, vaciar archivo' }}</button>
      </template>
    </UiModal>
  </section>
</template>

<style scoped>
.registro-errores { display: flex; flex-direction: column; gap: 1rem; min-width: 0; }
.encabezado, .entrada header { display: flex; align-items: center; flex-wrap: wrap; gap: 0.75rem; }
.encabezado { justify-content: space-between; }
h2, .encabezado p { margin: 0 0 0.35rem; }
.filtros { display: grid; grid-template-columns: minmax(0, 1.2fr) minmax(0, 1fr) minmax(0, 1.5fr) auto; gap: 0.8rem; padding: 1rem; align-items: end; }
.campo { min-width: 0; }
.entrada { padding: 1rem; border-left: 3px solid var(--danger); overflow-wrap: anywhere; }
.entrada time { margin-left: auto; color: var(--muted); font-size: 0.85rem; }
.entrada small { font-weight: normal; }
.mensaje, pre { white-space: pre-wrap; overflow-wrap: anywhere; }
.ruta { font-family: monospace; color: var(--muted); }
summary { cursor: pointer; color: var(--acento); }
pre { font-size: 0.8rem; background: var(--panel-2); padding: 0.75rem; border-radius: var(--radio-sm); max-height: 24rem; overflow: auto; }
.fallo { color: var(--danger); }
.anteriores { align-self: center; }
.acciones-archivo { display: flex; flex-wrap: wrap; align-items: center; gap: 0.75rem; }
.vaciar { color: var(--danger); border-color: var(--danger); }
@media (max-width: 850px) { .filtros { grid-template-columns: 1fr 1fr; } }
@media (max-width: 560px) { .filtros { grid-template-columns: 1fr; } .entrada time { width: 100%; margin-left: 0; } }
</style>
