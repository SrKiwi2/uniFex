import { ref, computed } from 'vue';

/**
 * Indicador global de "estoy trabajando", con paciencia declarada.
 *
 * <h2>Por que existe</h2>
 * En la feria la red es mala. Una peticion que en la oficina tarda 200 ms ahi tarda ocho
 * segundos, y durante esos ocho segundos la aplicacion no decia nada: el vendedor, delante
 * del cliente, concluia que el APK se habia colgado y volvia a pulsar. Esto no acelera nada,
 * pero convierte "esta roto" en "esta tardando", que son dos cosas distintas.
 *
 * <h2>Que NO hace</h2>
 * No bloquea la pantalla con un muro opaco. El velo es translucido y por debajo se sigue
 * viendo donde estaba uno, porque tapar la pantalla entera refuerza justo la impresion que se
 * quiere evitar. Solo se come los toques, para que no se dispare dos veces la misma venta.
 *
 * <h2>Cuenta de referencias</h2>
 * `mostrar()` y `ocultar()` se pueden anidar: dos peticiones a la vez levantan el indicador
 * una sola vez y lo bajan cuando termina la ultima. Sin esto, la primera en acabar lo
 * quitaba y la segunda se quedaba trabajando a escondidas.
 */
const pendientes = ref(0);
export const textoCarga = ref('');
/** Milisegundos que lleva esperando la operacion actual. Lo actualiza el componente. */
export const esperaMs = ref(0);

export const cargando = computed(() => pendientes.value > 0);

/**
 * Lo que se le dice al usuario segun cuanto lleve esperando.
 *
 * Los umbrales no son redondos por gusto: por debajo de ~2,5 s la gente no siente que algo
 * vaya lento y avisar de la conexion seria mentir; pasados ~9 s ya no es lentitud, es que
 * probablemente no hay señal, y conviene decirlo para que no siga esperando de balde.
 */
export const AVISO_LENTO_MS = 2500;
export const AVISO_SIN_SENAL_MS = 9000;

export const estadoEspera = computed(() => {
  if (esperaMs.value >= AVISO_SIN_SENAL_MS) return 'sin-senal';
  if (esperaMs.value >= AVISO_LENTO_MS) return 'lento';
  return 'normal';
});

export function mostrarCarga(texto = 'Un momento…') {
  if (pendientes.value === 0) {
    textoCarga.value = texto;
    esperaMs.value = 0;
  }
  pendientes.value++;
}

/**
 * Cambia el texto del velo que ya esta puesto, para una tarea de varios pasos
 * ("subiendo la foto…", "enviando la credencial…").
 *
 * Existe para no tener que exportar el ref y que cada vista le escriba encima: eso ya costo un
 * cuelgue en produccion, porque `textoCarga` se usaba en MisVentas SIN importarlo y el
 * ReferenceError caia en el `catch` que abria un aviso invisible bajo el velo.
 *
 * No hace nada si no hay velo: cambiar el texto de algo que no se ve solo dejaria el rotulo
 * preparado para la proxima vez, que es peor que no hacer nada.
 */
export function cambiarTextoCarga(texto) {
  if (pendientes.value > 0 && texto) textoCarga.value = texto;
}

export function ocultarCarga() {
  pendientes.value = Math.max(0, pendientes.value - 1);
  if (pendientes.value === 0) esperaMs.value = 0;
}

/**
 * Envuelve una promesa con el indicador. Es la forma recomendada: el `finally` garantiza que
 * el indicador baja aunque la operacion falle, que es justo cuando mas facil es olvidarlo.
 *
 *   const r = await conCarga('Registrando la venta…', () => apiFetch(...));
 */
export async function conCarga(texto, tarea) {
  mostrarCarga(texto);
  try {
    return await tarea();
  } finally {
    ocultarCarga();
  }
}
