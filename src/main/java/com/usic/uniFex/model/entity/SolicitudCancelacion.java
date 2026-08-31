package com.usic.uniFex.model.entity;

import java.time.LocalDateTime;

import com.usic.uniFex.Config.AuditoriaConfig;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Solicitud de cancelacion de una venta, con aprobacion de administracion (V11).
 *
 * Ciclo de vida del estado: {@code PENDIENTE} -> {@code APROBADA} | {@code RECHAZADA}.
 * El vendedor que registro la venta solicita con un motivo; administracion la
 * aprueba o la rechaza dejando una respuesta; solo con una solicitud APROBADA
 * vigente el vendedor puede ejecutar la cancelacion (los datos de la cancelacion
 * en si viven en {@link Inscripcion}, ver V10).
 *
 * La baja logica heredada de {@link AuditoriaConfig} (columna {@code _estado}) es
 * independiente del estado de la solicitud ({@code estadoSolicitud}): una solicitud
 * resuelta se conserva en el historico, y una 'X' solo marca lo anulado.
 */
@Entity
@Table(name = "solicitud_cancelacion")
@Setter
@Getter
public class SolicitudCancelacion extends AuditoriaConfig {

    public static final String PENDIENTE = "PENDIENTE";
    public static final String APROBADA = "APROBADA";
    public static final String RECHAZADA = "RECHAZADA";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Venta cuya cancelacion se pide (sin FK a proposito, como auditoria). */
    @Column(name = "id_inscripcion", nullable = false)
    private Long idInscripcion;

    @Column(nullable = false)
    private String motivo;

    /** Respuesta de administracion: obligatoria al rechazar, opcional al aprobar. */
    private String respuesta;

    /** PENDIENTE, APROBADA o RECHAZADA (constantes de esta clase). */
    @Column(name = "estado_solicitud", nullable = false, length = 20)
    private String estadoSolicitud = PENDIENTE;

    @Column(name = "fecha_solicitud")
    private LocalDateTime fechaSolicitud;

    @Column(name = "fecha_resolucion")
    private LocalDateTime fechaResolucion;

    /**
     * Quien la resolvio (administracion): la columna `_modificacion_id_usuario` la
     * hereda de {@link AuditoriaConfig} como {@code modificacionIdUsuario}. El detalle
     * del nombre se resuelve aparte, como en el resto del modulo.
     */

    /** Venta a la que pertenece la solicitud, para el detalle del admin. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_inscripcion", insertable = false, updatable = false)
    private Inscripcion inscripcion;
}
