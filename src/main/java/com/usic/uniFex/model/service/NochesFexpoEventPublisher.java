package com.usic.uniFex.model.service;

import java.util.List;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import com.usic.uniFex.model.dto.NocheFexpoDTO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Difunde por WebSocket la cartelera de "Noches de FEXPO": cada alta, edicion, foto nueva o
 * baja hecha en el panel aparece en la vista publica (y en los demas paneles abiertos) sin
 * recargar la pagina.
 *
 * El topic cuelga de {@code /topic/publico/}, el unico prefijo al que puede suscribirse una
 * conexion sin token (ver WebSocketConfig): la vista publica es anonima.
 *
 * Cada mensaje lleva la LISTA ENTERA de noches, no un delta: son unas pocas filas, y asi un
 * alta, una edicion y una baja se aplican igual —se reemplaza la lista— y un cliente que se
 * perdio un mensaje queda al dia con el siguiente. Lo que cambie mientras un cliente esta
 * desconectado lo recupera el propio cliente al reconectar (GET /api/publico/feria/noches).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NochesFexpoEventPublisher {

    public static final String TOPIC = "/topic/publico/noches";

    private final SimpMessagingTemplate messaging;
    private final NochesFexpoService servicio;

    /**
     * Llamar DESPUES de que la escritura se confirmo (el controlador lo hace fuera de la
     * transaccion del servicio), o se difundiria un cambio que aun puede revertirse.
     *
     * Como PuestoEventPublisher: un fallo al difundir nunca tumba la operacion ya guardada.
     */
    public void publicar() {
        try {
            List<NocheFexpoDTO> lista = servicio.listarDeEdicionActiva().stream()
                    .map(NocheFexpoDTO::de).toList();
            messaging.convertAndSend(TOPIC, lista);
            log.info("Cartelera de noches difundida ({} noches)", lista.size());
        } catch (Exception e) {
            log.warn("No se pudo difundir la cartelera de noches: {}", e.getMessage());
        }
    }
}
