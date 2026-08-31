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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import com.usic.uniFex.security.JwtService;
import com.usic.uniFex.security.JwtUser;

import lombok.RequiredArgsConstructor;

/**
 * Config de WebSocket (STOMP) para difundir el estado de los puestos en tiempo real.
 *
 * - Los clientes se conectan al endpoint {@code /ws} por WebSocket nativo.
 * - El servidor publica los cambios en el topic {@code /topic/puestos}.
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtService jwtService;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes("/app");
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
     * Autentica el WebSocket en el frame STOMP {@code CONNECT}, no en el handshake HTTP.
     *
     * La razon es del navegador, no de la libreria: la API {@code WebSocket} del navegador
     * **no permite** anadir cabeceras propias al handshake, asi que un cliente web nunca
     * podria mandar ahi el {@code Authorization} y validar en el handshake dejaria fuera a
     * todos los clientes legitimos. El frame CONNECT, en cambio, siempre lleva las cabeceras
     * que el cliente STOMP le pasa en {@code connectHeaders}.
     *
     * Lanzar aqui aborta la conexion: el cliente recibe un frame ERROR y no se suscribe.
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> mensaje, MessageChannel canal) {
                StompHeaderAccessor acc = MessageHeaderAccessor.getAccessor(mensaje, StompHeaderAccessor.class);
                if (acc == null || !StompCommand.CONNECT.equals(acc.getCommand())) {
                    return mensaje; // solo se autentica el CONNECT; el resto viaja ya autenticado
                }
                String cabecera = acc.getFirstNativeHeader("Authorization");
                if (cabecera == null || !cabecera.startsWith("Bearer ")) {
                    throw new MessagingException("Falta el token en el CONNECT");
                }
                try {
                    JwtUser usuario = jwtService.validar(cabecera.substring(7));
                    acc.setUser(new UsernamePasswordAuthenticationToken(
                            usuario, null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + usuario.rolNormalizado()))));
                } catch (MessagingException e) {
                    throw e;
                } catch (Exception e) {
                    throw new MessagingException("Token invalido o expirado");
                }
                return mensaje;
            }
        });
    }
}
