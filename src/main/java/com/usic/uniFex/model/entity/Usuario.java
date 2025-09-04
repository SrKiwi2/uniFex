package com.usic.uniFex.model.entity;

import java.util.Set;

import com.usic.uniFex.Config.AuditoriaConfig;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "usuario")
@Setter @Getter
public class Usuario extends AuditoriaConfig{
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable=false, unique=true, length=50)
    private String username;

    @Column(nullable=false, length=120)
    private String password; // BCRYPT

    @OneToOne(optional=false)
    @JoinColumn(name="persona_id")
    private Persona persona;

    @OneToOne(optional=false)
    @JoinColumn(name="rol_id")
    private Rol rol;
}
