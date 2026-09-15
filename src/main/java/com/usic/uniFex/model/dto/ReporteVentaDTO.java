package com.usic.uniFex.model.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ReporteVentaDTO(
        Long inscripcionId,
        LocalDateTime fechaCompra,
        String entidad,
        String promotor,
        String responsables,
        String categorias,
        String casetas,
        Integer cantidadCasetas,
        BigDecimal totalBs,
        Boolean conComprobante) {
}
