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
        /** Nombre y apellidos ya unidos: es lo unico que se pinta en una lista. */
        String nombre,
        /*
         * Y ademas por separado, porque la ficha de la venta deja CORREGIRLOS. Un formulario
         * no puede rellenarse desde la cadena unida: partirla por espacios adivinaria mal en
         * cuanto alguien tenga dos nombres de pila, y guardar esa adivinanza estropearia el
         * dato que se venia a arreglar.
         */
        String paterno,
        String materno,
        String ci,
        String celular,
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
                p != null ? p.getPaterno() : null,
                p != null ? p.getMaterno() : null,
                p != null ? p.getCi() : null,
                p != null ? p.getCelular() : null,
                r.isEsTitular(),
                hay ? "/files/" + foto : null,
                hay);
    }
}
