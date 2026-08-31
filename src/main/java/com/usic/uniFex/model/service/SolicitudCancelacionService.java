package com.usic.uniFex.model.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.usic.uniFex.model.dao.IInscripcionDao;
import com.usic.uniFex.model.dao.ISolicitudCancelacionDao;
import com.usic.uniFex.model.dao.IUsuarioDao;
import com.usic.uniFex.model.dto.SolicitudCancelacionDTO;
import com.usic.uniFex.model.entity.Inscripcion;
import com.usic.uniFex.model.entity.SolicitudCancelacion;
import com.usic.uniFex.model.entity.Usuario;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Flujo de solicitud de cancelacion con aprobacion de administracion (V11):
 *
 *  1. El vendedor que registro la venta solicita cancelarla con un motivo
 *     (obligatorio). La solicitud nace PENDIENTE. Una venta admite UNA solicitud
 *     pendiente a la vez: la segunda se rechaza con "ya hay una en espera".
 *  2. Administracion ve la cola de pendientes y aprueba o rechaza (con respuesta
 *     obligatoria al rechazar). Cada transicion es un UPDATE condicional: si dos
 *     admins resuelven la misma a la vez, gana la BD, no el codigo.
 *  3. Solo con una solicitud APROBADA vigente el vendedor puede cancelar la venta;
 *     eso lo valida CancelarInscripcionService al ejecutar la cancelacion.
 *
 * Cada paso deja huella en auditoria (SOLICITUD_CANCELACION, APROBACION_CANCELACION,
 * RECHAZO_CANCELACION) y notifica por WebSocket al interesado despues del commit.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SolicitudCancelacionService {

    /** Motivo que el vendedor escribe al pedir la cancelacion (mismo tope que el de la cancelacion). */
    public static final int MOTIVO_MAX = 500;

    private final ISolicitudCancelacionDao solicitudDao;
    private final IInscripcionDao inscripcionDao;
    private final IUsuarioDao usuarioDao;
    private final AuditoriaService auditoria;
    private final NotificacionService notificaciones;

    /** Resultado de una operacion del flujo: ok + mensaje para la SPA. */
    public record Resultado(boolean ok, String mensaje, Long solicitudId) {
        static Resultado error(String mensaje) {
            return new Resultado(false, mensaje, null);
        }
    }

    /** Cuerpo del POST de solicitud: el motivo es lo unico que llega del cliente. */
    public record PeticionSolicitud(String motivo) {
    }

    /** Cuerpo del POST de rechazo: la respuesta es obligatoria. */
    public record PeticionResolucion(String respuesta) {
    }

    /**
     * El vendedor pide cancelar su venta. Valida motivo, que la venta exista, que no este
     * ya cancelada y que no haya otra solicitud pendiente. Audita y avisa a administracion.
     */
    @Transactional
    public Resultado solicitar(Long inscripcionId, String motivo, Long usuarioId, String origen) {
        if (motivo == null || motivo.isBlank()) {
            return Resultado.error("El motivo de la solicitud es obligatorio");
        }
        if (motivo.trim().length() > MOTIVO_MAX) {
            return Resultado.error("El motivo no puede superar los " + MOTIVO_MAX + " caracteres");
        }
        Inscripcion i = inscripcionDao.findById(inscripcionId).orElse(null);
        if (i == null) {
            return Resultado.error("La venta no existe");
        }
        if ("X".equals(i.getEstado())) {
            return Resultado.error("La venta ya esta cancelada");
        }
        if (solicitudDao.pendienteDeInscripcion(inscripcionId).isPresent()) {
            return Resultado.error("Ya hay una solicitud de cancelacion en espera de revision");
        }

        SolicitudCancelacion s = new SolicitudCancelacion();
        s.setIdInscripcion(inscripcionId);
        s.setMotivo(motivo.trim());
        s.setEstadoSolicitud(SolicitudCancelacion.PENDIENTE);
        s.setFechaSolicitud(LocalDateTime.now());
        s.setRegistroIdUsuario(usuarioId);
        s.setEstado("ACTIVO");
        solicitudDao.save(s);

        auditoria.registrar(AuditoriaService.TABLA_INSCRIPCION, inscripcionId,
                AuditoriaService.ACCION_SOLICITUD_CANCELACION, motivo.trim(), usuarioId, origen);

        notificaciones.trasCommit(() -> notificaciones.notificarAdministracion(
                NotificacionService.TIPO_SOLICITUD_NUEVA,
                "Solicitud de cancelacion pendiente de revision", inscripcionId));
        log.info("Solicitud de cancelacion {} para inscripcion {} por usuario={}",
                s.getId(), inscripcionId, usuarioId);
        return new Resultado(true, "Solicitud enviada. Queda en espera de la revision de administracion.",
                s.getId());
    }

    /** La cola de pendientes para administracion. */
    @Transactional(readOnly = true)
    public List<SolicitudCancelacionDTO> pendientes() {
        return solicitudDao.pendientes().stream().map(this::aDTO).collect(Collectors.toList());
    }

    /** El historico de resueltas para administracion. */
    @Transactional(readOnly = true)
    public List<SolicitudCancelacionDTO> resueltas() {
        return solicitudDao.resueltas().stream().map(this::aDTO).collect(Collectors.toList());
    }

    /** Las solicitudes que pidio un vendedor ("mis solicitudes": aprobada, rechazada o en espera). */
    @Transactional(readOnly = true)
    public List<SolicitudCancelacionDTO> delVendedor(Long usuarioId) {
        return solicitudDao.delVendedor(usuarioId).stream().map(this::aDTO).collect(Collectors.toList());
    }

    /**
     * La solicitud vigente de una venta: la pendiente si la hay, si no la aprobada,
     * si no la ultima (rechazada). O null si nunca se solicito. La usa la SPA para
     * saber si el boton de cancelar del vendedor esta habilitado o si sigue en espera.
     */
    @Transactional(readOnly = true)
    public SolicitudCancelacionDTO estadoDeVenta(Long inscripcionId) {
        var pendiente = solicitudDao.pendienteDeInscripcion(inscripcionId);
        if (pendiente.isPresent()) return aDTO(pendiente.get());
        var aprobada = solicitudDao.aprobadaDeInscripcion(inscripcionId);
        if (aprobada.isPresent()) return aDTO(aprobada.get());
        return solicitudDao.ultimaDeInscripcion(inscripcionId).map(this::aDTO).orElse(null);
    }

    /**
     * Administracion aprueba una solicitud pendiente. El UPDATE condicional decide:
     * si ya la resolvio otro admin en paralelo, no se aprueba dos veces. Al aprobarse,
     * el vendedor queda habilitado para cancelar y se le avisa por WebSocket.
     */
    @Transactional
    public Resultado aprobar(Long solicitudId, Long adminId, String origen) {
        return resolver(solicitudId, SolicitudCancelacion.APROBADA, "Aprobada", adminId, origen,
                NotificacionService.TIPO_APROBADA, "Tu solicitud de cancelacion fue aprobada");
    }

    /**
     * Administracion rechaza una solicitud pendiente con una respuesta (obligatoria):
     * el vendedor necesita saber POR QUE no se aprobo, no solo que no se aprobo.
     */
    @Transactional
    public Resultado rechazar(Long solicitudId, String respuesta, Long adminId, String origen) {
        if (respuesta == null || respuesta.isBlank()) {
            return Resultado.error("La respuesta del rechazo es obligatoria");
        }
        return resolver(solicitudId, SolicitudCancelacion.RECHAZADA, respuesta.trim(), adminId, origen,
                NotificacionService.TIPO_RECHAZADA, "Tu solicitud de cancelacion fue rechazada");
    }

    /** Resolucion comun a aprobar y rechazar: UPDATE condicional + auditoria + aviso al vendedor. */
    private Resultado resolver(Long solicitudId, String nuevoEstado, String respuesta,
                               Long adminId, String origen, String tipoAviso, String mensajeAviso) {
        SolicitudCancelacion s = solicitudDao.findById(solicitudId).orElse(null);
        if (s == null) {
            return Resultado.error("La solicitud no existe");
        }
        int filas = (nuevoEstado.equals(SolicitudCancelacion.APROBADA))
                ? solicitudDao.aprobarSiPendiente(solicitudId, respuesta, adminId)
                : solicitudDao.rechazarSiPendiente(solicitudId, respuesta, adminId);
        if (filas == 0) {
            return Resultado.error("La solicitud ya fue resuelta");
        }
        s.setModificacionIdUsuario(adminId);

        String detalle = "Inscripcion " + s.getIdInscripcion() + " | Motivo: " + s.getMotivo()
                + (respuesta == null || respuesta.isBlank() ? "" : " | " + respuesta);
        auditoria.registrar(AuditoriaService.TABLA_INSCRIPCION, s.getIdInscripcion(),
                nuevoEstado.equals(SolicitudCancelacion.APROBADA)
                        ? AuditoriaService.ACCION_APROBACION_CANCELACION
                        : AuditoriaService.ACCION_RECHAZO_CANCELACION,
                detalle, adminId, origen);

        // El vendedor que pidio la cancelacion es el dueño de la solicitud (registroIdUsuario).
        Long vendedorId = s.getRegistroIdUsuario();
        notificaciones.trasCommit(() ->
                notificaciones.notificar(vendedorId, tipoAviso, mensajeAviso, s.getIdInscripcion()));
        log.info("Solicitud {} {} por admin={} inscripcion={}", solicitudId, nuevoEstado, adminId,
                s.getIdInscripcion());
        return new Resultado(true, "Solicitud " + nuevoEstado.toLowerCase(), solicitudId);
    }

    private SolicitudCancelacionDTO aDTO(SolicitudCancelacion s) {
        Inscripcion i = s.getInscripcion();
        String entidad = i != null && i.getEntidad() != null ? i.getEntidad().getNombre() : null;
        String vendedor = nombreDe(s.getRegistroIdUsuario());
        String resueltoPor = nombreDe(s.getModificacionIdUsuario());
        return new SolicitudCancelacionDTO(
                s.getId(),
                s.getIdInscripcion(),
                entidad,
                vendedor,
                s.getMotivo(),
                s.getEstadoSolicitud(),
                s.getRespuesta(),
                s.getFechaSolicitud(),
                s.getFechaResolucion(),
                resueltoPor);
    }

    /** Nombre para mostrar de un usuario: el de su persona, o el username como respaldo. */
    private String nombreDe(Long usuarioId) {
        if (usuarioId == null) return null;
        return Optional.ofNullable(usuarioDao.findById(usuarioId).orElse(null)).map(us -> {
            var p = us.getPersona();
            return (p != null && p.getNombreCompleto() != null && !p.getNombreCompleto().isBlank())
                    ? p.getNombreCompleto()
                    : us.getUsername();
        }).orElse(null);
    }
}
