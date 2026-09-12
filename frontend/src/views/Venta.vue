<script setup>
import { ref, reactive, computed, onMounted, onUnmounted, watch, nextTick } from 'vue';
import { useRouter } from 'vue-router';
import { apiFetch } from '../api';
import { useAuthStore } from '../stores/auth';
import { usePuestosStore } from '../stores/puestos';
import { toast } from '../ui/toast';
import { descargarRecibo } from '../ui/descargas';
import { guardarBorrador, leerBorrador, borrarBorrador } from '../ui/borrador';

/*
 * Registro de una venta. Es la pantalla que convierte un carrito de casetas en una
 * inscripcion, y la que se usa desde el APK, asi que esta pensada para una mano y una
 * pantalla pequeña: un paso por vez, campos grandes y el resumen siempre visible.
 *
 * Las casetas no viven aqui: son las que el vendedor ya tiene reservadas (estado T a su
 * nombre), asi que salir de esta vista no pierde nada. Lo que si se guarda en local es lo
 * tecleado, para que cerrar la app a mitad no obligue a repetirlo.
 *
 * Quien es quien, que era la mayor fuente de confusion delante del cliente:
 *   - Responsable legal  -> el DUEÑO. Va con la entidad, porque es de la empresa, y es a
 *                           quien hay que llamar por un cobro. Obligatorio.
 *   - Responsable 1 y 2  -> quien ATIENDE la caseta durante la feria. Pueden ser terceros,
 *                           y son los que necesitan credencial (de ahi la foto).
 * Antes se llamaban "Titular - dueño de la caseta" y "Acompañante", que decia justo lo
 * contrario de lo que el sistema hace con ellos.
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

// Sin `correo`: no se usaba para nada y era un campo mas que rellenar delante del cliente.
const personaVacia = () => ({ nombre: '', paterno: '', materno: '', ci: '', celular: '' });

const form = reactive({
  entidadNombre: '', nit: '', descripcion: '', objeto: '',
  representanteLegal: '', ciRepresentante: '', celularRepresentante: '',
  tipoEntidadId: null,
  fechaInicio: '', fechaFin: '',
  /** El Responsable 1 es el mismo responsable legal: copia sus datos y bloquea los campos. */
  copiaLegal: false,
  responsables: [personaVacia()],
  entidadBancaria: '', numComprobante: null, pagoContado: false,
});

/*
 * Fotos de los responsables, FUERA de `form` a proposito.
 *
 * Son objetos File y el borrador se guarda como JSON: metidas ahi se convertirian en `{}` al
 * recuperarlas, y ademas no tiene sentido conservar un archivo de una sesion a otra. Se suben
 * DESPUES de que la venta exista, porque hasta entonces el responsable no tiene id.
 */
const fotos = ref([null, null]);

const hayResponsable2 = computed(() => form.responsables.length > 1);

/** Mientras "es el mismo" este marcado, el Responsable 1 sigue al responsable legal. */
watch(
  () => [form.copiaLegal, form.representanteLegal, form.ciRepresentante, form.celularRepresentante],
  () => {
    if (!form.copiaLegal) return;
    const r = form.responsables[0];
    if (!r) return; // un borrador recuperado puede llegar sin la lista todavia
    // El responsable legal se pide como nombre completo en un solo campo (asi lo guarda
    // `entidad`), asi que va entero al nombre y los apellidos quedan vacios.
    r.nombre = form.representanteLegal;
    r.paterno = '';
    r.materno = '';
    r.ci = form.ciRepresentante;
    r.celular = form.celularRepresentante;
  },
);

// ---- validacion por paso ----
/*
 * Antes esto era un unico `toast` con la primera falta ("El titular necesita nombre"), y en
 * un formulario de doce campos el vendedor tenia que ADIVINAR cual. Ahora se devuelven todas
 * las que faltan, con la etiqueta que se lee en pantalla: se listan arriba, se marcan en rojo
 * y el foco salta a la primera. Solo se resaltan tras pulsar "Siguiente", para no ir en rojo
 * desde el primer segundo.
 */
const intentado = ref(false);

const algoEscrito = (p) => Boolean(p.nombre || p.paterno || p.materno || p.ci || p.celular);

const faltantes = computed(() => {
  const falta = new Map();
  const pide = (clave, etiqueta, cumplido) => {
    if (!cumplido) falta.set(clave, etiqueta);
  };

  if (paso.value === 0) {
    pide('entidadNombre', 'Nombre de la entidad', form.entidadNombre.trim());
    pide('tipoEntidadId', 'Tipo de entidad', form.tipoEntidadId);
    pide('representanteLegal', 'Nombre del responsable legal', form.representanteLegal.trim());
    pide('ciRepresentante', 'C.I. del responsable legal', form.ciRepresentante.trim());
    pide('celularRepresentante', 'Celular del responsable legal', form.celularRepresentante.trim());
  }
  if (paso.value === 1) {
    const r0 = form.responsables[0] || {};
    pide('r0.nombre', 'Nombre del Responsable 1', (r0.nombre || '').trim());
    pide('r0.ci', 'C.I. del Responsable 1', (r0.ci || '').trim());
    const r1 = form.responsables[1];
    // Un Responsable 2 a medias es peor que ninguno: o se completa o se quita.
    if (r1 && algoEscrito(r1)) {
      pide('r1.nombre', 'Nombre del Responsable 2', r1.nombre.trim());
      pide('r1.ci', 'C.I. del Responsable 2', r1.ci.trim());
    }
  }
  return falta;
});

const falta = (clave) => intentado.value && faltantes.value.has(clave);

function siguiente() {
  intentado.value = true;
  if (faltantes.value.size) {
    nextTick(() => {
      const primero = document.querySelector('.control.falta');
      if (primero) {
        primero.focus({ preventScroll: true });
        primero.scrollIntoView({ block: 'center', behavior: 'smooth' });
      }
    });
    return;
  }
  intentado.value = false;
  if (paso.value < PASOS.length - 1) paso.value++;
}

function atras() {
  intentado.value = false;
  if (paso.value > 0) paso.value--;
}

function agregarResponsable2() {
  if (form.responsables.length >= 2) return; // el sistema permite dos
  form.responsables.push(personaVacia());
}
function quitarResponsable2() {
  form.responsables.splice(1);
  fotos.value[1] = null;
}

// ---- foto opcional de cada responsable ----
/*
 * Se toma aqui porque es el unico momento en que la persona esta DELANTE del vendedor. Si no
 * se toma, no pasa nada: la venta se registra igual y las fotos se completan luego desde
 * "Mis ventas", que es el modulo que ya existe para eso.
 */
function elegirFoto(indice, evento) {
  const archivo = evento.target.files?.[0];
  evento.target.value = '';
  if (!archivo) return;
  if (fotos.value[indice]?.url) URL.revokeObjectURL(fotos.value[indice].url);
  fotos.value[indice] = { archivo, url: URL.createObjectURL(archivo) };
}

function quitarFoto(indice) {
  if (fotos.value[indice]?.url) URL.revokeObjectURL(fotos.value[indice].url);
  fotos.value[indice] = null;
}

/**
 * Sube las fotos tomadas, ya con la venta registrada.
 *
 * Se empareja por C.I. y no por posicion: el servidor devuelve los responsables en el orden
 * que le da la consulta, y confiar en el pondria la cara de uno en la ficha del otro.
 * Ningun fallo de aqui puede tumbar la venta: ya esta hecha y las casetas ya son suyas.
 */
async function subirFotos(inscripcionId) {
  const pendientes = fotos.value
    .map((f, i) => ({ f, i }))
    .filter((x) => x.f?.archivo && form.responsables[x.i]);
  if (!pendientes.length) return true;

  try {
    const r = await apiFetch(`/api/app/inscripciones/${inscripcionId}/responsables`);
    const lista = (await r.json())?.responsables || [];
    let subidas = 0;
    for (const { f, i } of pendientes) {
      const ci = (form.responsables[i].ci || '').trim();
      const destino = lista.find((x) => (x.ci || '').trim() === ci);
      if (!destino) continue;
      const datos = new FormData();
      datos.append('archivo', f.archivo);
      const res = await apiFetch(
        `/api/app/inscripciones/${inscripcionId}/responsables/${destino.id}/foto`,
        { method: 'POST', body: datos },
      );
      if (res.ok) subidas++;
    }
    return subidas === pendientes.length;
  } catch {
    return false;
  }
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

/*
 * Los nombres van en MAYUSCULAS, como el resto del sistema (las entidades y personas que ya
 * hay estan asi). Se normaliza al enviar y ademas se ven en mayusculas mientras se teclea
 * (clase `.mayus`): transformar la tecla en vivo obliga a recolocar el cursor a mano y se
 * rompe con el teclado predictivo de Android.
 */
const mayus = (v) => (typeof v === 'string' ? v.trim().toUpperCase() : v);

async function registrar() {
  if (enviando.value) return;
  if (!carrito.value.length) { toast('No tienes casetas seleccionadas.', 'error'); return; }
  enviando.value = true;
  perdidas.value = [];
  try {
    // Se manda el Responsable 2 solo si de verdad lo rellenaron: un bloque vacio haria
    // fallar la validacion del servidor por "cada responsable necesita nombre".
    const responsables = form.responsables
      .filter((p) => p.nombre.trim() && p.ci.trim())
      .map((p) => ({
        nombre: mayus(p.nombre), paterno: mayus(p.paterno), materno: mayus(p.materno),
        ci: p.ci.trim(), celular: p.celular.trim(), correo: null,
      }));
    const r = await apiFetch('/api/app/inscripciones', {
      method: 'POST',
      body: JSON.stringify({
        entidadNombre: mayus(form.entidadNombre),
        nit: form.nit,
        descripcion: mayus(form.descripcion),
        objeto: mayus(form.objeto),
        representanteLegal: mayus(form.representanteLegal),
        ciRepresentante: form.ciRepresentante.trim(),
        celularRepresentante: form.celularRepresentante.trim(),
        tipoEntidadId: form.tipoEntidadId,
        fechaInicio: form.fechaInicio || null,
        fechaFin: form.fechaFin || null,
        responsables,
        entidadBancaria: mayus(form.entidadBancaria),
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

    // La venta ya existe. Las fotos van despues y no pueden hacerla fracasar.
    const todas = await subirFotos(d.inscripcionId);
    borrarBorrador(auth.id);
    toast(`Venta registrada: ${Number(d.total).toLocaleString('es-BO')} Bs`, 'ok');
    if (!todas) {
      toast('Alguna foto no se subió. Puedes completarla desde Mis ventas.', 'info');
    }
    // El recibo se baja SOLO, que es el momento en que el cliente lo está esperando. Si algo
    // falla no se toca la venta: ya está hecha, y se avisa de dónde volver a pedirlo.
    await descargarRecibo(d.inscripcionId);
    router.push({ path: '/mis-ventas', query: { registrada: d.inscripcionId } });
  } catch (e) {
    toast(e.message, 'error');
  } finally {
    enviando.value = false;
  }
}

// ---- borrador local: se guarda al vuelo y se recupera al volver ----
watch(form, () => guardarBorrador(auth.id, form), { deep: true });

/*
 * Indicador de "hay más abajo". En pantallas pequeñas el formulario no cabe entero y nada
 * delata que sigue: el vendedor rellena lo que ve y busca el botón de continuar.
 * Ahora es SOLO un indicador visual (no botón): el formulario es naturalmente scrolleable
 * con el dedo/ratón. El indicador aparece solo mientras quede contenido por debajo
 * y se oculta al llegar al final.
 */
const hayMasAbajo = ref(false);
function revisarDesplazamiento() {
  const d = document.documentElement;
  hayMasAbajo.value = d.scrollHeight - window.scrollY - d.clientHeight > 24;
}
// Cambiar de paso reinicia el alto de la página: hay que volver a medir DESPUÉS de pintar.
watch(paso, () => {
  window.scrollTo({ top: 0 });
  nextTick(revisarDesplazamiento);
});

onMounted(async () => {
  window.addEventListener('scroll', revisarDesplazamiento, { passive: true });
  window.addEventListener('resize', revisarDesplazamiento);

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
  nextTick(revisarDesplazamiento);
});

onUnmounted(() => {
  window.removeEventListener('scroll', revisarDesplazamiento);
  window.removeEventListener('resize', revisarDesplazamiento);
  // Las previsualizaciones son URLs de objeto: sin revocarlas se quedan en memoria.
  fotos.value.forEach((f) => f?.url && URL.revokeObjectURL(f.url));
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
      <router-link to="/mapa" class="btn btn-fantasma btn-sm">← Volver al mapa</router-link>
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

      <!-- Lo que falta, dicho con las mismas palabras que las etiquetas de abajo. Aparece
           solo tras pulsar "Siguiente": ir en rojo desde el primer segundo no informa. -->
      <div v-if="intentado && faltantes.size" class="faltan card" role="alert">
        <strong>Falta completar:</strong>
        <ul>
          <li v-for="[clave, etiqueta] in faltantes" :key="clave">{{ etiqueta }}</li>
        </ul>
      </div>

      <!-- Paso 1: entidad -->
      <section v-show="paso === 0" class="card bloque">
        <label class="campo">
          <span>Nombre de la entidad *</span>
          <input class="control mayus" :class="{ falta: falta('entidadNombre') }"
                 v-model="form.entidadNombre" placeholder="Ej. Artesanías Illimani" />
        </label>
        <label class="campo">
          <span>Tipo de entidad *</span>
          <select class="control" :class="{ falta: falta('tipoEntidadId') }" v-model="form.tipoEntidadId">
            <option :value="null" disabled>Elige una opción…</option>
            <option v-for="t in tiposEntidad" :key="t.id" :value="t.id">{{ t.nombre }}</option>
          </select>
        </label>
        <div class="dos">
          <label class="campo">
            <span>NIT</span>
            <input class="control" v-model="form.nit" inputmode="numeric" placeholder="Solo números" />
          </label>
          <label class="campo">
            <span>Rubro o descripción</span>
            <input class="control mayus" v-model="form.descripcion" placeholder="Qué vende o expone" />
          </label>
        </div>

        <div class="separador"></div>

        <!-- El dueño va con la ENTIDAD, no con quien atiende la caseta. Es a quien se llama
             por un cobro, y antes era opcional: se colaban ventas sin nadie a quien reclamar. -->
        <h3 class="sub">
          Responsable legal
          <span class="muted">— el dueño de la caseta</span>
        </h3>
        <label class="campo">
          <span>Nombre completo *</span>
          <input class="control mayus" :class="{ falta: falta('representanteLegal') }"
                 v-model="form.representanteLegal" placeholder="Ej. María Quispe Mamani" />
        </label>
        <div class="dos">
          <label class="campo">
            <span>C.I. *</span>
            <input class="control" :class="{ falta: falta('ciRepresentante') }"
                   v-model="form.ciRepresentante" inputmode="numeric" placeholder="Ej. 8765432" />
          </label>
          <label class="campo">
            <span>Celular *</span>
            <input class="control" :class="{ falta: falta('celularRepresentante') }"
                   v-model="form.celularRepresentante" type="tel" inputmode="tel" placeholder="Ej. 71234567" />
          </label>
        </div>

        <div class="separador"></div>

        <div class="dos">
          <label class="campo"><span>Desde</span><input class="control" type="date" v-model="form.fechaInicio" /></label>
          <label class="campo"><span>Hasta</span><input class="control" type="date" v-model="form.fechaFin" /></label>
        </div>
      </section>

      <!-- Paso 2: responsables -->
      <!-- Los dos piden exactamente los mismos datos, asi que van por el mismo bucle: un
           campo nuevo se agrega una vez y sale en los dos. -->
      <section v-show="paso === 1" class="card bloque">
        <p class="nota">
          Quién <strong>atiende</strong> la caseta durante la feria. Son los que reciben
          credencial, por eso se les puede tomar la foto aquí mismo.
        </p>

        <template v-for="(r, i) in form.responsables" :key="i">
          <div v-if="i > 0" class="separador"></div>
          <h3 class="sub">Responsable {{ i + 1 }}</h3>

          <label v-if="i === 0" class="fila-check">
            <input type="checkbox" v-model="form.copiaLegal" />
            Es el mismo responsable legal
          </label>

          <div class="dos">
            <label class="campo">
              <span>Nombre {{ i === 0 ? '*' : '' }}</span>
              <input class="control mayus" :class="{ falta: falta(`r${i}.nombre`) }"
                     :disabled="i === 0 && form.copiaLegal"
                     v-model="r.nombre" placeholder="Ej. María" />
            </label>
            <label class="campo">
              <span>C.I. {{ i === 0 ? '*' : '' }}</span>
              <input class="control" :class="{ falta: falta(`r${i}.ci`) }"
                     :disabled="i === 0 && form.copiaLegal"
                     v-model="r.ci" inputmode="numeric" placeholder="Ej. 8765432" />
            </label>
          </div>
          <div class="dos">
            <label class="campo">
              <span>Apellido paterno</span>
              <input class="control mayus" :disabled="i === 0 && form.copiaLegal"
                     v-model="r.paterno" placeholder="Ej. Quispe" />
            </label>
            <label class="campo">
              <span>Apellido materno</span>
              <input class="control mayus" :disabled="i === 0 && form.copiaLegal"
                     v-model="r.materno" placeholder="Ej. Mamani" />
            </label>
          </div>
          <label class="campo">
            <span>Celular</span>
            <input class="control" type="tel" inputmode="tel" :disabled="i === 0 && form.copiaLegal"
                   v-model="r.celular" placeholder="Ej. 71234567" />
          </label>

          <!-- Foto opcional. Es el unico momento en que la persona esta delante; si no se
               toma, la venta se registra igual y se completa desde "Mis ventas". -->
          <div class="foto">
            <img v-if="fotos[i]?.url" :src="fotos[i].url" alt="Foto del responsable" />
            <div v-else class="sinfoto">Sin foto</div>
            <div class="foto-acciones">
              <!-- Sin `capture`: forzar la cámara quita la galería en Android, y a veces la
                   foto ya existe. Mismo criterio que FotosResponsables y el comprobante. -->
              <input :id="`foto-${i}`" class="oculto" type="file" accept="image/*"
                     @change="elegirFoto(i, $event)" />
              <label :for="`foto-${i}`" class="btn btn-sm">
                📷 {{ fotos[i] ? 'Cambiar foto' : 'Tomar foto' }}
              </label>
              <button v-if="fotos[i]" class="btn btn-peligro btn-sm" @click="quitarFoto(i)">Quitar</button>
              <span class="muted opcional">Opcional</span>
            </div>
          </div>
        </template>

        <button v-if="hayResponsable2" class="btn btn-peligro" @click="quitarResponsable2">
          Quitar Responsable 2
        </button>
        <button v-else class="btn" @click="agregarResponsable2">＋ Agregar Responsable 2</button>
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
          <label class="campo">
            <span>Banco</span>
            <input class="control mayus" v-model="form.entidadBancaria" placeholder="Ej. Banco Unión" />
          </label>
          <label class="campo">
            <span>N.º de comprobante</span>
            <input class="control" type="number" inputmode="numeric" v-model.number="form.numComprobante" placeholder="Solo números" />
          </label>
        </div>
        <p v-if="!form.pagoContado" class="nota">
          La foto del comprobante se sube después, desde <strong>Mis pendientes</strong>.
          Registrar ahora asegura las casetas.
        </p>
      </section>

<!-- El aviso de "hay más abajo" viaja en la MISMA franja pegajosa que los botones,
             sobre fondo opaco: flotando suelto se posaba encima de un campo y parecía que
             lo tapaba. Ahora es SOLO un indicador visual (no botón clickeable): el formulario
             es naturalmente scrolleable con el dedo/ratón en cualquier dispositivo. -->
      <div class="pie">
        <div v-if="hayMasAbajo" class="mas-abajo" role="status" aria-live="polite">
          <span class="flecha">↓</span> Desliza para ver más
        </div>
        <div class="acciones">
          <button class="btn" :disabled="paso === 0 || enviando" @click="atras">Atrás</button>
          <button v-if="paso < 2" class="btn btn-primario" @click="siguiente">Siguiente</button>
          <button v-else class="btn btn-primario" :disabled="enviando" @click="registrar">
            {{ enviando ? 'Registrando…' : `Registrar venta (${total.toLocaleString('es-BO')} Bs)` }}
          </button>
        </div>
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

/* Lista de lo que falta. Con las MISMAS palabras que las etiquetas de los campos: si aquí
   dice "Celular del responsable legal", abajo hay un campo que se llama así. */
.faltan {
  padding: 0.8rem 1rem; border-color: color-mix(in srgb, var(--danger) 40%, var(--border));
  background: var(--danger-suave); color: var(--danger); font-size: 0.9rem;
}
.faltan ul { margin: 0.3rem 0 0; padding-left: 1.1rem; }
.faltan li { margin: 0.1rem 0; }

.bloque { padding: 1.1rem; display: flex; flex-direction: column; gap: 0.85rem; }
.sub { margin: 0; font-size: 0.95rem; font-weight: 700; }
.dos { display: grid; gap: 0.85rem; grid-template-columns: 1fr 1fr; }
.separador { height: 1px; background: var(--border); }
.fila-check { display: flex; align-items: center; gap: 0.5rem; font-size: 0.92rem; }
.nota { margin: 0; font-size: 0.83rem; color: var(--muted); line-height: 1.45; }

/* Campo obligatorio sin rellenar. Borde grueso y fondo tenue, no solo el color del texto:
   sobre un formulario largo el color solo no se encuentra de un vistazo. */
.control.falta {
  border-color: var(--danger); border-width: 2px;
  background: var(--danger-suave);
}
.control:disabled { opacity: 0.65; cursor: not-allowed; }

/* Los nombres se ven en mayúsculas mientras se teclean; el valor se normaliza al enviar.
   El placeholder se queda como está: en mayúsculas parecería un valor ya escrito. */
.mayus { text-transform: uppercase; }
.mayus::placeholder { text-transform: none; }

/* ---- foto del responsable ---- */
.foto { display: flex; align-items: center; gap: 0.8rem; }
.foto img, .foto .sinfoto {
  width: 72px; height: 72px; border-radius: var(--radio-sm); flex: none;
  border: 1px solid var(--border); object-fit: cover;
}
.foto .sinfoto {
  display: grid; place-items: center; background: var(--panel-2);
  color: var(--muted); font-size: 0.72rem; text-align: center;
}
.foto-acciones { display: flex; align-items: center; gap: 0.4rem; flex-wrap: wrap; }
.foto-acciones .opcional { font-size: 0.78rem; }
.oculto { display: none; }

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

/* Franja inferior: aviso de "hay más" + botones, juntos y sobre fondo opaco. */
.pie { display: flex; flex-direction: column; align-items: stretch; gap: 0.5rem; }

/* El aviso de "hay más abajo" es SOLO un indicador visual (no botón clickeable).
   El formulario es naturalmente scrolleable con el dedo/ratón en cualquier dispositivo.
   Usa el color de acento y la flecha se mueve para llamar la atención. */
.mas-abajo {
  align-self: center; font: inherit;
  display: inline-flex; align-items: center; gap: 0.45rem;
  border: 1px solid color-mix(in srgb, var(--acento) 45%, transparent);
  background: color-mix(in srgb, var(--acento) 12%, var(--panel));
  color: var(--acento); font-weight: 700; font-size: 0.9rem;
  border-radius: 999px; padding: 0.5rem 1.1rem; box-shadow: var(--sombra);
  pointer-events: none; /* No es clickeable, solo indicador */
}
.mas-abajo .flecha { display: inline-block; animation: rebote 1.4s ease-in-out infinite; }
@keyframes rebote {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(3px); }
}
@media (prefers-reduced-motion: reduce) {
  .mas-abajo .flecha { animation: none; }
}

/* En móvil el formulario se vuelve de una columna y los botones ocupan el ancho:
   es la misma pantalla que va en el APK y ahí se usa con una mano. */
@media (max-width: 560px) {
  .dos { grid-template-columns: 1fr; }
  .pie { position: sticky; bottom: var(--tabbar-h); background: var(--bg); padding: 0.6rem 0 1rem; z-index: 6; }
  .acciones { padding-bottom: 0; }
  .acciones .btn { flex: 1; }

  /* Campos y textos más grandes: es la pantalla que más se teclea, y en un teléfono
     chico los tamaños de escritorio obligan a apuntar. Los botones de acción crecen a
     52px de alto, bastante por encima del mínimo táctil, porque se pulsan de pie y
     delante del cliente. */
  .venta { gap: 1.1rem; }
  .bloque { padding: 1.15rem 1rem 1.3rem; gap: 1.1rem; }
  .sub { font-size: 1.1rem; }
  .pasos li { font-size: 0.95rem; }
  .pasos .num { width: 1.75rem; height: 1.75rem; font-size: 0.85rem; }
  .resumen { padding: 0.9rem 1rem; font-size: 1rem; }
  .resumen .total { font-size: 1.15rem; }
  .casetas li { font-size: 1rem; padding: 0.7rem 0; gap: 0.7rem; }
  .fila-check { font-size: 1.02rem; gap: 0.7rem; }
  /* Casilla grande: con la de por defecto (13px) hay que apuntar con la uña. */
  .fila-check input[type='checkbox'] { width: 24px; height: 24px; }
  .nota { font-size: 0.95rem; }
  .faltan { font-size: 1rem; }
  .acciones .btn { min-height: 52px; font-size: 1.05rem; }
  .bloque > .btn { min-height: 50px; font-size: 1rem; }
  .mas-abajo { font-size: 1rem; padding: 0.6rem 1.2rem; }
  .foto img, .foto .sinfoto { width: 88px; height: 88px; }
  .foto-acciones .btn { min-height: 44px; }
}
</style>
