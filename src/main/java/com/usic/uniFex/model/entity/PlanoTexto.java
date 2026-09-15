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

/**
 * Un rotulo libre sobre el plano: "ENTRADA", "TARIMA", "ZONA A".
 *
 * Todo va en FRACCIONES 0..1 del ancho del plano, igual que las casetas. Es lo que hace que un
 * rotulo colocado desde un portatil caiga en el mismo sitio en un telefono: en pixeles, cada
 * pantalla lo pondria en otro lado.
 *
 * Ojo con los nombres de columna: `mapaX` en Java se convertiria en `mapax` (sin guion bajo
 * antes de una letra sola), asi que van con `@Column` explicito. Es la misma trampa que ya
 * costo un 500 con `puesto.mapa_x`.
 */
@Entity
@Table(name = "plano_texto")
@Setter @Getter
public class PlanoTexto extends AuditoriaConfig {

    public static final String REGISTRO_ACTIVO  = "A";
    public static final String REGISTRO_ANULADO = "X";

    /** Alto de letra por defecto, en fracciones del ancho: parecido al alto de una caseta. */
    public static final double TAMANO_POR_DEFECTO = 0.02;
    /** Contorno por defecto, como fraccion del tamaño de la letra. */
    public static final double GROSOR_BORDE_POR_DEFECTO = 0.15;

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_edicion")
    private Edicion edicion;

    private String contenido;

    @Column(name = "mapa_x")
    private Double mapaX;

    @Column(name = "mapa_y")
    private Double mapaY;

    /** Alto de la letra como fraccion del ANCHO del plano. */
    private Double tamano;

    private String color;

    @Column(name = "color_borde")
    private String colorBorde;

    /** Grosor del contorno como fraccion del tamaño de letra: escala con ella. */
    @Column(name = "grosor_borde")
    private Double grosorBorde;

    private Double rotacion;
}
