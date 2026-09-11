package com.usic.uniFex.model.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.usic.uniFex.model.dao.INotificacionDao;
import com.usic.uniFex.model.dao.IUsuarioDao;
import com.usic.uniFex.model.entity.Notificacion;
import com.usic.uniFex.model.entity.Usuario;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Notificaciones persistentes + tiempo real (WebSocket).
 *
 * Cada usuario tiene su topic personal {@code /topic/notificaciones/{id}}.
 * La tabla {@code notificacion} es la fuente de verdad (sobrevive a recargas,
 * cambios de dispositivo, app cerrada). El WebSocket solo avisa en vivo.
 *
 * Tipos de notificación:
 * - SOLICITUD_CANCELACION  (vendedor → admin)
 * - APROBACION_CANCELACION / RECHAZO_CANCELACION  (admin → vendedor)
 * - OBSERVACION_ADMIN      (admin → vendedor, hilo con respuesta)
 * - VENTA_REGISTRADA       (sistema → admin)
 * - SISTEMA                (genérico)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificacionService {

    public static final String TOPIC_PERSONAL = "/topic/notificaciones/";

    /** Tipos estándar. */
    public static final String TIPO_SOLICITUD_NUEVA = "SOLICITUD_CANCELACION";
    public static final String TIPO_APROBADA = "APROBACION_CANCELACION";
    public static final String TIPO_RECHAZADA = "RECHAZO_CANCELACION";
    public static final String TIPO_OBSERVACION_ADMIN = "OBSERVACION_ADMIN";
    public static final String TIPO_VENTA_REGISTRADA = "VENTA_REGISTRADA";
    public static final String TIPO_SISTEMA = "SISTEMA";
    /**
     * Cambio en lo que un vendedor tiene habilitado para vender. La SPA y el APK lo usan como
     * señal para RECARGAR el mapa: lo que puede ver acaba de cambiar y su lista quedo vieja.
     */
    public static final String TIPO_ASIGNACION = "ASIGNACION_CAMBIADA";

    /** Estados de hilo para OBSERVACION_ADMIN. */
    public static final String HILO_ABIERTA = "ABIERTA";
    public static final String HILO_RESPONDIDA = "RESPONDIDA";
    public static final String HILO_RESUELTA = "RESUELTA";

    private final SimpMessagingTemplate messaging;
    private final IUsuarioDao usuarioDao;
    private final INotificacionDao notificacionDao;

    /**
     * Crea la notificación en BD y la difunde por WebSocket (tras commit si hay tx).
     */
    public void notificar(Long usuarioId, String tipo, String asunto, String cuerpo,
                          Long inscripcionId, Long puestoId) {
        if (usuarioId == null) return;
        Usuario destino = usuarioDao.findById(usuarioId).orElse(null);
        if (destino == null) return;

        Notificacion n = new Notificacion();
        n.setUsuarioDestino(destino);
        n.setTipo(tipo);
        n.setAsunto(asunto);
        n.setCuerpo(cuerpo);
        if (inscripcionId != null) {
            n.setInscripcion(new com.usic.uniFex.model.entity.Inscripcion());
            n.getInscripcion().setId(inscripcionId);
        }
        if (puestoId != null) {
            n.setPuesto(new com.usic.uniFex.model.entity.Puesto());
            n.getPuesto().setId(puestoId);
        }
        n.setEstadoHilo(HILO_ABIERTA);

        // Persistir + broadcast tras commit
        trasCommit(() -> {
            Notificacion guardada = notificacionDao.save(n);
            difundir(guardada);
        });
    }

    /** Sobrecarga simple (solo asunto = mensaje). */
    public void notificar(Long usuarioId, String tipo, String mensaje, Long inscripcionId) {
        notificar(usuarioId, tipo, mensaje, mensaje, inscripcionId, null);
    }

    /** Difunde a todos los administradores (SUPER USUARIO + ADMINISTRADOR). */
    public void notificarAdministracion(String tipo, String asunto, String cuerpo,
                                        Long inscripcionId, Long puestoId) {
        List<Long> admins = usuarioDao.idsDeAdministracion();
        admins.forEach(id -> notificar(id, tipo, asunto, cuerpo, inscripcionId, puestoId));
    }

    /** Versión simple para admins. */
    public void notificarAdministracion(String tipo, String mensaje, Long inscripcionId) {
        notificarAdministracion(tipo, mensaje, mensaje, inscripcionId, null);
    }

    /** Envía la notificación ya guardada por WebSocket al topic personal. */
    private void difundir(Notificacion n) {
        try {
            messaging.convertAndSend(TOPIC_PERSONAL + n.getUsuarioDestino().getId(),
                    new NotificacionWS(n.getTipo(), n.getAsunto(), n.getCuerpo(),
                            n.getInscripcion() != null ? n.getInscripcion().getId() : null,
                            n.getPuesto() != null ? n.getPuesto().getId() : null,
                            n.getFechaRegistro()));
        } catch (Exception e) {
            log.warn("No se pudo difundir notificación {} a usuario {}: {}",
                    n.getId(), n.getUsuarioDestino().getId(), e.getMessage());
        }
    }

    /** Igual que las casetas: la acción va tras el commit, o directa si no hay tx. */
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

    // ---------- Consultas para la bandeja ----------

    /** Lista completa para la bandeja (no leídas primero). */
    @Transactional(readOnly = true)
    public List<Notificacion> getBandeja(Usuario usuario) {
        return notificacionDao.findBandejaByUsuario(usuario);
    }

    /** Solo no leídas (para badge). */
    @Transactional(readOnly = true)
    public List<Notificacion> getNoLeidas(Usuario usuario) {
        return notificacionDao.findNoLeidasByUsuario(usuario);
    }

    /** Contador de no leídas. */
    @Transactional(readOnly = true)
    public long countNoLeidas(Usuario usuario) {
        return notificacionDao.countNoLeidasByUsuario(usuario);
    }

    /** Marca una notificación como leída. */
    @Transactional
    public void marcarLeida(Long notificacionId, Usuario usuario) {
        Notificacion n = notificacionDao.findById(notificacionId).orElse(null);
        if (n != null && n.getUsuarioDestino().getId().equals(usuario.getId()) && !n.getLeida()) {
            n.setLeida(true);
            n.setLeidaEn(LocalDateTime.now());
            notificacionDao.save(n);
        }
    }

    /** Marca todas como leídas. */
    @Transactional
    public void marcarTodasLeidas(Usuario usuario) {
        List<Notificacion> noLeidas = notificacionDao.findNoLeidasByUsuario(usuario);
        noLeidas.forEach(n -> {
            n.setLeida(true);
            n.setLeidaEn(LocalDateTime.now());
        });
        notificacionDao.saveAll(noLeidas);
    }

    /** Hilo completo de una observación (padre + respuestas). */
    @Transactional(readOnly = true)
    public List<Notificacion> getHilo(Long notificacionPadreId) {
        Notificacion padre = notificacionDao.findById(notificacionPadreId).orElse(null);
        if (padre == null) return List.of();
        return notificacionDao.findHiloByPadre(padre);
    }

    /** Responde a una observación (vendedor responde a admin). */
    @Transactional
    public void responderObservacion(Long notificacionPadreId, Usuario vendedor, String respuesta) {
        Notificacion padre = notificacionDao.findById(notificacionPadreId).orElse(null);
        if (padre == null || !TIPO_OBSERVACION_ADMIN.equals(padre.getTipo())) return;

        // La respuesta va al admin que creó la observación original
        Usuario adminDestino = padre.getRegistradoPor();
        if (adminDestino == null) return;

        Notificacion resp = new Notificacion();
        resp.setUsuarioDestino(adminDestino);
        resp.setTipo(TIPO_OBSERVACION_ADMIN);
        resp.setAsunto("Respuesta: " + padre.getAsunto());
        resp.setCuerpo(respuesta);
        resp.setNotificacionPadre(padre);
        resp.setEstadoHilo(HILO_RESPONDIDA);
        resp.setRespuesta(respuesta);
        resp.setRespondidaPor(vendedor);
        resp.setRespondidaEn(LocalDateTime.now());
        if (padre.getInscripcion() != null) resp.setInscripcion(padre.getInscripcion());
        if (padre.getPuesto() != null) resp.setPuesto(padre.getPuesto());

        Notificacion guardada = notificacionDao.save(resp);

        // Actualizar padre
        padre.setEstadoHilo(HILO_RESPONDIDA);
        padre.setRespuesta(respuesta);
        padre.setRespondidaPor(vendedor);
        padre.setRespondidaEn(LocalDateTime.now());
        notificacionDao.save(padre);

        // Difundir respuesta al admin
        difundir(guardada);
    }

    /** Admin cierra el hilo (marca RESUELTA). */
    @Transactional
    public void resolverHilo(Long notificacionPadreId, Usuario admin, String respuestaFinal) {
        Notificacion padre = notificacionDao.findById(notificacionPadreId).orElse(null);
        if (padre == null || !TIPO_OBSERVACION_ADMIN.equals(padre.getTipo())) return;

        padre.setEstadoHilo(HILO_RESUELTA);
        padre.setRespuesta(respuestaFinal);
        padre.setRespondidaPor(admin);
        padre.setRespondidaEn(LocalDateTime.now());
        notificacionDao.save(padre);

        // Notificar al vendedor que se resolvió
        notificar(padre.getUsuarioDestino().getId(),
                TIPO_OBSERVACION_ADMIN,
                "Resuelta: " + padre.getAsunto(),
                respuestaFinal,
                padre.getInscripcion() != null ? padre.getInscripcion().getId() : null,
                padre.getPuesto() != null ? padre.getPuesto().getId() : null);
    }

    /** Payload WebSocket (mismo record que antes, compatible). */
    public record NotificacionWS(String tipo, String asunto, String cuerpo,
                                 Long inscripcionId, Long puestoId, LocalDateTime fecha) {
    }
}