package com.usic.uniFex.model.dto;

import com.usic.uniFex.model.entity.Edicion;

/**
 * Vista de una edicion para la SPA: identificador, nombre (para el selector) y si es
 * la activa (la que filtran por defecto los listados).
 */
public record EdicionDTO(
        Long id,
        String nombre,
        Integer anio,
        boolean activa) {

    public static EdicionDTO de(Edicion e) {
        return new EdicionDTO(e.getId(), e.getNombre(), e.getAnio(), Boolean.TRUE.equals(e.getActiva()));
    }
}
