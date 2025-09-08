package com.usic.uniFex.model.dao;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.usic.uniFex.model.entity.Responsable;

public interface IResponsableDao extends JpaRepository <Responsable, Long>{
    List<Responsable> findByEntidadId(Long entidadId);

    @EntityGraph(attributePaths = {"persona"})
    @Query("select r from Responsable r")
    List<Responsable> findAllConPersona();

    @Query("""
        select r
        from Responsable r
        left join fetch r.persona p
        left join fetch r.entidad e
    """)
    List<Responsable> findAllConPersonaYEntidad();
}
