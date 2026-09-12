package com.usic.uniFex.model.entity;

import java.time.LocalDate;

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
 * Una noche de la cartelera de "Noches de FEXPO" (V26): la seccion de artistas de la vista
 * publica ({@code FeriaPublica.vue}). Administrable desde el panel (Fase de contenido publico),
 * a diferencia de antes, donde las 3 noches estaban hardcodeadas en el frontend.
 *
 * Por edicion, igual que {@code Inscripcion}/{@code VentaBoleto}: la cartelera de una edicion no
 * debe aparecer en la siguiente.
 */
@Entity
@Table(name = "noche_fexpo")
@Setter
@Getter
public class NocheFexpo extends AuditoriaConfig {

    /** Valores de {@code _estado} en esta tabla: igual que puesto/categoria, no "ACTIVO"/"ELIMINADO". */
    public static final String REGISTRO_ACTIVO = "A";
    public static final String REGISTRO_ANULADO = "X";

    /** Valores de {@code medio_tipo}. */
    public static final String MEDIO_FOTO = "FOTO";
    public static final String MEDIO_VIDEO = "VIDEO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_edicion", nullable = false)
    private Edicion edicion;

    /** Orden manual como desempate cuando dos noches caen en la misma fecha. */
    @Column(name = "orden", nullable = false)
    private Integer orden = 0;

    @Column(name = "fecha", nullable = false)
    private LocalDate fecha;

    @Column(name = "titulo", nullable = false, length = 120)
    private String titulo;

    @Column(name = "descripcion", length = 600)
    private String descripcion;

    /** NULL o vacio = "Artista por revelar" (silueta animada) en la vista publica. */
    @Column(name = "nombre_artista", length = 150)
    private String nombreArtista;

    /** Color de acento de la tarjeta. NULL = la vista publica cicla la paleta por defecto. */
    @Column(name = "color", length = 20)
    private String color;

    /** {@link #MEDIO_FOTO} o {@link #MEDIO_VIDEO}, segun la extension subida. NULL = sin medio. */
    @Column(name = "medio_tipo", length = 10)
    private String medioTipo;

    /** Ruta relativa bajo {@code app.upload-root} (bucket "noches"), servida en {@code /files/**}. */
    @Column(name = "medio_archivo", length = 300)
    private String medioArchivo;
}
