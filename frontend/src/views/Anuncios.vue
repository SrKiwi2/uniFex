<script setup>
/**
 * Publicar anuncios para todos los usuarios.
 *
 * Lo que se publica aqui aparece **al instante** en la pantalla de todos los que tengan la
 * aplicacion abierta, estén donde estén —mapa, registro de venta, credenciales—, como una
 * tarjeta arriba a la derecha que no les tapa el trabajo. Quien entre despues lo recibe al abrir.
 *
 * El aviso de "esto lo van a ver todos" esta a la vista a proposito: un anuncio no se puede
 * "deshacer" en la cabeza de nadie, solo retirar de la pantalla.
 */
import { ref, reactive, computed, onMounted } from 'vue';
import { apiFetch } from '../api';
import { toast } from '../ui/toast';
import { aviso, alertaConAccion } from '../ui/alerta';
import { useAnunciosStore } from '../stores/anuncios';

const tienda = useAnunciosStore();
const historial = ref([]);
const cargando = ref(true);
const publicando = ref(false);
const ocupado = ref(false);

const NIVELES = [
  { v: 'info', txt: 'ℹ️ Información', desc: 'Algo que conviene saber.' },
  { v: 'aviso', txt: '⚠️ Aviso', desc: 'Algo que cambia cómo trabajan.' },
  { v: 'urgente', txt: '🚨 Urgente', desc: 'Hay que verlo ya.' },
];

/** Duraciones en minutos. "Sin límite" = hasta que alguien lo retire a mano. */
const DURACIONES = [
  { v: 30, txt: '30 minutos' },
  { v: 120, txt: '2 horas' },
  { v: 480, txt: 'Toda la jornada (8 h)' },
  { v: null, txt: 'Sin límite (hasta retirarlo)' },
];

const form = reactive({ titulo: '', mensaje: '', nivel: 'info', minutos: 120 });

const vigentes = computed(() => historial.value.filter((a) => a.activo && !vencido(a)));
const pasados = computed(() => historial.value.filter((a) => !a.activo || vencido(a)));

function vencido(a) {
  return !!a.vigenteHasta && new Date(a.vigenteHasta) <= new Date();
}

const cuando = (t) => {
  if (!t) return '';
  try { return new Date(t).toLocaleString('es-BO', { dateStyle: 'short', timeStyle: 'short' }); }
  catch { return ''; }
};

async function cargar() {
  cargando.value = true;
  try {
    const r = await apiFetch('/api/app/anuncios/historial');
    if (r.ok) historial.value = await r.json();
  } catch (e) {
    toast(e.message, 'error');
  } finally {
    cargando.value = false;
  }
}
onMounted(cargar);

async function publicar() {
  if (!form.mensaje.trim()) { toast('Escribe el mensaje', 'error'); return; }
  const si = await alertaConAccion(
    `Esto va a salir en la pantalla de TODOS los usuarios conectados ahora mismo. ¿Publicarlo?`,
    form.nivel === 'urgente' ? 'advertencia' : 'info', () => {});
  if (!si || publicando.value) return;

  publicando.value = true;
  try {
    const r = await apiFetch('/api/app/anuncios', {
      method: 'POST',
      body: JSON.stringify({
        titulo: form.titulo.trim() || null,
        mensaje: form.mensaje.trim(),
        nivel: form.nivel,
        minutos: form.minutos,
      }),
    });
    const d = await r.json().catch(() => ({}));
    if (!r.ok) { await aviso(d.mensaje || 'No se pudo publicar', 'error', 0); return; }
    // Se aplica tambien aqui: quien publica tiene que ver su propio anuncio salir, igual que
    // los demas. Es la confirmacion de que de verdad se envio.
    tienda.aplicar(d);
    form.titulo = '';
    form.mensaje = '';
    await cargar();
    toast('Anuncio publicado', 'ok');
  } catch (e) {
    await aviso(e.message, 'error', 0);
  } finally {
    publicando.value = false;
  }
}

async function retirar(a) {
  const si = await alertaConAccion(
    `¿Retirar este anuncio? Desaparecerá de la pantalla de todos, hayan cerrado el aviso o no.`,
    'advertencia', () => {});
  if (!si || ocupado.value) return;
  ocupado.value = true;
  try {
    const r = await apiFetch(`/api/app/anuncios/${a.id}`, { method: 'DELETE' });
    if (!r.ok) { toast('No se pudo retirar', 'error'); return; }
    tienda.aplicar({ id: a.id, activo: false });
    await cargar();
    toast('Anuncio retirado', 'ok');
  } catch (e) {
    toast(e.message, 'error');
  } finally {
    ocupado.value = false;
  }
}
</script>

<template>
  <p class="muted nota">
    Lo que publiques aquí aparece <strong>al instante</strong> en la pantalla de todos los
    usuarios conectados, estén en el mapa, registrando una venta o en cualquier otra opción.
    Sale arriba a la derecha y <strong>no les bloquea el trabajo</strong>: cada uno lo cierra
    cuando lo leyó.
  </p>

  <section class="card redactar">
    <h2>Nuevo anuncio</h2>

    <label class="campo">
      <span>Título <span class="muted">(opcional)</span></span>
      <input v-model="form.titulo" class="control" maxlength="120"
             placeholder="Ej.: Cambio de horario" />
    </label>

    <label class="campo">
      <span>Mensaje *</span>
      <textarea v-model="form.mensaje" class="control" rows="3" maxlength="1000"
                placeholder="Ej.: La feria abre hoy a las 9:00. No vendan la zona C hasta nuevo aviso."></textarea>
    </label>

    <div class="campo">
      <span>Tipo</span>
      <div class="niveles">
        <button v-for="n in NIVELES" :key="n.v" type="button" class="nivel"
                :class="[`n-${n.v}`, { marcado: form.nivel === n.v }]"
                @click="form.nivel = n.v">
          <strong>{{ n.txt }}</strong>
          <span class="muted">{{ n.desc }}</span>
        </button>
      </div>
    </div>

    <label class="campo">
      <span>Cuánto tiempo se muestra</span>
      <select v-model="form.minutos" class="control">
        <option v-for="d in DURACIONES" :key="String(d.v)" :value="d.v">{{ d.txt }}</option>
      </select>
      <!-- Lo que vence solo es lo que no hay que acordarse de retirar. -->
      <span class="muted chico">
        Pasado ese tiempo deja de verse solo. Con «sin límite» hay que retirarlo a mano.
      </span>
    </label>

    <button class="btn btn-primario btn-publicar" :disabled="publicando || !form.mensaje.trim()"
            @click="publicar">
      {{ publicando ? 'Publicando…' : '📣 Publicar para todos' }}
    </button>
  </section>

  <section class="listado">
    <h2>En pantalla ahora ({{ vigentes.length }})</h2>
    <div v-if="cargando" class="vacio">Cargando…</div>
    <p v-else-if="!vigentes.length" class="muted">No hay ningún anuncio publicado.</p>
    <ul v-else class="lista">
      <li v-for="a in vigentes" :key="a.id" class="card fila" :class="`n-${a.nivel}`">
        <div class="quien">
          <strong v-if="a.titulo">{{ a.titulo }}</strong>
          <p class="mensaje">{{ a.mensaje }}</p>
          <span class="muted chico">
            {{ a.publicadoPor || '—' }} · {{ cuando(a.publicado) }}
            <template v-if="a.vigenteHasta"> · hasta {{ cuando(a.vigenteHasta) }}</template>
            <template v-else> · sin límite</template>
          </span>
        </div>
        <button class="btn btn-peligro btn-sm" :disabled="ocupado" @click="retirar(a)">Retirar</button>
      </li>
    </ul>

    <template v-if="pasados.length">
      <h2>Anteriores ({{ pasados.length }})</h2>
      <ul class="lista">
        <li v-for="a in pasados" :key="a.id" class="card fila apagada">
          <div class="quien">
            <strong v-if="a.titulo">{{ a.titulo }}</strong>
            <p class="mensaje">{{ a.mensaje }}</p>
            <span class="muted chico">
              {{ a.publicadoPor || '—' }} · {{ cuando(a.publicado) }} ·
              {{ a.activo ? 'venció' : 'retirado' }}
            </span>
          </div>
        </li>
      </ul>
    </template>
  </section>
</template>

<style scoped>
.nota { margin: 0 0 1rem; line-height: 1.55; }
.chico { font-size: 0.83rem; }
.redactar { padding: 1rem; display: flex; flex-direction: column; gap: 0.9rem; margin-bottom: 1.5rem; }
.redactar h2, .listado h2 { margin: 0; font-size: 1.05rem; }
.campo { display: flex; flex-direction: column; gap: 0.3rem; }

.niveles { display: grid; grid-template-columns: repeat(3, 1fr); gap: 0.5rem; }
.nivel {
  display: flex; flex-direction: column; gap: 0.15rem; text-align: left;
  padding: 0.6rem 0.7rem; border-radius: 10px; cursor: pointer; font: inherit; color: inherit;
  border: 2px solid var(--border); background: var(--panel);
  min-height: 56px;
}
.nivel.marcado { border-color: var(--color-nivel); background: color-mix(in srgb, var(--color-nivel) 12%, transparent); }
.n-info    { --color-nivel: var(--acento, #2563eb); }
.n-aviso   { --color-nivel: var(--tramite, #d97706); }
.n-urgente { --color-nivel: var(--danger, #dc2626); }

.btn-publicar { min-height: 50px; font-size: 1rem; }

.listado { display: flex; flex-direction: column; gap: 0.6rem; }
.lista { list-style: none; margin: 0 0 1rem; padding: 0; display: flex; flex-direction: column; gap: 0.6rem; }
.fila {
  display: flex; align-items: flex-start; gap: 0.75rem; padding: 0.8rem;
  border-left: 5px solid var(--color-nivel, var(--border));
}
.fila.apagada { opacity: 0.6; border-left-color: var(--border); }
.quien { display: flex; flex-direction: column; gap: 0.15rem; min-width: 0; flex: 1; }
.mensaje { margin: 0; line-height: 1.45; overflow-wrap: anywhere; }

@media (max-width: 560px) {
  .niveles { grid-template-columns: 1fr; }
}
</style>
