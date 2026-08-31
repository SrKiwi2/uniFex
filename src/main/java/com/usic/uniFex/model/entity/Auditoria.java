package com.usic.uniFex.model.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Evento de auditoria del ciclo de vida de un registro de negocio (p. ej. una
 * inscripcion/venta): quien lo hizo, cuando (timestamp con segundos) y desde
 * donde (WEB o APK).
 *
 * Sin FK a la tabla auditada a proposito: el historico debe sobrevivir aunque el
 * registro de negocio se borre, y una FK bloquearia limpiezas manuales de pruebas.
 */
@Entity
@Table(name = "auditoria")
@Setter
@Getter
public class Auditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Nombre de la tabla auditada, p. ej. "inscripcion". */
    private String tabla;

    @Column(name = "id_registro")
    private Long idRegistro;

    /** Codigo del evento: REGISTRO, COMPROBANTE, CANCELACION… */
    private String accion;

    private String detalle;

    @Column(name = "id_usuario")
    private Long idUsuario;

    @Column(name = "usuario_nombre")
    private String usuarioNombre;

    /** Origen de la peticion: WEB o APK. */
    private String origen;

    private LocalDateTime fecha;
}
