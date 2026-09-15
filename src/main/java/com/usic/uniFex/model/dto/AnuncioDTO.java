package com.usic.uniFex.model.dto;

import java.time.LocalDateTime;

import com.usic.uniFex.model.entity.Anuncio;

/**
 * Un anuncio tal como lo ven las pantallas. Contrato unico: lo devuelve el API y se difunde por
 * WebSocket, igual que el estado de una caseta.
 *
 * `activo=false` es como viaja la retirada, asi que el mismo mensaje sirve para publicar y para
 * quitar; no hace falta un segundo tipo de aviso.
 */
public record AnuncioDTO(
        Long id,
        String titulo,
        String mensaje,
        String nivel,
        LocalDateTime vigenteHasta,
        LocalDateTime publicado,
        String publicadoPor,
        boolean activo) {

    public static AnuncioDTO de(Anuncio a, String autor) {
        return new AnuncioDTO(
                a.getId(), a.getTitulo(), a.getMensaje(), a.getNivel(), a.getVigenteHasta(),
                a.getRegistro() == null ? null
                        : LocalDateTime.ofInstant(a.getRegistro().toInstant(),
                                                  java.time.ZoneId.systemDefault()),
                autor,
                Anuncio.PUBLICADO.equals(a.getEstado()));
    }

    /** Retirada: al cliente le basta el id para quitarlo de la pantalla. */
    public static AnuncioDTO retirado(Long id) {
        return new AnuncioDTO(id, null, null, null, null, null, null, false);
    }
}
