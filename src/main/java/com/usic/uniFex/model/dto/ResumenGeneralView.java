package com.usic.uniFex.model.dto;

import java.math.BigDecimal;

/** Totales generales de la feria para el reporte administrativo (KPIs). */
public interface ResumenGeneralView {
    Long getInscripciones();
    Long getPuestos();
    BigDecimal getTotalBs();
}
