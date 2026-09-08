package com.usic.uniFex.model.dto;

import com.usic.uniFex.model.entity.Edicion;

/**
 * El plano de la feria tal como lo necesita el visor.
 *
 * `url` ya viene con la version pegada (?v=N): es lo que hace que al reemplazar el plano el
 * APK descargue el nuevo en vez de seguir mostrando el de su cache, que es un problema sin
 * solucion desde el telefono del vendedor.
 *
 * `ancho`/`alto` no son decorativos: el visor calcula con ellos la proporcion del encuadre.
 * Cuando la edicion todavia no tiene plano propio, se devuelve el empaquetado de respaldo
 * para que el mapa siga funcionando igual que antes de V13.
 */
public record PlanoDTO(
        String url,
        Integer ancho,
        Integer alto,
        int version,
        /** false = es el plano de respaldo que viaja en la app, nadie ha subido uno todavia. */
        boolean propio) {

    /** Plano empaquetado en la SPA, el que se usaba antes de que fuera configurable. */
    private static final String RESPALDO_URL = "/mapa.png";
    private static final int RESPALDO_ANCHO = 1836;
    private static final int RESPALDO_ALTO = 2376;

    public static PlanoDTO respaldo() {
        return new PlanoDTO(RESPALDO_URL, RESPALDO_ANCHO, RESPALDO_ALTO, 0, false);
    }

    public static PlanoDTO de(Edicion e) {
        if (e == null || e.getPlanoArchivo() == null
                || e.getPlanoAncho() == null || e.getPlanoAlto() == null) {
            return respaldo();
        }
        int version = e.getPlanoVersion() == null ? 0 : e.getPlanoVersion();
        return new PlanoDTO(
                "/files/" + e.getPlanoArchivo() + "?v=" + version,
                e.getPlanoAncho(), e.getPlanoAlto(), version, true);
    }
}
