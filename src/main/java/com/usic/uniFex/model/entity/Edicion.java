package com.usic.uniFex.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Edicion de la feria (la de cada anio: FEXPO 2025, FEXPO 2026…). Las inscripciones y
 * ventas se etiquetan por edicion ({@code inscripcion.id_edicion}, {@code venta_boleto.id_edicion})
 * y los listados filtran por la edicion {@code activa} (ver V6 y fn_get_inscripciones).
 * No hereda de {@code AuditoriaConfig}: la tabla no tiene columnas de auditoria.
 */
@Entity
@Table(name = "edicion")
@Setter
@Getter
public class Edicion {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String nombre;

    @Column(nullable = false)
    private Integer anio;

    @Column(nullable = false)
    private Boolean activa;
}
