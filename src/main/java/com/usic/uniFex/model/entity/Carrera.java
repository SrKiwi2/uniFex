package com.usic.uniFex.model.entity;

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

/**
 * Una carrera de la UAP, dentro de un {@link Area} (ver V35).
 *
 * La persona apunta aqui, y el area se deduce por este camino: nadie escribe la sigla dos
 * veces. Cambiar una carrera de area es un UPDATE de una fila y se refleja en todos los
 * vendedores de esa carrera.
 */
@Entity
@Table(name = "carrera")
@Setter @Getter
public class Carrera extends AuditoriaConfig {

    /** Valores de {@code _estado} en esta tabla. Ver {@link Puesto#REGISTRO_ACTIVO}. */
    public static final String REGISTRO_ACTIVO  = "A";
    public static final String REGISTRO_ANULADO = "X";

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "id_area", nullable = false)
    private Area area;

    private String nombre;
}
