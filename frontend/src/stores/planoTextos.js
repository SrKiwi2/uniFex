import { defineStore } from 'pinia';
import { ref } from 'vue';
import { apiFetch } from '../api';
import { escucharTopic } from '../ws';

/**
 * Los rotulos libres del plano ("ENTRADA", "TARIMA", "ZONA A"), compartidos por el Mapa y el
 * Editor y actualizados en vivo.
 *
 * Mismo patron que las casetas: **escritura optimista + difusion como fuente de verdad**. Quien
 * edita mueve el rotulo en su pantalla al instante y el servidor confirma por `/topic/plano-textos`;
 * nadie vuelve a pedir la lista entera despues de escribir.
 *
 * Canal propio y no `/topic/puestos`: son dos cosas con ritmos muy distintos. El estado de una
 * caseta cambia en cada venta; un rotulo se toca al armar el plano y casi nunca mas. Meterlos en
 * el mismo mensaje obligaria a que cada venta cargara tambien los rotulos.
 */
export const usePlanoTextosStore = defineStore('planoTextos', () => {
  const textos = ref([]);
  const cargando = ref(false);
  /** true cuando el canal esta abierto: si es false, lo que se ve puede estar desfasado. */
  const enVivo = ref(false);

  let cerrarCanal = null;
  let promesa = null;

  /**
   * Aplica un mensaje del servidor.
   *
   * `activo=false` es como viaja una baja, asi que el mismo mensaje sirve para alta, cambio y
   * retirada; no hace falta un segundo tipo de aviso para "este ya no esta".
   */
  function aplicar(dto) {
    if (!dto || dto.id == null) return;
    const i = textos.value.findIndex((t) => t.id === dto.id);
    if (dto.activo === false) {
      if (i >= 0) textos.value.splice(i, 1);
      return;
    }
    if (i < 0) textos.value.push(dto);
    else textos.value[i] = dto;
  }

  async function cargar() {
    cargando.value = true;
    try {
      const r = await apiFetch('/api/app/plano-textos');
      if (r.ok) textos.value = await r.json();
    } catch {
      // El plano se ve igual sin rotulos; no vale la pena romper la pantalla por esto.
    } finally {
      cargando.value = false;
    }
  }

  /** Idempotente: la llaman el Mapa y el Editor al montarse y solo una abre el canal. */
  function asegurar() {
    if (promesa) return promesa;
    promesa = cargar();
    if (!cerrarCanal) {
      cerrarCanal = escucharTopic('/topic/plano-textos', {
        onMensaje: aplicar,
        // En CADA (re)conexion se vuelve a pedir la lista: mientras no habia canal pudo
        // cambiar algo, y el topic no guarda historial de lo que uno se perdio.
        onConectado: () => { enVivo.value = true; cargar(); },
        onCerrado: () => { enVivo.value = false; },
      });
    }
    return promesa;
  }

  function desconectar() {
    if (cerrarCanal) cerrarCanal();
    cerrarCanal = null;
    promesa = null;
    enVivo.value = false;
    textos.value = [];
  }

  return { textos, cargando, enVivo, asegurar, cargar, aplicar, desconectar };
});
