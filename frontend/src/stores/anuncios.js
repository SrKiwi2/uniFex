import { defineStore } from 'pinia';
import { ref, computed } from 'vue';
import { apiFetch } from '../api';
import { escucharTopic } from '../ws';

/** Los anuncios que este usuario ya cerró, por id. Se guardan por usuario. */
const CLAVE_CERRADOS = 'anuncios.cerrados.v1';

/**
 * Anuncios para todos los usuarios, en vivo.
 *
 * Dos caminos y los dos hacen falta: la DIFUSION para quien ya esta dentro (el aviso aparece sin
 * recargar, que es la gracia) y el LISTADO al entrar, para quien abrio la aplicacion despues de
 * que se publicara. Con solo lo primero, un anuncio de las 8:00 no existiria para quien entra a
 * las 8:05.
 *
 * Lo cerrado se recuerda en el propio telefono y no en el servidor: que Ana haya leido un aviso
 * no le importa a nadie mas, y guardarlo en la base seria una fila por usuario y anuncio para
 * algo que se olvida al dia siguiente. El precio es que al cambiar de telefono vuelve a salir
 * una vez, que es un precio bajo.
 */
export const useAnunciosStore = defineStore('anuncios', () => {
  const lista = ref([]);
  const cerrados = ref(leerCerrados());
  let cerrarCanal = null;
  let arrancado = false;

  function leerCerrados() {
    try {
      const v = JSON.parse(localStorage.getItem(CLAVE_CERRADOS) || '[]');
      return new Set(Array.isArray(v) ? v : []);
    } catch {
      // Modo privado o almacenamiento bloqueado: se muestran todos, que es el lado seguro.
      return new Set();
    }
  }

  function guardarCerrados() {
    try {
      // Solo los de los anuncios que siguen existiendo: si no, la lista crece sin fin con ids
      // de avisos de hace meses.
      const vivos = new Set(lista.value.map((a) => a.id));
      const podados = [...cerrados.value].filter((id) => vivos.has(id));
      localStorage.setItem(CLAVE_CERRADOS, JSON.stringify(podados));
    } catch { /* si no se puede guardar, se volvera a mostrar: molesto, no grave */ }
  }

  /** Los que este usuario tiene que ver ahora mismo. */
  const visibles = computed(() => lista.value.filter((a) => !cerrados.value.has(a.id)));

  function aplicar(dto) {
    if (!dto || dto.id == null) return;
    const i = lista.value.findIndex((a) => a.id === dto.id);
    if (dto.activo === false) {
      // Retirado: desaparece de la pantalla de todos, hayan cerrado el aviso o no.
      if (i >= 0) lista.value.splice(i, 1);
      return;
    }
    if (i < 0) lista.value.unshift(dto);
    else lista.value[i] = dto;
  }

  async function cargar() {
    try {
      const r = await apiFetch('/api/app/anuncios');
      if (r.ok) lista.value = await r.json();
    } catch {
      // Sin anuncios la aplicacion funciona igual; no se rompe la pantalla por esto.
    }
  }

  /** Idempotente: la llama AppShell al montarse, una vez por sesion. */
  function asegurar() {
    if (arrancado) return;
    arrancado = true;
    cargar();
    cerrarCanal = escucharTopic('/topic/anuncios', {
      onMensaje: aplicar,
      // En cada (re)conexion se vuelve a pedir: el topic no guarda lo que uno se perdio.
      onConectado: cargar,
    });
  }

  function cerrar(id) {
    cerrados.value = new Set(cerrados.value).add(id);
    guardarCerrados();
  }

  function desconectar() {
    if (cerrarCanal) cerrarCanal();
    cerrarCanal = null;
    arrancado = false;
    lista.value = [];
  }

  return { lista, visibles, asegurar, cargar, aplicar, cerrar, desconectar };
});
