package com.usic.uniFex.model.dao;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.usic.uniFex.model.dto.ResponsableListadoView;
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

    // EAGER solo para esta consulta (evita N+1)
    @EntityGraph(attributePaths = {"persona", "entidad"})
    @Query("""
        select r
        from Responsable r
        join r.persona p
        join r.entidad e
        order by e.nombre asc, p.paterno asc, p.nombre asc
    """)
    List<Responsable> listarConPersonaYEntidad();

    @Query("""
        select 
           r.id            as id,
           e.id            as entidadId,
           e.nombre        as entidadNombre,
           p.id            as personaId,
           p.nombre        as nombre,
           p.paterno       as paterno,
           p.materno       as materno,
           p.ci            as ci,
           p.celular       as celular,
           p.foto          as foto
        from Responsable r
        join r.persona p
        join r.entidad e
        order by e.nombre asc, p.paterno asc, p.nombre asc
    """)
    List<ResponsableListadoView> listarVista();


    @EntityGraph(attributePaths = {"persona"}) // evita LazyInitialization en la vista
    @Query("""
            select r
            from Responsable r
            where r.entidad.id = :entidadId
            order by r.persona.paterno, r.persona.materno, r.persona.nombre
            """)
    List<Responsable> findByEntidadIdWithPersona(@Param("entidadId") Long entidadId);
}