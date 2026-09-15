package com.usic.uniFex.model.dto;

import java.util.List;

import com.usic.uniFex.model.entity.Area;

/**
 * Un area con sus carreras dentro.
 *
 * Va anidado porque asi se usa: un desplegable agrupado por area (un {@code optgroup} por
 * sigla) y un filtro por area. Dos listas planas obligarian al cliente a rehacer el
 * agrupamiento en cada pantalla que lo necesite.
 */
public record AreaDTO(Long id, String sigla, String nombre, List<CarreraDTO> carreras) {

    public static AreaDTO de(Area a, List<CarreraDTO> carreras) {
        return new AreaDTO(a.getId(), a.getSigla(), a.getNombre(), carreras);
    }
}
