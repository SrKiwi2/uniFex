package com.usic.uniFex.model.entity;

import java.util.List;

import com.usic.uniFex.Config.AuditoriaConfig;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "entidad")
@Setter
@Getter
public class Entidad extends AuditoriaConfig{
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nombre;
    private String nit;
    private String descripcion;
    private String Objeto;
    private String RepresentanteLegal;
    private String ciRepresentante;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_tipo_entidad")
    private TipoEntidad tipoEntidad;

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "entidad", fetch = FetchType.LAZY)
	private List<Responsable> responsables;
}