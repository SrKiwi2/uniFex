<script setup>
import { ref, onMounted } from 'vue';
import { useRoute } from 'vue-router';
import { url as urlApi } from '../config.js';

/*
 * Lo que se ve al escanear el QR de una credencial.
 *
 * SIN sesion a proposito: quien controla la puerta lo abre con su propio telefono, muchas
 * veces sin cuenta en el sistema y con la red de la feria. Por eso esta pantalla no usa
 * `apiFetch` —que añade el token y cierra sesion ante un 401— sino `fetch` pelado contra
 * /api/publico.
 *
 * Lo que hace autentica a una credencial no es esta pantalla, es la FIRMA del codigo: sin el
 * secreto del servidor no se puede fabricar uno que llegue siquiera a consultar la base. Un
 * codigo alterado responde 404 y aqui sale en rojo.
 */

const route = useRoute();
const cargando = ref(true);
const cred = ref(null);
const error = ref('');
/*
 * La foto puede faltar en disco aunque la base diga que existe: pasa al restaurar una copia
 * de la base sin copiar los archivos subidos. Sin esto, el navegador pinta el texto alterno
 * dentro del recuadro y queda peor que no enseñar nada.
 */
const fotoRota = ref(false);

onMounted(async () => {
  try {
    const r = await fetch(urlApi(`/api/publico/credencial/${encodeURIComponent(route.params.codigo)}`));
    const d = await r.json().catch(() => ({}));
    if (r.ok && d.valida) cred.value = d;
    else error.value = d.mensaje || 'Esta credencial no es válida';
  } catch {
    error.value = 'No se pudo verificar. Revisa la conexión e inténtalo otra vez.';
  } finally {
    cargando.value = false;
  }
});
</script>

<template>
  <div class="credencial-publica">
    <div v-if="cargando" class="estado">
      <div class="giro" aria-hidden="true"></div>
      <p>Verificando credencial…</p>
    </div>

    <!-- El fallo va en rojo y ocupa la pantalla: en la puerta se mira de reojo y a un metro
         de distancia, asi que no puede ser un texto pequeño junto a los datos. -->
    <div v-else-if="error" class="tarjeta invalida">
      <span class="marca" aria-hidden="true">✕</span>
      <h1>Credencial no válida</h1>
      <p>{{ error }}</p>
      <p class="pie">No permitir el ingreso con este código.</p>
    </div>

    <div v-else class="tarjeta valida">
      <header class="cabecera">
        <span class="marca" aria-hidden="true">✓</span>
        <div>
          <h1>Credencial válida</h1>
          <p>FEXPO UAP · Expositor</p>
        </div>
      </header>

      <div class="persona">
        <img v-if="cred.fotoUrl && !fotoRota" :src="urlApi(cred.fotoUrl)" :alt="cred.nombre"
             class="foto" @error="fotoRota = true" />
        <div v-else class="foto sinfoto" aria-hidden="true">Sin foto</div>
        <div class="quien">
          <h2>{{ cred.nombre }}</h2>
          <p class="ci">C.I. {{ cred.ci || '—' }}</p>
          <span class="rol">{{ cred.esTitular ? 'Responsable 1' : 'Responsable 2' }}</span>
        </div>
      </div>

      <dl class="datos">
        <div><dt>Entidad</dt><dd>{{ cred.entidad || '—' }}</dd></div>
        <div v-if="cred.rubro"><dt>Rubro</dt><dd>{{ cred.rubro }}</dd></div>
        <div><dt>Categoría</dt><dd>{{ cred.categoria || '—' }}</dd></div>
        <!-- La caseta es el dato que se contrasta de verdad en la puerta: va destacado. -->
        <div class="ancho"><dt>Caseta{{ (cred.casetas || '').includes(',') ? 's' : '' }}</dt>
          <dd class="casetas">{{ cred.casetas || '—' }}</dd></div>
      </dl>
    </div>
  </div>
</template>

<style scoped>
.credencial-publica {
  min-height: 100dvh; min-height: 100vh;
  display: grid; place-items: center;
  padding: calc(1rem + var(--safe-top)) 1rem calc(1rem + var(--safe-bottom));
  background: var(--bg);
}

.estado { text-align: center; color: var(--muted); display: grid; gap: 0.9rem; justify-items: center; }
.giro {
  width: 34px; height: 34px; border-radius: 50%;
  border: 3px solid var(--border); border-top-color: var(--acento);
  animation: girar 0.8s linear infinite;
}
@keyframes girar { to { transform: rotate(360deg); } }

.tarjeta {
  width: 100%; max-width: 460px;
  background: var(--panel); border: 1px solid var(--border);
  border-radius: var(--radio); box-shadow: var(--sombra-md);
  padding: 1.3rem; display: flex; flex-direction: column; gap: 1.1rem;
}
/* Una franja de color arriba: dice si vale o no antes de leer una sola palabra. */
.tarjeta.valida { border-top: 6px solid var(--ok); }
.tarjeta.invalida { border-top: 6px solid var(--danger); text-align: center; gap: 0.6rem; }

.marca {
  display: grid; place-items: center; width: 46px; height: 46px; flex: none;
  border-radius: 50%; font-size: 1.5rem; font-weight: 800; color: #fff;
}
.valida .marca { background: var(--ok); }
.invalida .marca { background: var(--danger); margin: 0 auto; }

.cabecera { display: flex; align-items: center; gap: 0.8rem; }
.cabecera h1 { margin: 0; font-size: 1.15rem; }
.cabecera p { margin: 0.1rem 0 0; font-size: 0.85rem; color: var(--muted); }
.invalida h1 { margin: 0.3rem 0 0; font-size: 1.25rem; color: var(--danger); }
.invalida .pie { font-size: 0.88rem; color: var(--muted); margin: 0.4rem 0 0; }

.persona { display: flex; gap: 1rem; align-items: center; }
.foto {
  width: 104px; height: 104px; flex: none; object-fit: cover;
  border-radius: var(--radio-sm); border: 1px solid var(--border);
}
.foto.sinfoto {
  display: grid; place-items: center; background: var(--panel-2);
  color: var(--muted); font-size: 0.78rem; text-align: center;
}
.quien { min-width: 0; }
.quien h2 { margin: 0; font-size: 1.2rem; line-height: 1.2; word-break: break-word; }
.ci { margin: 0.25rem 0 0.4rem; font-variant-numeric: tabular-nums; font-size: 1rem; }
.rol {
  display: inline-block; padding: 0.15rem 0.55rem; border-radius: 999px;
  background: var(--acento-suave); color: var(--acento);
  font-size: 0.75rem; font-weight: 700;
}

.datos { display: grid; grid-template-columns: 1fr 1fr; gap: 0.8rem; margin: 0; }
.datos .ancho { grid-column: 1 / -1; }
.datos dt { font-size: 0.72rem; text-transform: uppercase; letter-spacing: 0.04em; color: var(--muted); font-weight: 700; }
.datos dd { margin: 0.15rem 0 0; font-size: 1rem; word-break: break-word; }
.datos .casetas { font-size: 1.6rem; font-weight: 800; font-variant-numeric: tabular-nums; color: var(--acento); }
</style>
