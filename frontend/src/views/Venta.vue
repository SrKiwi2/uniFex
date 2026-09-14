<script setup>
import { ref, reactive, computed, onMounted, onUnmounted, watch, nextTick } from 'vue';
import { useRouter } from 'vue-router';
import { apiFetch } from '../api';
import { useAuthStore } from '../stores/auth';
import { usePuestosStore } from '../stores/puestos';
import { toast } from '../ui/toast';
import { descargarRecibo, descargarCredencialVirtual } from '../ui/descargas';
import { guardarBorrador, leerBorrador, borrarBorrador } from '../ui/borrador';
import { partirNombre } from '../ui/nombres';
import { mostrarCarga, ocultarCarga, textoCarga } from '../ui/cargando';
import { alerta, aviso, alertaConAccion } from '../ui/alerta';

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

/**
 * El carrito agrupado por categoria, que es como el vendedor lo nombra en voz alta.
 *
 * "2 casetas · 100 Bs" no dice lo unico que el cliente esta preguntando: CUALES. El numero de
 * caseta es como se llama a lo que se esta vendiendo ("la 6 y la 7 de PYMES"), y no salia por
 * ningun lado hasta el ultimo paso. Ahora se agrupa por categoria, con sus numeros y su
 * subtotal, y eso mismo sirve para la cabecera y para la confirmacion.
 */
const porCategoria = computed(() => {
  const m = new Map();
  for (const p of carrito.value) {
    const clave = p.categoria || 'Sin categoría';
    if (!m.has(clave)) m.set(clave, { categoria: clave, color: p.color, casetas: [], subtotal: 0 });
    const g = m.get(clave);
    g.casetas.push(p);
    g.subtotal += Number(p.precio || 0);
  }
  // Los numeros, en orden: "6, 7 y 14" se lee; "14, 6 y 7" hace dudar de si falta alguna.
  for (const g of m.values()) {
    g.casetas.sort((a, b) => String(a.codigo).localeCompare(String(b.codigo), 'es', { numeric: true }));
  }
  return [...m.values()];
});

/** "6", "6 y 7", "6, 7 y 14" — como se dice, no como se programa. */
function listar(codigos) {
  if (codigos.length === 0) return '';
  if (codigos.length === 1) return String(codigos[0]);
  return `${codigos.slice(0, -1).join(', ')} y ${codigos[codigos.length - 1]}`;
}

const bs = (n) => Number(n || 0).toLocaleString('es-BO');

/*
 * Ayuda por campo.
 *
 * Varios de estos nombres significan cosas distintas segun a quien se le pregunte, y el
 * vendedor no siempre es el mismo de la semana pasada. Antes la unica forma de saber que iba
 * en "Objeto" o quien era el "Responsable 1" era preguntarle a alguien. El texto vive aqui, en
 * un solo sitio, y se abre en el modal que ya existe: ningun componente nuevo.
 */
const AYUDAS = {
  entidadNombre: ['Nombre de la entidad',
    'El nombre con el que el expositor quiere aparecer en la feria y en su credencial. '
    + 'Puede ser una empresa, una asociación o un emprendimiento personal.'],
  tipoEntidad: ['Tipo de entidad',
    'La clasificación que usa la feria para esa entidad. Si no estás seguro, pregunta en '
    + 'administración: cambia el trato y a veces el precio.'],
  nit: ['NIT',
    'El número de identificación tributaria, si lo tiene. Es opcional: muchos emprendimientos '
    + 'pequeños no lo tienen y la venta se registra igual.'],
  rubro: ['Rubro o descripción',
    'Qué vende o expone, en pocas palabras: "artesanía en cuero", "café", "ropa infantil". '
    + 'Sale impreso en la credencial y sirve para agrupar por sector.'],
  responsableLegal: ['Responsable legal',
    'El DUEÑO de la caseta: a quien hay que llamar por un cobro o un problema. Es obligatorio. '
    + 'No tiene por qué ser quien atienda la caseta durante la feria.'],
  ciLegal: ['C.I. del responsable legal',
    'La cédula de identidad del dueño, solo los números. Es el dato con el que se le identifica '
    + 'si hay que reclamar algo.'],
  celularLegal: ['Celular del responsable legal',
    'El número al que se le puede llamar durante la feria. Es el contacto que verá '
    + 'administración si necesita ubicarlo.'],
  fechas: ['Desde y hasta',
    'Los días que ocupará la caseta, si es un periodo distinto al de toda la feria. Se pueden '
    + 'dejar en blanco.'],
  responsable: ['Responsables',
    'Quién ATIENDE la caseta durante la feria. Son los que reciben credencial y los que entran '
    + 'por la puerta con ella, así que necesitan foto. Pueden ser hasta dos, y pueden ser '
    + 'personas distintas del dueño.'],
  fotoResp: ['Foto del responsable',
    'Se imprime en su credencial. Es opcional ahora: si no la tomas, la venta se registra igual '
    + 'y la puedes subir después desde Mis ventas. Pero sin foto no se le puede emitir la '
    + 'credencial con etiquetas.'],
  contado: ['Pagó al contado',
    'Marca esto si te pagó en efectivo, en el momento. OJO: marcarlo dice CÓMO pagó, no que '
    + 'exista el recibo. El comprobante hay que subirlo igual, o no se le puede acreditar.'],
  banco: ['Banco y N.º de comprobante',
    'Si pagó por transferencia o depósito, el banco y el número que figura en el papel. Sirve '
    + 'para cuadrar el cobro con el extracto.'],
};

function ayuda(clave) {
  const [titulo, texto] = AYUDAS[clave] || ['Ayuda', ''];
  return aviso(texto, 'info', 0, titulo);
}

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
  /*
   * Como pago: '' (sin elegir), 'contado' o 'deposito'.
   *
   * Antes era una casilla "Pagó al contado", y eso tenia un agujero silencioso: NO marcarla no
   * significaba "fue deposito", significaba "no se toco la casilla". Las ventas salian con
   * pagoContado=false por omision y despues nadie sabia si eso era un credito de verdad o un
   * despiste. Ahora hay que elegir, y confirmar no deja pasar sin eleccion.
   */
  formaPago: '',
  entidadBancaria: '', numComprobante: null, pagoContado: false,
});

/** El comprobante del deposito, si se adjunta ya. Fuera de `form` por lo mismo que las fotos. */
const comprobante = ref(null);

/*
 * Fotos de los responsables, FUERA de `form` a proposito.
 *
 * Son objetos File y el borrador se guarda como JSON: metidas ahi se convertirian en `{}` al
 * recuperarlas, y ademas no tiene sentido conservar un archivo de una sesion a otra. Se suben
 * DESPUES de que la venta exista, porque hasta entonces el responsable no tiene id.
 */
const fotos = ref([null, null]);

const hayResponsable2 = computed(() => form.responsables.length > 1);

/** `pagoContado` es lo que entiende el servidor; aqui se deriva de la eleccion. */
watch(() => form.formaPago, (v) => {
  form.pagoContado = v === 'contado';
  if (v === 'contado') { form.entidadBancaria = ''; form.numComprobante = null; }
});

function elegirComprobante(evento) {
  const archivo = evento.target.files?.[0];
  evento.target.value = '';
  if (!archivo) return;
  if (comprobante.value?.url) URL.revokeObjectURL(comprobante.value.url);
  comprobante.value = {
    archivo,
    url: archivo.type.startsWith('image/') ? URL.createObjectURL(archivo) : null,
    nombre: archivo.name,
  };
}

function quitarComprobante() {
  if (comprobante.value?.url) URL.revokeObjectURL(comprobante.value.url);
  comprobante.value = null;
}

/**
 * Rellenar el Responsable 1 con los datos del responsable legal.
 *
 * El responsable legal se escribe como UN campo con el nombre completo (asi lo guarda
 * `entidad`), y aqui hacen falta nombre, paterno y materno por separado. Antes se copiaba la
 * cadena entera a "nombre" y los apellidos quedaban vacios: la credencial salia con el nombre
 * completo en el hueco del nombre y sin apellidos. Ahora se reparte (ver `ui/nombres.js`).
 *
 * Y los campos NO se bloquean. La division acierta casi siempre, pero un "DE LA CRUZ" la
 * rompe, y bloquear los campos convertia un error de un segundo en un dato imposible de
 * corregir sin desmarcar la casilla. Se rellena y se deja tocar.
 */
function copiarDelLegal() {
  const r = form.responsables[0];
  if (!r) return;
  const partido = partirNombre(form.representanteLegal);
  r.nombre = partido.nombre;
  r.paterno = partido.paterno;
  r.materno = partido.materno;
  r.ci = form.ciRepresentante;
  r.celular = form.celularRepresentante;
}

/**
 * Al marcar la casilla: si el Responsable 1 ya tiene algo escrito, se PREGUNTA.
 *
 * Antes se sobreescribia sin avisar, y era facil llegar ahi por accidente: se rellenan los
 * datos de quien atiende la caseta, se marca la casilla por curiosidad y se pierde lo
 * tecleado, sin forma de recuperarlo. Vaciar el trabajo de alguien nunca puede ser el efecto
 * secundario de un toque.
 */
async function alMarcarCopia(evento) {
  const marcado = evento.target.checked;
  if (!marcado) { form.copiaLegal = false; return; }

  const r = form.responsables[0];
  if (r && algoEscrito(r)) {
    // Se deja sin marcar mientras se decide: si cancela, la casilla no debe quedar activa.
    form.copiaLegal = false;
    const sigue = await alertaConAccion(
      'El Responsable 1 ya tiene datos escritos. Si continúas se reemplazan por los del '
      + 'responsable legal.',
      'advertencia', null, 0);
    if (!sigue) return;
  }
  form.copiaLegal = true;
  copiarDelLegal();
}

/** Mientras la casilla siga marcada, el Responsable 1 sigue al responsable legal. */
watch(
  () => [form.representanteLegal, form.ciRepresentante, form.celularRepresentante],
  () => { if (form.copiaLegal) copiarDelLegal(); },
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
    /*
     * Del segundo responsable solo se exige el NOMBRE.
     *
     * El C.I. era obligatorio y frenaba ventas por un dato que muchas veces no esta a mano: el
     * segundo suele ser un familiar o un empleado que ni siquiera esta en el mostrador. Sin
     * C.I. se registra igual y se completa despues desde Mis ventas; lo que no se puede es
     * dejarlo sin nombre, porque entonces no hay a quien acreditar.
     */
    if (r1 && algoEscrito(r1)) {
      pide('r1.nombre', 'Nombre del Responsable 2', r1.nombre.trim());
    }
  }
  if (paso.value === 2) {
    // Sin forma de pago la venta queda sin decir como se cobro, y eso no se deduce despues:
    // o fue efectivo o fue un deposito, y son cobros que se cuadran distinto.
    pide('formaPago', 'Cómo pagó (contado o depósito)', form.formaPago);
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


/*
 * Los nombres van en MAYUSCULAS, como el resto del sistema (las entidades y personas que ya
 * hay estan asi). Se normaliza al enviar y ademas se ven en mayusculas mientras se teclea
 * (clase `.mayus`): transformar la tecla en vivo obliga a recolocar el cursor a mano y se
 * rompe con el teclado predictivo de Android.
 */
const mayus = (v) => (typeof v === 'string' ? v.trim().toUpperCase() : v);

async function registrar() {
  if (enviando.value) return;
  if (!carrito.value.length) { alerta('No tienes casetas seleccionadas.', 'error'); return; }

  /*
   * El ultimo paso tambien se valida.
   *
   * "Siguiente" comprobaba lo suyo, pero "Registrar venta" no comprobaba nada: era el unico
   * boton que salia del formulario sin pasar por la validacion, asi que lo que se pidiera en
   * el paso de confirmar —la forma de pago— se colaba vacio.
   */
  intentado.value = true;
  if (faltantes.value.size) {
    nextTick(() => {
      document.querySelector('.faltan')?.scrollIntoView({ block: 'center', behavior: 'smooth' });
    });
    return;
  }
  intentado.value = false;
  enviando.value = true;
  perdidas.value = [];
  // Registrar es lo mas lento del circuito y lo que mas se repite con mala red. Sin aviso, el
  // vendedor concluia que la aplicacion se colgo y volvia a pulsar.
  mostrarCarga('Registrando la venta…');
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
      // asi que se refresca el mapa y se le explica que paso sin perderle lo escrito. Va en
      // modal y no en aviso pequeño: es lo unico de esta pantalla que obliga a decidir algo.
      await alerta(d.mensaje || 'Una caseta ya no está disponible', 'error', 0);
      await tienda.recargar();
      paso.value = 2;
      return;
    }
    if (!r.ok || !d.ok) {
      await alerta(d.mensaje || 'No se pudo registrar la venta', 'error', 0);
      return;
    }

    // La venta ya existe. Lo que se sube despues no puede hacerla fracasar: si algo falla,
    // la venta sigue hecha y se completa desde Mis ventas.
    let comprobanteOk = true;
    if (comprobante.value?.archivo) {
      textoCarga.value = 'Subiendo el comprobante…';
      try {
        const datos = new FormData();
        datos.append('archivo', comprobante.value.archivo);
        if (form.entidadBancaria) datos.append('entidadBancaria', form.entidadBancaria);
        if (form.numComprobante) datos.append('numComprobante', String(form.numComprobante));
        const rc = await apiFetch(`/api/app/inscripciones/${d.inscripcionId}/comprobante`,
                                  { method: 'POST', body: datos });
        comprobanteOk = rc.ok;
      } catch {
        comprobanteOk = false;
      }
    }

    textoCarga.value = 'Subiendo las fotos…';
    const todas = await subirFotos(d.inscripcionId);
    borrarBorrador(auth.id);

    /*
     * La credencial virtual se baja SOLA, aqui mismo.
     *
     * Es el momento de la entrega: el expositor esta delante y se le manda la imagen al
     * telefono. Solo salen las que ya se pueden emitir —con comprobante y con foto—, porque
     * una credencial sin foto no sirve para identificar a nadie en la puerta. A quien le falte
     * algo, se le dice donde completarlo en vez de bajarle un papel a medias.
     */
    textoCarga.value = 'Preparando las credenciales…';
    const conCredencial = [];
    if (comprobanteOk && comprobante.value?.archivo) {
      try {
        const rr = await apiFetch(`/api/app/inscripciones/${d.inscripcionId}/responsables`);
        const lista = (await rr.json())?.responsables || [];
        for (const r of lista) if (r.tieneFoto) conCredencial.push(r);
      } catch { /* sin la lista no se baja ninguna: la venta ya esta hecha */ }
    }
    for (const r of conCredencial) {
      await descargarCredencialVirtual(r.id, r.nombre);
    }

    textoCarga.value = 'Preparando el recibo…';
    // El recibo se baja SOLO, que es el momento en que el cliente lo está esperando. Si algo
    // falla no se toca la venta: ya está hecha, y se avisa de dónde volver a pedirlo.
    await descargarRecibo(d.inscripcionId);
    ocultarCarga();

    /*
     * La confirmacion va en el modal del centro y NO en el aviso de la esquina.
     *
     * Registrar una venta es el momento mas importante de la aplicacion y el aviso pequeño de
     * arriba se perdia: aparecia y se iba mientras el vendedor miraba el telefono del cliente.
     * Aqui hay que enterarse, asi que ocupa el centro, dice el importe y espera un toque —o se
     * va solo a los 6 s, para no estorbar a quien ya lo leyo.
     */
    const casetas = listar(carrito.value.map((p) => p.codigo));
    const pendiente = [
      todas ? null : 'alguna foto',
      comprobanteOk ? null : 'el comprobante',
    ].filter(Boolean).join(' y ');

    let sobreCredenciales = '';
    if (conCredencial.length) {
      sobreCredenciales = `\n\nSe descargó ${conCredencial.length === 1
        ? 'la credencial virtual'
        : `${conCredencial.length} credenciales virtuales`}. Ya se puede${conCredencial.length === 1 ? '' : 'n'} mandar al expositor.`;
    } else {
      sobreCredenciales = '\n\nLa credencial virtual se descarga desde Credenciales, en cuanto '
        + 'estén el comprobante y la foto del responsable.';
    }

    await aviso(
      `Caseta${carrito.value.length === 1 ? '' : 's'} ${casetas} · ${bs(d.total)} Bs`
      + sobreCredenciales
      + (pendiente ? `\n\nNo se pudo subir ${pendiente}. Puedes completarlo desde Mis ventas.` : ''),
      'ok', 0, '¡Venta registrada!');
    router.push({ path: '/mis-ventas', query: { registrada: d.inscripcionId } });
  } catch (e) {
    await alerta(e.message, 'error', 0);
  } finally {
    ocultarCarga();
    enviando.value = false;
  }
}

// ---- borrador local: se guarda al vuelo y se recupera al volver ----
watch(form, () => guardarBorrador(auth.id, form), { deep: true });

/*
 * Aqui hubo un aviso de "desliza para ver mas". Se quito, y conviene saber por que para no
 * volver a ponerlo: existia porque los botones de continuar quedaban fuera de pantalla y el
 * vendedor no sabia que el formulario seguia. Desde que la franja de botones esta SIEMPRE a
 * la vista, pegada justo encima de la barra de opciones, ese problema ya no existe — y en un
 * telefono de 400 px el aviso se posaba encima de los campos, tapando el titulo del bloque
 * que se iba a rellenar. Curaba algo ya curado y estorbaba.
 */
const ventaRef = ref(null);

/** Cambiar de paso deja al usuario mirando la mitad del formulario nuevo si no se sube. */
watch(paso, () => {
  ventaRef.value?.scrollIntoView({ block: 'start', behavior: 'auto' });
});

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

onUnmounted(() => {
  // Las previsualizaciones son URLs de objeto: sin revocarlas se quedan en memoria.
  fotos.value.forEach((f) => f?.url && URL.revokeObjectURL(f.url));
  if (comprobante.value?.url) URL.revokeObjectURL(comprobante.value.url);
});
</script>

<template>
  <div class="venta" ref="ventaRef">
    <!-- Resumen siempre visible: en el movil, saber cuanto se esta cobrando no puede
         depender de bajar hasta el final del formulario. -->
    <header class="resumen card">
      <div class="lineas">
        <!-- Que se esta vendiendo, con nombre y numero. Es lo que el vendedor le esta
             diciendo al cliente mientras rellena, y antes no salia hasta el ultimo paso. -->
        <div v-for="g in porCategoria" :key="g.categoria" class="grupo-cab">
          <span class="punto" :style="{ background: g.color || 'var(--acento)' }"></span>
          <span class="cat">{{ g.categoria }}</span>
          <span class="nums">
            caseta{{ g.casetas.length === 1 ? '' : 's' }}
            {{ listar(g.casetas.map((c) => c.codigo)) }}
          </span>
          <!-- El subtotal solo cuando hay mas de una categoria. Con una sola es el mismo
               numero que el total, y repetirlo hace dudar de si son dos cobros distintos. -->
          <span v-if="porCategoria.length > 1" class="sub-bs">{{ bs(g.subtotal) }} Bs</span>
        </div>
        <div class="total-cab">
          <span>Total</span>
          <strong class="total">{{ bs(total) }} Bs</strong>
        </div>
      </div>
      <router-link to="/mapa" class="btn btn-fantasma btn-sm volver">← Volver al mapa</router-link>
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
          <span>Nombre de la entidad *<button type="button" class="ayuda" @click.prevent="ayuda('entidadNombre')" aria-label="Qué es esto">?</button></span>
          <input class="control mayus" :class="{ falta: falta('entidadNombre') }"
                 v-model="form.entidadNombre" placeholder="Ej. Artesanías Illimani" />
        </label>
        <label class="campo">
          <span>Tipo de entidad *<button type="button" class="ayuda" @click.prevent="ayuda('tipoEntidad')" aria-label="Qué es esto">?</button></span>
          <select class="control" :class="{ falta: falta('tipoEntidadId') }" v-model="form.tipoEntidadId">
            <option :value="null" disabled>Elige una opción…</option>
            <option v-for="t in tiposEntidad" :key="t.id" :value="t.id">{{ t.nombre }}</option>
          </select>
        </label>
        <div class="dos">
          <label class="campo">
            <span>NIT<button type="button" class="ayuda" @click.prevent="ayuda('nit')" aria-label="Qué es esto">?</button></span>
            <input class="control" v-model="form.nit" inputmode="numeric" placeholder="Solo números" />
          </label>
          <label class="campo">
            <span>Rubro o descripción<button type="button" class="ayuda" @click.prevent="ayuda('rubro')" aria-label="Qué es esto">?</button></span>
            <input class="control mayus" v-model="form.descripcion" placeholder="Qué vende o expone" />
          </label>
        </div>

        <div class="separador"></div>

        <!-- El dueño va con la ENTIDAD, no con quien atiende la caseta. Es a quien se llama
             por un cobro, y antes era opcional: se colaban ventas sin nadie a quien reclamar. -->
        <h3 class="sub">
          Responsable legal
          <span class="muted">— el dueño de la caseta</span>
          <button type="button" class="ayuda" @click.prevent="ayuda('responsableLegal')" aria-label="Qué es esto">?</button>
        </h3>
        <label class="campo">
          <span>Nombre completo *<button type="button" class="ayuda" @click.prevent="ayuda('responsableLegal')" aria-label="Qué es esto">?</button></span>
          <input class="control mayus" :class="{ falta: falta('representanteLegal') }"
                 v-model="form.representanteLegal" placeholder="Ej. María Quispe Mamani" />
        </label>
        <div class="dos">
          <label class="campo">
            <span>C.I. *<button type="button" class="ayuda" @click.prevent="ayuda('ciLegal')" aria-label="Qué es esto">?</button></span>
            <input class="control" :class="{ falta: falta('ciRepresentante') }"
                   v-model="form.ciRepresentante" inputmode="numeric" placeholder="Ej. 8765432" />
          </label>
          <label class="campo">
            <span>Celular *<button type="button" class="ayuda" @click.prevent="ayuda('celularLegal')" aria-label="Qué es esto">?</button></span>
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
          <button type="button" class="ayuda" @click.prevent="ayuda('responsable')" aria-label="Saber más">?</button>
        </p>

        <template v-for="(r, i) in form.responsables" :key="i">
          <div v-if="i > 0" class="separador"></div>
          <h3 class="sub">Responsable {{ i + 1 }}</h3>

          <label v-if="i === 0" class="fila-check">
            <input type="checkbox" :checked="form.copiaLegal" @change="alMarcarCopia" />
            Es el mismo responsable legal
          </label>
          <p v-if="i === 0 && form.copiaLegal" class="nota">
            Se repartió el nombre completo en nombre y apellidos. Si quedó mal, corrígelo aquí
            mismo: los campos siguen editables.
          </p>

          <div class="dos">
            <label class="campo">
              <span>Nombre {{ i === 0 ? '*' : '' }}</span>
              <input class="control mayus" :class="{ falta: falta(`r${i}.nombre`) }"
                     v-model="r.nombre" placeholder="Ej. María" />
            </label>
            <label class="campo">
              <span>C.I. {{ i === 0 ? '*' : '' }}</span>
              <input class="control" :class="{ falta: falta(`r${i}.ci`) }"
                     v-model="r.ci" inputmode="numeric" placeholder="Ej. 8765432" />
            </label>
          </div>
          <div class="dos">
            <label class="campo">
              <span>Apellido paterno</span>
              <input class="control mayus"
                     v-model="r.paterno" placeholder="Ej. Quispe" />
            </label>
            <label class="campo">
              <span>Apellido materno</span>
              <input class="control mayus"
                     v-model="r.materno" placeholder="Ej. Mamani" />
            </label>
          </div>
          <label class="campo">
            <span>Celular</span>
            <input class="control" type="tel" inputmode="tel"
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
              <button type="button" class="ayuda" @click.prevent="ayuda('fotoResp')" aria-label="Qué es esto">?</button>
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
        <h3 class="sub">Qué se está vendiendo</h3>
        <!-- Agrupado por categoria y con subtotal: es la factura que el vendedor va a cantar
             en voz alta, no un listado de filas sueltas. -->
        <div v-for="g in porCategoria" :key="g.categoria" class="grupo-conf">
          <header>
            <span class="sw" :style="{ background: g.color || '#94a3b8' }"></span>
            <strong>{{ g.categoria }}</strong>
            <span class="sub-bs">{{ bs(g.subtotal) }} Bs</span>
          </header>
          <!-- Aqui ya no se quita nada. Este paso es para REVISAR antes de cobrar, y una ✕
               al lado de cada caseta invita a tocarla justo cuando el vendedor esta leyendo la
               lista en voz alta. Para cambiar la seleccion esta el mapa, que es donde se
               eligieron. -->
          <ul class="casetas">
            <li v-for="p in g.casetas" :key="p.id" :class="{ perdida: perdidas.includes(p.id) }">
              <span class="nom">Caseta {{ p.codigo }}</span>
              <span class="muted">{{ p.tamano }}</span>
              <span class="precio">{{ bs(p.precio) }} Bs</span>
            </li>
          </ul>
        </div>

        <!-- UN solo total. Antes salia la cuenta dos veces —"2 casetas · 100 Bs" y debajo
             "Total a cobrar · 100 Bs"— y ver el mismo importe repetido hace dudar de si son
             dos cobros o si uno es un subtotal de algo. -->
        <div class="cuenta">
          <div class="linea grande">
            <span>Total a cobrar</span>
            <strong>{{ bs(total) }} Bs</strong>
          </div>
        </div>
        <p class="nota">
          ¿Hay que cambiar alguna caseta?
          <router-link to="/mapa">Vuelve al mapa</router-link> para quitarla o agregar otra.
        </p>

        <div class="separador"></div>

        <h3 class="sub">
          Pago
          <button type="button" class="ayuda" @click.prevent="ayuda('contado')" aria-label="Qué es esto">?</button>
        </h3>

        <!-- Dos botones y no una casilla. Con la casilla, "no marcada" queria decir dos cosas
             a la vez —fue deposito, o nadie la toco— y la venta salia igual. Aqui hay que
             elegir, y confirmar no deja pasar sin eleccion. -->
        <div class="formas" :class="{ falta: falta('formaPago') }" role="radiogroup"
             aria-label="Cómo pagó">
          <button type="button" class="forma" :class="{ activa: form.formaPago === 'contado' }"
                  role="radio" :aria-checked="form.formaPago === 'contado'"
                  @click="form.formaPago = 'contado'">
            <span class="ico">💵</span>
            <strong>Al contado</strong>
            <small>Efectivo, en el momento</small>
          </button>
          <button type="button" class="forma" :class="{ activa: form.formaPago === 'deposito' }"
                  role="radio" :aria-checked="form.formaPago === 'deposito'"
                  @click="form.formaPago = 'deposito'">
            <span class="ico">🏦</span>
            <strong>Depósito</strong>
            <small>Transferencia o banco</small>
          </button>
        </div>

        <!-- Los datos del banco solo cuando hacen falta: con "al contado" son ruido. -->
        <template v-if="form.formaPago === 'deposito'">
          <div class="dos">
            <label class="campo">
              <span>Banco<button type="button" class="ayuda" @click.prevent="ayuda('banco')" aria-label="Qué es esto">?</button></span>
              <input class="control mayus" v-model="form.entidadBancaria" placeholder="Ej. Banco Unión" />
            </label>
            <label class="campo">
              <span>N.º de depósito</span>
              <input class="control" type="number" inputmode="numeric"
                     v-model.number="form.numComprobante" placeholder="Solo números" />
            </label>
          </div>
        </template>

        <!-- El comprobante se puede adjuntar YA, en las dos formas de pago: es el momento en
             que el cliente lo tiene en la mano. Sigue siendo opcional para no frenar la venta,
             pero se dice sin rodeos que hace falta y para que. -->
        <div class="comprobante-adj">
          <div class="cabecera-adj">
            <strong>Comprobante de pago</strong>
            <span class="badge" :class="comprobante ? 'badge-ok' : 'badge-aviso'">
              {{ comprobante ? 'adjuntado' : 'pendiente' }}
            </span>
          </div>
          <p class="nota">
            Hace falta <strong>siempre</strong>, también al contado: sin él no se le puede
            emitir la credencial al expositor. Si lo tienes ahora, adjúntalo; si no, se sube
            después desde <strong>Mis ventas</strong>.
          </p>
          <div class="acciones-adj">
            <input id="comprobante-venta" class="oculto" type="file"
                   accept="image/*,application/pdf" @change="elegirComprobante" />
            <label for="comprobante-venta" class="btn btn-sm">
              📎 {{ comprobante ? 'Cambiar' : 'Adjuntar comprobante' }}
            </label>
            <button v-if="comprobante" type="button" class="btn btn-peligro btn-sm"
                    @click="quitarComprobante">Quitar</button>
          </div>
          <div v-if="comprobante" class="previa-adj">
            <img v-if="comprobante.url" :src="comprobante.url" alt="Comprobante elegido" />
            <span v-else class="muted">📄 {{ comprobante.nombre }}</span>
          </div>
        </div>
      </section>

<!-- Franja de botones: siempre a la vista, pegada justo encima de la barra de opciones. -->
      <div class="pie">
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
/*
 * La pantalla NO se saca del flujo con `position: absolute`.
 *
 * Estuvo asi, anclada con `top: 70px; bottom: 70px`, y esos dos numeros eran el problema que
 * se veia en el telefono: no coinciden con nada real. La barra superior mide lo que mida mas
 * la franja de estado del sistema (`--safe-top`), y la inferior es `--tabbar-h`, que ya
 * incluye el area segura de abajo. Con 70px fijos, arriba se recortaba la fila del total y el
 * boton de volver al mapa, y abajo quedaba una rendija por la que se veia pasar el contenido
 * por debajo de los botones.
 *
 * Ahora vive dentro de `.contenido`, que ya reserva esos espacios, y el desplazamiento es el
 * de la pagina. Menos codigo y, sobre todo, un solo sitio donde estan esas medidas.
 */
.venta {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  max-width: 720px;
  margin: 0 auto;
  width: 100%;
}
/* El total se queda a la vista al desplazar: es el dato que el vendedor esta cantando en voz
   alta. `top` cuenta con la barra superior, que tambien es pegajosa. */
.resumen {
  display: flex; align-items: flex-start; justify-content: space-between; gap: 0.8rem;
  padding: 0.8rem 1rem; position: sticky; top: 0; z-index: 5;
}
.lineas { display: flex; flex-direction: column; gap: 0.25rem; min-width: 0; flex: 1; }
.grupo-cab { display: flex; align-items: baseline; gap: 0.35rem; flex-wrap: wrap; font-size: 0.9rem; }
.grupo-cab .punto { width: 10px; height: 10px; border-radius: 3px; flex: none; }
.grupo-cab .cat { font-weight: 700; }
.grupo-cab .nums { color: var(--muted); }
.grupo-cab .sub-bs { margin-left: auto; font-variant-numeric: tabular-nums; }
.total-cab {
  display: flex; align-items: baseline; justify-content: space-between; gap: 0.6rem;
  border-top: 1px solid var(--border); padding-top: 0.3rem; margin-top: 0.15rem;
  font-size: 0.88rem; color: var(--muted);
}
.resumen .total { font-variant-numeric: tabular-nums; font-size: 1.15rem; color: var(--texto); }
.volver { flex: none; white-space: nowrap; }

/* ---- confirmar ---- */
.grupo-conf { display: flex; flex-direction: column; gap: 0.2rem; }
.grupo-conf > header { display: flex; align-items: center; gap: 0.5rem; }
.grupo-conf > header .sub-bs { margin-left: auto; font-variant-numeric: tabular-nums; font-weight: 700; }
.cuenta {
  display: flex; flex-direction: column; gap: 0.3rem;
  padding: 0.7rem 0.9rem; border-radius: var(--radio-sm); background: var(--panel-2);
}
.cuenta .linea { display: flex; justify-content: space-between; gap: 0.8rem; font-size: 0.9rem; color: var(--muted); }
.cuenta .linea.grande { font-size: 1.1rem; color: var(--texto); font-weight: 700; }
.cuenta .linea.grande strong { font-variant-numeric: tabular-nums; }

/* ---- forma de pago ---- */
.formas { display: grid; grid-template-columns: 1fr 1fr; gap: 0.6rem; }
.formas.falta { outline: 2px solid var(--danger); outline-offset: 4px; border-radius: var(--radio-sm); }
.forma {
  display: flex; flex-direction: column; align-items: center; gap: 0.15rem;
  padding: 0.85rem 0.6rem; border-radius: var(--radio-sm);
  border: 2px solid var(--border); background: var(--panel); color: var(--texto);
  cursor: pointer; transition: border-color 0.15s ease, background 0.15s ease, transform 0.1s ease;
  font: inherit; text-align: center;
}
.forma .ico { font-size: 1.5rem; line-height: 1.1; }
.forma strong { font-size: 1rem; }
.forma small { color: var(--muted); font-size: 0.78rem; }
.forma:active { transform: scale(0.98); }
.forma.activa {
  border-color: var(--acento);
  background: color-mix(in srgb, var(--acento) 12%, var(--panel));
}
.forma.activa strong { color: var(--acento); }

/* ---- comprobante adjunto en el registro ---- */
.comprobante-adj {
  display: flex; flex-direction: column; gap: 0.5rem;
  padding: 0.8rem 0.9rem; border-radius: var(--radio-sm); background: var(--panel-2);
  border-left: 3px solid var(--tramite);
}
.cabecera-adj { display: flex; align-items: center; justify-content: space-between; gap: 0.6rem; }
.badge-aviso {
  background: color-mix(in srgb, var(--tramite) 16%, transparent);
  color: var(--tramite); font-weight: 700;
}
.acciones-adj { display: flex; gap: 0.5rem; flex-wrap: wrap; align-items: center; }
.acciones-adj label.btn { cursor: pointer; }
.previa-adj img {
  width: 100%; max-height: 200px; object-fit: contain; border-radius: var(--radio-sm);
  border: 1px solid var(--border); background: var(--panel);
}

/* El "?" de cada campo: discreto en reposo, grande para el dedo. */
.ayuda {
  display: inline-grid; place-items: center; width: 22px; height: 22px; margin-left: 0.35rem;
  border-radius: 50%; border: 1px solid var(--border); background: var(--panel-2);
  color: var(--muted); font-size: 0.75rem; font-weight: 800; cursor: pointer;
  vertical-align: middle; flex: none;
}
.ayuda:hover, .ayuda:focus-visible { border-color: var(--acento); color: var(--acento); }

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
.pie { display: flex; flex-direction: column; align-items: stretch; gap: 0.5rem; position: relative; }


/* En móvil el formulario se vuelve de una columna y los botones ocupan el ancho:
   es la misma pantalla que va en el APK y ahí se usa con una mano. */
@media (max-width: 560px) {
  .dos { grid-template-columns: 1fr; }

  /*
   * Los botones se pegan al borde de abajo del CONTENIDO, que termina justo encima de la
   * barra de opciones del usuario. El margen negativo cancela el relleno lateral de
   * `.contenido` para que la franja llegue de lado a lado, como en cualquier aplicacion:
   * media franja con los bordes al aire se veia como un recorte.
   *
   * `--tabbar-h` es cero cuando el teclado esta abierto (la barra se esconde), asi que al
   * teclear los botones suben con el y no se quedan detras.
   */
  .pie {
    position: sticky;
    /* Justo ENCIMA de la barra de opciones, sin hueco entre las dos. Con `bottom: 0` la
       franja se pegaba al borde de la ventana, o sea DETRAS de esa barra, y quedaba la
       rendija por la que se veia pasar el formulario al desplazar. */
    bottom: var(--tabbar-h);
    /* Los margenes negativos cancelan el relleno lateral del contenedor: la franja tiene que
       llegar de lado a lado o parece un recorte flotando. */
    margin: 0 -1.4rem;
    padding: 0.7rem 1.4rem;
    background: var(--bg);
    z-index: 6;
    border-top: 1px solid var(--border);
  }
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
  /* Letra mas grande en el telefono: es la pantalla que mas se teclea, de pie y a menudo con
     el sol encima. Los tamaños de escritorio obligaban a acercarse. */
  .campo > span { font-size: 0.95rem; font-weight: 650; }
  .control { font-size: 1.05rem; min-height: 50px; }
  select.control { min-height: 50px; }
  .grupo-cab { font-size: 0.98rem; }
  .resumen .total { font-size: 1.3rem; }
  .cuenta .linea { font-size: 0.98rem; }
  .cuenta .linea.grande { font-size: 1.25rem; }
  .ayuda { width: 28px; height: 28px; font-size: 0.9rem; }
  .forma { padding: 1rem 0.6rem; }
  .forma strong { font-size: 1.05rem; }
  .acciones-adj .btn { min-height: 46px; }
  .faltan { font-size: 1rem; }
  /* Se pulsan de pie y delante del cliente: mas altos que el minimo tactil a proposito. */
  .acciones .btn { min-height: 56px; font-size: 1.08rem; font-weight: 700; }
  .bloque > .btn { min-height: 50px; font-size: 1rem; }
  .foto img, .foto .sinfoto { width: 88px; height: 88px; }
  .foto-acciones .btn { min-height: 44px; }
}
</style>
