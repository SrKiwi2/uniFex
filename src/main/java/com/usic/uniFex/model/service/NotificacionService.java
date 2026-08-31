package com.usic.uniFex.model.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.usic.uniFex.model.dao.IUsuarioDao;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Notificaciones en tiempo real del modulo de cancelaciones (V11).
 *
 * Cada usuario recibe las suyas en un topic propio, {@code /topic/notificaciones/{id}}:
 * el vendedor se entera al instante de que su solicitud fue aprobada o rechazada, y
 * administracion se entera de que llego una solicitud nueva. Asi el proceso entero
 * (solicitar -> aprobar/rechazar -> cancelar) es asincrono: nadie recarga la pagina.
 *
 * El patron de difusion es el mismo que el de las casetas: si la escritura ocurre
 * dentro de una transaccion, el aviso se publica DESPUES del commit, nunca antes —
 * notificar algo que todavia puede revertirse deja a todos viendo humo.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificacionService {

    public static final String TOPIC_PERSONAL = "/topic/notificaciones/";

    /** Tipos de notificacion (los consume la SPA para decidir que mostrar). */
    public static final String TIPO_SOLICITUD_NUEVA = "SOLICITUD_CANCELACION";
    public static final String TIPO_APROBADA = "APROBACION_CANCELACION";
    public static final String TIPO_RECHAZADA = "RECHAZO_CANCELACION";

    private final SimpMessagingTemplate messaging;
    private final IUsuarioDao usuarioDao;

    /**
     * Aviso para un solo usuario, a su topic personal. Los destinatarios de cada
     * evento del flujo: al vendedor le llegan APROBADA/RECHAZADA; a los admins,
     * SOLICITUD_NUEVA (uno por cada admin activo).
     */
    public void notificar(Long usuarioId, String tipo, String mensaje, Long inscripcionId) {
        if (usuarioId == null) return;
        try {
            messaging.convertAndSend(TOPIC_PERSONAL + usuarioId,
                    new Notificacion(tipo, mensaje, inscripcionId, LocalDateTime.now()));
        } catch (Exception e) {
            log.warn("No se pudo notificar al usuario {} ({}): {}", usuarioId, tipo, e.getMessage());
        }
    }

    /** Difunde la misma notificacion a toda la administracion (roles SUPER USUARIO y ADMINISTRADOR). */
    public void notificarAdministracion(String tipo, String mensaje, Long inscripcionId) {
        List<Long> admins = usuarioDao.idsDeAdministracion();
        admins.forEach(id -> notificar(id, tipo, mensaje, inscripcionId));
    }

    /** Igual que las casetas: el broadcast solo va tras el commit, o directo si no hay tx. */
    public void trasCommit(Runnable accion) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            accion.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                accion.run();
            }
        });
    }

    /** Payload del mensaje WebSocket: tipo + texto + venta a la que se refiere. */
    public record Notificacion(String tipo, String mensaje, Long inscripcionId, LocalDateTime fecha) {
    }
}
