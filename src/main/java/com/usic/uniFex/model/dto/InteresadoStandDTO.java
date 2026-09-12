package com.usic.uniFex.model.dto;

import java.time.LocalDateTime;

import com.usic.uniFex.model.entity.Categoria;
import com.usic.uniFex.model.entity.InteresadoStand;

/**
 * Un interesado en exponer, tal como lo lista y edita el panel "Interesados en exponer".
 * La categoria viaja aplanada (id, nombre, color) para filtrar y pintar sin otra consulta.
 */
public record InteresadoStandDTO(
        Long id,
        String nombreCompleto,
        String celular,
        String empresa,
        String rubro,
        Long categoriaId,
        String categoriaNombre,
        String categoriaColor,
        String estado,
        LocalDateTime fechaRegistro) {

    public static InteresadoStandDTO de(InteresadoStand i) {
        Categoria c = i.getCategoria();
        return new InteresadoStandDTO(
                i.getId(), i.getNombreCompleto(), i.getCelular(), i.getEmpresa(), i.getRubro(),
                c != null ? c.getId() : null,
                c != null ? c.getNombre() : null,
                c != null ? c.getColor() : null,
                i.getEstado(), i.getFechaRegistro());
    }
}
