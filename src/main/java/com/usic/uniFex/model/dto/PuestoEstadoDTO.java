package com.usic.uniFex.model.dto;

import java.time.LocalDateTime;

import com.usic.uniFex.model.entity.Categoria;
import com.usic.uniFex.model.entity.Puesto;

/**
 * Estado de una caseta para la API REST y los mensajes de WebSocket.
 * Contrato unico que consume el mapa en vivo (web y app), incluida su
 * apariencia en el plano (color, figura y tamaño de su categoria).
 */
public record PuestoEstadoDTO(
        Long id,
        String codigo,
        String tamano,           // medida de negocio ("3x3"), NO sirve para dibujar
        Long categoriaId,
        String categoria,
        String color,
        String forma,
        String estado,            // L, T, O, X
        LocalDateTime reservaExpira,
        /*
         * Quien tiene la reserva temporal (estado T), o null. Va el ID y no un booleano
         * "es mia" porque este DTO se difunde TAL CUAL por WebSocket a todos los clientes:
         * un campo calculado para un usuario seria mentira para los demas. Cada cliente lo
         * compara con su propio id (el del JWT).
         *
         * Sin esto, el mapa no puede distinguir una caseta que reservo el propio vendedor
         * de una que reservo otro, y ofrece "liberar" en las dos — pidiendo al servidor algo
         * que siempre va a rechazar.
         */
        Long reservadoPor,
        Double mapaX,
        Double mapaY,
        Double tamanoMapa,        // tamaño base de la categoria, fraccion del ancho del plano
        Double mapaEscala,        // multiplicador propio de esta caseta
        /*
         * Precio de venta en Bs (el de la categoria). Viaja al mapa porque el vendedor lo
         * necesita en la mano: toca una caseta delante del cliente y le dice cuanto cuesta,
         * sin salir a otra pantalla. Es el precio VIGENTE, no el de una venta ya hecha —
         * ese se congela en inscripcion_puesto.costo al vender.
         */
        java.math.BigDecimal precio,
        /** Donde esta, en palabras ("frente a la puerta 3"). Lo lee el vendedor en la ficha. */
        String referencia,
        boolean activo) {         // false = anulada; el cliente debe quitarla del mapa

    /** Marca de anulacion compartida por las tablas del sistema (ver AuditoriaConfig._estado). */
    private static final String ANULADO = "X";

    /** Tamaño base por defecto si la categoria aun no lo tiene (mismo valor que el DEFAULT de V5). */
    private static final double TAMANO_POR_DEFECTO = 0.012;

    public static PuestoEstadoDTO de(Puesto p) {
        Categoria c = p.getCategoria();
        return new PuestoEstadoDTO(
                p.getId(),
                p.getCodigo(),
                p.getTamano(),
                c != null ? c.getId() : null,
                c != null ? c.getNombre() : null,
                c != null ? c.getColor() : null,
                c != null ? c.getForma() : null,
                p.getEstadoPuesto(),
                p.getReservaExpira(),
                p.getReservadoPorIdUsuario(),
                p.getMapaX(),
                p.getMapaY(),
                (c != null && c.getTamanoMapa() != null) ? c.getTamanoMapa() : TAMANO_POR_DEFECTO,
                p.getMapaEscala() != null ? p.getMapaEscala() : 1.0,
                (c != null && c.getPrecioBase() != null) ? c.getPrecioBase() : java.math.BigDecimal.ZERO,
                p.getReferencia(),
                !ANULADO.equals(p.getEstado()));
    }
}
