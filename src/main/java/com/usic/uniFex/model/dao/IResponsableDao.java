package com.usic.uniFex.model.dao;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.usic.uniFex.model.dto.ResponsableListadoExplodeView;
import com.usic.uniFex.model.dto.ResponsableListadoView;
import com.usic.uniFex.model.entity.Responsable;
import com.usic.uniFex.model.service.ResponsableDetalleRow;

public interface IResponsableDao extends JpaRepository<Responsable, Long> {
    List<Responsable> findByEntidadId(Long entidadId);

    @EntityGraph(attributePaths = { "persona" })
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
    @EntityGraph(attributePaths = { "persona", "entidad" })
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

    @EntityGraph(attributePaths = { "persona" }) // evita LazyInitialization en la vista
    @Query("""
            select r
            from Responsable r
            where r.entidad.id = :entidadId
            order by r.persona.paterno, r.persona.materno, r.persona.nombre
            """)
    List<Responsable> findByEntidadIdWithPersona(@Param("entidadId") Long entidadId);

    @Query("""
                select
                r.id              as id,
                e.id              as entidadId,
                e.nombre          as entidadNombre,
                te.nombre         as tipoEntidadNombre,
                e.Objeto          as entidadObjeto,

                p.id              as personaId,
                p.nombre          as nombre,
                p.paterno         as paterno,
                p.materno         as materno,
                p.ci              as ci,
                p.celular         as celular,
                p.foto            as foto,

                c.id              as categoriaId,
                c.nombre          as categoriaNombre,
                pu.codigo         as puestoCodigo

                from Responsable r
                join r.persona p
                join r.entidad e
                left join e.tipoEntidad te
                left join Inscripcion i on i.entidad = e
                left join i.inscripcionPuestos ip
                left join ip.puesto pu
                left join pu.categoria c
                order by e.nombre asc, c.nombre asc, p.paterno asc, p.nombre asc
            """)
    List<ResponsableListadoExplodeView> listarVistaExplode();

    // PARA BUSCAR POR CI A LOS RESPONSABLES
    @Query(value = """
    SELECT
      p.id                                                            AS idPersona,
      CONCAT_WS(' ', p.nombre, p.paterno, p.materno)                  AS nombreCompleto,
      p.ci                                                            AS ci,
      p.celular                                                       AS celular,
      p.foto                                                          AS foto,
      e.id                                                            AS idEntidad,
      e.nombre                                                        AS entidad,
      pu.codigo                                                       AS codigoPuesto,
      pu.tamano                                                       AS tamano,
      c.id                                                            AS idCategoria,
      c.nombre                                                        AS categoria
    FROM persona p
    JOIN responsable r
      ON r.id_persona = p.id
     AND r._estado <> 'X'
    JOIN entidad e
      ON e.id = r.id_entidad
     AND e._estado <> 'X'
    JOIN inscripcion i
      ON i.id_entidad = e.id
     AND i._estado <> 'X'
    JOIN inscripcion_puesto ip
      ON ip.id_inscripcion = i.id
     AND ip._estado <> 'X'
    JOIN puesto pu
      ON pu.id = ip.id_puesto
     AND pu._estado <> 'X'
     AND pu.estado_puesto = 'O'
    JOIN categoria c
      ON c.id = pu.id_categoria
     AND c._estado <> 'X'
    WHERE p._estado <> 'X'
      AND TRIM(UPPER(p.ci)) = TRIM(UPPER(:ci))
    ORDER BY
      e.nombre ASC,
      (NULLIF(REGEXP_REPLACE(pu.codigo, '\\D', '', 'g'), '')::INT) NULLS LAST,
      pu.codigo ASC,
      c.nombre ASC
    """, nativeQuery = true)
List<ResponsableDetalleRow> findDetallePorCi(@Param("ci") String ci);


}