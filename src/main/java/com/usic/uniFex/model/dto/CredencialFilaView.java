package com.usic.uniFex.model.dto;

/**
 * Una fila cruda de la consulta de credenciales. Proyeccion de Spring Data: los nombres de
 * los metodos casan con los alias del SELECT.
 *
 * No se expone tal cual al cliente — {@link CredencialDTO} es lo que sale por el API, ya con
 * el nombre armado, el codigo firmado y los requisitos resueltos.
 */
public interface CredencialFilaView {
    Long getResponsableId();
    String getNombre();
    String getPaterno();
    String getMaterno();
    String getCi();
    String getFoto();
    Boolean getEsTitular();
    /** true si esta por encima de los dos responsables por caseta y se le cobro (V34). */
    Boolean getEsExtra();
    java.math.BigDecimal getMontoExtra();
    String getComprobanteExtra();
    String getEntidad();
    String getRubro();
    Long getInscripcionId();
    Boolean getPagoContado();
    String getComprobante();
    /** Lo que cuesta la venta entera, sumando sus casetas vivas. 0 = no hay nada que pagar. */
    java.math.BigDecimal getTotalVenta();
    String getCategorias();
    String getCasetas();
}
