package com.usic.uniFex.model.dto;

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
        boolean tieneUsuario) {

    public static PersonaDTO de(Persona p, boolean tieneUsuario) {
        return new PersonaDTO(
                p.getId(), p.getNombre(), p.getPaterno(), p.getMaterno(),
                p.getCi(), p.getCorreo(), p.getCelular(),
                nombreCompleto(p), tieneUsuario);
    }

    private static String nombreCompleto(Persona p) {
        return java.util.stream.Stream.of(p.getNombre(), p.getPaterno(), p.getMaterno())
                .filter(s -> s != null && !s.isBlank())
                .reduce((a, b) -> a + " " + b).orElse("(sin nombre)");
    }
}
