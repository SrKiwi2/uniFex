package com.usic.uniFex.model.entity;

import java.beans.Transient;

import com.usic.uniFex.Config.AuditoriaConfig;

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

@Entity
@Table(name = "persona")
@Setter
@Getter
public class Persona extends AuditoriaConfig{
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nombre;
    private String paterno;
    private String materno;
    private String ci;
    private String correo;
    private String celular;
    private String foto;

    /**
     * Carrera de la que sale esta persona (ver V35). De ella cuelga el area, que es por lo que
     * administracion filtra a los vendedores.
     *
     * <p><b>Es opcional, y tiene que serlo.</b> En esta tabla conviven las personas del sistema
     * con los responsables de entidad y los promotores traidos de la API de la UAP; exigirla
     * rompería el alta de todos ellos, que no tienen ninguna.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_carrera")
    private Carrera carrera;

    @Transient
    public String getNombreCompleto() {
        String n = nombre != null ? nombre.trim() : "";
        String p = paterno != null ? paterno.trim() : "";
        String m = materno != null ? materno.trim() : "";
        return String.join(" ", n, p, m).replaceAll("\\s{2,}", " ").trim();
    }
}