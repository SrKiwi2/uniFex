package com.usic.uniFex.model.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Persona interesada en exponer un stand, capturada desde el formulario público "Quiero
 * exponer" de la SPA (sin autenticación — quien la crea es siempre un visitante anónimo).
 *
 * No extiende {@link com.usic.uniFex.Config.AuditoriaConfig}: esas columnas asumen un
 * usuario logueado que registra/modifica, y aquí no lo hay.
 */
@Entity
@Table(name = "interesado_stand")
@Getter
@Setter
public class InteresadoStand {

    public static final String ESTADO_NUEVO = "NUEVO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nombre_completo", nullable = false, length = 200)
    private String nombreCompleto;

    @Column(name = "celular", nullable = false, length = 30)
    private String celular;

    @Column(name = "empresa", nullable = false, length = 200)
    private String empresa;

    @Column(name = "rubro", nullable = false, length = 200)
    private String rubro;

    @ManyToOne
    @JoinColumn(name = "id_categoria", nullable = false)
    private Categoria categoria;

    @Column(name = "estado", nullable = false, length = 20)
    private String estado = ESTADO_NUEVO;

    @Column(name = "fecha_registro", nullable = false)
    private LocalDateTime fechaRegistro = LocalDateTime.now();
}
