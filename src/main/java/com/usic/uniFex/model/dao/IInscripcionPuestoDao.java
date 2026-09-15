package com.usic.uniFex.model.dao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.usic.uniFex.model.entity.InscripcionPuesto;

public interface IInscripcionPuestoDao extends JpaRepository<InscripcionPuesto, Long>{
    

    /** Cuantas casetas VIVAS tiene esta venta: de ahi sale el derecho a responsables (V34). */
    @Query("select count(ip) from InscripcionPuesto ip where ip.inscripcion.id = :inscripcionId "
         + "and (ip.estado is null or ip.estado <> 'X')")
    int contarActivosDeInscripcion(@Param("inscripcionId") Long inscripcionId);
}
