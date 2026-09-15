package com.usic.uniFex.model.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.usic.uniFex.model.entity.PlanoTexto;

public interface IPlanoTextoDao extends JpaRepository<PlanoTexto, Long> {

    /**
     * Los rotulos vivos de una edicion, mas los que no tienen edicion asignada.
     *
     * Los sin edicion entran a proposito: si alguien crea un rotulo cuando no hay edicion
     * activa, quedaria invisible para siempre y nadie sabria por que. Mejor que se vea.
     */
    @Query("SELECT t FROM PlanoTexto t WHERE t.estado <> 'X' "
         + "AND (:edicionId IS NULL OR t.edicion IS NULL OR t.edicion.id = :edicionId) "
         + "ORDER BY t.id")
    List<PlanoTexto> vivosDe(@Param("edicionId") Long edicionId);
}
