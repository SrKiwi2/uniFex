package com.usic.uniFex.model.dao;

import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.springframework.stereotype.Repository;

/**
 * Implementacion de {@link ISeguimientoFacultadDao}.
 *
 * NO es un JpaRepository: las consultas cruzan cinco tablas para devolver filas planas que luego
 * se agrupan, y no corresponden a ninguna entidad. Mismo patron que `VendedorAsignacionDaoImpl`.
 */
@Repository
public class SeguimientoFacultadDao implements ISeguimientoFacultadDao {

    @PersistenceContext
    private EntityManager em;

    @Override
    @SuppressWarnings("unchecked")
    public List<Object[]> ventasDeArea(Long areaId, Long edicionId) {
        return em.createNativeQuery("""
                SELECT u.id                                                  AS usuario_id,
                       u.username                                            AS username,
                       concat_ws(' ', per.nombre, per.paterno, per.materno)  AS nombre,
                       car.nombre                                            AS carrera,
                       c.id                                                  AS categoria_id,
                       c.nombre                                              AS categoria,
                       p.codigo                                              AS codigo,
                       ent.nombre                                            AS entidad
                  FROM inscripcion i
                  JOIN inscripcion_puesto ip ON ip.id_inscripcion = i.id
                  -- INNER y no LEFT: hay filas huerfanas con id_puesto NULL (ventas sin caseta)
                  -- que descuadran cualquier conteo. El join las deja fuera, igual que hacen las
                  -- consultas de Reportes desde que se detectaron.
                  JOIN puesto p              ON p.id = ip.id_puesto
                  JOIN categoria c           ON c.id = p.id_categoria
                  JOIN usuario u             ON u.id = i."_registro_id_usuario"
                  JOIN persona per           ON per.id = u.persona_id
                  JOIN carrera car           ON car.id = per.id_carrera
                  LEFT JOIN entidad ent      ON ent.id = i.id_entidad
                 WHERE car.id_area = :areaId
                   AND i."_estado" <> 'X'
                   AND (ip."_estado" IS NULL OR ip."_estado" <> 'X')
                   -- Por edicion, o el informe mezclaria la feria de este año con la del anterior.
                   AND (:edicionId IS NULL OR i.id_edicion = :edicionId)
                 ORDER BY nombre, c.nombre, length(p.codigo), p.codigo
                """)
                .setParameter("areaId", areaId)
                .setParameter("edicionId", edicionId)
                .getResultList();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Object[]> vendedoresDeArea(Long areaId) {
        // Tambien los que NO han vendido nada: un vendedor sin ventas es justo lo que el
        // seguimiento tiene que poder ver. Con solo la consulta de arriba, quien no vendio
        // desaparece del informe y parece que no existe.
        return em.createNativeQuery("""
                SELECT u.id, u.username,
                       concat_ws(' ', per.nombre, per.paterno, per.materno) AS nombre,
                       car.nombre AS carrera
                  FROM usuario u
                  JOIN persona per ON per.id = u.persona_id
                  JOIN carrera car ON car.id = per.id_carrera
                 WHERE car.id_area = :areaId
                   AND (u."_estado" IS NULL OR u."_estado" <> 'ELIMINADO')
                 ORDER BY nombre
                """)
                .setParameter("areaId", areaId)
                .getResultList();
    }
}
