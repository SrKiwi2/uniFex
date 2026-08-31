package com.usic.uniFex.model.dto;

import com.usic.uniFex.model.entity.PuestoFoto;

/**
 * Una foto de caseta tal como la consume el mapa.
 *
 * `url` ya viene lista para poner en un {@code <img src>}: el backend sirve las subidas
 * bajo /files/**, asi que el cliente no tiene que saber como se guardan las rutas.
 */
public record PuestoFotoDTO(Long id, String url, String descripcion, Integer orden) {

    public static PuestoFotoDTO de(PuestoFoto f) {
        return new PuestoFotoDTO(
                f.getId(),
                "/files/" + f.getRuta(),
                f.getDescripcion(),
                f.getOrden());
    }
}
