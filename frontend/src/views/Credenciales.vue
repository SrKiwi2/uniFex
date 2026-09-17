<script setup>
import { ref, computed, onMounted } from 'vue';
import { useAuthStore } from '../stores/auth';
import { apiFetch } from '../api';
import { descargarPdf, descargarCredencialVirtual } from '../ui/descargas';
import { mostrarCarga, ocultarCarga } from '../ui/cargando';
import { toast } from '../ui/toast';
import { alerta } from '../ui/alerta';
import { asegurarCategorias, categorias } from '../ui/catalogoCategorias';
import UiModal from '../components/UiModal.vue';

/*
 * Acreditacion: preparar y emitir las credenciales de la feria.
 *
 * Son ~800 y el trabajo real no es imprimirlas, es AVERIGUAR CUALES YA SE PUEDEN imprimir y
 * COMPLETAR las que no. Por eso la pantalla se organiza alrededor de lo que falta, y deja
 * adjuntar el comprobante o la foto sin salir de aqui: el expositor suele estar delante.
 *
 * Dos reglas que conviene tener presentes al leer esto:
 *
 *   - el COMPROBANTE hace falta siempre que la venta CUESTE algo, tambien en las ventas "al
 *     contado" —marcar contado dice como se pago, no que exista el recibo—. La excepcion son
 *     las ventas de 0 Bs (categorias y casetas cedidas): ahi no hay ningun recibo que
 *     adjuntar, y exigirlo dejaba esas credenciales bloqueadas para siempre;
 *   - la FOTO solo la exige la plantilla con etiquetas, que es la que imprime los datos de la
 *     persona. La de QR grande no lleva ni nombre ni C.I., asi que no la pide.
 *
 * Por eso "listo" depende de la plantilla elegida, y el servidor manda el resultado para las
 * dos: la regla vive alli, no duplicada aqui.
 *
 * Se puede imprimir algo incompleto a proposito —a veces el papel hace falta ya y el
 * comprobante llega despues— pero queda registrado quien lo hizo y que faltaba.
 *
 * Todo el filtrado y la seleccion pasan en memoria: la lista se baja UNA vez.
 */

/*
 * El vendedor tambien acredita, pero solo a LO SUYO: el servidor le manda unicamente las
 * credenciales de las ventas que el registro. Aqui solo se usa para que los textos no mientan
 * —"no hay inscripciones" no es lo mismo que "no has registrado ninguna venta"— y para
 * decirlo en pantalla, que ahorra el "¿por que no me sale fulano?".
 */
const auth = useAuthStore();
const soloMias = computed(() => auth.esVendedor);

/*
 * El vendedor solo ve la CREDENCIAL VIRTUAL.
 *
 * Es la que se entrega de verdad: se manda al telefono del expositor y se enseña en la puerta.
 * Las otras dos son papel para casos concretos —tandas impresas, acreditacion en mesa— y las
 * maneja administracion. Enseñarle al vendedor tres plantillas, un selector de tamaño y una
 * vista previa sobre hoja carta es ofrecerle decisiones que no le tocan, justo cuando tiene al
 * expositor delante.
 */
const soloVirtual = computed(() => auth.esVendedor);
const ID_VIRTUAL = 'CREDENCIAL_VIRTUAL';

/** Las credenciales de esta inscripcion que YA se pueden emitir en virtual. */
const virtualesListas = (c) => grupoDe(c).filter((x) => x.listo?.[ID_VIRTUAL]);

/**
 * Lo que de verdad le falta a un grupo, en palabras.
 *
 * Antes el aviso decia siempre "hace falta el comprobante y la foto". A una venta de 0 Bs no
 * le hace falta ningun comprobante, asi que ese texto mandaba a buscar un papel inexistente y
 * escondia lo unico que si faltaba: la foto.
 */
function loQueFalta(lista) {
  const faltas = new Set();
  for (const c of lista) for (const f of c.faltantes?.[ID_VIRTUAL] || []) faltas.add(f);
  if (!faltas.size) return 'falta algo';
  return [...faltas].join(' y ');
}

/**
 * Descarga la credencial virtual de un responsable, o de toda su entidad.
 *
 * Una a una y no en un archivo unico: cada credencial es de UNA persona y se le manda a esa
 * persona por separado. Un paquete con las tres obligaria a quien lo recibe a repartirlas.
 */
async function bajarVirtual(lista) {
  if (generando.value) return;
  const aptas = lista.filter((x) => x.listo?.[ID_VIRTUAL]);
  if (!aptas.length) {
    alerta(`Todavía no se puede emitir: ${loQueFalta(lista)}.`, 'advertencia');
    return;
  }
  generando.value = true;
  mostrarCarga(aptas.length === 1 ? 'Generando la credencial…'
                                  : `Generando ${aptas.length} credenciales…`);
  try {
    // A la carpeta de su entidad, la misma en la que cayeron el recibo y las demas: asi lo
    // que se descarga hoy se junta con lo que se descargo el dia de la venta.
    for (const c of aptas) {
      await descargarCredencialVirtual(c.responsableId, c.nombre, c.entidad);
    }
    await cargar();
  } finally {
    ocultarCarga();
    generando.value = false;
  }
}

const reenvio = ref(null);
const incluirRecibo = ref(false);

function reenviarWhatsApp(lista) {
  if (generando.value) return;
  const aptas = lista.filter((x) => x.listo?.[ID_VIRTUAL]);
  if (!aptas.length) {
    alerta(`Todavía no se puede reenviar: ${loQueFalta(lista)}.`, 'advertencia');
    return;
  }
  incluirRecibo.value = false;
  reenvio.value = aptas;
}

async function confirmarReenvio() {
  if (generando.value || !reenvio.value) return;
  const aptas = reenvio.value;
  generando.value = true;
  mostrarCarga(aptas.length === 1 ? 'Reenviando por WhatsApp…'
                                  : `Reenviando ${aptas.length} credenciales por WhatsApp…`);
  try {
    const r = await apiFetch('/api/app/credenciales/whatsapp', {
      method: 'POST',
      body: JSON.stringify({
        inscripcionId: aptas[0].inscripcionId,
        responsables: aptas.map((c) => c.responsableId),
        incluirRecibo: incluirRecibo.value,
      }),
    });
    const d = await r.json().catch(() => ({}));
    if (!r.ok || d.ok === false) throw new Error(d.mensaje || 'No se pudo reenviar por WhatsApp');
    toast(d.mensaje || 'Reenviado por WhatsApp', 'ok');
    reenvio.value = null;
  } catch (e) {
    alerta(e.message, 'error', 0);
  } finally {
    ocultarCarga();
    generando.value = false;
  }
}

const cargando = ref(true);
const credenciales = ref([]);
const busqueda = ref('');
const filtro = ref('listas');        // listas | pendientes | todas
const categoriaFiltro = ref(null);   // null = todas, o id de la categoria
const seleccion = ref(new Set());
const generando = ref(false);
/** Fotos que la base dice tener pero que no estan en disco. Ver CredencialPublica.vue. */
const rotas = ref(new Set());
/** Fila sobre la que se esta subiendo un archivo, para deshabilitar sus botones. */
const subiendo = ref(null);
const historial = ref(null);   // { credencial, filas[] } del panel de impresiones

// ---- ajustes de impresion ----
const plantilla = ref('CON_ETIQUETAS');
const anchoCm = ref(10);

/*
 * El catalogo de plantillas lo manda el SERVIDOR, no esta escrito aqui.
 *
 * Es la misma lista que usa el generador del PDF. Mientras estuvo duplicada, una plantilla
 * añadida solo aqui salia en los botones pero llegaba al servidor sin existir, y una añadida
 * solo alli no habia manera de elegirla. Cada entrada trae ademas lo que la pantalla no puede
 * adivinar: si esa plantilla exige la foto, y su proporcion (alto/ancho) medida sobre la imagen
 * de verdad, que es lo que dibuja la vista previa sobre la hoja.
 */
const plantillas = ref([]);
const plantillasRotas = ref(false);

const plantillaActual = computed(() =>
  plantillas.value.find((p) => p.id === plantilla.value) || null);

/** ¿La plantilla elegida imprime datos de la persona? Mientras carga se asume que si. */
const pideFoto = computed(() => plantillaActual.value?.requiereFoto ?? true);

/** Alto impreso, derivado del ancho y de la proporcion de LA PLANTILLA elegida. */
const altoCm = computed(() =>
  anchoCm.value * (plantillaActual.value?.proporcion || 1534 / 1182));

const nombreDePlantilla = (id) =>
  plantillas.value.find((p) => p.id === id)?.etiqueta || id;

async function cargarPlantillas() {
  try {
    const r = await apiFetch('/api/app/credenciales/plantillas');
    if (!r.ok) throw new Error('No se pudo cargar la lista de plantillas');
    plantillas.value = await r.json();
    // Si la que estaba elegida ya no existe, se cae en la primera antes de que alguien
    // imprima contra un id que el servidor va a rechazar.
    if (plantillas.value.length && !plantillas.value.some((p) => p.id === plantilla.value)) {
      plantilla.value = plantillas.value[0].id;
    }
  } catch (e) {
    plantillasRotas.value = true;
    toast(e.message, 'error');
  }
}

async function cargar() {
  cargando.value = true;
  try {
    const r = await apiFetch('/api/app/credenciales');
    if (!r.ok) throw new Error('No se pudo cargar la lista');
    credenciales.value = await r.json();
  } catch (e) {
    toast(e.message, 'error');
  } finally {
    cargando.value = false;
  }
}

/** "Listo" depende de la plantilla elegida: la de QR grande no pide foto. */
const esListo = (c) => Boolean(c.listo?.[plantilla.value]);
const faltaDe = (c) => c.faltantes?.[plantilla.value] || [];

const listas = computed(() => credenciales.value.filter(esListo));
const pendientes = computed(() => credenciales.value.filter((c) => !esListo(c)));

const visibles = computed(() => {
  const q = busqueda.value.trim().toLowerCase();
  const base = filtro.value === 'listas' ? listas.value
    : filtro.value === 'pendientes' ? pendientes.value
    : credenciales.value;
  let filtradas = base;
  if (categoriaFiltro.value) {
    filtradas = filtradas.filter((c) => c.categoriaId === categoriaFiltro.value);
  }
  if (!q) return filtradas;
  return filtradas.filter((c) =>
    `${c.nombre} ${c.entidad} ${c.ci} ${c.categoria} ${c.casetas}`.toLowerCase().includes(q));
});

/**
 * Las credenciales visibles, AGRUPADAS POR VENTA.
 *
 * Una credencial es de una persona, pero el trabajo se organiza por entidad: quien llega al
 * mostrador es un expositor con sus dos o tres responsables, y se lleva las de todos de una
 * vez. Con la lista plana habia que buscar a mano cuales de las 800 filas eran de la misma
 * empresa, y eso con nombres de personas que no se parecen entre si.
 *
 * Se agrupa lo VISIBLE, no todo: asi el filtro y la busqueda siguen mandando, y una entidad
 * aparece mientras alguna de sus credenciales encaje.
 */
const porEntidad = computed(() => {
  const m = new Map();
  for (const c of visibles.value) {
    if (!m.has(c.inscripcionId)) {
      m.set(c.inscripcionId, {
        inscripcionId: c.inscripcionId,
        entidad: c.entidad,
        rubro: c.rubro,
        categoria: c.categoria,
        casetas: c.casetas,
        responsables: [],
      });
    }
    m.get(c.inscripcionId).responsables.push(c);
  }
  for (const g of m.values()) {
    // El titular primero: es quien firma y por quien se pregunta.
    g.responsables.sort((a, b) => (b.esTitular ? 1 : 0) - (a.esTitular ? 1 : 0));
    g.listasVirtual = g.responsables.filter((x) => x.listo?.[ID_VIRTUAL]).length;
    g.seleccionadasVirtual = g.responsables.filter((c) =>
      seleccion.value.has(c.responsableId) && c.listo?.[ID_VIRTUAL]);
  }
  return [...m.values()];
});

/** Cuantas de las visibles se pueden imprimir: es lo que dice si el boton hace algo. */
const seleccionadasAptas = computed(() =>
  credenciales.value.filter((c) => seleccion.value.has(c.responsableId) && esListo(c)));

function alternar(c) {
  const s = new Set(seleccion.value);
  s.has(c.responsableId) ? s.delete(c.responsableId) : s.add(c.responsableId);
  seleccion.value = s;
}

function marcarVisibles(marcar) {
  const s = new Set(seleccion.value);
  // Solo se marcan las que se pueden imprimir: marcar una sin foto solo lleva a un error luego.
  for (const c of visibles.value) {
    if (!esListo(c)) continue;
    marcar ? s.add(c.responsableId) : s.delete(c.responsableId);
  }
  seleccion.value = s;
}

/**
 * Lanza la impresion. `ids` vacio = todas las listas de la edicion activa.
 * `forzar` imprime aunque falte algo; el servidor lo registra con lo que faltaba.
 *
 * `plantillaId` se puede fijar para tener un boton que SIEMPRE imprime con una plantilla
 * concreta, sin depender de la que este elegida arriba.
 */
async function imprimir(ids, etiqueta, forzar = false, plantillaId = plantilla.value) {
  if (generando.value) return;
  generando.value = true;
  try {
    const nombre = ids.length === 1 ? 'credencial.pdf' : `credenciales-${etiqueta}.pdf`;
    const r = await descargarPdf('/api/app/credenciales/pdf', nombre, {
      method: 'POST',
      body: JSON.stringify({
        responsables: ids,
        plantilla: plantillaId,
        anchoCm: Number(anchoCm.value),
        forzar,
      }),
    });
    toast(r?.destino === 'telefono'
      ? 'Guardado en Documentos del teléfono'
      : 'Credenciales generadas', 'ok');
    // La lista lleva el contador de impresiones: hay que refrescarla para que se vea.
    await cargar();
  } catch (e) {
    alerta(e.message, 'error', 0);   // 0 = no se cierra solo: hay que leerlo
  } finally {
    generando.value = false;
  }
}

/** Imprimir una incompleta. Se avisa de lo que falta ANTES, y queda registrado. */
async function imprimirIgual(c) {
  const falta = faltaDe(c).join(' y ');
  if (!confirm(
    `A esta credencial le falta ${falta}.\n\n`
    + 'Se puede imprimir igual, pero quedará registrado que la imprimiste incompleta '
    + 'y la inscripción seguirá pendiente de completar.\n\n¿Imprimir de todas formas?')) return;
  await imprimir([c.responsableId], 1, true);
}

/*
 * Adjuntar lo que falta sin salir de aqui.
 *
 * El comprobante y la foto llegan muchas veces en el mostrador de acreditacion, con el
 * expositor delante. Mandarlo de vuelta a su vendedor para que lo suba desde otra pantalla
 * detendria la cola por algo que se resuelve en diez segundos.
 */
async function subir(c, tipo, evento) {
  const archivo = evento.target.files?.[0];
  evento.target.value = '';
  if (!archivo || subiendo.value) return;

  subiendo.value = c.responsableId;
  try {
    const datos = new FormData();
    datos.append('archivo', archivo);
    const ruta = tipo === 'comprobante'
      ? `/api/app/inscripciones/${c.inscripcionId}/comprobante`
      : `/api/app/inscripciones/${c.inscripcionId}/responsables/${c.responsableId}/foto`;
    const r = await apiFetch(ruta, { method: 'POST', body: datos });
    const d = await r.json().catch(() => ({}));
    if (!r.ok || d.ok === false) throw new Error(d.mensaje || 'No se pudo subir');
    toast(tipo === 'comprobante' ? 'Comprobante adjuntado' : 'Foto adjuntada', 'ok');
    await cargar();
  } catch (e) {
    alerta(e.message, 'error', 0);
  } finally {
    subiendo.value = null;
  }
}

/** Quien imprimio esta credencial y que faltaba entonces. */
async function verHistorial(c) {
  try {
    const r = await apiFetch(`/api/app/credenciales/${c.responsableId}/impresiones`);
    historial.value = { credencial: c, filas: r.ok ? await r.json() : [] };
  } catch (e) {
    toast(e.message, 'error');
  }
}

/**
 * Las credenciales que salen de la MISMA inscripcion.
 *
 * Una credencial es de una persona, no de una venta: una entidad con tres responsables son
 * tres credenciales. Se agrupa en memoria porque la lista ya trae `inscripcionId` — pedirle
 * al servidor "los responsables de la inscripcion N" seria un viaje para algo que ya tenemos.
 */
function grupoDe(c) {
  return credenciales.value.filter((x) => x.inscripcionId === c.inscripcionId);
}

/**
 * Imprime la inscripcion entera: quien esta en el mostrador se lleva las de toda su entidad
 * en un PDF, no una por una.
 *
 * Se filtra por `listo` DE ESTA PLANTILLA, que es lo que el servidor va a exigir; y si no
 * queda ninguna se avisa en vez de mandar la lista vacia, que para el servidor significa
 * "todas las de la feria".
 */
function imprimirInscripcion(c, plantillaId = plantilla.value) {
  const aptas = grupoDe(c).filter((x) => x.listo?.[plantillaId]);
  if (!aptas.length) {
    alerta(`Ninguna credencial de ${c.entidad} está lista para esta plantilla.`, 'advertencia');
    return;
  }
  imprimir(aptas.map((x) => x.responsableId), `inscripcion-${aptas.length}`, false, plantillaId);
}

function imprimirTodasListas() {
  if (!listas.value.length) {
    alerta('Ninguna credencial está lista todavía para esta plantilla. Hace falta el '
      + 'comprobante de pago' + (pideFoto.value ? ' y la foto del responsable.' : '.'),
      'advertencia');
    return;
  }
  imprimir([], `todas-${listas.value.length}`);
}

onMounted(() => {
  cargarPlantillas();
  asegurarCategorias();
  cargar();
});
</script>

<template>
  <div class="credenciales">
    <!-- Resumen arriba: lo primero que se quiere saber es cuantas se pueden imprimir YA. -->
    <header class="resumen">
      <button class="tarjeta" :class="{ activa: filtro === 'listas' }" @click="filtro = 'listas'">
        <strong>{{ listas.length }}</strong>
        <span>listas para imprimir</span>
      </button>
      <button class="tarjeta" :class="{ activa: filtro === 'pendientes' }" @click="filtro = 'pendientes'">
        <strong class="pend">{{ pendientes.length }}</strong>
        <span>les falta algo</span>
      </button>
      <button class="tarjeta" :class="{ activa: filtro === 'todas' }" @click="filtro = 'todas'">
        <strong class="muted">{{ credenciales.length }}</strong>
        <span>en total</span>
      </button>
    </header>

    <!-- Ajustes de impresion. Con una vista previa de como queda en la hoja, porque "10 cm"
         no le dice nada a nadie hasta que lo ve sobre una carta. -->
    <p v-if="soloMias" class="alcance">
      Aquí salen las credenciales de <strong>tus ventas</strong>. Puedes adjuntar el comprobante
      y las fotos de tus responsables, y descargar su <strong>credencial virtual</strong>: una
      imagen para el teléfono, con el QR que se escanea en la puerta. Se la mandas al expositor
      y listo, no hay que imprimir nada.
    </p>

    <section v-if="!soloVirtual" class="ajustes card">
      <div class="grupo">
        <span class="rotulo">Plantilla</span>
        <div class="opciones">
          <button v-for="p in plantillas" :key="p.id" class="opcion"
                  :class="{ elegida: plantilla === p.id }" @click="plantilla = p.id">
            <strong>{{ p.etiqueta }}</strong>
            <small>{{ p.detalle }}</small>
          </button>
          <!-- Sin catalogo no hay donde elegir, y una fila de botones vacia no explica nada. -->
          <p v-if="!plantillas.length" class="nota">
            {{ plantillasRotas ? 'No se pudieron cargar las plantillas.' : 'Cargando plantillas…' }}
          </p>
        </div>
      </div>

      <div class="grupo">
        <span class="rotulo">Tamaño impreso</span>
        <div class="medida">
          <input type="range" min="6" max="16" step="0.5" v-model.number="anchoCm" />
          <span class="valor">{{ anchoCm }} × {{ altoCm.toFixed(1) }} cm</span>
        </div>
        <p class="nota">Una credencial por hoja carta, centrada.</p>
      </div>

      <div class="grupo previa" aria-hidden="true">
        <span class="rotulo">En la hoja</span>
        <!-- La hoja carta mide 21.6 × 27.9 cm; la credencial se dibuja a esa misma escala. -->
        <div class="hoja">
          <div class="cred" :style="{
            width: (anchoCm / 21.6 * 100) + '%',
            height: (altoCm / 27.9 * 100) + '%',
          }"></div>
        </div>
      </div>
    </section>

    <div class="barra">
      <input v-model="busqueda" class="control" placeholder="Buscar por nombre, entidad, C.I. o caseta…" />
      <select v-model="categoriaFiltro" class="control" style="min-width: 180px;" :disabled="!categorias?.length">
        <option :value="null">Todas las categorías</option>
        <option v-for="c in categorias" :key="c.id" :value="c.id">{{ c.nombre }}</option>
        <option v-if="!categorias?.length" disabled>Cargando…</option>
      </select>
      <button class="btn btn-fantasma" @click="categoriaFiltro = null" :disabled="!categoriaFiltro">Quitar filtro</button>
      <button class="btn btn-fantasma" @click="marcarVisibles(true)">Marcar visibles</button>
      <button class="btn btn-fantasma" :disabled="!seleccion.size" @click="seleccion = new Set()">
        Quitar marcas
      </button>
      <span class="crecer"></span>
      <button class="btn" :disabled="generando || !seleccionadasAptas.length"
              @click="imprimir(seleccionadasAptas.map((c) => c.responsableId), seleccionadasAptas.length)">
        Imprimir {{ seleccionadasAptas.length || '' }} marcada{{ seleccionadasAptas.length === 1 ? '' : 's' }}
      </button>
      <!-- Con cero listas el boton no se esconde: sigue siendo el sitio donde se mira cuantas
           hay, y esconderlo dejaria la barra sin explicacion. Solo se desactiva. -->
      <button v-if="soloVirtual" class="btn btn-primario" :disabled="generando || !listas.length"
              @click="bajarVirtual(listas)">
        {{ generando ? 'Generando…'
           : !listas.length ? 'Ninguna lista todavía'
           : `📱 Descargar ${listas.length} credencial${listas.length === 1 ? '' : 'es'}` }}
      </button>
      <button v-else class="btn btn-primario" :disabled="generando || !listas.length"
              @click="imprimirTodasListas">
        {{ generando ? 'Generando…'
           : !listas.length ? 'Ninguna lista todavía'
           : listas.length === 1 ? 'Imprimir la única lista'
           : `Imprimir las ${listas.length} listas` }}
      </button>
    </div>

    <div v-if="cargando" class="vacio">Cargando credenciales…</div>
    <!-- Vacio por buscar no es lo mismo que vacio de verdad, y "lista" depende de la plantilla:
         decir "hace falta la foto" con el QR grande elegido manda a buscar algo que no se pide. -->
    <div v-else-if="!visibles.length" class="vacio">
      <template v-if="busqueda.trim()">No hay resultados para esa búsqueda.</template>
      <template v-else-if="filtro === 'listas'">
        Todavía no hay ninguna credencial lista para esta plantilla. Hace falta el comprobante
        de pago<template v-if="pideFoto"> y la foto del responsable</template>.
      </template>
      <template v-else-if="filtro === 'pendientes'">
        No falta nada: todas las credenciales están listas para esta plantilla.
      </template>
      <template v-else-if="soloMias">
        Todavía no has registrado ninguna venta en esta edición.
      </template>
      <template v-else>Todavía no hay inscripciones en esta edición.</template>
    </div>

    <!-- Agrupado por VENTA: la cabecera es la entidad con su caseta y su categoria, y dentro
         van sus responsables. Es como llega el trabajo al mostrador — un expositor con los
         suyos— y no una lista de 800 personas sueltas entre las que hay que buscar. -->
    <ul v-else class="entidades">
      <li v-for="g in porEntidad" :key="g.inscripcionId" class="entidad card">
        <header class="cab-ent">
          <div class="quien-ent">
            <strong class="nom-ent">{{ g.entidad }}</strong>
            <span class="muted">
              {{ g.categoria || 'sin categoría' }} ·
              caseta{{ (g.casetas || '').includes(',') ? 's' : '' }} {{ g.casetas || '—' }}
              <template v-if="g.rubro"> · {{ g.rubro }}</template>
            </span>
          </div>
          <div class="acc-ent">
            <span class="cuenta-ent">
              {{ g.responsables.length }} responsable{{ g.responsables.length === 1 ? '' : 's' }}
            </span>
            <!-- El boton de la entidad entera solo tiene sentido con mas de uno: con uno solo
                 haria lo mismo que el de la fila de abajo. -->
            <button v-if="g.responsables.length > 1" class="btn btn-primario btn-sm"
                    :disabled="generando || !g.listasVirtual"
                    :title="g.listasVirtual
                      ? `Descargar las ${g.listasVirtual} credenciales de ${g.entidad}`
                      : 'Ninguna está lista todavía'"
                    @click="bajarVirtual(g.responsables)">
              📱 Las {{ g.listasVirtual }}
            </button>
            <button v-if="g.responsables.length > 1" class="btn btn-sm"
                    :disabled="generando || !g.seleccionadasVirtual.length"
                    :title="g.seleccionadasVirtual.length
                      ? `Reenviar por WhatsApp las ${g.seleccionadasVirtual.length} credenciales seleccionadas de ${g.entidad}`
                      : 'Marca las credenciales listas que quieras reenviar de esta empresa'"
                    @click="reenviarWhatsApp(g.seleccionadasVirtual)">
              Reenviar seleccionados
            </button>
          </div>
        </header>

        <ul class="lista">
          <li v-for="c in g.responsables" :key="c.responsableId" class="fila"
              :class="{ marcada: seleccion.has(c.responsableId), bloqueada: !esListo(c) }">
            <label class="marca">
              <input type="checkbox" :disabled="!esListo(c)"
                     :checked="seleccion.has(c.responsableId)" @change="alternar(c)" />
            </label>

            <img v-if="c.fotoUrl && !rotas.has(c.responsableId)" :src="c.fotoUrl" :alt="c.nombre"
                 class="foto" @error="rotas = new Set(rotas).add(c.responsableId)" />
            <div v-else class="foto sinfoto" aria-hidden="true">?</div>

            <div class="quien">
              <strong>{{ c.nombre }}</strong>
              <!-- El distintivo del que se agrego de mas y pago. Sin el, una credencial de
                   pago se mezcla con las de derecho y no hay forma de saber cual hubo que
                   cobrar. El detalle del cobro vive en "Responsables extra". -->
              <span v-if="c.esExtra" class="badge badge-extra"
                    :title="c.comprobanteExtraUrl
                      ? `Responsable adicional. Se cobraron ${c.montoExtra} Bs y tiene comprobante.`
                      : 'Responsable adicional: se le cobró, pero NO tiene comprobante adjunto.'">
                extra{{ c.montoExtra ? ` · ${c.montoExtra} Bs` : '' }}
              </span>
              <span class="muted">
                C.I. {{ c.ci || '—' }}
                <template v-if="c.esTitular"> · titular</template>
              </span>
            </div>

            <div class="estado">
              <span v-if="esListo(c)" class="badge badge-ok">Lista</span>
              <!-- Por qué a ésta no se le pide comprobante. Sin decirlo, una credencial sin
                   recibo marcada «Lista» junto a otra bloqueada por lo mismo parece un fallo,
                   y la razón —que la caseta va a 0 Bs— no se ve desde la lista. -->
              <span v-if="c.sinCosto" class="badge badge-gratis"
                    title="Esta venta no tiene costo, así que no hay comprobante que adjuntar.">
                sin costo
              </span>
              <span v-for="f in faltaDe(c)" :key="f" class="badge badge-danger">{{ f }}</span>
              <!-- Ya entregada: importa saberlo antes de volver a generarla, y sobre todo si
                   se entrego cuando aun faltaba algo. -->
              <button v-if="c.impresa" class="badge"
                      :class="c.impresaIncompleta ? 'badge-aviso' : 'badge-muted'"
                      :title="`Entregada ${c.vecesImpresa} vez(ces). Toca para ver quién y cuándo.`"
                      @click="verHistorial(c)">
                {{ c.impresaIncompleta ? '⚠ entregada incompleta' : '✓ entregada' }}
                <template v-if="c.vecesImpresa > 1"> ×{{ c.vecesImpresa }}</template>
              </button>
            </div>

            <!-- Completar lo que falta sin salir de la pantalla. -->
            <div class="acciones">
              <!-- El botón de comprobante NO sale cuando la venta no cuesta nada: no existe
                   ningún recibo que adjuntar, y ofrecerlo mandaba a buscar un papel que nadie
                   emitió. `requiereComprobante` lo decide el servidor, donde vive la regla. -->
              <template v-if="!c.conComprobante && c.requiereComprobante !== false">
                <input :id="`comp-${c.responsableId}`" class="oculto" type="file"
                       accept="image/*,application/pdf" @change="subir(c, 'comprobante', $event)" />
                <label :for="`comp-${c.responsableId}`" class="btn btn-sm"
                       :class="{ inerte: subiendo === c.responsableId }"
                       title="Adjuntar el comprobante de pago de esta venta">🧾 Comprobante</label>
              </template>
              <template v-if="!c.conFoto">
                <input :id="`foto-${c.responsableId}`" class="oculto" type="file" accept="image/*"
                       @change="subir(c, 'foto', $event)" />
                <label :for="`foto-${c.responsableId}`" class="btn btn-sm"
                       :class="{ inerte: subiendo === c.responsableId }"
                       :title="`Adjuntar la foto de ${c.nombre}`">📷 Foto</label>
              </template>

              <button class="btn btn-primario btn-sm"
                      :disabled="generando || !c.listo?.[ID_VIRTUAL]"
                      :title="c.listo?.[ID_VIRTUAL]
                        ? `Descargar la credencial virtual de ${c.nombre}`
                        : 'Falta el comprobante o la foto'"
                      @click="bajarVirtual([c])">📱 Credencial</button>

              <button class="btn btn-sm"
                      :disabled="generando || !c.listo?.[ID_VIRTUAL]"
                      :title="c.listo?.[ID_VIRTUAL]
                        ? `Reenviar por WhatsApp la credencial de ${c.nombre}`
                        : 'Falta el comprobante o la foto'"
                      @click="reenviarWhatsApp([c])">WhatsApp</button>

              <!-- El par v-if / v-else tiene que quedar PEGADO: entre medias, Vue se queda sin
                   el v-if al que engancharse y pinta los dos botones en la misma fila. -->
              <template v-if="!soloVirtual">
                <button v-if="esListo(c)" class="btn btn-fantasma btn-sm" :disabled="generando"
                        title="Imprimir en papel solo esta"
                        @click="imprimir([c.responsableId], 1)">🖨</button>
                <button v-else class="btn btn-fantasma btn-sm" :disabled="generando"
                        :title="`Imprimir igual, aunque le falte ${faltaDe(c).join(' y ')}`"
                        @click="imprimirIgual(c)">🖨 igual</button>
              </template>
            </div>
          </li>
        </ul>
      </li>
    </ul>

    <UiModal v-if="reenvio" titulo="Reenviar credenciales por WhatsApp" @cerrar="!generando && (reenvio = null)">
      <p>Se reenviarán {{ reenvio.length }} credencial(es) de <strong>{{ reenvio[0].entidad }}</strong> al celular del cliente registrado en la venta.</p>
      <p v-if="reenvio.some(c => c.esExtra)">Las credenciales extra seleccionadas incluirán automáticamente su propio recibo de compra.</p>
      <label class="fila">
        <input v-model="incluirRecibo" type="checkbox" :disabled="generando" />
        Incluir recibo de venta
      </label>
      <template #pie>
        <button class="btn" :disabled="generando" @click="reenvio = null">Cancelar</button>
        <button class="btn btn-primario" :disabled="generando" @click="confirmarReenvio">{{ generando ? 'Enviando…' : 'Reenviar' }}</button>
      </template>
    </UiModal>

    <!-- Historial de impresiones de una credencial -->
    <div v-if="historial" class="velo" @click.self="historial = null">
      <div class="panel card">
        <header>
          <h3>Impresiones de {{ historial.credencial.nombre }}</h3>
          <button class="btn btn-fantasma btn-icono" @click="historial = null">✕</button>
        </header>
        <p v-if="!historial.filas.length" class="muted">Todavía no se ha impreso.</p>
        <ul v-else class="historial">
          <li v-for="(h, i) in historial.filas" :key="i">
            <span class="cuando">{{ h.cuando.slice(0, 16).replace('T', ' ') }}</span>
            <span class="quien2">{{ h.usuario }}</span>
            <span class="plant">{{ nombreDePlantilla(h.plantilla) }}</span>
            <span v-if="h.faltaba" class="badge badge-aviso">faltaba: {{ h.faltaba }}</span>
            <span v-else class="badge badge-ok">completa</span>
          </li>
        </ul>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* "Sin costo" en azul informativo: no es un estado bueno ni malo de la credencial, es un
   hecho de la venta que explica por qué no se le pide el recibo. Verde diría "listo" (que ya
   lo dice la otra insignia) y rojo diría "falta algo", que es justo lo contrario. */
.badge-gratis {
  background: color-mix(in srgb, var(--acento) 15%, transparent);
  color: var(--acento); font-weight: 700;
}

/* El extra se marca en violeta, distinto del verde de "lista" y del rojo de lo que falta:
   no es un estado de la credencial, es de dónde salió esa persona. */
.badge-extra {
  background: color-mix(in srgb, #a855f7 18%, transparent);
  color: #7e22ce; font-weight: 700;
}

.credenciales { display: flex; flex-direction: column; gap: 1rem; }

/* ---- resumen ---- */
.resumen { display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); gap: 0.7rem; }
.tarjeta {
  font: inherit; text-align: left; cursor: pointer;
  display: flex; flex-direction: column; gap: 0.15rem;
  padding: 0.85rem 1rem; border-radius: var(--radio-sm);
  background: var(--panel); border: 1px solid var(--border); color: var(--text);
}
.tarjeta.activa { border-color: var(--acento); box-shadow: 0 0 0 1px var(--acento); }
.tarjeta strong { font-size: 1.6rem; line-height: 1; color: var(--ok); font-variant-numeric: tabular-nums; }
.tarjeta strong.pend { color: var(--danger); }
.tarjeta strong.muted { color: var(--muted); }
.tarjeta span { font-size: 0.82rem; color: var(--muted); }

/* ---- ajustes ---- */
.ajustes { display: grid; grid-template-columns: 1.2fr 1fr auto; gap: 1.2rem; padding: 1rem; align-items: start; }
.grupo { display: flex; flex-direction: column; gap: 0.5rem; min-width: 0; }
.rotulo { font-size: 0.72rem; text-transform: uppercase; letter-spacing: 0.04em; color: var(--muted); font-weight: 700; }
.opciones { display: flex; gap: 0.5rem; flex-wrap: wrap; }
.opcion {
  font: inherit; cursor: pointer; text-align: left;
  display: flex; flex-direction: column; gap: 0.1rem;
  padding: 0.5rem 0.75rem; border-radius: var(--radio-sm);
  border: 1px solid var(--border); background: var(--panel); color: var(--text);
}
.opcion.elegida { border-color: var(--acento); background: color-mix(in srgb, var(--acento) 10%, var(--panel)); }
.opcion small { color: var(--muted); font-size: 0.75rem; }
.medida { display: flex; align-items: center; gap: 0.7rem; }
.medida input { flex: 1; }
.valor { font-variant-numeric: tabular-nums; font-weight: 700; white-space: nowrap; }
.nota { margin: 0; font-size: 0.78rem; color: var(--muted); }

.previa .hoja {
  width: 92px; aspect-ratio: 21.6 / 27.9;
  border: 1px solid var(--border); background: var(--panel-2);
  display: grid; place-items: center; border-radius: 2px;
}
.previa .cred { background: var(--acento); border-radius: 1px; }

/* ---- barra ---- */
.barra { display: flex; align-items: center; gap: 0.6rem; flex-wrap: wrap; }
.barra .control { flex: 1; min-width: 220px; }

/* ---- agrupado por entidad ---- */
.entidades { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 0.7rem; }
.entidad { padding: 0; overflow: hidden; }
.cab-ent {
  display: flex; align-items: center; justify-content: space-between; gap: 0.8rem;
  padding: 0.7rem 0.9rem; background: var(--panel-2); border-bottom: 1px solid var(--border);
  flex-wrap: wrap;
}
.quien-ent { display: flex; flex-direction: column; min-width: 0; }
.nom-ent { font-size: 1rem; }
.quien-ent .muted { font-size: 0.8rem; }
.acc-ent { display: flex; align-items: center; gap: 0.6rem; }
.cuenta-ent { font-size: 0.8rem; color: var(--muted); white-space: nowrap; }
/* Dentro de la tarjeta las filas no llevan borde propio: ya las separa la tarjeta. */
.entidad .lista { gap: 0; }
.entidad .fila { border: none; border-bottom: 1px solid var(--border); border-radius: 0; background: transparent; }
.entidad .fila:last-child { border-bottom: none; }

/* ---- lista ---- */
.vacio { padding: 2.5rem 1rem; text-align: center; color: var(--muted); line-height: 1.5; }
.lista { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 0.4rem; }
/* Cada fila es su propia rejilla, asi que las columnas NO se alinean solas entre filas: con
   pistas `auto` el numero de caseta bailaba de sitio segun lo ancho que fuera lo de al lado.
   De ahi los anchos fijos en las tres ultimas — es una lista para recorrer con la vista. */
.fila {
  display: grid; align-items: center; gap: 0.8rem;
  /* La ultima columna da para DOS botones por linea: con 200px, una fila pendiente
     (comprobante, foto, imprimir igual y la inscripcion entera) se partia en tres. */
  /* Sin la columna de categoria/caseta: ahora eso vive en la cabecera de la entidad y
     repetirlo en cada fila era decir tres veces lo mismo. */
  grid-template-columns: auto 46px minmax(0, 1fr) 210px 250px;
  padding: 0.55rem 0.8rem; border-radius: var(--radio-sm);
  background: var(--panel); border: 1px solid var(--border);
}
.fila.marcada { border-color: var(--acento); background: color-mix(in srgb, var(--acento) 7%, var(--panel)); }
/* A la que le falta algo se le baja el contraste, pero se la SIGUE viendo: es el trabajo
   pendiente, y esconderla haria creer que no existe. */
.fila.bloqueada { opacity: 0.72; }
.marca input { width: 18px; height: 18px; }
.foto { width: 46px; height: 46px; border-radius: var(--radio-sm); object-fit: cover; border: 1px solid var(--border); }
.foto.sinfoto { display: grid; place-items: center; background: var(--panel-2); color: var(--muted); font-weight: 700; }
.quien { display: flex; flex-direction: column; min-width: 0; }
.quien strong { font-size: 0.95rem; }
.quien span { font-size: 0.8rem; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.donde { display: flex; flex-direction: column; min-width: 0; }
.donde .cat { font-size: 0.78rem; color: var(--muted); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.donde .casetas { font-weight: 750; font-variant-numeric: tabular-nums; }
.estado { display: flex; gap: 0.3rem; flex-wrap: wrap; justify-content: flex-start; }
.badge { padding: 0.12rem 0.5rem; border-radius: 999px; font-size: 0.7rem; font-weight: 700; white-space: nowrap; }

.alcance {
  margin: 0; padding: 0.6rem 0.8rem; border-radius: var(--radio-sm);
  background: color-mix(in srgb, var(--acento) 10%, transparent);
  font-size: 0.88rem; line-height: 1.45;
}

.oculto { display: none; }
.acciones { display: flex; gap: 0.3rem; align-items: center; flex-wrap: wrap; justify-content: flex-end; }
.acciones .btn { cursor: pointer; }
.acciones .inerte { opacity: 0.5; pointer-events: none; }
/* El sello de "impresa" es un boton (abre el historial), y un boton trae borde, fondo y
   tipografia propios del navegador que taparian la insignia. */
button.badge { border: none; font-family: inherit; font-weight: 700; cursor: pointer; }
/* Ambar y no rojo: una credencial impresa cuando faltaba algo no es un error, es trabajo
   pendiente que alguien decidio adelantar. El rojo esta para lo que impide imprimir. */
.badge-aviso {
  background: color-mix(in srgb, var(--tramite) 16%, transparent);
  color: var(--tramite);
}

.velo {
  position: fixed; inset: 0; z-index: 80; background: rgba(2, 6, 23, 0.5);
  display: grid; place-items: center; padding: 1rem;
}
.panel { width: 100%; max-width: 540px; padding: 1.1rem; display: flex; flex-direction: column; gap: 0.8rem; }
.panel header { display: flex; align-items: center; justify-content: space-between; gap: 0.6rem; }
.panel h3 { margin: 0; font-size: 1.05rem; }
.historial { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 0.45rem; }
.historial li {
  display: flex; align-items: center; gap: 0.6rem; flex-wrap: wrap;
  padding: 0.5rem 0.6rem; border-radius: var(--radio-sm); background: var(--panel-2);
  font-size: 0.85rem;
}
.historial .cuando { font-variant-numeric: tabular-nums; color: var(--muted); }
.historial .quien2 { font-weight: 700; }
.historial .plant { color: var(--muted); }

@media (max-width: 820px) {
  .ajustes { grid-template-columns: 1fr; }
  .previa { display: none; }
  .fila { grid-template-columns: auto 46px 1fr; row-gap: 0.5rem; }
  .estado, .acciones { grid-column: 2 / -1; flex-direction: row; gap: 0.5rem; align-items: center; justify-content: flex-start; }
  .cab-ent { align-items: flex-start; }
  .acc-ent { flex-basis: 100%; justify-content: space-between; }
}
</style>
