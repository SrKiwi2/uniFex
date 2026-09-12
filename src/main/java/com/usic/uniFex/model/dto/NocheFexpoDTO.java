package com.usic.uniFex.model.dto;

import java.time.LocalDate;

import com.usic.uniFex.model.entity.NocheFexpo;

/**
 * Una noche de "Noches de FEXPO", tal como la ve tanto el panel de admin como la vista publica.
 * {@code urlMedio} ya trae la ruta lista para {@code <img>}/{@code <video src>} (bajo /files/**),
 * o null si esta noche todavia no tiene foto/video.
 */
public record NocheFexpoDTO(
        Long id,
        Integer orden,
        LocalDate fecha,
        String titulo,
        String descripcion,
        String nombreArtista,
        String color,
        String medioTipo,
        String urlMedio) {

    public static NocheFexpoDTO de(NocheFexpo n) {
        String url = n.getMedioArchivo() != null ? "/files/" + n.getMedioArchivo() : null;
        return new NocheFexpoDTO(
                n.getId(), n.getOrden(), n.getFecha(), n.getTitulo(), n.getDescripcion(),
                n.getNombreArtista(), n.getColor(), n.getMedioTipo(), url);
    }
}
