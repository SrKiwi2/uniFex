package com.usic.uniFex.model.dao;

import java.util.List;

/** Acceso a {@code rol_pantalla}: que pantallas ve cada rol. */
public interface IPermisoPantallaDao {

    /** Filas {rolId, nombreRol, pantalla}. Los roles sin ninguna salen con pantalla nula. */
    List<Object[]> listarTodo();

    List<String> pantallasDeRol(Long rolId);

    /** Las pantallas del rol que tiene asignado ese usuario. */
    List<String> pantallasDeUsuario(Long usuarioId);

    /** Sustituye la seleccion entera de un rol. */
    void reemplazar(Long rolId, List<String> pantallas, Long adminId);
}
