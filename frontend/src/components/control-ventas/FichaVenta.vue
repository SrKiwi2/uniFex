<script setup>
/**
 * La ficha de una venta en Control de ventas: todas sus celdas, su comprobante y la credencial
 * de cada responsable.
 *
 * <h2>Rendir una venta = adjuntar su comprobante</h2>
 * La foto del depósito, el voucher o el recibo basta; banco y número son opcionales. Se usa el
 * MISMO endpoint que Mis ventas e Inscripciones (`/api/app/inscripciones/{id}/comprobante`): así
 * la venta queda con comprobante para todo el sistema —credenciales, tablero de dirección—, y no
 * solo para esta pantalla.
 */
import { ref, computed, onMounted } from 'vue';
import UiModal from '../UiModal.vue';
import { apiFetch } from '../../api';
import { url as urlApi } from '../../config';
import { toast } from '../../ui/toast';
import { bs, fecha, ESTADOS, resultado } from './formato';

const props = defineProps({
  id: { type: Number, required: true },
  opciones: { type: Object, required: true },
});
const emit = defineEmits(['cerrar', 'cambio']);

const ficha = ref(null);
const cargando = ref(true);
const error = ref('');
const guardando = ref(false);

const v = computed(() => ficha.value?.venta);
const n = (x) => Number(x || 0);
const urlArchivo = (ruta) => (ruta ? urlApi(ruta) : '');
const tel = (c) => (c ? `tel:${String(c).replace(/[^\d+]/g, '')}` : null);
const titulo = computed(() => (v.value ? `Venta #${v.value.inscripcionId} · ${v.value.entidad || ''}` : 'Venta'));

async function cargar() {
  try {
    const r = await apiFetch(`/api/app/control-ventas/ventas/${props.id}`);
    const cuerpo = await r.json().catch(() => ({}));
    if (!r.ok) throw new Error(cuerpo.mensaje || 'No se pudo cargar la venta');
    ficha.value = cuerpo;
    error.value = '';
  } catch (e) {
    error.value = e.message;
  } finally {
    cargando.value = false;
  }
}
onMounted(cargar);

// ---------------------------------------------------------------- adjuntar comprobante
const puedeAdjuntar = computed(() => !!props.opciones.puedeAdjuntar
  && v.value && !v.value.conComprobante && n(v.value.importePuestos) > 0);
const form = ref(null);

function abrir() {
  form.value = { archivo: null, entidadBancaria: '', numComprobante: '' };
}

async function guardar() {
  const f = form.value;
  if (!f.archivo) {
    toast('Elige la foto o el PDF del comprobante', 'error');
    return;
  }
  const datos = new FormData();
  datos.append('archivo', f.archivo);
  // Opcionales: muchos comprobantes son una foto sin número legible, y eso basta.
  if (f.entidadBancaria.trim()) datos.append('entidadBancaria', f.entidadBancaria.trim());
  if (f.numComprobante.trim()) datos.append('numComprobante', f.numComprobante.trim());
  guardando.value = true;
  try {
    const cuerpo = await resultado(await apiFetch(`/api/app/inscripciones/${props.id}/comprobante`, {
      method: 'POST', body: datos,
    }));
    toast(cuerpo.mensaje || 'Comprobante adjuntado', 'ok');
    form.value = null;
    await cargar();
    emit('cambio');
  } catch (e) {
    toast(e.message, 'error');
  } finally {
    guardando.value = false;
  }
}
</script>

<template>
  <UiModal :titulo="titulo" ancho="980px" @cerrar="emit('cerrar')">
    <p v-if="error" class="aviso-error">{{ error }}</p>
    <div v-else-if="cargando" class="vacio">Cargando…</div>

    <template v-else-if="v">
      <!-- Las tres cifras que se buscan al abrir una venta. -->
      <section class="cifras">
        <div><span class="rotulo">Total vendido</span><strong>{{ bs(v.totalVendido) }} Bs</strong>
          <span class="muted chico">{{ bs(v.importePuestos) }} puestos + {{ bs(v.importeExtras) }} extras</span></div>
        <div><span class="rotulo">Recaudado (rendido)</span><strong>{{ bs(v.totalRecaudado) }} Bs</strong>
          <span class="muted chico">con comprobante adjunto</span></div>
        <div><span class="rotulo">Por rendir</span><strong :class="{ debe: n(v.porRendir) > 0 }">{{ bs(v.porRendir) }} Bs</strong>
          <span class="badge" :class="ESTADOS[v.estadoPago]?.clase">{{ ESTADOS[v.estadoPago]?.texto }}</span></div>
      </section>

      <!-- Comprobante: lo que decide si la venta está rendida. -->
      <section class="comprobante" :class="{ falta: !v.conComprobante && n(v.importePuestos) > 0 }">
        <div class="cab-seccion">
          <div>
            <h4>Comprobante de pago</h4>
            <p v-if="v.conComprobante" class="muted chico">
              Adjunto. {{ v.formaPago || '' }}<template v-if="v.entidadBancaria || v.numComprobante">
                · {{ v.entidadBancaria || '—' }} · N° {{ v.numComprobante || '—' }}</template>
            </p>
            <p v-else-if="n(v.importePuestos) > 0" class="chico">
              <strong>Falta el comprobante.</strong> Adjunta la foto del depósito, el voucher o el recibo;
              banco y número son opcionales.<template v-if="v.formaPago === 'Al contado'"> La venta se marcó «al contado».</template>
            </p>
            <p v-else class="muted chico">Venta sin costo: no hay nada que rendir.</p>
            <p v-if="v.extrasSinComprobante" class="chico">
              {{ v.extrasSinComprobante }} credencial(es) extra sin su comprobante (ver responsables).
            </p>
          </div>
          <div class="botones">
            <a v-if="v.comprobanteUrl" class="btn btn-sm" :href="urlArchivo(v.comprobanteUrl)" target="_blank" rel="noopener">Ver comprobante</a>
            <button v-if="puedeAdjuntar && !form" class="btn btn-primario btn-sm" @click="abrir">Adjuntar comprobante</button>
          </div>
        </div>

        <form v-if="form" class="formulario" @submit.prevent="guardar">
          <div class="rejilla-form">
            <label class="campo ancho"><span>Comprobante (foto o PDF)</span>
              <input type="file" class="control" accept="image/*,application/pdf" required
                     @change="form.archivo = $event.target.files[0] || null" /></label>
            <label class="campo"><span>Banco (opcional)</span>
              <input v-model="form.entidadBancaria" class="control" maxlength="120" /></label>
            <label class="campo"><span>N° de comprobante (opcional)</span>
              <input v-model="form.numComprobante" class="control" maxlength="100" /></label>
          </div>
          <div class="botones">
            <button type="button" class="btn btn-fantasma btn-sm" @click="form = null">Cancelar</button>
            <button class="btn btn-primario btn-sm" :disabled="guardando">{{ guardando ? 'Subiendo…' : 'Guardar comprobante' }}</button>
          </div>
        </form>
      </section>

      <!-- Todas las celdas del registro, agrupadas. -->
      <section class="datos">
        <div class="grupo">
          <h4>Venta</h4>
          <dl>
            <dt>N° / código</dt><dd>#{{ v.inscripcionId }} · {{ v.codigoVenta }}</dd>
            <dt>Fecha</dt><dd>{{ fecha(v.fecha, true) }}</dd>
            <dt>Vendedor</dt><dd>{{ v.vendedor || '—' }} <span class="muted">{{ v.vendedorUsuario }}</span></dd>
            <dt>Estado</dt><dd>{{ v.estadoInscripcion || '—' }}</dd>
            <dt>Forma de pago</dt><dd>{{ v.formaPago || '—' }}</dd>
          </dl>
        </div>
        <div class="grupo">
          <h4>Expositor</h4>
          <dl>
            <dt>Código</dt>
            <dd>{{ v.codigoExpositor }} <span class="muted">({{ v.baseCodigo }}<template v-if="v.comprasExpositor > 1">, {{ v.comprasExpositor }} compras</template>)</span></dd>
            <dt>Entidad</dt><dd>{{ v.entidad || '—' }}</dd>
            <dt>Rubro / tipo</dt><dd>{{ v.rubro || '—' }} · {{ v.tipoEntidad || '—' }}</dd>
            <dt>NIT</dt><dd>{{ v.nit || '—' }}</dd>
            <dt>Representante</dt><dd>{{ v.representante || '—' }} <span class="muted">C.I. {{ v.ciRepresentante || '—' }}</span></dd>
            <dt>Celular</dt>
            <dd><a v-if="v.celularRepresentante" :href="tel(v.celularRepresentante)">{{ v.celularRepresentante }}</a><span v-else>—</span></dd>
            <dt>Titular</dt>
            <dd>{{ v.titular || '—' }} <span class="muted">C.I. {{ v.ciTitular || '—' }}</span>
              <a v-if="v.celularTitular" :href="tel(v.celularTitular)"> · {{ v.celularTitular }}</a></dd>
          </dl>
        </div>
        <div class="grupo">
          <h4>Puestos, carpas y credenciales</h4>
          <dl>
            <dt>Puestos</dt><dd>{{ v.cantidadPuestos }} · {{ v.puestos || '—' }}</dd>
            <dt>Categoría</dt><dd>{{ v.categorias || '—' }}</dd>
            <dt>Subcategoría</dt><dd>{{ v.subcategorias || '—' }}</dd>
            <dt>Precio unitario</dt><dd>{{ v.preciosUnitarios || '—' }} Bs <span class="muted">(lista {{ bs(v.precioLista) }})</span></dd>
            <dt>Descuento / exención</dt>
            <dd>{{ n(v.descuento) > 0 ? bs(v.descuento) + ' Bs' : '—' }}<span v-if="v.motivoExencion" class="muted"> · {{ v.motivoExencion }}</span></dd>
            <dt>Carpas</dt><dd>{{ v.carpasCorresponden }} (una por puesto)</dd>
            <dt>Credenciales</dt>
            <dd>{{ v.credencialesIncluidas }} incluidas + {{ v.extras }} extra = {{ v.credencialesTotal }}</dd>
            <dt>Emitidas</dt>
            <dd>{{ v.credencialesEmitidas }} de {{ v.responsablesRegistrados }} registrados
              <span v-if="v.fechaEmision" class="muted">· desde {{ fecha(v.fechaEmision) }}</span></dd>
          </dl>
        </div>
      </section>

      <!-- Puestos -->
      <section class="seccion">
        <h4>Puestos adquiridos</h4>
        <div class="tabla-scroll">
          <table class="tabla compacta">
            <thead><tr><th>Puesto</th><th>Categoría</th><th>Subcategoría</th>
              <th class="num">Lista</th><th class="num">Precio</th><th class="num">Descuento</th><th>Exención</th></tr></thead>
            <tbody>
              <tr v-for="p in ficha.puestos" :key="p.puestoId">
                <td><strong>{{ p.codigo }}</strong></td><td>{{ p.categoria }}</td><td>{{ p.subcategoria }}</td>
                <td class="num">{{ bs(p.precioLista) }}</td><td class="num">{{ bs(p.precio) }}</td>
                <td class="num">{{ bs(p.descuento) }}</td><td>{{ p.exento ? p.motivoExencion : '—' }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <!-- Responsables y su credencial -->
      <section class="seccion">
        <h4>Responsables y credenciales ({{ ficha.responsables.length }})</h4>
        <p class="muted chico">
          «Emitida» = su credencial se generó (la virtual es la que se enviaba por WhatsApp al
          responsable legal). Que el envío llegara no quedó registrado.
        </p>
        <div class="tabla-scroll">
          <table class="tabla compacta">
            <thead><tr><th>Tipo</th><th>Nombre</th><th>C.I.</th><th>Celular</th><th class="num">Extra Bs</th><th>Credencial</th></tr></thead>
            <tbody>
              <tr v-for="r in ficha.responsables" :key="r.responsableId">
                <td><span class="badge" :class="r.tipo === 'EXTRA' ? 'badge-parcial' : 'badge-muted'">{{ r.tipo }}</span></td>
                <td>{{ r.nombre || '—' }}</td><td>{{ r.ci || '—' }}</td>
                <td><a v-if="r.celular" :href="tel(r.celular)">{{ r.celular }}</a><span v-else>—</span></td>
                <td class="num">
                  <template v-if="r.montoExtra != null">{{ bs(r.montoExtra) }}
                    <a v-if="r.comprobanteUrl" :href="urlArchivo(r.comprobanteUrl)" target="_blank" rel="noopener">comp.</a>
                    <span v-else class="badge badge-danger">sin comp.</span>
                  </template><span v-else class="muted">—</span>
                </td>
                <td class="chico">
                  <template v-if="r.credencialEmitida">
                    <span class="badge badge-ok">Emitida</span>
                    {{ fecha(r.emitidaEn, true) }}<span v-if="r.emitidaPor" class="muted"> · {{ r.emitidaPor }}</span>
                  </template>
                  <span v-else class="badge badge-muted">No emitida</span>
                </td>
              </tr>
              <tr v-if="!ficha.responsables.length"><td colspan="6" class="vacio">Sin responsables registrados.</td></tr>
            </tbody>
          </table>
        </div>
      </section>
    </template>

    <template #pie>
      <button class="btn" @click="emit('cerrar')">Cerrar</button>
    </template>
  </UiModal>
</template>

<style scoped>
.muted { color: var(--muted); }
.chico { font-size: 0.8rem; }
.aviso-error { color: var(--danger); }
.debe { color: var(--tramite); }
.rotulo { font-size: 0.7rem; font-weight: 800; text-transform: uppercase; letter-spacing: 0.06em; color: var(--muted); }

.cifras { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(180px, 100%), 1fr)); gap: 0.6rem; }
.cifras > div { display: flex; flex-direction: column; gap: 0.15rem; padding: 0.7rem 0.8rem; border: 1px solid var(--border); border-radius: var(--radio-sm); background: var(--panel-2); }
.cifras strong { font-size: 1.25rem; font-variant-numeric: tabular-nums; }
.cifras .badge { align-self: flex-start; }

.comprobante { display: flex; flex-direction: column; gap: 0.6rem; padding: 0.75rem 0.85rem; border: 1px solid var(--border); border-radius: var(--radio-sm); }
/* Ámbar y no rojo: falta un papel, no hay una avería. */
.comprobante.falta { border-left: 3px solid var(--tramite); }
.comprobante h4 { margin: 0; font-size: 0.9rem; }
.comprobante p { margin: 0.2rem 0 0; }

.datos { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(270px, 100%), 1fr)); gap: 0.8rem; }
.grupo h4, .seccion h4 { margin: 0 0 0.4rem; font-size: 0.9rem; }
dl { display: grid; grid-template-columns: max-content 1fr; gap: 0.25rem 0.7rem; margin: 0; font-size: 0.86rem; }
dt { color: var(--muted); font-weight: 600; }
dd { margin: 0; overflow-wrap: anywhere; }

.seccion { display: flex; flex-direction: column; gap: 0.4rem; min-width: 0; }
.seccion p { margin: 0; }
.cab-seccion { display: flex; justify-content: space-between; align-items: flex-start; gap: 0.6rem; flex-wrap: wrap; }
.botones { display: flex; gap: 0.4rem; flex-wrap: wrap; justify-content: flex-end; }

.tabla-scroll { overflow-x: auto; min-width: 0; max-width: 100%; }
.compacta { font-size: 0.84rem; }
.compacta th, .compacta td { padding: 0.45rem 0.55rem; vertical-align: top; }
.compacta .num { text-align: right; font-variant-numeric: tabular-nums; white-space: nowrap; }

.formulario { display: flex; flex-direction: column; gap: 0.6rem; padding: 0.8rem; border: 1px solid var(--border); border-radius: var(--radio-sm); background: var(--panel-2); }
.rejilla-form { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(200px, 100%), 1fr)); gap: 0.6rem; }
.rejilla-form .ancho { grid-column: 1 / -1; }
</style>
