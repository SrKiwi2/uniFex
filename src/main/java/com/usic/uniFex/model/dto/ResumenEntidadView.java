package com.usic.uniFex.model.dto;

import java.math.BigDecimal;

public interface ResumenEntidadView {
    Long getUsuarioId();
    String getUsername();
    Long getEntidadId();
    String getEntidad();
    Long getCantidadInscripciones();
    Long getCantidadPuestos();
    BigDecimal getTotalBs();
}
