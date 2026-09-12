import { Client } from '@stomp/stompjs';
import { url, urlWebSocket } from './config.js';

const TOPIC = '/topic/publico/noches';

/**
 * Cartelera de "Noches de FEXPO" en vivo, por el WebSocket de la app (ver
 * NochesFexpoEventPublisher).
 *
 * Se conecta SIN token a proposito: la vista publica es anonima, y el backend acepta un
 * CONNECT sin Authorization pero lo limita a suscribirse a /topic/publico/** (ver
 * WebSocketConfig). Por eso no reutiliza crearClientePuestos de ws.js, que siempre manda el
 * token y escucha topics que un anonimo no puede ver.
 *
 * Cada mensaje trae la LISTA COMPLETA de noches. Al conectar —y al reconectar tras un corte—
 * se pide la lista una vez por HTTP, porque lo que cambio mientras no habia conexion no se
 * vuelve a difundir.
 *
 * @param onLista recibe la lista de noches (NocheFexpoDTO[]) cada vez que cambia
 * @returns funcion para cerrar la conexion (llamarla al desmontar la vista)
 */
export function escucharNoches(onLista) {
  // Cuantos mensajes llegaron por el socket. Si llega uno mientras la resincronizacion por
  // HTTP esta en vuelo, esa respuesta puede ser mas vieja que el mensaje: se descarta.
  let recibidos = 0;

  async function resincronizar() {
    const antes = recibidos;
    try {
      const r = await fetch(url('/api/publico/feria/noches'));
      if (!r.ok) return;
      const lista = await r.json();
      if (Array.isArray(lista) && recibidos === antes) onLista(lista);
    } catch (_) {
      /* sin red: el proximo onConnect lo reintenta */
    }
  }

  const client = new Client({
    brokerURL: urlWebSocket(),
    reconnectDelay: 3000,
    onConnect: () => {
      client.subscribe(TOPIC, (msg) => {
        try {
          const lista = JSON.parse(msg.body);
          if (!Array.isArray(lista)) return;
          recibidos++;
          onLista(lista);
        } catch (_) {
          /* ignora mensajes malformados */
        }
      });
      resincronizar();
    },
  });
  client.activate();
  return () => client.deactivate();
}
