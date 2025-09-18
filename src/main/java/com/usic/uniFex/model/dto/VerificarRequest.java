package com.usic.uniFex.model.dto;

public class VerificarRequest {
    private String ci;
    private Integer idEntidad;

    // Getters y Setters
    public String getCi() {
        return ci;
    }
    public void setCi(String ci) {
        this.ci = ci;
    }

    public Integer getIdEntidad() {
        return idEntidad;
    }
    public void setIdEntidad(Integer idEntidad) {
        this.idEntidad = idEntidad;
    }
}
