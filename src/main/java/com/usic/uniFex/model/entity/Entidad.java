package com.usic.uniFex.model.entity;

import java.util.List;

import com.usic.uniFex.Config.AuditoriaConfig;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
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
@Setter @Getter
public class Entidad extends AuditoriaConfig{
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nombre;
    private String nit;
    private String descripcion;
    private String Objeto;
    private String RepresentanteLegal;
    private String ciRepresentante;

    /**
     * Telefono del responsable legal: el dueño real de la caseta.
     *
     * Los celulares que ya se guardaban son los de los responsables que ATIENDEN el puesto, y
     * muchas veces son terceros. Cuando hay que llamar por un cobro o una incidencia, el que
     * hace falta es este (V16).
     *
     * @Column explicito: sin el, Hibernate deriva "celularrepresentante" —no mete guion bajo
     * antes de una letra sola ni parte bien esta forma— y falla con "no existe la columna".
     */
    @Column(name = "celular_representante", length = 30)
    private String celularRepresentante;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_tipo_entidad")
    private TipoEntidad tipoEntidad;

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "entidad", fetch = FetchType.LAZY)
	private List<Responsable> responsables;
}