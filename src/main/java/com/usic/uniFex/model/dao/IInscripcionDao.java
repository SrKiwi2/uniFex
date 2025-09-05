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
        "puesto", "puesto.categoria",
        "entidad", "entidad.tipoEntidad"
    })

    List<Inscripcion> findAll();

    @Query(value = """
        select
        u.id         as usuarioId,
        u.username   as username,
        c.id         as categoriaId,
        c.nombre     as categoria,
        count(i.id)  as cantidadInscripciones,
        count(i.id)  as cantidadPuestos,
        coalesce(sum( (i.precio)::numeric ), 0) as totalBs
        from inscripcion i
        join puesto p     on p.id = i.id_puesto
        join categoria c  on c.id = p.id_categoria
        left join usuario u on u.id = i._registro_id_usuario
        group by u.id, u.username, c.id, c.nombre
        order by u.username asc nulls last, c.nombre asc
        """, nativeQuery = true)
    List<ResumenCategoriaView> resumenPorCategoria();

    @Query(value = """
        select
        u.id         as usuarioId,
        u.username   as username,
        e.id         as entidadId,
        e.nombre     as entidad,
        count(i.id)  as cantidadInscripciones,
        count(i.id)  as cantidadPuestos,
        coalesce(sum( (i.precio)::numeric ), 0) as totalBs
        from inscripcion i
        join entidad e   on e.id = i.id_entidad
        left join usuario u on u.id = i._registro_id_usuario
        group by u.id, u.username, e.id, e.nombre
        order by u.username asc nulls last, e.nombre asc
        """, nativeQuery = true)
    List<ResumenEntidadView> resumenPorEntidad();


}
