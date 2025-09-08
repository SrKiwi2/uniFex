package com.usic.uniFex.model.dao;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.usic.uniFex.model.entity.Persona;
import com.usic.uniFex.model.entity.Puesto;

public interface IPuestoDao extends JpaRepository <Puesto, Long>{
    @EntityGraph(attributePaths = {"categoria"})
    List<Puesto> findAll();

    @Query("SELECT p FROM Puesto p WHERE p.estadoPuesto = 'L' ORDER BY p.codigo ASC")
    List<Puesto> listarPuestos();

    List<Puesto> findByEstadoPuestoAndCategoriaIdOrderByCodigoAsc(String estadoPuesto, Long categoriaId);
}
