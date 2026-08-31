package com.usic.uniFex.model.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.usic.uniFex.model.entity.SolicitudCancelacion;

/**
 * Solicitudes de cancelacion (V11). El ciclo de vida es PENDIENTE -> APROBADA /
 * RECHAZADA y cada transicion es un UPDATE condicional en el servicio, siguiendo
 * el invariante 1 del proyecto (la BD arbitra, no Java).
 *
 * Nota de nombres: `estadoSolicitud` es el estado del flujo de aprobacion;
 * `estado` (heredado de AuditoriaConfig) es la baja logica `_estado` ('X').
 */
public interface ISolicitudCancelacionDao extends JpaRepository<SolicitudCancelacion, Long> {

    /** La solicitud PENDIENTE de una venta, si la hay (maximo una por regla de negocio). */
    @Query("select s from SolicitudCancelacion s where s.idInscripcion = :idInscripcion "
            + "and s.estadoSolicitud = 'PENDIENTE' and (s.estado is null or s.estado <> 'X')")
    Optional<SolicitudCancelacion> pendienteDeInscripcion(@Param("idInscripcion") Long idInscripcion);

    /** Las PENDIENTES en orden de llegada, con la venta para el detalle del admin. */
    @EntityGraph(attributePaths = { "inscripcion", "inscripcion.entidad", "inscripcion.entidad.tipoEntidad" })
    @Query("select s from SolicitudCancelacion s where s.estadoSolicitud = 'PENDIENTE' "
            + "and (s.estado is null or s.estado <> 'X') order by s.fechaSolicitud asc")
    List<SolicitudCancelacion> pendientes();

    /** Las resueltas (APROBADA o RECHAZADA), las mas recientes primero. */
    @EntityGraph(attributePaths = { "inscripcion", "inscripcion.entidad", "inscripcion.entidad.tipoEntidad" })
    @Query("select s from SolicitudCancelacion s where s.estadoSolicitud <> 'PENDIENTE' "
            + "and (s.estado is null or s.estado <> 'X') order by s.fechaResolucion desc")
    List<SolicitudCancelacion> resueltas();

    /** La solicitud APROBADA vigente de una venta, si la hay (requisito para cancelar). */
    @Query("select s from SolicitudCancelacion s where s.idInscripcion = :idInscripcion "
            + "and s.estadoSolicitud = 'APROBADA' and (s.estado is null or s.estado <> 'X') "
            + "order by s.fechaResolucion desc")
    Optional<SolicitudCancelacion> aprobadaDeInscripcion(@Param("idInscripcion") Long idInscripcion);

    /** Las solicitudes que pidio un vendedor (para "mis solicitudes"). */
    @Query("select s from SolicitudCancelacion s where s.registroIdUsuario = :usuarioId "
            + "and (s.estado is null or s.estado <> 'X') order by s.fechaSolicitud desc")
    List<SolicitudCancelacion> delVendedor(@Param("usuarioId") Long usuarioId);

    /** La ultima solicitud de una venta, sea cual sea su estado (o vacio si nunca se solicito). */
    @Query("select s from SolicitudCancelacion s where s.idInscripcion = :idInscripcion "
            + "and (s.estado is null or s.estado <> 'X') order by s.fechaSolicitud desc")
    java.util.Optional<SolicitudCancelacion> ultimaDeInscripcion(@Param("idInscripcion") Long idInscripcion);

    /**
     * Aprobar: solo se aprueba lo que sigue PENDIENTE. El conteo de filas es la respuesta:
     * 1 = se aprobo, 0 = ya estaba resuelta o no existe (la carrera la gana la BD).
     */
    @Modifying(clearAutomatically = true)
    @Query("update SolicitudCancelacion s set s.estadoSolicitud = 'APROBADA', s.respuesta = :respuesta, "
            + "s.fechaResolucion = current_timestamp, s.modificacionIdUsuario = :usuarioId "
            + "where s.id = :id and s.estadoSolicitud = 'PENDIENTE'")
    int aprobarSiPendiente(@Param("id") Long id, @Param("respuesta") String respuesta,
                           @Param("usuarioId") Long usuarioId);

    /** Rechazar: igual que aprobar, la condicion viaja en la escritura. */
    @Modifying(clearAutomatically = true)
    @Query("update SolicitudCancelacion s set s.estadoSolicitud = 'RECHAZADA', s.respuesta = :respuesta, "
            + "s.fechaResolucion = current_timestamp, s.modificacionIdUsuario = :usuarioId "
            + "where s.id = :id and s.estadoSolicitud = 'PENDIENTE'")
    int rechazarSiPendiente(@Param("id") Long id, @Param("respuesta") String respuesta,
                            @Param("usuarioId") Long usuarioId);
}
