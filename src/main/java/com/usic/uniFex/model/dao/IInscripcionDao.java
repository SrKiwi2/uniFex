package com.usic.uniFex.model.dao;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.usic.uniFex.model.dto.ResumenCategoriaView;
import com.usic.uniFex.model.dto.ResumenEntidadView;
import com.usic.uniFex.model.dto.ResumenGeneralView;
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
            where i."_estado" <> 'X'
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
            left join inscripcion_puesto ip on ip.id_inscripcion = i.id and ip.id_puesto is not null
            where i."_estado" <> 'X'
            group by u.id, per.nombre, per.paterno, per.materno, e.id, e.nombre
            order by nombreCompleto asc nulls last, e.nombre asc
            """, nativeQuery = true)
    List<ResumenEntidadView> resumenPorEntidad();


    @EntityGraph(attributePaths = {
        "entidad",
        "entidad.tipoEntidad",
        "inscripcionPuestos",
        "inscripcionPuestos.puesto",
        "inscripcionPuestos.puesto.categoria",
        "registroUsuario",
        "registroUsuario.persona",
        "canceladaPorUsuario",
        "canceladaPorUsuario.persona"
    })
    // Inscripciones no anuladas. Antes filtraba `inscripcionEstado = 'ACTIVO'`, pero ese campo
    // vale PENDIENTE/X en los datos reales (nunca ACTIVO), asi que devolvia SIEMPRE 0 filas —el
    // listado, tanto en Thymeleaf como en la SPA, salia vacio. La baja logica esta en `_estado`.
    @Query("select i from Inscripcion i where i.estado is null or i.estado <> 'X'")
    List<Inscripcion> findAllConTodo();

    /**
     * El historico de canceladas: inscripciones con baja logica, con los datos de la
     * cancelacion (motivo, quien, cuando) y la persona de quien la cancelo.
     */
    @EntityGraph(attributePaths = {
        "entidad",
        "entidad.tipoEntidad",
        "inscripcionPuestos",
        "inscripcionPuestos.puesto",
        "inscripcionPuestos.puesto.categoria",
        "registroUsuario",
        "registroUsuario.persona",
        "canceladaPorUsuario",
        "canceladaPorUsuario.persona"
    })
    @Query("select i from Inscripcion i where i.estado = 'X'")
    List<Inscripcion> findAllCanceladas();

    /**
     * Una inscripcion con todo lo que necesita el detalle: entidad con su tipo,
     * puestos con categoria, quien la registro, quien la cancelo (si procede) y
     * la edicion.
     *
     * Los responsables NO se traen aqui a proposito: fetch de dos colecciones a la
     * vez dispara MultipleBagFetchException en Hibernate, asi que se cargan aparte
     * con IResponsableDao.findByEntidadIdWithPersona.
     */
    @EntityGraph(attributePaths = {
        "entidad",
        "entidad.tipoEntidad",
        "inscripcionPuestos",
        "inscripcionPuestos.puesto",
        "inscripcionPuestos.puesto.categoria",
        "registroUsuario",
        "registroUsuario.persona",
        "canceladaPorUsuario",
        "canceladaPorUsuario.persona",
        "edicion"
    })
    @Query("select i from Inscripcion i where i.id = :id")
    java.util.Optional<Inscripcion> findConTodoPorId(@Param("id") Long id);

    /**
     * Totales generales de la feria (KPIs del reporte). No filtra por estado de la caseta —a
     * diferencia de fn_get_inscripciones—, asi que cuenta todo lo inscrito, no solo lo confirmado.
     */
    @Query(value = """
            select
            count(distinct i.id)         as inscripciones,
            count(ip.id)                 as puestos,
            coalesce(sum(ip.costo), 0)   as totalBs
            from inscripcion i
            join inscripcion_puesto ip on ip.id_inscripcion = i.id
            join puesto p              on p.id = ip.id_puesto
            where i."_estado" <> 'X'
            """, nativeQuery = true)
    ResumenGeneralView resumenGeneral();

    /**
     * Ventas del vendedor que siguen **sin comprobante de pago**.
     *
     * El estado de pago no es una columna a proposito: se deduce de los datos que ya existen
     * —pago al contado, o comprobante adjunto—. Añadir un estado nuevo obligaria a enseñarselo
     * a todas las consultas y a la stored function, y en este proyecto ya hubo totales
     * descuadrados por filtros de estado que no coincidian.
     *
     * El aislamiento es por diseño: se filtra por el usuario que la registro, asi que un
     * vendedor solo ve sus pendientes.
     */
    @Query("""
           select i from Inscripcion i
            where i.registroIdUsuario = :usuarioId
              and (i.estado is null or i.estado <> 'X')
              and i.pagoContado = false
              and (i.imgComprobante is null or i.imgComprobante = '')
            order by i.fechaCompra desc
           """)
    List<Inscripcion> pendientesDeComprobante(@Param("usuarioId") Long usuarioId);
}
