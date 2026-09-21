package com.usic.uniFex.model.dao;

import java.util.List;

/** Acceso a {@code rol_pantalla} y {@code usuario_pantalla}: que pantallas ve cada rol y usuario. */
public interface IPermisoPantallaDao {

    /** Filas {rolId, nombreRol, pantalla}. Los roles sin ninguna salen con pantalla nula. */
    List<Object[]> listarTodo();

    List<String> pantallasDeRol(Long rolId);

    /** Lo que ve ese usuario: las pantallas de su rol MAS las suyas propias (V49). */
    List<String> pantallasDeUsuario(Long usuarioId);

    /** Solo las que le vienen por su rol. */
    List<String> pantallasDelRolDeUsuario(Long usuarioId);

    /** Solo las que se le dieron a el (V49), aparte de su rol. */
    List<String> pantallasPropiasDeUsuario(Long usuarioId);

    /** Filas {id, username, nombre, rol, estado, cantidadPropias} de los usuarios no eliminados. */
    List<Object[]> listarUsuarios();

    /** {id, username, nombre, rol} de un usuario no eliminado, o null. */
    Object[] usuario(Long usuarioId);

    /** Sustituye la seleccion entera de un rol. */
    void reemplazar(Long rolId, List<String> pantallas, Long adminId);

    /** Sustituye las pantallas propias de un usuario. */
    void reemplazarDeUsuario(Long usuarioId, List<String> pantallas, Long adminId);
}
