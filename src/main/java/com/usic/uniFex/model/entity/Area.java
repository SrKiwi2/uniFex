package com.usic.uniFex.model.entity;

import com.usic.uniFex.Config.AuditoriaConfig;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Un area academica de la UAP: ACEF, ACBN, ACYT (ver V35).
 *
 * Es el nivel por el que administracion filtra a los vendedores ("los de ACYT"). Lo que se
 * enseña y por lo que se busca es la {@link #sigla}; el {@link #nombre} largo esta para la
 * ficha y para los listados impresos.
 */
@Entity
@Table(name = "area")
@Setter @Getter
public class Area extends AuditoriaConfig {

    /** Valores de {@code _estado} en esta tabla. Ver {@link Puesto#REGISTRO_ACTIVO}. */
    public static final String REGISTRO_ACTIVO  = "A";
    public static final String REGISTRO_ANULADO = "X";

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** ACEF, ACBN, ACYT. Unica entre las vivas (indice parcial de V35). */
    private String sigla;

    private String nombre;

    /** Orden en que salen en los desplegables. Sin esto salen por id, que no significa nada. */
    private Integer orden;
}
