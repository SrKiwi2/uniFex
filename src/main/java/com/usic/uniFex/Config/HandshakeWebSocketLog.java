package com.usic.uniFex.Config;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

/**
 * Dice QUIEN esta llamando mal a {@code /ws}.
 *
 * <h2>El problema que resuelve</h2>
 * Spring rechaza cualquier peticion a {@code /ws} que no traiga las cabeceras de upgrade a
 * WebSocket, y lo registra asi:
 *
 * <pre>Handshake failed due to invalid Upgrade header: null</pre>
 *
 * Ese mensaje no dice de donde viene, ni con que cliente, ni a que ruta. En produccion salia
 * cada pocos segundos, sin parar, y con el no habia forma de saber si era un vendedor con el
 * APK atascado, un monitor de red o alguien escaneando el puerto. Un registro que se repite
 * eternamente y no permite actuar es ruido: llena el disco y enseña a ignorar los errores.
 *
 * <h2>Por que se repite para siempre</h2>
 * Casi siempre es un cliente NUESTRO reintentando: {@code ws.js} reconecta cada 3 s y no se
 * rinde nunca — es lo correcto en la feria, donde la señal va y viene. Si el handshake falla
 * por una causa que no se arregla sola (un proxy que se come la cabecera {@code Upgrade}, una
 * red que bloquea el upgrade, un APK viejo), el bucle no tiene fin. Y lo importante no es el
 * registro: es que ESE cliente se quedo sin tiempo real, con el mapa congelado.
 *
 * <h2>Por que se agrupa</h2>
 * Se escribe como mucho UNA linea por origen cada {@link #SILENCIO_MS}, con cuantos intentos
 * hubo mientras tanto. Una linea por intento multiplica el ruido que se venia a quitar.
 */
public class HandshakeWebSocketLog implements HandshakeInterceptor {

    private static final Logger log = LoggerFactory.getLogger(HandshakeWebSocketLog.class);

    /** Como mucho una linea por origen en este tiempo. */
    private static final long SILENCIO_MS = 60_000;

    private record Contador(AtomicInteger intentos, java.util.concurrent.atomic.AtomicLong ultimoAviso) {
    }

    private final Map<String, Contador> porOrigen = new ConcurrentHashMap<>();

    @Override
    public boolean beforeHandshake(ServerHttpRequest peticion, ServerHttpResponse respuesta,
                                   WebSocketHandler handler, Map<String, Object> atributos) {
        String upgrade = peticion.getHeaders().getUpgrade();
        if (upgrade != null && upgrade.equalsIgnoreCase("websocket")) return true;

        // No es un upgrade: Spring lo va a rechazar. Se deja pasar igual —rechazar aqui
        // cambiaria el codigo de respuesta— y solo se anota de donde vino.
        String quien = origen(peticion);
        Contador c = porOrigen.computeIfAbsent(quien,
                k -> new Contador(new AtomicInteger(), new java.util.concurrent.atomic.AtomicLong()));
        int van = c.intentos().incrementAndGet();
        long ahora = System.currentTimeMillis();
        long ultimo = c.ultimoAviso().get();
        if (ahora - ultimo >= SILENCIO_MS && c.ultimoAviso().compareAndSet(ultimo, ahora)) {
            log.warn("Peticion a /ws SIN cabecera Upgrade desde {} · {} intento(s) desde el ultimo aviso"
                    + " · Upgrade={} Connection={} Origin={} User-Agent={}"
                    + " · ese cliente NO tiene tiempo real (mapa congelado)",
                    quien, van,
                    peticion.getHeaders().getFirst("Upgrade"),
                    peticion.getHeaders().getFirst("Connection"),
                    peticion.getHeaders().getOrigin(),
                    peticion.getHeaders().getFirst("User-Agent"));
            c.intentos().set(0);
        }
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest peticion, ServerHttpResponse respuesta,
                               WebSocketHandler handler, Exception excepcion) {
        // Nada: el handshake correcto no tiene por que dejar rastro.
    }

    /**
     * De donde viene, mirando primero las cabeceras del proxy.
     *
     * Sin {@code X-Forwarded-For}, detras de un proxy TODOS los clientes se veen como la IP del
     * proxy y el registro no distingue a uno de treinta y cinco.
     */
    private static String origen(ServerHttpRequest peticion) {
        String reenviado = peticion.getHeaders().getFirst("X-Forwarded-For");
        if (reenviado != null && !reenviado.isBlank()) return reenviado.split(",")[0].trim();
        if (peticion instanceof ServletServerHttpRequest s) {
            return s.getServletRequest().getRemoteAddr();
        }
        return peticion.getRemoteAddress() == null ? "?" : String.valueOf(peticion.getRemoteAddress());
    }
}
