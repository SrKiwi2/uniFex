package com.usic.uniFex.model.entity;

import java.time.LocalDateTime;

import com.usic.uniFex.Config.AuditoriaConfig;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "puesto")
@Setter
@Getter
public class Puesto extends AuditoriaConfig{
    /** Estados posibles de {@link #estadoPuesto}. */
    public static final String LIBRE      = "L";
    public static final String EN_TRAMITE = "T";
    public static final String OCUPADO    = "O";
    public static final String BLOQUEADO  = "X";

    /**
     * Valores de la columna de auditoria {@code _estado} en las tablas puesto y categoria:
     * 'A' = el registro existe, 'X' = anulado (baja logica).
     *
     * Cuidado: {@code _estado} no significa lo mismo en todas las tablas. En usuario e
     * inscripcion vale "ACTIVO"; en persona guarda un tipo ("RESPONSABLE", "PROMOTOR").
     * Lo unico transversal es que 'X' marca lo anulado. No copies "ACTIVO" aqui.
     */
    public static final String REGISTRO_ACTIVO  = "A";
    public static final String REGISTRO_ANULADO = "X";

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String codigo;
    private String tamano;
    private String estadoPuesto; // L=libre, T=en tramite, O=ocupado, X=bloqueado

    /** Usuario que tiene la reserva temporal (estado T). */
    private Long reservadoPorIdUsuario;

    /** Momento en que caduca la reserva temporal (estado T). */
    private LocalDateTime reservaExpira;

    /** Bloqueo optimista: evita perder actualizaciones concurrentes. */
    @Version
    private Long version;

    /** Posicion en el mapa (coordenadas normalizadas 0..1). NULL = sin ubicar. */
    @Column(name = "mapa_x")
    private Double mapaX;
    @Column(name = "mapa_y")
    private Double mapaY;

    /**
     * Multiplicador sobre el tamaño base de su categoria (1 = igual que sus hermanas).
     * Permite agrandar o achicar una caseta suelta sin partir la categoria.
     */
    @Column(name = "mapa_escala")
    private Double mapaEscala;

    /**
     * Giro de la caseta en el plano, en grados 0..359 en sentido horario (V25).
     *
     * NULL y 0 significan lo mismo. Se deja nulo por defecto para no tener que reescribir las
     * 500+ filas que ya existian, y quien lo lee lo trata como 0.
     */
    @Column(name = "mapa_rotacion")
    private Integer mapaRotacion;

    /**
     * Donde esta la caseta, en palabras ("frente a la puerta 3", "esquina norte").
     * Complementa al plano: sirve para explicarselo al cliente por telefono, donde el mapa
     * no ayuda.
     */
    @Column(length = 200)
    private String referencia;

    /**
     * Precio propio de ESTA caseta, en Bs. {@code null} = usa el de su categoria (V37).
     *
     * El caso que lo pide: se crea la categoria con su precio, se colocan sus casetas, y
     * despues resulta que algunas valen distinto —la esquina, la que da a la puerta—. Sin
     * esto, la unica forma de cambiarle el precio a una era cambiarselo a todas.
     *
     * <b>{@code null} no es 0.</b> Nulo significa "sigue a tu categoria", y si mañana esa
     * categoria sube de precio, la caseta sube con ella; un 0 significa "esta es gratis", y lo
     * dijo alguien a proposito. Por eso la columna es nullable y sin DEFAULT.
     *
     * Cuando esta puesto, <b>manda sobre la opcion de precio de la categoria</b>: la caseta
     * cuesta esto, se elija la opcion que se elija. Es la regla mas predecible —el precio de
     * la caseta ES el precio— y la unica que cumple lo que se pidio: tocar unas casetas sin
     * alterar las demas.
     */
    @Column(precision = 10, scale = 2)
    private java.math.BigDecimal precio;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_categoria")
    private Categoria categoria;
}