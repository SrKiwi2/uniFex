package com.usic.uniFex.model.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record InscripcionListadoDTO(
    Long id,
    String promotor,
    String entidad,
    String tipoEntidad,
    String nit,
    Long nroComprobante,
    Integer cantidadPuestos,
    List<String> categorias,   // para chips/badges
    BigDecimal total,
    LocalDateTime fecha,
    String estado,
    String imgComprobante,
        boolean pagoContado,          // <— nuevo
    List<String> codigosPuestos,      // <— nuevo
    String motivoCancelacion,         // <— cancelacion (V10)
    LocalDateTime fechaCancelacion,
    String canceladaPor,
    String origenCancelacion
) {
    public Long getNroComprobante() { return nroComprobante; }
    public String getPromotor() { return promotor; }
}
