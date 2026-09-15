package com.usic.uniFex.model.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.usic.uniFex.model.entity.Persona;

public interface IPersonasDao extends JpaRepository <Persona, Long>{
    @Query("SELECT p FROM Persona p WHERE p.ci = ?1 AND p.estado = 'ACTIVO'")
    Persona buscarPersonaPorCI(String ci);

    /**
     * Las personas del sistema, con su carrera y su area ya cargadas.
     *
     * Los {@code left join fetch} evitan dos SELECT por fila al armar el PersonaDTO (carrera y
     * area son LAZY). Son LEFT y no INNER porque la carrera es opcional: con un INNER el
     * listado se quedaria solo con quien tiene carrera, que hoy no es nadie.
     */
    @Query("SELECT p FROM Persona p LEFT JOIN FETCH p.carrera c LEFT JOIN FETCH c.area "
            + "WHERE p.estado = 'ACTIVO'")
    List<Persona> listarPersonas();

    @Query("SELECT p FROM Persona p WHERE p.nombre = ?1 AND p.paterno = ?2 AND p.materno = ?3 AND p.estado = 'ACTIVO'")
    List<Persona> buscarPersonaPorNombrePaternoMaterno(String nombre, String paterno, String materno);

    @Query("SELECT p FROM Persona p WHERE p.nombre = ?1 AND p.paterno = ?2 AND p.materno = ?3 AND p.estado = 'ACTIVO'")
    Persona buscarPersonaPorNombreCompletoUno(String nombre, String paterno, String materno);

    @Query("SELECT p FROM Persona p WHERE p.nombre = ?1 AND p.paterno = ?2 AND p.estado = 'ACTIVO'")
    Persona buscarPersonaPorNombrePaterno(String nombre, String paterno);

    @Query("SELECT p FROM Persona p WHERE p.nombre = ?1 AND p.estado = 'ACTIVO'")
    Persona buscarPersonaNombre(String nombre);

    Optional<Persona> findFirstByCi(String ci);
}