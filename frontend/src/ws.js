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
 * @param onCerrado      la conexion se cayo (no es un rechazo: stompjs reintentara sola)
 * @param onAsignaciones lista de cambios de "que caseta lleva quien"
 * @returns el cliente (usar .deactivate() al cerrar sesion)
 */
/**
 * Suscripcion suelta a UN topic autenticado, con su propia conexion. Para una vista que
 * necesita un canal que el cliente general (crearClientePuestos) no escucha, sin tocar ese
 * cliente ni obligar a toda la app a recibir mensajes que solo usa una pantalla.
 *
 * @param topic                 destino STOMP (p. ej. '/topic/interesados')
 * @param opciones.onMensaje    recibe cada mensaje ya parseado
 * @param opciones.onConectado  se llama en CADA (re)conexion: es el momento de volver a pedir
 *                              por HTTP lo que pudo cambiar mientras no habia conexion
 * @param opciones.onCerrado    la conexion se cayo (stompjs reintentara sola)
 * @returns funcion para cerrar la conexion (llamarla al desmontar la vista)
 */
export function escucharTopic(topic, { onMensaje, onConectado, onCerrado } = {}) {
  const auth = useAuthStore();
  const client = new Client({
    brokerURL: urlWebSocket(),
    reconnectDelay: 3000,
    connectHeaders: { Authorization: `Bearer ${auth.token}` },
    onConnect: () => {
      client.subscribe(topic, (msg) => {
        try {
          onMensaje?.(JSON.parse(msg.body));
        } catch (_) {
          /* ignora mensajes malformados */
        }
      });
      onConectado?.();
    },
    onWebSocketClose: () => onCerrado?.(),
    // Token vencido o suscripcion no permitida para este rol: reintentar en bucle no lo arregla.
    onStompError: () => client.deactivate(),
  });
  client.activate();
  return () => client.deactivate();
}

export function crearClientePuestos(onEstado, onRechazo, onConectado, onNotificacion, onCerrado,
                                    onAsignaciones) {
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
      // Cambios de asignacion: que caseta pasa a llevar quien. Llega el DELTA —solo las
      // casetas que cambiaron— para que reasignar tres no cueste la lista entera a cada
      // movil conectado. Una caseta sin vendedor viaja con vendedorId nulo.
      if (onAsignaciones) {
        client.subscribe('/topic/asignaciones', (msg) => {
          try {
            onAsignaciones(JSON.parse(msg.body));
          } catch (_) {
            /* ignora mensajes malformados */
          }
        });
      }
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
    // Una caida NO es un rechazo: stompjs reintenta sola cada reconnectDelay. Pero hasta
    // que vuelva, el mapa esta congelado y el usuario tiene que saberlo. Sin esto,
    // `enVivo` se quedaba en true para siempre despues de la primera conexion: el indicador
    // habria mentido justo cuando mas importa (Android corta los sockets al mandar la app
    // al fondo, y ahi es cuando el mapa se queda viejo).
    onWebSocketClose: () => {
      if (onCerrado) onCerrado();
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
