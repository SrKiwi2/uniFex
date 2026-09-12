package com.usic.uniFex.model.service;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import com.usic.uniFex.model.dto.InteresadoStandDTO;
import com.usic.uniFex.model.entity.InteresadoStand;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Difunde por WebSocket cada interesado nuevo (formulario publico "Quiero exponer") o editado
 * (panel), para que la lista de "Interesados en exponer" se actualice sola en todos los paneles
 * abiertos.
 *
 * El topic NO cuelga de /topic/publico/ a proposito: cada mensaje lleva nombre y celular de una
 * persona. WebSocketConfig solo deja suscribirse a administracion (Roles.AUTORIDADES_ADMINISTRA).
 *
 * Viaja un DTO por mensaje (el registro que cambio), no la lista entera: el cliente lo inserta
 * o reemplaza por id, y al reconectar vuelve a pedir la lista por HTTP.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InteresadoStandEventPublisher {

    public static final String TOPIC = "/topic/interesados";

    private final SimpMessagingTemplate messaging;

    /**
     * Llamar DESPUES del commit (los controladores lo hacen al volver del servicio).
     * Como PuestoEventPublisher: un fallo al difundir nunca tumba el registro ya guardado.
     */
    public void publicar(InteresadoStand interesado) {
        try {
            messaging.convertAndSend(TOPIC, InteresadoStandDTO.de(interesado));
        } catch (Exception e) {
            log.warn("No se pudo difundir el interesado {}: {}", interesado.getId(), e.getMessage());
        }
    }
}
