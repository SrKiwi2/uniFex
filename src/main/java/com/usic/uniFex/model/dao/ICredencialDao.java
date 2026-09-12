package com.usic.uniFex.model.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.usic.uniFex.model.dto.CredencialFilaView;
import com.usic.uniFex.model.entity.Responsable;

/**
 * Lo que hace falta para una credencial, en UNA consulta por responsable.
 *
 * Va aparte de {@link IResponsableDao} porque la pregunta es distinta: alli se listan
 * responsables, aqui se arma una credencial, que cruza responsable + persona + entidad +
 * inscripcion + casetas + categoria y ademas calcula si cumple los requisitos.
 *
 * Es nativa y no JPQL porque necesita {@code string_agg}: las casetas de un expositor son
 * varias ("14, 15, 16") y traerlas como filas obligaria a agrupar en Java 800 veces.
 */
public interface ICredencialDao extends JpaRepository<Responsable, Long> {

    String SELECT = """
            SELECT r.id                                   AS responsableId,
                   p.nombre                               AS nombre,
                   p.paterno                              AS paterno,
                   p.materno                              AS materno,
                   p.ci                                   AS ci,
                   p.foto                                 AS foto,
                   r.es_titular                           AS esTitular,
                   e.nombre                               AS entidad,
                   e.descripcion                          AS rubro,
                   i.id                                   AS inscripcionId,
                   i.pago_contado                         AS pagoContado,
                   i.img_comprobante                      AS comprobante,
                   string_agg(DISTINCT c.nombre, ', ')    AS categorias,
                   string_agg(DISTINCT pu.codigo, ', ')   AS casetas
              FROM responsable r
              INNER JOIN persona p    ON p.id = r.id_persona
              INNER JOIN entidad e    ON e.id = r.id_entidad
              INNER JOIN inscripcion i ON i.id_entidad = e.id
                                      AND (i."_estado" IS NULL OR i."_estado" <> 'X')
              LEFT  JOIN inscripcion_puesto ip ON ip.id_inscripcion = i.id
                                      AND (ip."_estado" IS NULL OR ip."_estado" <> 'X')
              LEFT  JOIN puesto pu    ON pu.id = ip.id_puesto
              LEFT  JOIN categoria c  ON c.id = pu.id_categoria
             WHERE (r."_estado" IS NULL OR r."_estado" <> 'X')
               AND (e."_estado" IS NULL OR e."_estado" <> 'X')
            """;

    String GROUP = """
             GROUP BY r.id, p.nombre, p.paterno, p.materno, p.ci, p.foto, r.es_titular,
                      e.nombre, e.descripcion, i.id, i.pago_contado, i.img_comprobante
            """;

    /**
     * Todos los responsables de la edicion activa, con sus datos y el estado de los requisitos.
     *
     * Se filtra por edicion porque sin eso saldrian los expositores de FEXPO 2025 mezclados
     * con los de 2026 y se imprimirian credenciales de una feria que ya paso.
     */
    @Query(value = SELECT
            + " AND i.id_edicion = (SELECT ed.id FROM edicion ed WHERE ed.activa LIMIT 1) "
            + GROUP + " ORDER BY e.nombre, r.es_titular DESC, p.nombre",
            nativeQuery = true)
    List<CredencialFilaView> listarDeEdicionActiva();

    /** Una sola credencial, para la vista publica del QR y para imprimir de a una. */
    @Query(value = SELECT + " AND r.id = :responsableId " + GROUP, nativeQuery = true)
    Optional<CredencialFilaView> buscarPorResponsable(@Param("responsableId") Long responsableId);
}
