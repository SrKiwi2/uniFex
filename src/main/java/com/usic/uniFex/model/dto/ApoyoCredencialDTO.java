package com.usic.uniFex.model.dto;

import com.usic.uniFex.model.entity.PersonalApoyo;
import com.usic.uniFex.model.service.ApoyoCodigoService;

/** Una ficha de apoyo lista para credencial: datos + su codigo QR (FXA-...). */
public record ApoyoCredencialDTO(
        Long id,
        String codigo,
        String nombreCompleto,
        String ci,
        Long idDependencia,
        String dependenciaNombre,
        String rol,
        String descripcionTarea,
        /** URL servida en /files/**, o null si no tiene foto. */
        String fotoUrl) {

    public static ApoyoCredencialDTO de(PersonalApoyo p, ApoyoCodigoService codigos) {
        return new ApoyoCredencialDTO(
                p.getId(),
                codigos.codigoDe(p.getId()),
                p.getNombreCompleto(),
                p.getCi(),
                p.getDependencia() != null ? p.getDependencia().getId() : null,
                p.getDependencia() != null ? p.getDependencia().getNombre() : null,
                p.getRol(),
                p.getDescripcionTarea(),
                PersonalApoyoDTO.fotoUrl(p.getFoto()));
    }
}
