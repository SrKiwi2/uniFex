package com.usic.uniFex.model.dto;

import java.time.LocalDate;

import com.usic.uniFex.model.entity.Noticia;

/**
 * Una noticia, tal como la ven el panel de admin y el carrusel de la vista publica.
 * {@code urlMedio} ya trae la ruta lista para {@code <img>}/{@code <video src>} (bajo /files/**).
 */
public record NoticiaDTO(
        Long id,
        LocalDate fecha,
        Integer dia,
        String titulo,
        String texto,
        String medioTipo,
        String urlMedio) {

    public static NoticiaDTO de(Noticia n) {
        return new NoticiaDTO(n.getId(), n.getFecha(), n.getDia(), n.getTitulo(), n.getTexto(),
                n.getMedioTipo(), "/files/" + n.getMedioArchivo());
    }
}
