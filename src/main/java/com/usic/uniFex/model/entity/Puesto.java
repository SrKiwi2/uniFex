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
     * Donde esta la caseta, en palabras ("frente a la puerta 3", "esquina norte").
     * Complementa al plano: sirve para explicarselo al cliente por telefono, donde el mapa
     * no ayuda.
     */
    @Column(length = 200)
    private String referencia;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_categoria")
    private Categoria categoria;
}