package com.usic.uniFex.model.dto;

import java.time.LocalDateTime;

/**
 * Quien TIENE una caseta ahora mismo: el que la esta registrando, o el que ya la vendio.
 *
 * <h2>Por que no va dentro de {@code PuestoEstadoDTO}</h2>
 * Por lo mismo que las asignaciones viajan aparte: el nombre de una persona no cabe en un
 * mensaje que se difunde por cada movimiento del plano. Pero sobre todo porque los dos datos
 * <b>no se averiguan igual</b>:
 *
 * <ul>
 *   <li><b>En tramite (T)</b> — sale de {@code puesto.reservado_por_id_usuario}, que ya viaja
 *       en cada difusion como {@code reservadoPor}. Aqui solo se le pone nombre y telefono.</li>
 *   <li><b>Vendida (O)</b> — <b>no esta en la caseta</b>. Al vender,
 *       {@code ocuparSiDisponible} pone {@code reservado_por_id_usuario = NULL}, asi que el
 *       unico rastro fiable de quien la vendio es la inscripcion que la contiene
 *       ({@code inscripcion_puesto} → {@code inscripcion._registro_id_usuario}).</li>
 * </ul>
 *
 * De ahi que el cliente pueda resolver una caseta que acaba de pasar a {@code T} sin preguntar
 * nada —el id ya le llego y el nombre lo tiene en su directorio— pero tenga que volver a pedir
 * esta lista cuando una caseta pasa a {@code O}. Vender es raro; reservar, constante.
 *
 * El telefono viaja igual que en {@code AsignacionPuestoDTO} y por la misma razon: el vendedor
 * esta delante del cliente y necesita poder llamar al compañero, no leer que existe.
 */
public record OcupacionPuestoDTO(
        Long puestoId,
        Long vendedorId,
        /** Nombre completo ya armado: es lo unico que se lee en pantalla. */
        String vendedor,
        String celular,
        /** {@code T} la esta registrando, {@code O} ya la vendio. Mismo alfabeto que la caseta. */
        String estado,
        /** Cuando la tomo (reserva) o cuando la vendio (inscripcion). Da el "hace 4 min". */
        LocalDateTime desde) {
}
