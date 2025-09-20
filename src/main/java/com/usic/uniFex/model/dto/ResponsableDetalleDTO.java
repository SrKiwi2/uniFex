package com.usic.uniFex.model.dto;

import java.util.List;

public record ResponsableDetalleDTO(

Long   idPersona,
        String nombreCompleto,
        String ci,
        String celular,
        String foto,
        List<PuestoDTO> puestos
) {
    
}
