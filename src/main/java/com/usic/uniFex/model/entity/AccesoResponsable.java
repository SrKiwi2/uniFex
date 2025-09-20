package com.usic.uniFex.model.entity;

import java.time.LocalDateTime;

import com.usic.uniFex.Config.AuditoriaConfig;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "control_acceso_responsable")
@Getter @Setter
public class AccesoResponsable extends AuditoriaConfig{
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long idPersona;
    private String ci;

    private LocalDateTime fechaEntrada;
    private LocalDateTime fechaSalida;

    private String observacion;

    @Column(name = "_creado_en")
    private LocalDateTime creadoEn;

    @PrePersist
    public void prePersist() {
        if (fechaEntrada == null) fechaEntrada = LocalDateTime.now();
        if (creadoEn == null) creadoEn = LocalDateTime.now();
    }
}
