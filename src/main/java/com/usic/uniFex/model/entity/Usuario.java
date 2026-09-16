package com.usic.uniFex.model.entity;

import com.usic.uniFex.Config.AuditoriaConfig;

import jakarta.persistence.Column;
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
@Table(name = "usuario")
@Setter @Getter
public class Usuario extends AuditoriaConfig{
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable=false, unique=true, length=50)
    private String username;

    @Column(nullable=false, length=120)
    private String password; // BCRYPT

    @ManyToOne(optional=false)
    @JoinColumn(name="persona_id")
    private Persona persona;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "rol_id", nullable = false)
    private Rol rol;

    /**
     * El area academica (facultad) que este usuario MONITOREA (V41).
     *
     * No es "de que carrera es" —eso vive en la persona, `persona.carrera`— sino "que facultad
     * le toca vigilar". Lo normal es que este vacio: solo lo lleva quien hace seguimiento.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_area_seguimiento")
    private Area areaSeguimiento;
}
