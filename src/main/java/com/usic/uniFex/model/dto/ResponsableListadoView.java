package com.usic.uniFex.model.dto;

public interface ResponsableListadoView {
        Long getId();

    Long getEntidadId();
    String getEntidadNombre();

    Long getPersonaId();
    String getNombre();
    String getPaterno();
    String getMaterno();
    String getCi();
    String getCelular();
    String getFoto();

    default String getNombreCompleto() {
        String n = getNombre()  != null ? getNombre().trim()  : "";
        String p = getPaterno() != null ? getPaterno().trim() : "";
        String m = getMaterno() != null ? getMaterno().trim() : "";
        return String.join(" ", n, p, m).replaceAll("\\s{2,}", " ").trim();
    }
}
