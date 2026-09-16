import { Client } from '@stomp/stompjs';
import { url, urlWebSocket } from './config.js';

/**
 * Listas de la vista publica en vivo, por el WebSocket de la app: la cartelera de "Noches de
 * FEXPO" (NochesFexpoEventPublisher) y el carrusel de noticias (NoticiasEventPublisher).
 *
 * Se conecta SIN token a proposito: la vista publica es anonima, y el backend acepta un
 * CONNECT sin Authorization pero lo limita a suscribirse a /topic/publico/** (ver
 * WebSocketConfig). Por eso no reutiliza crearClientePuestos de ws.js, que siempre manda el
 * token y escucha topics que un anonimo no puede ver.
 *
 * Una sola conexion para todas las suscripciones. Cada mensaje trae la LISTA COMPLETA. Al
 * conectar —y al reconectar tras un corte— cada lista se pide una vez por HTTP, porque lo que
 * cambio mientras no habia conexion no se vuelve a difundir.
 *
 * @param suscripciones [{ topic, ruta, onLista }]: `ruta` es el GET publico que devuelve la
 *   misma lista que difunde `topic`; `onLista` la recibe cada vez que cambia
 * @returns funcion para cerrar la conexion (llamarla al desmontar la vista)
 */
export function escucharListasPublicas(suscripciones) {
  const estados = suscripciones.map((s) => ({ ...s, recibidos: 0 }));

  // Si llega un mensaje mientras la resincronizacion por HTTP esta en vuelo, esa respuesta
  // puede ser mas vieja que el mensaje: se descarta.
  async function resincronizar(e) {
    const antes = e.recibidos;
    try {
      const r = await fetch(url(e.ruta));
      if (!r.ok) return;
      const lista = await r.json();
      if (Array.isArray(lista) && e.recibidos === antes) e.onLista(lista);
    } catch (_) {
      /* sin red: el proximo onConnect lo reintenta */
    }
  }

  const client = new Client({
    brokerURL: urlWebSocket(),
    reconnectDelay: 3000,
    onConnect: () => {
      for (const e of estados) {
        client.subscribe(e.topic, (msg) => {
          try {
            const lista = JSON.parse(msg.body);
            if (!Array.isArray(lista)) return;
            e.recibidos++;
            e.onLista(lista);
          } catch (_) {
            /* ignora mensajes malformados */
          }
        });
        resincronizar(e);
      }
    },
  });
  client.activate();
  return () => client.deactivate();
}

export const SUSCRIPCION_NOCHES = { topic: '/topic/publico/noches', ruta: '/api/publico/feria/noches' };
export const SUSCRIPCION_NOTICIAS = { topic: '/topic/publico/noticias', ruta: '/api/publico/feria/noticias' };
