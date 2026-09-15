package com.usic.uniFex.model.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.usic.uniFex.model.entity.Area;

/** Areas academicas (ACEF, ACBN, ACYT). Ver V35. */
public interface IAreaDao extends JpaRepository<Area, Long> {

    /** Las areas vivas, en el orden en que salen en los desplegables. */
    @Query("select a from Area a where a.estado is null or a.estado <> 'X' order by a.orden, a.sigla")
    List<Area> listarVivas();
}
