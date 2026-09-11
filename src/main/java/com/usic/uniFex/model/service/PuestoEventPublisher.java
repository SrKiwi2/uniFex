package com.usic.uniFex.model.service;

import java.util.Collection;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.usic.uniFex.model.dao.IPuestoDao;
import com.usic.uniFex.model.dto.PuestoEstadoDTO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Difunde el estado actual de una caseta a todos los clientes suscritos al
 * topic {@code /topic/puestos}. Es el punto unico de publicacion en tiempo real.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PuestoEventPublisher {

    public static final String TOPIC = "/topic/puestos";

    /**
     * Topic de los cambios de asignacion: que caseta pasa a llevar quien.
     *
     * Va aparte de {@link #TOPIC} porque son dos cosas con ritmos muy distintos. El estado de
     * una caseta cambia en cada venta; quien la lleva, casi nunca. Meter el nombre y el
     * telefono del vendedor en PuestoEstadoDTO habria engordado TODOS los mensajes de venta
     * —los que mas viajan— para transportar algo que se toca una vez al mes.
     */
    public static final String TOPIC_ASIGNACIONES = "/topic/asignaciones";

    private final SimpMessagingTemplate messaging;
    private final IPuestoDao puestoDao;

    /** Publica el estado vigente del puesto (se lee dentro de la tx para resolver la categoria lazy). */
    @Transactional(readOnly = true)
    public void publicar(Long puestoId) {
        try {
            puestoDao.findById(puestoId)
                    .map(PuestoEstadoDTO::de)
                    .ifPresent(dto -> messaging.convertAndSend(TOPIC, dto));
        } catch (Exception e) {
            log.warn("No se pudo difundir el estado del puesto {}: {}", puestoId, e.getMessage());
        }
    }

    /**
     * Difunde los cambios de asignacion: solo las casetas que cambiaron de dueño.
     *
     * Se manda el DELTA y no la tabla entera a proposito: en la feria la red es mala y
     * reasignar tres casetas no puede costar la lista completa a cada movil conectado. Una
     * caseta que se queda sin vendedor viaja con {@code vendedorId} nulo, que es como el
     * cliente sabe que tiene que pintarla de gris y olvidar el contacto.
     */
    public void publicarAsignaciones(java.util.List<com.usic.uniFex.model.dto.AsignacionPuestoDTO> cambios) {
        if (cambios == null || cambios.isEmpty()) return;
        try {
            messaging.convertAndSend(TOPIC_ASIGNACIONES, cambios);
            log.info("Difundidos {} cambios de asignacion", cambios.size());
        } catch (Exception e) {
            // Igual que el estado de las casetas: un fallo al difundir NUNCA puede tumbar la
            // operacion que ya se guardo. El cliente se enterara al resincronizar.
            log.warn("No se pudieron difundir los cambios de asignacion: {}", e.getMessage());
        }
    }

    /**
     * Publica varias casetas de una vez. Lo usan el guardado por lotes del editor y los cambios
     * de categoria (color, forma, tamaño), que alteran la apariencia de todas sus casetas.
     *
     * Se emite un mensaje por caseta y no un lote unico para no cambiar el contrato que ya
     * consumen el mapa y la app: cada mensaje sigue siendo un PuestoEstadoDTO.
     */
    @Transactional(readOnly = true)
    public void publicarVarios(Collection<Long> puestoIds) {
        if (puestoIds == null || puestoIds.isEmpty()) return;
        puestoIds.forEach(this::publicar);
        log.info("Difundidas {} casetas", puestoIds.size());
    }
}
