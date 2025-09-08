package com.usic.uniFex.model.dao;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.usic.uniFex.model.dto.ResumenCategoriaView;
import com.usic.uniFex.model.dto.ResumenEntidadView;
import com.usic.uniFex.model.entity.Inscripcion;

public interface IInscripcionDao extends JpaRepository <Inscripcion, Long> {
    // Evita N+1: trae puesto->categoria y entidad->tipoEntidad
    @EntityGraph(attributePaths = {
        "entidad", "entidad.tipoEntidad",
        "inscripcionPuestos",
        "inscripcionPuestos.puesto",
        "inscripcionPuestos.puesto.categoria"
    })
    List<Inscripcion> findAll();

    @Query(value = """
            select
            u.id                         as usuarioId,
            concat_ws(' ', per.nombre, per.paterno, per.materno) as nombreCompleto,
            c.id                         as categoriaId,
            c.nombre                     as categoria,
            count(distinct i.id)         as cantidadInscripciones,
            count(ip.id)                 as cantidadPuestos,
            coalesce(sum(ip.costo), 0)   as totalBs
            from inscripcion i
            join inscripcion_puesto ip on ip.id_inscripcion = i.id
            join puesto p             on p.id = ip.id_puesto
            join categoria c          on c.id = p.id_categoria
            left join usuario u       on u.id = i._registro_id_usuario
            left join persona per     on per.id = u.persona_id
            group by u.id, per.nombre, per.paterno, per.materno, c.id, c.nombre
            order by nombreCompleto asc nulls last, c.nombre asc
            """, nativeQuery = true)
    List<ResumenCategoriaView> resumenPorCategoria();

    @Query(value = """
            select
            u.id                         as usuarioId,
            concat_ws(' ', per.nombre, per.paterno, per.materno) as nombreCompleto,
            e.id                         as entidadId,
            e.nombre                     as entidad,
            count(distinct i.id)         as cantidadInscripciones,
            count(ip.id)                 as cantidadPuestos,
            coalesce(sum(ip.costo), 0)   as totalBs
            from inscripcion i
            join entidad e           on e.id = i.id_entidad
            left join usuario u      on u.id = i._registro_id_usuario
            left join persona per    on per.id = u.persona_id
            left join inscripcion_puesto ip on ip.id_inscripcion = i.id
            group by u.id, per.nombre, per.paterno, per.materno, e.id, e.nombre
            order by nombreCompleto asc nulls last, e.nombre asc
            """, nativeQuery = true)
    List<ResumenEntidadView> resumenPorEntidad();


        @EntityGraph(attributePaths = {
        "entidad",
        "entidad.tipoEntidad",
        "inscripcionPuestos",
        "inscripcionPuestos.puesto",
        "inscripcionPuestos.puesto.categoria"
    })
    @Query("select i from Inscripcion i")
    List<Inscripcion> findAllConTodo();
}
