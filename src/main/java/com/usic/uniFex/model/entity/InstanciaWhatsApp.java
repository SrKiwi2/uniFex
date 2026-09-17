package com.usic.uniFex.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "instancia_whatsapp")
@Getter
@Setter
public class InstanciaWhatsApp {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 120)
    private String nombre;
    @Column(name = "url_api", nullable = false, length = 500)
    private String urlApi;
    @JsonIgnore
    @Column(name = "clave_api", nullable = false, length = 500)
    private String claveApi;
    @Column(nullable = false, length = 200)
    private String instancia;
    @Column(nullable = false)
    private boolean activa;
}
