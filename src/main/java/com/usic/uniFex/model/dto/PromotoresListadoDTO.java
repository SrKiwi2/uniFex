package com.usic.uniFex.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PromotoresListadoDTO {
    private Long id;        // id del Responsable
    private Long entidadId;       // id de Entidad (útil para links futuros)
    private String entidadNombre; // nombre de la Entidad
    private String persona; // nombre completo
    private String ci;
    private String foto;
}
