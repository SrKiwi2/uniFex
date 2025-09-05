package com.usic.uniFex.model.entity;

import com.usic.uniFex.Config.AuditoriaConfig;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "admistrativo")
@Setter
@Getter
public class Administrativo extends AuditoriaConfig{
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String codigoFuncionario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_persona")
    private Persona persona;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_cargo")
    private Cargo cargo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_oficina")
    private Oficina oficina;
}