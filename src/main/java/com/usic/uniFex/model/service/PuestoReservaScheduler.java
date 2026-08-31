package com.usic.uniFex.model.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Job que libera periodicamente las reservas de casetas vencidas (estado T cuyo
 * TTL ya paso), devolviendolas a LIBRE. Es la contraparte del TTL: si un usuario
 * reserva y no confirma la compra a tiempo, la caseta vuelve a estar disponible.
 *
 * Requiere {@code @EnableScheduling} (habilitado en UniFexApplication).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PuestoReservaScheduler {

    private final PuestoReservaService reservaService;
    private final PuestoEventPublisher publisher;

    /** Se ejecuta cada {@code unifex.reserva.barrido-ms} milisegundos (por defecto 30 s). */
    @Scheduled(fixedDelayString = "${unifex.reserva.barrido-ms:30000}")
    public void liberarVencidas() {
        try {
            // Libera las vencidas y difunde por WebSocket cada caseta que volvio a LIBRE.
            reservaService.liberarVencidas().forEach(publisher::publicar);
        } catch (Exception e) {
            // No dejar que un fallo transitorio mate el hilo del scheduler.
            log.warn("Fallo al liberar reservas vencidas: {}", e.getMessage());
        }
    }
}
