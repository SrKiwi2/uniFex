package com.usic.uniFex.model.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.usic.uniFex.model.entity.Rol;

public interface IRolDao extends JpaRepository<Rol, Long> {
    Optional<Rol> findByNombre(String nombre);

    /**
     * Busca ignorando mayusculas, para rechazar el alta de "Administrador" cuando ya existe
     * "ADMINISTRADOR". Los nombres se guardan siempre en la forma canonica de
     * {@code RolesSistema.normalizar}, pero una base traida de antes puede no cumplirlo.
     */
    Optional<Rol> findFirstByNombreIgnoreCase(String nombre);

    /** Roles vivos, en orden alfabetico: es el catalogo que ve el selector de usuarios. */
    @Query("select r from Rol r where r.estado is null or r.estado <> 'ELIMINADO' order by r.nombre")
    List<Rol> listarVivos();

    /** Cuantos roles vivos se llaman asi, sin contar el que se esta editando. */
    @Query("select count(r) from Rol r where upper(r.nombre) = upper(:nombre) "
            + "and (r.estado is null or r.estado <> 'ELIMINADO') and r.id <> :exceptoId")
    long contarPorNombre(@Param("nombre") String nombre, @Param("exceptoId") Long exceptoId);
}
