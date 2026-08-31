import { Client } from '@stomp/stompjs';
import { useAuthStore } from './stores/auth.js';
import { urlWebSocket } from './config.js';

/**
 * Cliente STOMP sobre WebSocket NATIVO suscrito a /topic/puestos y al topic
 * personal de notificaciones /topic/notificaciones/{id} (V11: avisos de
 * solicitudes de cancelacion, sin recargar la pagina).
 *
 * Se quito SockJS (pesaba 68 KB, casi un tercio del JavaScript de la app) porque era un
 * respaldo para navegadores sin WebSocket, que hoy no existen: lo soportan todos los
 * navegadores vigentes y el WebView de Android que usara el APK. Si algun dia una red
 * institucional bloquea el upgrade a WebSocket, la vuelta atras es una linea aqui y otra
 * en WebSocketConfig.
 *
 * El token viaja en el frame CONNECT (`connectHeaders`) y no en el handshake porque la API
 * WebSocket del navegador **no permite** poner cabeceras propias al abrir la conexion. Es
 * una limitacion del navegador, no de la libreria: el frame CONNECT es el primer sitio
 * donde se puede autenticar. Si el backend lo rechaza, llega un frame ERROR -> onRechazo.
 *
 * @param onEstado       recibe cada PuestoEstadoDTO difundido
 * @param onRechazo      el token fue rechazado o expiro
 * @param onConectado    se llamo al suscribirse con exito
 * @param onNotificacion recibe las notificaciones personales (solicitudes de cancelacion)
 * @returns el cliente (usar .deactivate() al cerrar sesion)
 */
export function crearClientePuestos(onEstado, onRechazo, onConectado, onNotificacion) {
  const auth = useAuthStore();

  const client = new Client({
    // En web: mismo host y puerto que la pagina (en dev lo proxya Vite con `ws: true`, en
    // produccion lo sirve el propio Spring), asi que no hay CORS ni URL que mantener.
    // En el APK no vale `location.host` —seria el contenedor de Capacitor, no el servidor—,
    // asi que se deriva de la base configurada al compilar. Ver config.js.
    brokerURL: urlWebSocket(),
    reconnectDelay: 3000,
    connectHeaders: { Authorization: `Bearer ${auth.token}` },
    onConnect: () => {
      client.subscribe('/topic/puestos', (msg) => {
        try {
          onEstado(JSON.parse(msg.body));
        } catch (_) {
          /* ignora mensajes malformados */
        }
      });
      // El topic personal: cada usuario recibe solo lo suyo. Si aun no hay id
      // (no habia token), no hay nada que escuchar.
      if (auth.id != null && onNotificacion) {
        client.subscribe(`/topic/notificaciones/${auth.id}`, (msg) => {
          try {
            onNotificacion(JSON.parse(msg.body));
          } catch (_) {
            /* ignora mensajes malformados */
          }
        });
      }
      if (onConectado) onConectado();
    },
    onStompError: (frame) => {
      // Token ausente, invalido o expirado. Reintentar en bucle no arregla nada:
      // paramos y avisamos para que la vista cierre la sesion.
      client.deactivate();
      if (onRechazo) onRechazo(frame.headers?.message || 'WebSocket rechazado');
    },
  });
  client.activate();
  return client;
}
