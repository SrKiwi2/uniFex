package com.usic.uniFex.model.dao;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.usic.uniFex.model.entity.InteresadoStand;

public interface IInteresadoStandDao extends JpaRepository<InteresadoStand, Long> {

    /** Todos, los mas recientes primero, con la categoria en la misma consulta (sin N+1). */
    @EntityGraph(attributePaths = "categoria")
    List<InteresadoStand> findAllByOrderByFechaRegistroDesc();
}
