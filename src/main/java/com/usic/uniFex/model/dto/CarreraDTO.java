package com.usic.uniFex.model.dto;

import java.util.List;

import com.usic.uniFex.model.entity.Area;
import com.usic.uniFex.model.entity.Carrera;

/**
 * Una carrera tal como la consumen los desplegables y los filtros de la SPA.
 *
 * Lleva el area DENTRO y no como referencia suelta: quien pinta una carrera casi siempre
 * quiere enseñar tambien de que area es ("INGENIERIA CIVIL · ACYT"), y obligar al cliente a
 * cruzarla contra otra lista es pedirle que repita aqui el join que la base ya sabe hacer.
 */
public record CarreraDTO(Long id, String nombre, Long areaId, String areaSigla, String areaNombre) {

    public static CarreraDTO de(Carrera c) {
        if (c == null) return null;
        Area a = c.getArea();
        return new CarreraDTO(c.getId(), c.getNombre(),
                a != null ? a.getId() : null,
                a != null ? a.getSigla() : null,
                a != null ? a.getNombre() : null);
    }

    public static List<CarreraDTO> de(List<Carrera> carreras) {
        return carreras.stream().map(CarreraDTO::de).toList();
    }
}
