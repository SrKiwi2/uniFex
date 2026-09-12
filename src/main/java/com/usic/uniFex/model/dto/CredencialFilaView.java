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
    String getEntidad();
    String getRubro();
    Long getInscripcionId();
    Boolean getPagoContado();
    String getComprobante();
    String getCategorias();
    String getCasetas();
}
