<script setup>
import { ref, computed, onMounted } from 'vue';
import { apiFetch } from '../api';
import { url as urlApi } from '../config.js';
import { toast } from '../ui/toast';
import { alerta } from '../ui/alerta';
import { descargarPdf } from '../ui/descargas';

/*
 * Credenciales del personal de apoyo. Vista solo de administracion (el servidor exige
 * ADMINISTRA): agrupa TODAS las fichas activas por dependencia e imprime sus credenciales
 * 10x15 con QR. A diferencia de expositores no hay "aptas/no aptas": el apoyo no exige
 * comprobante ni foto, asi que todo lo activo sale.
 */
const cargando = ref(true);
const busqueda = ref('');
const generando = ref(false);
const fichas = ref([]);
const seleccion = ref(new Set());
const abiertas = ref(new Set());
/** Fotos que la base dice tener pero que no estan en disco: se marcan una vez. */
const rotas = ref(new Set());

async function cargar() {
  cargando.value = true;
  try {
    const r = await apiFetch('/api/app/personal-apoyo/credenciales');
    if (!r.ok) throw new Error('No se pudo cargar el personal');
    fichas.value = await r.json();
  } catch (e) {
    toast(e.message, 'error');
  } finally {
    cargando.value = false;
  }
}

/** Las fichas, AGRUPADAS POR DEPENDENCIA: asi se imprimen, por unidades. */
const porDependencia = computed(() => {
  const q = busqueda.value.trim().toLowerCase();
  const m = new Map();
  for (const f of fichas.value) {
    if (q && ![f.nombreCompleto, f.ci, f.rol, f.descripcionTarea]
        .some((c) => (c || '').toLowerCase().includes(q))) continue;
    const clave = f.idDependencia ?? 0;
    if (!m.has(clave)) {
      m.set(clave, { id: clave, nombre: f.dependenciaNombre || 'Sin dependencia', fichas: [] });
    }
    m.get(clave).fichas.push(f);
  }
  return [...m.values()].sort((a, b) => a.nombre.localeCompare(b.nombre));
});

const marcadas = computed(() => fichas.value.filter((f) => seleccion.value.has(f.id)));

function alternar(f) {
  const s = new Set(seleccion.value);
  s.has(f.id) ? s.delete(f.id) : s.add(f.id);
  seleccion.value = s;
}

function marcarGrupo(g, marcar) {
  const s = new Set(seleccion.value);
  for (const f of g.fichas) marcar ? s.add(f.id) : s.delete(f.id);
  seleccion.value = s;
}

function alternarGrupo(g) {
  const id = g.id;
  const s = new Set(abiertas.value);
  s.has(id) ? s.delete(id) : s.add(id);
  abiertas.value = s;
}

/*
 * Lanza la impresion duplex 4-up A4. `ids` vacio = TODO el personal activo (el servidor lo resuelve).
 * 4 credenciales por cara (2x2), reversos espejados para imprimir a doble cara por borde largo.
 */
async function imprimir(ids, etiqueta) {
  if (generando.value) return;
  generando.value = true;
  try {
    const nombre = `credenciales-apoyo-duplex-${etiqueta}.pdf`;
    await descargarPdf('/api/app/personal-apoyo/credenciales/pdf/duplex', nombre, {
      method: 'POST',
      body: JSON.stringify({ ids }),
    });
    toast('Credenciales duplex generadas', 'ok');
  } catch (e) {
    alerta(e.message, 'error', 0);
  } finally {
    generando.value = false;
  }
}

function imprimirMarcadas() {
  imprimir([...seleccion.value], `marcadas-${seleccion.value.size}`);
}

function imprimirTodas() {
  imprimir([], `todas-${fichas.value.length}`);
}

onMounted(async () => {
  await cargar();
  // Todo abierto al entrar: es una lista para recorrer, no un acordeon que esconder.
  abiertas.value = new Set(porDependencia.value.map((g) => g.id));
});
</script>

<template>
  <div class="cred-apoyo">
    <p class="muted nota">
      Credenciales del personal de apoyo en A4 duplex 4-up: 4 por cara, reversos espejados.
      Imprimir a doble cara por borde largo. Agrupadas por dependencia.
    </p>

    <div class="barra">
      <input v-model="busqueda" class="control" placeholder="Buscar por nombre, C.I., rol o tarea…" />
      <span class="crecer"></span>
      <button class="btn" :disabled="generando || !marcadas.length" @click="imprimirMarcadas">
        Imprimir {{ marcadas.length || '' }} marcada{{ marcadas.length === 1 ? '' : 's' }}
      </button>
      <button class="btn btn-primario" :disabled="generando || !fichas.length" @click="imprimirTodas">
        {{ generando ? 'Generando…' : !fichas.length ? 'Nada que imprimir' : `Imprimir las ${fichas.length}` }}
      </button>
    </div>

    <div v-if="cargando" class="vacio">Cargando…</div>
    <div v-else-if="!porDependencia.length" class="vacio">No hay personal de apoyo activo.</div>

    <section v-for="g in porDependencia" :key="g.id" class="card grupo">
      <header class="cab-grupo" @click="alternarGrupo(g)">
        <span class="flecha" :class="{ abierta: abiertas.has(g.id) }">▶</span>
        <strong>{{ g.nombre }}</strong>
        <span class="muted">{{ g.fichas.length }} persona{{ g.fichas.length === 1 ? '' : 's' }}</span>
        <span class="crecer"></span>
        <button class="btn btn-sm" :disabled="generando"
                @click.stop="imprimir(g.fichas.map((f) => f.id), `dep-${g.fichas.length}`)">
          🖨 Imprimir dependencia
        </button>
        <button class="btn btn-sm btn-fantasma" @click.stop="marcarGrupo(g, true)">Marcar</button>
      </header>

      <table v-if="abiertas.has(g.id)" class="tabla">
        <thead>
          <tr><th></th><th></th><th>Nombre</th><th>C.I.</th><th>Rol</th><th>Tarea</th><th>Código</th></tr>
        </thead>
        <tbody>
          <tr v-for="f in g.fichas" :key="f.id">
            <td><input type="checkbox" :checked="seleccion.has(f.id)" @change="alternar(f)" /></td>
            <td><img v-if="f.fotoUrl && !rotas.has(f.id)" :src="urlApi(f.fotoUrl)" :alt="f.nombreCompleto"
                     class="mini" @error="rotas = new Set(rotas).add(f.id)" />
              <span v-else class="mini sinfoto">—</span></td>
            <td><strong>{{ f.nombreCompleto }}</strong></td>
            <td>{{ f.ci }}</td>
            <td><span class="badge">{{ f.rol }}</span></td>
            <td>{{ f.descripcionTarea || '—' }}</td>
            <td><code class="codigo">{{ f.codigo }}</code></td>
          </tr>
        </tbody>
      </table>
    </section>
  </div>
</template>

<style scoped>
.cred-apoyo { display: flex; flex-direction: column; gap: 1rem; }
.nota { margin: 0; font-size: 0.85rem; }
.barra { display: flex; align-items: center; gap: 0.6rem; flex-wrap: wrap; }
.barra .control { flex: 1; min-width: 200px; }
.crecer { flex: 1; }
.grupo { padding: 0; overflow: hidden; }
.cab-grupo { display: flex; align-items: center; gap: 0.6rem; padding: 0.7rem 1rem; cursor: pointer; }
.flecha { font-size: 0.7rem; color: var(--muted); transition: transform 0.15s; }
.flecha.abierta { transform: rotate(90deg); }
.codigo { font-size: 0.75rem; }
.mini { width: 32px; height: 32px; border-radius: 50%; object-fit: cover; }
.mini.sinfoto { display: inline-flex; align-items: center; justify-content: center; background: var(--border); color: var(--muted); }
.vacio { padding: 2rem; text-align: center; color: var(--muted); }
</style>
