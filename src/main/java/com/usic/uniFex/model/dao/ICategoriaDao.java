package com.usic.uniFex.model.dao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.usic.uniFex.model.entity.Categoria;

public interface ICategoriaDao extends JpaRepository <Categoria, Long>{

    /** Baja logica de la categoria (no se borra la fila: sus casetas historicas la referencian). */
    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE categoria SET \"_estado\" = 'X' WHERE id = :id", nativeQuery = true)
    int anular(@Param("id") Long id);
}
