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
