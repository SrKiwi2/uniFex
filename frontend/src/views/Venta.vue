<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue';
import { useRouter } from 'vue-router';
import { apiFetch } from '../api';
import { useAuthStore } from '../stores/auth';
import { usePuestosStore } from '../stores/puestos';
import { toast } from '../ui/toast';
import { guardarBorrador, leerBorrador, borrarBorrador } from '../ui/borrador';

/*
 * Registro de una venta. Es la pantalla que convierte un carrito de casetas en una
 * inscripcion, y la que acabara en el APK, asi que esta pensada para una mano y una
 * pantalla pequeña: un paso por vez, campos grandes y el resumen siempre visible.
 *
 * Las casetas no viven aqui: son las que el vendedor ya tiene reservadas (estado T a su
 * nombre), asi que salir de esta vista no pierde nada. Lo que si se guarda en local es lo
 * tecleado, para que cerrar la app a mitad no obligue a repetirlo.
 */

const auth = useAuthStore();
const tienda = usePuestosStore();
const router = useRouter();

const PASOS = ['Entidad', 'Responsables', 'Confirmar'];
const paso = ref(0);
const enviando = ref(false);
const tiposEntidad = ref([]);
/** Casetas que el servidor rechazo en el ultimo intento: se resaltan para explicar el fallo. */
const perdidas = ref([]);

const carrito = computed(() => tienda.carritoDe(auth.id));
const total = computed(() => carrito.value.reduce((s, p) => s + Number(p.precio || 0), 0));

const personaVacia = () => ({ nombre: '', paterno: '', materno: '', ci: '', correo: '', celular: '' });

const form = reactive({
  entidadNombre: '', nit: '', descripcion: '', objeto: '',
  representanteLegal: '', ciRepresentante: '', tipoEntidadId: null,
  fechaInicio: '', fechaFin: '',
  responsables: [personaVacia()],
  entidadBancaria: '', numComprobante: null, pagoContado: false,
});

const hayAcompaniante = computed(() => form.responsables.length > 1);

// ---- validacion por paso: el boton "Siguiente" no deja avanzar con datos incompletos,
// que es mejor que dejarle llegar al final y rechazarle todo de golpe.
const errorPaso = computed(() => {
  if (paso.value === 0) {
    if (!form.entidadNombre.trim()) return 'El nombre de la entidad es obligatorio.';
    if (!form.tipoEntidadId) return 'Elige el tipo de entidad.';
  }
  if (paso.value === 1) {
    const t = form.responsables[0];
    if (!t.nombre.trim()) return 'El titular necesita nombre.';
    if (!t.ci.trim()) return 'El titular necesita C.I.';
    if (hayAcompaniante.value) {
      const a = form.responsables[1];
      const algo = a.nombre || a.paterno || a.materno || a.ci || a.correo || a.celular;
      if (algo && (!a.nombre.trim() || !a.ci.trim())) {
        return 'El acompañante necesita al menos nombre y C.I. (o quítalo).';
      }
    }
  }
  return '';
});

function siguiente() {
  if (errorPaso.value) { toast(errorPaso.value, 'error'); return; }
  if (paso.value < PASOS.length - 1) paso.value++;
}
function atras() {
  if (paso.value > 0) paso.value--;
}

function agregarAcompaniante() {
  if (form.responsables.length >= 2) return; // el sistema permite el dueño y uno mas
  form.responsables.push(personaVacia());
}
function quitarAcompaniante() {
  form.responsables.splice(1);
}

/** Quita una caseta del carrito sin salir del formulario. */
async function quitarCaseta(p) {
  try {
    const r = await apiFetch('/api/app/puestos/carrito', {
      method: 'DELETE', body: JSON.stringify({ ids: [p.id] }),
    });
    const d = await r.json().catch(() => ({}));
    if ((d.logradas ?? []).includes(p.id)) {
      tienda.aplicar({ ...p, estado: 'L', reservadoPor: null });
    }
  } catch (e) {
    toast(e.message, 'error');
  }
}

async function registrar() {
  if (enviando.value) return;
  if (!carrito.value.length) { toast('No tienes casetas seleccionadas.', 'error'); return; }
  enviando.value = true;
  perdidas.value = [];
  try {
    // Se manda solo el acompañante si de verdad lo rellenaron: un bloque vacio haria
    // fallar la validacion del servidor por "cada responsable necesita nombre".
    const responsables = form.responsables.filter((p) => p.nombre.trim() && p.ci.trim());
    const r = await apiFetch('/api/app/inscripciones', {
      method: 'POST',
      body: JSON.stringify({
        entidadNombre: form.entidadNombre,
        nit: form.nit,
        descripcion: form.descripcion,
        objeto: form.objeto,
        representanteLegal: form.representanteLegal,
        ciRepresentante: form.ciRepresentante,
        tipoEntidadId: form.tipoEntidadId,
        fechaInicio: form.fechaInicio || null,
        fechaFin: form.fechaFin || null,
        responsables,
        entidadBancaria: form.entidadBancaria,
        numComprobante: form.numComprobante,
        pagoContado: form.pagoContado,
        puestos: carrito.value.map((p) => p.id),
      }),
    });
    const d = await r.json().catch(() => ({}));

    if (r.status === 409) {
      // Otro vendedor gano una caseta. La venta NO se registro (el servidor revirtio todo),
      // asi que se refresca el mapa y se le explica que paso sin perderle lo escrito.
      toast(d.mensaje || 'Una caseta ya no está disponible', 'error');
      await tienda.recargar();
      paso.value = 2;
      return;
    }
    if (!r.ok || !d.ok) {
      toast(d.mensaje || 'No se pudo registrar la venta', 'error');
      return;
    }

    borrarBorrador(auth.id);
    toast(`Venta registrada: ${Number(d.total).toLocaleString('es-BO')} Bs`, 'ok');
    router.push({ path: '/mis-ventas', query: { registrada: d.inscripcionId } });
  } catch (e) {
    toast(e.message, 'error');
  } finally {
    enviando.value = false;
  }
}

// ---- borrador local: se guarda al vuelo y se recupera al volver ----
watch(form, () => guardarBorrador(auth.id, form), { deep: true });

onMounted(async () => {
  const guardado = leerBorrador(auth.id);
  if (guardado) {
    Object.assign(form, guardado);
    // `responsables` es un array: Object.assign no lo reconstruye si venia vacio.
    if (!Array.isArray(form.responsables) || !form.responsables.length) {
      form.responsables = [personaVacia()];
    }
    toast('Se recuperó lo que habías escrito', 'info');
  }
  await tienda.asegurar();
  try {
    const r = await apiFetch('/api/app/catalogos/tipos-entidad');
    tiposEntidad.value = await r.json();
  } catch (e) {
    toast('No se pudieron cargar los tipos de entidad', 'error');
  }
});
</script>

<template>
  <div class="venta">
    <!-- Resumen siempre visible: en el movil, saber cuanto se esta cobrando no puede
         depender de bajar hasta el final del formulario. -->
    <header class="resumen card">
      <div>
        <strong>{{ carrito.length }}</strong> caseta{{ carrito.length === 1 ? '' : 's' }}
        <span class="muted"> · </span>
        <strong class="total">{{ total.toLocaleString('es-BO') }} Bs</strong>
      </div>
      <router-link to="/mapa" class="btn btn-fantasma btn-sm">Elegir en el mapa</router-link>
    </header>

    <p v-if="!carrito.length" class="vacio card">
      No tienes casetas seleccionadas.
      <router-link to="/mapa">Ve al mapa</router-link> y toca las que vas a vender.
    </p>

    <template v-else>
      <ol class="pasos">
        <li v-for="(p, i) in PASOS" :key="p" :class="{ activo: i === paso, hecho: i < paso }">
          <span class="num">{{ i + 1 }}</span>{{ p }}
        </li>
      </ol>

      <!-- Paso 1: entidad -->
      <section v-show="paso === 0" class="card bloque">
        <label class="campo">
          <span>Nombre de la entidad *</span>
          <input class="control" v-model="form.entidadNombre" placeholder="Ej. Artesanías Illimani" />
        </label>
        <label class="campo">
          <span>Tipo de entidad *</span>
          <select class="control" v-model="form.tipoEntidadId">
            <option :value="null" disabled>Elige una opción…</option>
            <option v-for="t in tiposEntidad" :key="t.id" :value="t.id">{{ t.nombre }}</option>
          </select>
        </label>
        <div class="dos">
          <label class="campo"><span>NIT</span><input class="control" v-model="form.nit" /></label>
          <label class="campo"><span>C.I. del representante</span><input class="control" v-model="form.ciRepresentante" /></label>
        </div>
        <label class="campo">
          <span>Representante legal</span>
          <input class="control" v-model="form.representanteLegal" />
        </label>
        <label class="campo">
          <span>Rubro o descripción</span>
          <input class="control" v-model="form.descripcion" placeholder="Qué vende o expone" />
        </label>
        <div class="dos">
          <label class="campo"><span>Desde</span><input class="control" type="date" v-model="form.fechaInicio" /></label>
          <label class="campo"><span>Hasta</span><input class="control" type="date" v-model="form.fechaFin" /></label>
        </div>
      </section>

      <!-- Paso 2: responsables -->
      <section v-show="paso === 1" class="card bloque">
        <h3 class="sub">Titular <span class="muted">— el dueño de la caseta</span></h3>
        <div class="dos">
          <label class="campo"><span>Nombre *</span><input class="control" v-model="form.responsables[0].nombre" /></label>
          <label class="campo"><span>C.I. *</span><input class="control" v-model="form.responsables[0].ci" /></label>
        </div>
        <div class="dos">
          <label class="campo"><span>Apellido paterno</span><input class="control" v-model="form.responsables[0].paterno" /></label>
          <label class="campo"><span>Apellido materno</span><input class="control" v-model="form.responsables[0].materno" /></label>
        </div>
        <div class="dos">
          <label class="campo"><span>Celular</span><input class="control" type="tel" v-model="form.responsables[0].celular" /></label>
          <label class="campo"><span>Correo</span><input class="control" type="email" v-model="form.responsables[0].correo" /></label>
        </div>

        <div class="separador"></div>

        <template v-if="hayAcompaniante">
          <h3 class="sub">Acompañante <span class="muted">— se permite uno</span></h3>
          <div class="dos">
            <label class="campo"><span>Nombre</span><input class="control" v-model="form.responsables[1].nombre" /></label>
            <label class="campo"><span>C.I.</span><input class="control" v-model="form.responsables[1].ci" /></label>
          </div>
          <div class="dos">
            <label class="campo"><span>Apellido paterno</span><input class="control" v-model="form.responsables[1].paterno" /></label>
            <label class="campo"><span>Apellido materno</span><input class="control" v-model="form.responsables[1].materno" /></label>
          </div>
          <div class="dos">
            <label class="campo"><span>Celular</span><input class="control" type="tel" v-model="form.responsables[1].celular" /></label>
            <label class="campo"><span>Correo</span><input class="control" type="email" v-model="form.responsables[1].correo" /></label>
          </div>
          <button class="btn btn-peligro btn-sm" @click="quitarAcompaniante">Quitar acompañante</button>
        </template>
        <button v-else class="btn btn-sm" @click="agregarAcompaniante">＋ Agregar acompañante</button>
      </section>

      <!-- Paso 3: confirmar -->
      <section v-show="paso === 2" class="card bloque">
        <h3 class="sub">Casetas</h3>
        <ul class="casetas">
          <li v-for="p in carrito" :key="p.id" :class="{ perdida: perdidas.includes(p.id) }">
            <span class="sw" :style="{ background: p.color || '#94a3b8' }"></span>
            <span class="nom">{{ p.categoria }} {{ p.codigo }}</span>
            <span class="muted">{{ p.tamano }}</span>
            <span class="precio">{{ Number(p.precio || 0).toLocaleString('es-BO') }} Bs</span>
            <button class="btn btn-fantasma btn-sm" title="Quitar" @click="quitarCaseta(p)">✕</button>
          </li>
        </ul>

        <div class="separador"></div>

        <h3 class="sub">Pago</h3>
        <label class="fila-check">
          <input type="checkbox" v-model="form.pagoContado" />
          Pagó al contado
        </label>
        <div v-if="!form.pagoContado" class="dos">
          <label class="campo"><span>Banco</span><input class="control" v-model="form.entidadBancaria" /></label>
          <label class="campo">
            <span>N.º de comprobante</span>
            <input class="control" type="number" v-model.number="form.numComprobante" />
          </label>
        </div>
        <p v-if="!form.pagoContado" class="nota">
          La foto del comprobante se sube después, desde <strong>Mis pendientes</strong>.
          Registrar ahora asegura las casetas.
        </p>
      </section>

      <div class="acciones">
        <button class="btn" :disabled="paso === 0 || enviando" @click="atras">Atrás</button>
        <button v-if="paso < 2" class="btn btn-primario" @click="siguiente">Siguiente</button>
        <button v-else class="btn btn-primario" :disabled="enviando" @click="registrar">
          {{ enviando ? 'Registrando…' : `Registrar venta (${total.toLocaleString('es-BO')} Bs)` }}
        </button>
      </div>
    </template>
  </div>
</template>

<style scoped>
.venta { display: flex; flex-direction: column; gap: 1rem; max-width: 720px; margin: 0 auto; }

.resumen {
  display: flex; align-items: center; justify-content: space-between; gap: 1rem;
  padding: 0.8rem 1rem; position: sticky; top: 0; z-index: 5;
}
.resumen .total { font-variant-numeric: tabular-nums; font-size: 1.05rem; }

.vacio { padding: 2rem 1rem; text-align: center; color: var(--muted); }

.pasos { display: flex; gap: 0.5rem; list-style: none; margin: 0; padding: 0; flex-wrap: wrap; }
.pasos li {
  display: flex; align-items: center; gap: 0.4rem; font-size: 0.85rem;
  color: var(--muted); font-weight: 600;
}
.pasos .num {
  display: grid; place-items: center; width: 1.5rem; height: 1.5rem; border-radius: 50%;
  background: var(--panel-2); border: 1px solid var(--border); font-size: 0.75rem;
}
.pasos .activo { color: var(--acento); }
.pasos .activo .num { background: var(--acento); border-color: var(--acento); color: var(--acento-texto); }
.pasos .hecho .num { background: var(--ok); border-color: var(--ok); color: #fff; }

.bloque { padding: 1.1rem; display: flex; flex-direction: column; gap: 0.85rem; }
.sub { margin: 0; font-size: 0.95rem; font-weight: 700; }
.dos { display: grid; gap: 0.85rem; grid-template-columns: 1fr 1fr; }
.separador { height: 1px; background: var(--border); }
.fila-check { display: flex; align-items: center; gap: 0.5rem; font-size: 0.92rem; }
.nota { margin: 0; font-size: 0.83rem; color: var(--muted); line-height: 1.45; }

.casetas { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; }
.casetas li {
  display: flex; align-items: center; gap: 0.6rem; padding: 0.5rem 0;
  border-bottom: 1px solid var(--border); font-size: 0.9rem;
}
.casetas li:last-child { border-bottom: none; }
.casetas .sw { width: 12px; height: 12px; border-radius: 3px; flex: none; }
.casetas .nom { font-weight: 650; }
.casetas .precio { margin-left: auto; font-variant-numeric: tabular-nums; font-weight: 650; }
.casetas .perdida { color: var(--danger); text-decoration: line-through; }

.acciones { display: flex; gap: 0.6rem; justify-content: flex-end; padding-bottom: 1rem; }

/* En móvil el formulario se vuelve de una columna y los botones ocupan el ancho:
   es la misma pantalla que irá en el APK y ahí se usa con una mano. */
@media (max-width: 560px) {
  .dos { grid-template-columns: 1fr; }
  .acciones { position: sticky; bottom: 0; background: var(--bg); padding: 0.6rem 0 1rem; }
  .acciones .btn { flex: 1; }
}
</style>
