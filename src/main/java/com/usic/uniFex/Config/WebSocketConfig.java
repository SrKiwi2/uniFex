package com.usic.uniFex.Config;

import java.util.List;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import com.usic.uniFex.security.JwtService;
import com.usic.uniFex.security.JwtUser;

import lombok.RequiredArgsConstructor;

/**
 * Config de WebSocket (STOMP) para difundir en tiempo real.
 *
 * - Los clientes se conectan al endpoint {@code /ws} por WebSocket nativo.
 * - El servidor publica el estado de las casetas en {@code /topic/puestos} (y demas topics
 *   internos), que exigen JWT, y el contenido de la vista publica bajo
 *   {@link #PREFIJO_PUBLICO} (hoy, la cartelera de "Noches de FEXPO"), abierto a anonimos.
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /**
     * Unico prefijo al que puede suscribirse una conexion SIN token. Todo lo que se publique
     * aqui debe ser informacion que la vista publica ya muestra de todos modos.
     */
    public static final String PREFIJO_PUBLICO = "/topic/publico/";

    private final JwtService jwtService;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Latido del servidor cada 10 s. Sin el, una conexion que no recibe mensajes —la vista
        // publica, donde la cartelera cambia muy de vez en cuando— queda muda, y un proxy
        // delante (nginx corta a los 60 s por defecto) la cierra: el cliente vive reconectando.
        // El segundo valor es 0 a proposito: el servidor NO exige latidos al cliente, porque el
        // navegador frena los temporizadores de las pestañas en segundo plano y el broker
        // cerraria conexiones sanas por "silencio".
        config.enableSimpleBroker("/topic")
                .setHeartbeatValue(new long[] { 10_000, 0 })
                .setTaskScheduler(programadorLatidos());
        config.setApplicationDestinationPrefixes("/app");
    }

    /**
     * Hilo propio para los latidos, y NO un @Bean a proposito: un TaskScheduler mas en el
     * contexto podria cambiar cual usan los @Scheduled (el barrido de reservas). Daemon para
     * no retener el cierre de la JVM.
     */
    private ThreadPoolTaskScheduler programadorLatidos() {
        ThreadPoolTaskScheduler s = new ThreadPoolTaskScheduler();
        s.setPoolSize(1);
        s.setThreadNamePrefix("ws-latido-");
        s.setDaemon(true);
        s.initialize();
        return s;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Sin SockJS a proposito: era un respaldo para navegadores sin WebSocket (ya no
        // existen) y costaba 68 KB en el bundle de la SPA, que tambien se empaqueta como APK.
        // Si alguna vez una red bloquea el upgrade a WebSocket, anadir .withSockJS() aqui y
        // devolver el webSocketFactory en frontend/src/ws.js.
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*"); // dev: web y app en otros origenes
    }

    /**
     * Autentica el WebSocket en el frame STOMP {@code CONNECT}, no en el handshake HTTP, y
     * decide a que puede suscribirse cada conexion.
     *
     * Por que en el CONNECT: la API {@code WebSocket} del navegador **no permite** anadir
     * cabeceras propias al handshake, asi que un cliente web nunca podria mandar ahi el
     * {@code Authorization}. El frame CONNECT, en cambio, siempre lleva las cabeceras que el
     * cliente STOMP le pasa en {@code connectHeaders}.
     *
     * Tres casos:
     * - CONNECT con token valido: sesion autenticada, puede suscribirse a todo.
     * - CONNECT con token invalido o expirado: se rechaza (frame ERROR), como siempre. Asi la
     *   SPA sigue enterandose de que tiene que volver a iniciar sesion.
     * - CONNECT SIN cabecera Authorization: sesion anonima (la vista publica de la feria).
     *   Solo puede SUSCRIBIRSE a {@link #PREFIJO_PUBLICO}; cualquier otra suscripcion —en
     *   especial /topic/puestos, el estado de venta en vivo— y cualquier SEND se rechazan.
     *
     * Lanzar aqui aborta el frame: el cliente recibe un frame ERROR.
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> mensaje, MessageChannel canal) {
                StompHeaderAccessor acc = MessageHeaderAccessor.getAccessor(mensaje, StompHeaderAccessor.class);
                if (acc == null || acc.getCommand() == null) return mensaje;
                if (StompCommand.CONNECT.equals(acc.getCommand())) {
                    autenticar(acc);
                } else if (StompCommand.SUBSCRIBE.equals(acc.getCommand())
                        || StompCommand.SEND.equals(acc.getCommand())) {
                    exigirPermiso(acc);
                }
                return mensaje;
            }
        });
    }

    private void autenticar(StompHeaderAccessor acc) {
        String cabecera = acc.getFirstNativeHeader("Authorization");
        if (cabecera == null) {
            return; // anonimo: sin usuario; exigirPermiso() lo limita a PREFIJO_PUBLICO
        }
        if (!cabecera.startsWith("Bearer ")) {
            throw new MessagingException("Cabecera Authorization mal formada en el CONNECT");
        }
        try {
            JwtUser usuario = jwtService.validar(cabecera.substring(7));
            acc.setUser(new UsernamePasswordAuthenticationToken(
                    usuario, null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + usuario.rolNormalizado()))));
        } catch (Exception e) {
            throw new MessagingException("Token invalido o expirado");
        }
    }

    /** El usuario lo recuerda Spring desde el CONNECT; null = sesion anonima. */
    private void exigirPermiso(StompHeaderAccessor acc) {
        if (acc.getUser() != null) return;
        String destino = acc.getDestination();
        boolean suscripcionPublica = StompCommand.SUBSCRIBE.equals(acc.getCommand())
                && destino != null && destino.startsWith(PREFIJO_PUBLICO);
        if (!suscripcionPublica) {
            throw new MessagingException("Destino no permitido sin autenticacion: " + destino);
        }
    }
}
