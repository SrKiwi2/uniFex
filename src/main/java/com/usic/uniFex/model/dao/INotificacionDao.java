package com.usic.uniFex.model.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.usic.uniFex.model.entity.Notificacion;
import com.usic.uniFex.model.entity.Usuario;

public interface INotificacionDao extends JpaRepository<Notificacion, Long> {

    /** Bandeja del usuario: no leídas primero, luego por fecha descendente. */
    @Query("SELECT n FROM Notificacion n WHERE n.usuarioDestino = :usuario ORDER BY n.leida ASC, n.fechaRegistro DESC")
    List<Notificacion> findBandejaByUsuario(@Param("usuario") Usuario usuario);

    /** Solo no leídas (para contador/badge). */
    @Query("SELECT n FROM Notificacion n WHERE n.usuarioDestino = :usuario AND n.leida = false ORDER BY n.fechaRegistro DESC")
    List<Notificacion> findNoLeidasByUsuario(@Param("usuario") Usuario usuario);

    /** Cuenta de no leídas (para badge numérico). */
    @Query("SELECT COUNT(n) FROM Notificacion n WHERE n.usuarioDestino = :usuario AND n.leida = false")
    long countNoLeidasByUsuario(@Param("usuario") Usuario usuario);

    /** Hilo de una observación (padre + respuestas) ordenado cronológico. */
    @Query("SELECT n FROM Notificacion n WHERE n = :padre OR n.notificacionPadre = :padre ORDER BY n.fechaRegistro ASC")
    List<Notificacion> findHiloByPadre(@Param("padre") Notificacion padre);

    /** Notificaciones ligadas a una inscripción (para mostrar en detalle de venta). */
    @Query("SELECT n FROM Notificacion n WHERE n.inscripcion = :inscripcion ORDER BY n.fechaRegistro DESC")
    List<Notificacion> findByInscripcion(@Param("inscripcion") com.usic.uniFex.model.entity.Inscripcion inscripcion);

    /** Notificaciones ligadas a un puesto. */
    @Query("SELECT n FROM Notificacion n WHERE n.puesto = :puesto ORDER BY n.fechaRegistro DESC")
    List<Notificacion> findByPuesto(@Param("puesto") com.usic.uniFex.model.entity.Puesto puesto);
}