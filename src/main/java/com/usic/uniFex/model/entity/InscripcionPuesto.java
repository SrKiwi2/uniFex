package com.usic.uniFex.model.entity;

import java.math.BigDecimal;

import com.usic.uniFex.Config.AuditoriaConfig;

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

@Entity
@Table(name = "inscripcion_puesto")
@Setter
@Getter
public class InscripcionPuesto extends AuditoriaConfig{
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private BigDecimal costo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_puesto")
    private Puesto puesto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_inscripcion")
    private Inscripcion inscripcion;
    
}
