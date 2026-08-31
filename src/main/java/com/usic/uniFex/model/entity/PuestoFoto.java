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
 * Foto de una caseta: como se ve de verdad en el lugar.
 *
 * Existe para que el vendedor se la enseñe al cliente desde el mapa, sin caminar hasta la
 * caseta. Van varias por caseta y con orden, por eso es una tabla y no una columna de texto
 * en {@code puesto}.
 */
@Entity
@Table(name = "puesto_foto")
@Setter
@Getter
public class PuestoFoto extends AuditoriaConfig {

    /** Valores de {@code _estado}: 'A' activa, 'X' borrada (baja logica, como en puesto). */
    public static final String ACTIVA = "A";
    public static final String BORRADA = "X";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_puesto")
    private Puesto puesto;

    /**
     * Ruta RELATIVA dentro de la carpeta de subidas ({@code puestos/2026/08/uuid-....jpg}),
     * tal cual la devuelve {@code FileStorageService}. Se sirve al navegador bajo /files/**.
     * Relativa a proposito: mover la carpeta de subidas no invalida lo guardado.
     */
    @Column(nullable = false, length = 300)
    private String ruta;

    @Column(length = 200)
    private String descripcion;

    /** Menor primero. La de menor orden hace de portada. */
    @Column(nullable = false)
    private Integer orden = 0;
}
