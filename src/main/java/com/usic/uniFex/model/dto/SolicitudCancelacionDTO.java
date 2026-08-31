package com.usic.uniFex.model.dto;

import java.time.LocalDateTime;

/**
 * Solicitud de cancelacion para la SPA (V11).
 *
 * El mismo DTO sirve para las tres vistas del flujo: la cola del administrador
 * (pendientes con la venta y quien la pidio), el historial de resueltas, y
 * "mis solicitudes" del vendedor (donde ve si le aprobaron o le rechazaron).
 *
 * Es un record: el orden de los campos es el orden del JSON.
 */
public record SolicitudCancelacionDTO(
        Long id,
        Long inscripcionId,
        String entidad,
        String vendedor,
        String motivo,
        String estado,
        String respuesta,
        LocalDateTime fechaSolicitud,
        LocalDateTime fechaResolucion,
        String resueltoPor) {
}
