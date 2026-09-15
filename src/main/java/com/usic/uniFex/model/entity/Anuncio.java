package com.usic.uniFex.model.entity;

import java.time.LocalDateTime;

import com.usic.uniFex.Config.AuditoriaConfig;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Un mensaje para TODOS los usuarios.
 *
 * No se confunde con {@code Notificacion} (V11), que va dirigida a una persona y guarda una fila
 * por destinatario: un anuncio general por esa via serian 35 filas iguales, y habria que
 * repetirlo por cada usuario que entre despues de publicarlo.
 */
@Entity
@Table(name = "anuncio")
@Setter @Getter
public class Anuncio extends AuditoriaConfig {

    public static final String PUBLICADO = "A";
    public static final String RETIRADO  = "X";

    /** Los tres niveles. Deciden el color y la insistencia con que se muestra. */
    public static final String INFO    = "info";
    public static final String AVISO   = "aviso";
    public static final String URGENTE = "urgente";

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String titulo;

    private String mensaje;

    private String nivel;

    /** Nulo = hasta que alguien lo retire a mano. */
    @Column(name = "vigente_hasta")
    private LocalDateTime vigenteHasta;
}
