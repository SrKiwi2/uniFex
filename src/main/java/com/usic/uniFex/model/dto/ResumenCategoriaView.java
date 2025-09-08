

package com.usic.uniFex.model.dto;

import java.math.BigDecimal;

public interface ResumenCategoriaView {
    Long getUsuarioId();
    String getNombreCompleto();   // aquí
    Long getCategoriaId();
    String getCategoria();
    Long getCantidadInscripciones();
    Long getCantidadPuestos();
    BigDecimal getTotalBs();
}
