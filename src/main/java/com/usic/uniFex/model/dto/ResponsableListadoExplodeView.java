package com.usic.uniFex.model.dto;

public interface ResponsableListadoExplodeView {
    Long getId();

    Long getEntidadId();
    String getEntidadNombre();
    String getTipoEntidadNombre();
    String getEntidadObjeto();

    Long getPersonaId();
    String getNombre();
    String getPaterno();
    String getMaterno();
    String getCi();
    String getCelular();
    String getFoto();

    Long getCategoriaId();
    String getCategoriaNombre();
    String getPuestoCodigo();

    default String getNombreCompleto() {
        String n = getNombre()  != null ? getNombre().trim()  : "";
        String p = getPaterno() != null ? getPaterno().trim() : "";
        String m = getMaterno() != null ? getMaterno().trim() : "";
        return String.join(" ", n, p, m).replaceAll("\\s{2,}", " ").trim();
    }
}
