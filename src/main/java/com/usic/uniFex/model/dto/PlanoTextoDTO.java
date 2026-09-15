package com.usic.uniFex.model.dto;

import com.usic.uniFex.model.entity.PlanoTexto;

/**
 * Un rotulo del plano tal como lo consumen el mapa y el editor.
 *
 * Contrato unico: lo devuelve el API **y** se difunde por WebSocket, igual que
 * {@code PuestoEstadoDTO}. Si el mapa necesita un dato nuevo del rotulo, se agrega aqui y los
 * dos caminos lo ven; dos formas distintas de describir lo mismo acaban divergiendo.
 *
 * `activo=false` es como viaja una baja: el cliente lo borra de su lista. Sin eso habria que
 * inventar un segundo mensaje para "este ya no esta".
 */
public record PlanoTextoDTO(
        Long id,
        String contenido,
        Double mapaX,
        Double mapaY,
        Double tamano,
        String color,
        String colorBorde,
        Double grosorBorde,
        Double rotacion,
        boolean activo) {

    public static PlanoTextoDTO de(PlanoTexto t) {
        return new PlanoTextoDTO(
                t.getId(),
                t.getContenido(),
                t.getMapaX(),
                t.getMapaY(),
                t.getTamano(),
                t.getColor(),
                t.getColorBorde(),
                t.getGrosorBorde(),
                t.getRotacion(),
                !PlanoTexto.REGISTRO_ANULADO.equals(t.getEstado()));
    }

    /** Aviso de baja: el cliente solo necesita el id para quitarlo del plano. */
    public static PlanoTextoDTO baja(Long id) {
        return new PlanoTextoDTO(id, null, null, null, null, null, null, null, null, false);
    }
}
