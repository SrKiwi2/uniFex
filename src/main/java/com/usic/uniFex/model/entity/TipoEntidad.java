package com.usic.uniFex.model.entity;
import java.math.BigDecimal;

import com.usic.uniFex.Config.AuditoriaConfig;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "tipo_entidad")
@Setter
@Getter
public class TipoEntidad extends AuditoriaConfig{
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nombre; //microEmpresa macroEmpresa
}