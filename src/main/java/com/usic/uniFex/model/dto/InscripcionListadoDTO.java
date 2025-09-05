package com.usic.uniFex.model.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record InscripcionListadoDTO(
    Long id,
    String entidad,
    String tipoEntidad,
    String nit,
    Integer cantidadPuestos,
    List<String> categorias,   // para chips/badges
    BigDecimal total,
    LocalDateTime fecha,
    String estado,
    String imgComprobante
) {
    
}
