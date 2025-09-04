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

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "usuario_rol",
            joinColumns = @JoinColumn(name = "usuario_id"),
            inverseJoinColumns = @JoinColumn(name = "rol_id"))
    private Set<Rol> roles;
}
