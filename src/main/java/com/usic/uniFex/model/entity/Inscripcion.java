package com.usic.uniFex.model.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.usic.uniFex.Config.AuditoriaConfig;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "inscripcion")
@Setter
@Getter
public class Inscripcion extends AuditoriaConfig{
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Integer num_comprobante;
    private String img_comprobante;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_entidad")
    private Entidad entidad;

    private LocalDateTime fechaCompra;
    private String inscripcionEstado;

    @OneToMany(mappedBy = "inscripcion", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private List<InscripcionPuesto> inscripcionPuestos = new ArrayList<>();
}