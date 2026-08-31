package com.usic.uniFex.model.entity;

import com.usic.uniFex.Config.AuditoriaConfig;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "responsable")
@Setter @Getter
public class Responsable extends AuditoriaConfig{
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_persona")
    private Persona persona;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_entidad")
    private Entidad entidad;

    /**
     * true = es el dueño de la caseta; false = acompañante.
     *
     * Antes los dos responsables de una entidad eran indistinguibles y el unico indicio era
     * el orden de creacion, que se pierde en cuanto se edita algo. Se necesita explicito para
     * emitir credenciales y para saber a quien reclamarle un pago pendiente.
     */
    @Column(name = "es_titular", nullable = false)
    private boolean esTitular;
}