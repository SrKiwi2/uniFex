package com.usic.uniFex.model.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Detalle completo de una inscripcion (venta) para el modulo de Inscripciones:
 * los datos de la entidad, sus responsables, las casetas con su costo congelado,
 * la situacion de pago, los datos de cancelacion (si la hay) y la traza de
 * auditoria del ciclo de vida completo (quien/cuando/desde donde).
 */
public record InscripcionDetalleDTO(
    Long id,
    String entidad,
    String nit,
    String tipoEntidad,
    String representanteLegal,
    String ciRepresentante,
    String descripcion,
    String objeto,
    LocalDate fechaInicio,
    LocalDate fechaFin,
    String promotor,
    List<ResponsableDetalle> responsables,
    List<PuestoDetalle> puestos,
    BigDecimal total,
    LocalDateTime fechaCompra,
    boolean pagoContado,
    String entidadBancaria,
    Long numComprobante,
    String imgComprobante,
    String inscripcionEstado,
    String edicion,
    String motivoCancelacion,
    LocalDateTime fechaCancelacion,
    String canceladaPor,
    String origenCancelacion,
    List<AuditoriaDetalle> auditoria
) {

    /** Un responsable de la entidad. El primero (esTitular) es el dueño de la caseta. */
    public record ResponsableDetalle(String nombreCompleto, String ci, String correo,
                                     String celular, boolean esTitular) {
    }

    /** Una caseta vendida, con el precio que se congelo el dia de la venta. */
    public record PuestoDetalle(String codigo, String categoria, BigDecimal costo) {
    }

    /** Un evento de la traza: quien lo hizo, cuando y desde donde. */
    public record AuditoriaDetalle(String accion, String detalle, String usuarioNombre,
                                   String origen, LocalDateTime fecha) {
    }
}
