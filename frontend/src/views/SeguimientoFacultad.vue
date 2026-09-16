<script setup>
/**
 * Seguimiento de la venta de una facultad.
 *
 * Aquí "facultad" es el ÁREA académica (ACEF, ACBN, ACYT): los vendedores cuelgan de ella por su
 * carrera. Quien monitorea ve la suya y sólo la suya —el servidor la resuelve desde su usuario,
 * no desde la URL—; administración puede mirar cualquiera y es quien asigna.
 *
 * **Aquí no hay dinero, y es el punto del módulo.** Nombre del vendedor, categoría, números de
 * caseta y cuántas. Ni precios ni totales. No está escondido en la pantalla: el servidor no los
 * manda, así que no hay nada que se pueda filtrar por un descuido de maquetación.
 */
import { ref, computed, onMounted } from 'vue';
import { apiFetch } from '../api';
import { toast } from '../ui/toast';
import { useAuthStore } from '../stores/auth';
import UiModal from '../components/UiModal.vue';

const auth = useAuthStore();
const esAdmin = computed(() => auth.puedeEditarPlano);

const informe = ref(null);
const sinAsignar = ref(false);
const mensajeSinAsignar = ref('');
const cargando = ref(true);
const filtro = ref('');

// --- sólo administración ---
const areas = ref([]);
const asignaciones = ref([]);
const usuarios = ref([]);
const areaVista = ref(null);
const modalConfig = ref(false);
const guardando = ref(false);

const vendedoresFiltrados = computed(() => {
  const q = filtro.value.trim().toLowerCase();
  const lista = informe.value?.vendedores || [];
  if (!q) return lista;
  return lista.filter((v) => `${v.nombre} ${v.carrera || ''}`.toLowerCase().includes(q));
});

/** Los que no han vendido nada. Se cuentan aparte: es el dato sobre el que se actúa. */
const sinVender = computed(() =>
  (informe.value?.vendedores || []).filter((v) => v.casetasVendidas === 0).length);

async function cargar() {
  cargando.value = true;
  sinAsignar.value = false;
  try {
    const ruta = areaVista.value
      ? `/api/app/seguimiento-facultad?areaId=${areaVista.value}`
      : '/api/app/seguimiento-facultad';
    const r = await apiFetch(ruta);
    const d = await r.json().catch(() => ({}));
    if (d?.sinAsignar) {
      sinAsignar.value = true;
      mensajeSinAsignar.value = d.mensaje || '';
      informe.value = null;
      return;
    }
    if (!r.ok) { toast(d.mensaje || 'No se pudo cargar el seguimiento', 'error'); return; }
    informe.value = d;
    if (!areaVista.value) areaVista.value = d.areaId;
  } catch (e) {
    toast(e.message, 'error');
  } finally {
    cargando.value = false;
  }
}

async function cargarConfig() {
  if (!esAdmin.value) return;
  try {
    const [rA, rAsig, rU] = await Promise.all([
      apiFetch('/api/app/areas'),
      apiFetch('/api/app/seguimiento-facultad/asignaciones'),
      apiFetch('/api/app/usuarios'),
    ]);
    if (rA.ok) areas.value = await rA.json();
    if (rAsig.ok) asignaciones.value = await rAsig.json();
    if (rU.ok) usuarios.value = await rU.json();
  } catch { /* la pantalla sigue sirviendo para mirar aunque no se pueda configurar */ }
}

onMounted(async () => { await cargar(); cargarConfig(); });

const areaDe = (usuarioId) => asignaciones.value.find((a) => a.usuarioId === usuarioId)?.areaId ?? '';

async function asignar(usuarioId, areaId) {
  guardando.value = true;
  try {
    const r = await apiFetch(`/api/app/seguimiento-facultad/asignaciones/${usuarioId}`, {
      method: 'PUT',
      // '' es "ninguna": el <select> devuelve texto y el servidor espera un id o null.
      body: JSON.stringify({ areaId: areaId === '' ? null : Number(areaId) }),
    });
    const d = await r.json().catch(() => ({}));
    if (!r.ok || d.ok === false) { toast(d.mensaje || 'No se pudo asignar', 'error'); return; }
    await cargarConfig();
    toast('Asignación guardada', 'ok');
  } catch (e) {
    toast(e.message, 'error');
  } finally {
    guardando.value = false;
  }
}

/** "6, 7 y 14" — como se dice en voz alta, no como se programa. */
function listar(codigos) {
  if (!codigos?.length) return '';
  if (codigos.length === 1) return String(codigos[0]);
  return `${codigos.slice(0, -1).join(', ')} y ${codigos[codigos.length - 1]}`;
}
</script>

<template>
  <!-- Se dice con todas las letras para qué sirve y para qué NO: alguien que abre esto buscando
       cuánto se recaudó tiene que saber en el primer renglón que aquí no está. -->
  <p class="muted nota">
    Cómo va la venta en tu facultad: quién ha vendido, de qué categoría y qué casetas.
    <strong>Es informativo</strong> — no muestra precios ni totales de dinero.
  </p>

  <div v-if="esAdmin" class="barra-admin">
    <label class="campo-inline">
      <span class="muted">Facultad</span>
      <select v-model="areaVista" class="control" @change="cargar">
        <option v-for="a in areas" :key="a.id" :value="a.id">{{ a.sigla }}</option>
      </select>
    </label>
    <span class="crecer"></span>
    <button class="btn" @click="modalConfig = true">⚙️ Quién sigue qué facultad</button>
  </div>

  <!-- Sin facultad asignada no es un error: es que falta configurarlo. -->
  <div v-if="sinAsignar" class="card sin-asignar">
    <strong>Todavía no tienes una facultad asignada.</strong>
    <p class="muted">{{ mensajeSinAsignar }}</p>
  </div>

  <div v-else-if="cargando" class="vacio">Cargando…</div>

  <template v-else-if="informe">
    <div class="tarjetas-resumen">
      <div class="card resumen">
        <strong>{{ informe.sigla }}</strong><span>facultad</span>
      </div>
      <div class="card resumen">
        <strong>{{ informe.totalVendedores }}</strong><span>vendedores</span>
      </div>
      <div class="card resumen">
        <strong>{{ informe.totalCasetas }}</strong><span>casetas vendidas</span>
      </div>
      <div class="card resumen" :class="{ ojo: sinVender > 0 }">
        <strong>{{ sinVender }}</strong><span>sin vender todavía</span>
      </div>
    </div>

    <input v-model="filtro" class="control busca" placeholder="Buscar por nombre o carrera…" />

    <p v-if="!vendedoresFiltrados.length" class="muted">
      No hay vendedores que mostrar en esta facultad.
    </p>

    <ul v-else class="lista">
      <li v-for="v in vendedoresFiltrados" :key="v.usuarioId" class="card vendedor"
          :class="{ apagado: v.casetasVendidas === 0 }">
        <header>
          <div class="quien">
            <strong>{{ v.nombre }}</strong>
            <span class="muted chico">{{ v.carrera || 'sin carrera' }}</span>
          </div>
          <!-- Casetas, no ventas: una venta de tres casetas son tres, que es como se lee el plano. -->
          <span class="badge" :class="v.casetasVendidas ? 'badge-ok' : 'badge-muted'">
            {{ v.casetasVendidas }} caseta{{ v.casetasVendidas === 1 ? '' : 's' }}
          </span>
        </header>

        <p v-if="!v.porCategoria.length" class="muted chico nada">
          Todavía no ha vendido ninguna caseta.
        </p>
        <table v-else class="tabla-cats">
          <thead>
            <tr><th>Categoría</th><th>Casetas</th><th class="num">Cantidad</th></tr>
          </thead>
          <tbody>
            <tr v-for="c in v.porCategoria" :key="c.categoriaId">
              <td class="cat">{{ c.categoria }}</td>
              <td class="codigos">{{ listar(c.casetas) }}</td>
              <td class="num">{{ c.cantidad }}</td>
            </tr>
          </tbody>
        </table>
      </li>
    </ul>
  </template>

  <!-- ------------------------------------------------- quién sigue qué facultad -->
  <UiModal v-if="modalConfig" titulo="Quién sigue qué facultad" ancho="720px"
           @cerrar="modalConfig = false">
    <p class="muted nota">
      Un usuario con facultad asignada ve <strong>sólo la suya</strong> al abrir este módulo.
      Administración puede mirar cualquiera.
    </p>
    <!-- El permiso es por ROL, no por usuario: asignar el área no da la pantalla por sí solo.
         Decirlo aquí evita el "se la asigné y no le sale". -->
    <p class="muted chico aviso-permiso">
      Si a alguien no le aparece el módulo en el menú, márcale la pantalla
      <strong>«Seguimiento por facultad»</strong> a su rol, en «Permisos por rol».
    </p>
    <div class="tabla-scroll">
      <table class="tabla">
        <thead><tr><th>Usuario</th><th>Rol</th><th>Facultad que sigue</th></tr></thead>
        <tbody>
          <tr v-for="u in usuarios" :key="u.id">
            <td>{{ u.persona ? `${u.persona.nombre} ${u.persona.paterno || ''}` : u.username }}</td>
            <td class="muted">{{ u.rol?.nombre }}</td>
            <td>
              <select class="control" :value="areaDe(u.id)" :disabled="guardando"
                      @change="asignar(u.id, $event.target.value)">
                <option value="">— ninguna —</option>
                <option v-for="a in areas" :key="a.id" :value="a.id">{{ a.sigla }}</option>
              </select>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <template #pie>
      <button class="btn btn-fantasma" @click="modalConfig = false">Cerrar</button>
    </template>
  </UiModal>
</template>

<style scoped>
.nota { margin: 0 0 1rem; line-height: 1.55; }
.chico { font-size: 0.83rem; }
.crecer { flex: 1; }

.barra-admin { display: flex; align-items: center; gap: 0.75rem; flex-wrap: wrap; margin-bottom: 1rem; }
.campo-inline { display: flex; align-items: center; gap: 0.4rem; }

.sin-asignar { padding: 1.2rem; display: flex; flex-direction: column; gap: 0.3rem; }
.sin-asignar p { margin: 0; }

.tarjetas-resumen { display: grid; grid-template-columns: repeat(4, 1fr); gap: 0.75rem; margin-bottom: 1rem; }
.resumen { padding: 0.8rem; display: flex; flex-direction: column; align-items: center; gap: 0.15rem; text-align: center; }
.resumen strong { font-size: 1.7rem; line-height: 1; }
.resumen span { font-size: 0.82rem; opacity: 0.75; }
/* Los que no han vendido nada son sobre lo que hay que actuar, así que saltan a la vista. */
.resumen.ojo strong { color: var(--tramite); }

.busca { width: 100%; margin-bottom: 1rem; }

.lista { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 0.7rem; }
.vendedor { padding: 0.85rem; display: flex; flex-direction: column; gap: 0.6rem; }
.vendedor.apagado { opacity: 0.7; }
.vendedor > header { display: flex; align-items: center; justify-content: space-between; gap: 0.6rem; flex-wrap: wrap; }
.quien { display: flex; flex-direction: column; min-width: 0; }
.nada { margin: 0; }

.tabla-cats { width: 100%; border-collapse: collapse; font-size: 0.92rem; }
.tabla-cats th, .tabla-cats td { text-align: left; padding: 0.35rem 0.5rem; border-bottom: 1px solid var(--border); }
.tabla-cats th { font-size: 0.72rem; text-transform: uppercase; letter-spacing: 0.04em; color: var(--muted); }
.tabla-cats tr:last-child td { border-bottom: none; }
.tabla-cats .cat { font-weight: 600; }
/* Los números de caseta son lo que más se lee de esta tabla: se les da sitio para respirar. */
.tabla-cats .codigos { overflow-wrap: anywhere; }
.tabla-cats .num { text-align: right; font-variant-numeric: tabular-nums; white-space: nowrap; }

.aviso-permiso { margin: 0 0 0.8rem; }
.tabla-scroll { overflow-x: auto; }

@media (max-width: 700px) {
  .tarjetas-resumen { grid-template-columns: repeat(2, 1fr); }
}
@media (max-width: 420px) {
  .tarjetas-resumen { grid-template-columns: 1fr; }
  .resumen { flex-direction: row; justify-content: flex-start; gap: 0.5rem; }
  .resumen strong { font-size: 1.25rem; }
}
</style>
