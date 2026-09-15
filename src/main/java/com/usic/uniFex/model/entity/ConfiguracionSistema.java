package com.usic.uniFex.model.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "configuracion_sistema")
@Getter
@Setter
public class ConfiguracionSistema {

    public static final String CLAVE_MANTENIMIENTO = "MODO_MANTENIMIENTO";

    @Id
    @Column(name = "clave", length = 80)
    private String clave;

    @Column(name = "activo", nullable = false)
    private Boolean activo = false;

    @Column(name = "mensaje", columnDefinition = "TEXT")
    private String mensaje;

    @Column(name = "actualizado_en", nullable = false)
    private LocalDateTime actualizadoEn = LocalDateTime.now();

    @Column(name = "actualizado_por")
    private Long actualizadoPor;
}
