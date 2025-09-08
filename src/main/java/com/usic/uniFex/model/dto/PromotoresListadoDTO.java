package com.usic.uniFex.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PromotoresListadoDTO {
    private Long id;        // id del Responsable
    private String persona; // nombre completo
    private String ci;
    private String foto;
}
