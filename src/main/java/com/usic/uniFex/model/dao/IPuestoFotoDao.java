package com.usic.uniFex.model.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.usic.uniFex.model.entity.PuestoFoto;

/** Fotos de las casetas. Las borradas se marcan, no se eliminan (igual que el resto del sistema). */
public interface IPuestoFotoDao extends JpaRepository<PuestoFoto, Long> {

    @Query("SELECT f FROM PuestoFoto f WHERE f.puesto.id = :puestoId "
         + "AND (f.estado IS NULL OR f.estado <> 'X') ORDER BY f.orden ASC, f.id ASC")
    List<PuestoFoto> activasDe(@Param("puestoId") Long puestoId);

    /** Cuantas fotos tiene ya la caseta: sirve para asignarle el orden a la siguiente. */
    @Query("SELECT count(f) FROM PuestoFoto f WHERE f.puesto.id = :puestoId "
         + "AND (f.estado IS NULL OR f.estado <> 'X')")
    long contarActivasDe(@Param("puestoId") Long puestoId);

    /**
     * Ids de las casetas que TIENEN al menos una foto.
     *
     * El mapa lo necesita para marcar cuales se pueden enseñar, y hacerlo de una vez evita
     * una consulta por caseta (con 500 en pantalla, eso seria N+1 puro).
     */
    @Query("SELECT DISTINCT f.puesto.id FROM PuestoFoto f WHERE f.estado IS NULL OR f.estado <> 'X'")
    List<Long> idsDePuestosConFoto();

    /** Baja logica: la fila se conserva por si hay que recuperar la imagen. */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE PuestoFoto f SET f.estado = 'X' WHERE f.id = :id")
    int marcarBorrada(@Param("id") Long id);
}
