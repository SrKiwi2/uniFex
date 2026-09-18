package com.usic.uniFex.model.entity;

import com.usic.uniFex.Config.AuditoriaConfig;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "personal_apoyo")
@Setter
@Getter
public class PersonalApoyo extends AuditoriaConfig {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "id_dependencia", nullable = false)
    private Dependencia dependencia;

    private String nombre;
    private String paterno;
    private String materno;
    private String ci;
    private String correo;
    private String celular;
    private String rol;

    /**
     * Ruta relativa de su foto (bucket "apoyo", V47). NULL = sin foto: la credencial sale
     * con el avatar generico de la plantilla.
     */
    private String foto;

    /** Que va a realizar, en breve (V43, tope V44). Opcional: NULL = sin tarea descrita. */
    @Column(name = "descripcion_tarea", length = 200)
    private String descripcionTarea;

    public String getNombreCompleto() {
        String n = nombre != null ? nombre.trim() : "";
        String p = paterno != null ? paterno.trim() : "";
        String m = materno != null ? materno.trim() : "";
        return java.util.stream.Stream.of(n, p, m)
                .filter(s -> !s.isEmpty())
                .reduce((a, b) -> a + " " + b).orElse("(sin nombre)");
    }
}