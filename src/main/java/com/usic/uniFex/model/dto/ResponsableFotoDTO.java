package com.usic.uniFex.model.dto;

import com.usic.uniFex.model.entity.Persona;
import com.usic.uniFex.model.entity.Responsable;

/**
 * Un responsable de la venta, visto desde la pantalla que reune las fotos para su credencial.
 *
 * Lleva `tieneFoto` aparte de `fotoUrl` porque es la pregunta que de verdad se hace el
 * vendedor —"¿a quien me falta?"— y asi la interfaz no tiene que interpretar un nulo.
 */
public record ResponsableFotoDTO(
        Long id,
        String nombre,
        String ci,
        boolean esTitular,
        /** Ruta servida en /files/**, o null si todavia no tiene foto. */
        String fotoUrl,
        boolean tieneFoto) {

    public static ResponsableFotoDTO de(Responsable r) {
        Persona p = r.getPersona();
        String foto = p != null ? p.getFoto() : null;
        boolean hay = foto != null && !foto.isBlank();
        String nombre = p == null ? "" : java.util.stream.Stream
                .of(p.getNombre(), p.getPaterno(), p.getMaterno())
                .filter(s -> s != null && !s.isBlank())
                .reduce((a, b) -> a + " " + b)
                .orElse("");
        return new ResponsableFotoDTO(
                r.getId(),
                nombre,
                p != null ? p.getCi() : null,
                r.isEsTitular(),
                hay ? "/files/" + foto : null,
                hay);
    }
}
