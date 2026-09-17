package com.usic.uniFex.model.dto;

import com.usic.uniFex.model.entity.InstanciaWhatsApp;

/** La clave nunca vuelve al navegador; al editar, una clave vacia conserva la guardada. */
public record InstanciaWhatsAppDTO(Long id, String nombre, String urlApi, String instancia, boolean activa) {
    public static InstanciaWhatsAppDTO de(InstanciaWhatsApp entidad) {
        return new InstanciaWhatsAppDTO(entidad.getId(), entidad.getNombre(), entidad.getUrlApi(),
                entidad.getInstancia(), entidad.isActiva());
    }
}
