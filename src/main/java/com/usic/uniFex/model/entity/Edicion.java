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

    /**
     * Plano de esta edicion (ver V13). Ruta relativa bajo {@code app.upload-root}, servida
     * en {@code /files/**}. Si es null, la SPA cae al plano empaquetado de respaldo.
     */
    @Column(name = "plano_archivo", length = 300)
    private String planoArchivo;

    /**
     * Medidas de la imagen. El visor las necesita para encuadrar: sin la proporcion real,
     * el plano se dibuja deformado o el zoom inicial cae en el sitio equivocado.
     */
    @Column(name = "plano_ancho")
    private Integer planoAncho;

    @Column(name = "plano_alto")
    private Integer planoAlto;

    /** Sube en cada reemplazo. Viaja en la URL (?v=) para invalidar la cache del APK. */
    @Column(name = "plano_version", nullable = false)
    private Integer planoVersion = 0;

    @Column(name = "plano_subido_en")
    private java.time.LocalDateTime planoSubidoEn;
}
