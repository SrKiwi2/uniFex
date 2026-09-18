package com.usic.uniFex.model.dto;

public interface ResponsableReporteView {
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

    Boolean getEsTitular();
    Boolean getEsExtra();

    Integer getVecesImpreso();
    java.time.LocalDateTime getUltimaImpresion();
    Boolean getAlgunaIncompleta();

    default String getNombreCompleto() {
        String n = getNombre()  != null ? getNombre().trim()  : "";
        String p = getPaterno() != null ? getPaterno().trim() : "";
        String m = getMaterno() != null ? getMaterno().trim() : "";
        return String.join(" ", n, p, m).replaceAll("\\s{2,}", " ").trim();
    }

    default String getTieneFoto() {
        return getFoto() != null && !getFoto().isBlank() ? "SI" : "NO";
    }

    default String getCredencialImpresa() {
        Integer veces = getVecesImpreso();
        return (veces != null && veces > 0) ? "SI" : "NO";
    }

    default String getSinCredencialImpresa() {
        return "";
    }
}