package com.usic.uniFex.model.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.usic.uniFex.Config.AuditoriaConfig;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "inscripcion")
@Setter
@Getter
public class Inscripcion extends AuditoriaConfig{
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String entidadBancaria;
    private Long numComprobante;
    private String imgComprobante;
    private boolean pagoContado;
    private Date fechaInicio;
    private Date fechaFin;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_entidad")
    private Entidad entidad;

    private LocalDateTime fechaCompra;
    private String inscripcionEstado;

    /**
     * Edicion de la feria a la que pertenece la venta.
     *
     * La columna {@code id_edicion} existia desde V3, pero esta entidad no la mapeaba y el
     * registro viejo nunca la rellenaba: las ventas nacian huerfanas y desaparecian de
     * cualquier listado filtrado por edicion. El registro por API la asigna siempre.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_edicion")
    private Edicion edicion;

    @OneToMany(mappedBy = "inscripcion", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private List<InscripcionPuesto> inscripcionPuestos = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "_registro_idUsuario", insertable = false, updatable = false)
    private Usuario registroUsuario;

    /**
     * Datos de la cancelacion (V10). La venta cancelada queda con baja logica
     * ({@code _estado = 'X'}) y estos campos conservan el motivo y quien/cuando/
     * desde donde la cancelo, para el historico.
     */
    private String motivoCancelacion;

    private LocalDateTime fechaCancelacion;

    @Column(name = "origen_cancelacion")
    private String origenCancelacion;

    @Column(name = "cancelada_por_id_usuario")
    private Long canceladaPorIdUsuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancelada_por_id_usuario", insertable = false, updatable = false)
    private Usuario canceladaPorUsuario;

}