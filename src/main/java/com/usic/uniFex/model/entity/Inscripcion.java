package com.usic.uniFex.model.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.usic.uniFex.Config.AuditoriaConfig;

import jakarta.persistence.CascadeType;
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

    @OneToMany(mappedBy = "inscripcion", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private List<InscripcionPuesto> inscripcionPuestos = new ArrayList<>();
}