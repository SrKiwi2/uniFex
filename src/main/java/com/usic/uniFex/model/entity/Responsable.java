package com.usic.uniFex.model.entity;

import com.usic.uniFex.Config.AuditoriaConfig;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "responsable")
@Setter @Getter
public class Responsable extends AuditoriaConfig{
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_persona")
    private Persona persona;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_entidad")
    private Entidad entidad;

    /**
     * true = es el dueño de la caseta; false = acompañante.
     *
     * Antes los dos responsables de una entidad eran indistinguibles y el unico indicio era
     * el orden de creacion, que se pierde en cuanto se edita algo. Se necesita explicito para
     * emitir credenciales y para saber a quien reclamarle un pago pendiente.
     */
    @Column(name = "es_titular", nullable = false)
    private boolean esTitular;

    /**
     * true si esta POR ENCIMA del derecho que dan las casetas (V34).
     *
     * Cada caseta da derecho a dos responsables, o sea a dos credenciales. Al que pasa de ahi
     * se le cobra, y ese cobro va aparte del de la venta: es otro dia y otro recibo. Por eso el
     * importe y el comprobante viven aqui y no en la inscripcion.
     */
    @Column(name = "es_extra", nullable = false)
    private boolean esExtra;

    /** Bs cobrados por este responsable extra, congelados: la tarifa puede cambiar. */
    @Column(name = "monto_extra")
    private java.math.BigDecimal montoExtra;

    /** Ruta del comprobante de ese cobro, dentro de app.upload-root. */
    @Column(name = "comprobante_extra")
    private String comprobanteExtra;
}