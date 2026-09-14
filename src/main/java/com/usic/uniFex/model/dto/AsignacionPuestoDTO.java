package com.usic.uniFex.model.dto;

/**
 * Quien responde por una caseta: un vendedor que la tiene habilitada, con su contacto.
 *
 * **Una por vendedor, no una por caseta.** Desde V32 la misma caseta puede llevarla mas de uno,
 * asi que el listado trae una fila por pareja y el cliente las agrupa. Una fila con
 * {@code vendedorId} nulo significa que esa caseta ya no la lleva nadie.
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
