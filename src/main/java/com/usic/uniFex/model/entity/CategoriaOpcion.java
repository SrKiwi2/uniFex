package com.usic.uniFex.model.entity;

import java.math.BigDecimal;

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

/**
 * Una forma de vender una caseta de esta categoria, con su nombre y su precio.
 *
 * Una categoria tiene al menos una —la PREDETERMINADA, que se llama como ella— y puede tener
 * mas: "PYMES" a 800 y "PYMES con tarima" a 1.200. Al registrar la venta se elige una por
 * categoria y de ahi sale el total.
 *
 * El precio de la predeterminada se refleja ademas en {@code categoria.precio_base}, porque lo
 * leen la funcion almacenada {@code obtenercostopuesto} y el mapa. Mantenerlos en el mismo valor
 * es responsabilidad de {@code CategoriaOpcionService}: no hay dos verdades, hay una verdad y
 * una copia para quien todavia no sabe leer esta tabla.
 */
@Entity
@Table(name = "categoria_opcion")
@Setter @Getter
public class CategoriaOpcion extends AuditoriaConfig {

    public static final String REGISTRO_ACTIVO  = "A";
    public static final String REGISTRO_ANULADO = "X";

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_categoria")
    private Categoria categoria;

    private String nombre;

    private BigDecimal precio;

    /** La que se aplica si nadie elige. Exactamente una viva por categoria (indice unico V33). */
    private Boolean predeterminada;

    @Column(name = "orden")
    private Integer orden;
}
