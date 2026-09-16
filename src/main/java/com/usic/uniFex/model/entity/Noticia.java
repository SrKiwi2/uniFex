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
 * Una noticia del carrusel de novedades de la vista publica ({@code FeriaPublica.vue}), V42.
 *
 * Siempre lleva foto o video: es lo que se ve en el carrusel, y el texto solo le da contexto.
 * Por edicion, igual que {@link NocheFexpo}. Se listan en el orden en que se registraron (id
 * descendente), de la ultima a la primera.
 */
@Entity
@Table(name = "noticia")
@Setter
@Getter
public class Noticia extends AuditoriaConfig {

    /** Valores de {@code _estado} en esta tabla: igual que noche_fexpo, no "ACTIVO"/"ELIMINADO". */
    public static final String REGISTRO_ACTIVO = "A";
    public static final String REGISTRO_ANULADO = "X";

    /** Valores de {@code medio_tipo}. */
    public static final String MEDIO_FOTO = "FOTO";
    public static final String MEDIO_VIDEO = "VIDEO";

    /** Primer y ultimo dia de la feria que puede elegirse ({@link #dia}). */
    public static final int DIA_MINIMO = 1;
    public static final int DIA_MAXIMO = 3;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_edicion", nullable = false)
    private Edicion edicion;

    /** Dia en que ocurrio la noticia. */
    @Column(name = "fecha", nullable = false)
    private LocalDate fecha;

    /** Dia de la feria al que pertenece (1, 2 o 3): agrupa la vista de todas las noticias. */
    @Column(name = "dia", nullable = false)
    private Integer dia;

    @Column(name = "titulo", nullable = false, length = 160)
    private String titulo;

    /** Contexto de la foto o el video. */
    @Column(name = "texto", nullable = false, length = 2000)
    private String texto;

    /** {@link #MEDIO_FOTO} o {@link #MEDIO_VIDEO}, segun la extension subida. */
    @Column(name = "medio_tipo", nullable = false, length = 10)
    private String medioTipo;

    /** Ruta relativa bajo {@code app.upload-root} (bucket "noticias"), servida en {@code /files/**}. */
    @Column(name = "medio_archivo", nullable = false, length = 300)
    private String medioArchivo;
}
