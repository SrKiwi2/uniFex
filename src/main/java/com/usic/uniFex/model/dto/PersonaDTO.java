package com.usic.uniFex.model.dto;

import com.usic.uniFex.model.entity.Area;
import com.usic.uniFex.model.entity.Carrera;
import com.usic.uniFex.model.entity.Persona;

/** Vista de una persona del sistema para la API de administracion. */
public record PersonaDTO(
        Long id,
        String nombre,
        String paterno,
        String materno,
        String ci,
        String correo,
        String celular,
        String nombreCompleto,
        boolean tieneUsuario,
        /** Carrera y area vienen aplanadas: la tabla las pinta, no navega por ellas. */
        Long carreraId,
        String carrera,
        Long areaId,
        String areaSigla) {

    public static PersonaDTO de(Persona p, boolean tieneUsuario) {
        Carrera c = p.getCarrera();
        Area a = c != null ? c.getArea() : null;
        return new PersonaDTO(
                p.getId(), p.getNombre(), p.getPaterno(), p.getMaterno(),
                p.getCi(), p.getCorreo(), p.getCelular(),
                nombreCompleto(p), tieneUsuario,
                c != null ? c.getId() : null,
                c != null ? c.getNombre() : null,
                a != null ? a.getId() : null,
                a != null ? a.getSigla() : null);
    }

    private static String nombreCompleto(Persona p) {
        return java.util.stream.Stream.of(p.getNombre(), p.getPaterno(), p.getMaterno())
                .filter(s -> s != null && !s.isBlank())
                .reduce((a, b) -> a + " " + b).orElse("(sin nombre)");
    }
}
