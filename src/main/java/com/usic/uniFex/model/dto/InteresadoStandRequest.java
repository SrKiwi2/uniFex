package com.usic.uniFex.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Cuerpo del POST público "Quiero exponer" (ver InteresadoStandController). */
public record InteresadoStandRequest(
        @NotBlank(message = "El nombre completo es obligatorio")
        @Size(max = 200, message = "El nombre completo es demasiado largo")
        String nombreCompleto,

        @NotBlank(message = "El número de celular es obligatorio")
        @Size(max = 30, message = "El número de celular es demasiado largo")
        String celular,

        @NotBlank(message = "El nombre de la empresa o emprendimiento es obligatorio")
        @Size(max = 200, message = "El nombre de la empresa es demasiado largo")
        String empresa,

        @NotBlank(message = "El rubro es obligatorio")
        @Size(max = 200, message = "El rubro es demasiado largo")
        String rubro,

        @NotNull(message = "Debes elegir una categoría")
        Long categoriaId) {
}
