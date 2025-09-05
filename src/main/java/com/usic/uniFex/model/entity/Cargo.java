package com.usic.uniFex.model.entity;

import com.usic.uniFex.Config.AuditoriaConfig;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "cargo")
@Setter @Getter
public class Cargo extends AuditoriaConfig{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idCargo;

    private String nombre;
    private String descripcion;
}