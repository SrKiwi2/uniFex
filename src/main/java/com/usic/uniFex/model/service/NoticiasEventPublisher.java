package com.usic.uniFex.model.service;

import java.util.List;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import com.usic.uniFex.model.dto.NoticiaDTO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Difunde por WebSocket el carrusel de noticias: cada alta, edicion o baja hecha en el panel
 * aparece en la vista publica sin recargar la pagina.
 *
 * Como {@link NochesFexpoEventPublisher}: cuelga de {@code /topic/publico/} (la vista publica es
 * anonima) y cada mensaje lleva la lista ENTERA de noticias de la edicion activa, no un delta.
 * La usan el carrusel (se queda con las 10 primeras) y la vista de todas las noticias.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NoticiasEventPublisher {

    public static final String TOPIC = "/topic/publico/noticias";

    private final SimpMessagingTemplate messaging;
    private final NoticiasService servicio;

    /** Llamar DESPUES de que la escritura se confirmo. Un fallo al difundir no tumba la operacion. */
    public void publicar() {
        try {
            List<NoticiaDTO> lista = servicio.listarDeEdicionActiva().stream().map(NoticiaDTO::de).toList();
            messaging.convertAndSend(TOPIC, lista);
            log.info("Carrusel de noticias difundido ({} noticias)", lista.size());
        } catch (Exception e) {
            log.error("No se pudo difundir el carrusel de noticias", e);
        }
    }
}
