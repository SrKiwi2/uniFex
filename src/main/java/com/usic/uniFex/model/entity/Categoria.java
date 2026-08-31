package com.usic.uniFex.model.entity;

import com.usic.uniFex.Config.AuditoriaConfig;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "categoria")
@Setter
@Getter
public class Categoria extends AuditoriaConfig{

    /** Valores de {@code _estado} en esta tabla. Ver {@link Puesto#REGISTRO_ACTIVO}. */
    public static final String REGISTRO_ACTIVO  = "A";
    public static final String REGISTRO_ANULADO = "X";

    /**
     * Tamaño base por defecto de una caseta en el plano (1.2% del ancho). Coincide con el
     * DEFAULT de V5, pero hay que ponerlo tambien aqui: Hibernate incluye la columna en el
     * INSERT con NULL, asi que el DEFAULT de la base nunca llega a aplicarse.
     */
    public static final double TAMANO_MAPA_POR_DEFECTO = 0.012;

    /**
     * Medida de negocio por defecto de las casetas ({@code Puesto.tamano}). Antes se quedaba
     * en NULL al crearlas desde el Editor, y eso rompia el calculo de precio de respaldo de
     * {@code obtenercostopuesto}, que compara el tamaño en texto.
     */
    public static final String TAMANO_POR_DEFECTO = "3x3";

    /** Precio por defecto de una categoria nueva: 0 = "todavia sin definir". */
    public static final java.math.BigDecimal PRECIO_POR_DEFECTO = java.math.BigDecimal.ZERO;

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nombre;
    private String descripcion;
    private String color;

    /** Figura del marcador en el mapa: cuadrado | circulo | triangulo. */
    private String forma;

    /**
     * Tamaño base de las casetas de esta categoria en el plano, como fraccion del ancho
     * (0..1), igual que mapa_x/mapa_y. El tamaño final de una caseta es
     * {@code tamanoMapa * puesto.mapaEscala}. No confundir con {@code Puesto.tamano},
     * que es la medida de negocio ("3x3").
     */
    @Column(name = "tamano_mapa")
    private Double tamanoMapa;

    /**
     * Precio de venta de una caseta de esta categoria, en Bs.
     *
     * La columna existia en la base desde siempre y la stored function
     * {@code obtenercostopuesto} la consulta como primera opcion, pero esta entidad **no la
     * mapeaba**: las categorias creadas desde el Editor nacian con precio 0 y, al no tener
     * tampoco {@code tamano}, el calculo de respaldo por tipo de entidad tampoco entraba.
     * El efecto era que toda venta se registraba en 0 Bs sin avisar de nada.
     */
    @Column(name = "precio_base")
    private java.math.BigDecimal precioBase;
}