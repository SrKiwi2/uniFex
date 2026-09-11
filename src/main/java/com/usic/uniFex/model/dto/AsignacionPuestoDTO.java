package com.usic.uniFex.model.dto;

/**
 * Quien responde por una caseta: el vendedor que la tiene asignada, con su contacto.
 *
 * Existe para que el mapa pueda hacer dos cosas a la vez sin pedir permisos distintos:
 * pintar de gris lo que uno NO puede vender, y —al tocarlo— dar el telefono del companiero
 * que si puede. Antes el vendedor simplemente no veia esas casetas, asi que a un cliente
 * parado delante de una caseta ajena no habia nada que responderle.
 *
 * El telefono viaja a todos los vendedores a proposito: es el dato que hace util la pantalla.
 * No lleva nada mas del usuario (ni su cuenta, ni su rol, ni su C.I.).
 */
public record AsignacionPuestoDTO(
        Long puestoId,
        Long vendedorId,
        /** Nombre completo, ya armado: es lo unico que se lee en pantalla. */
        String vendedor,
        String celular) {
}
