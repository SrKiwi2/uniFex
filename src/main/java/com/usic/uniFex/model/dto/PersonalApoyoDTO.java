package com.usic.uniFex.model.dto;

import com.usic.uniFex.model.entity.PersonalApoyo;

/** Vista de personal de apoyo para la API. */
public record PersonalApoyoDTO(
        Long id,
        Long idDependencia,
        String dependenciaNombre,
        String nombre,
        String paterno,
        String materno,
        String ci,
        String correo,
        String celular,
        String rol,
        String nombreCompleto) {

    public static PersonalApoyoDTO de(PersonalApoyo p) {
        return new PersonalApoyoDTO(
                p.getId(),
                p.getDependencia() != null ? p.getDependencia().getId() : null,
                p.getDependencia() != null ? p.getDependencia().getNombre() : null,
                p.getNombre(),
                p.getPaterno(),
                p.getMaterno(),
                p.getCi(),
                p.getCorreo(),
                p.getCelular(),
                p.getRol(),
                p.getNombreCompleto());
    }
}