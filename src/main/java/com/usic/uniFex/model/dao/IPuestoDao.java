package com.usic.uniFex.model.dao;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.usic.uniFex.model.entity.Puesto;

public interface IPuestoDao extends JpaRepository <Puesto, Long>{
    @EntityGraph(attributePaths = {"categoria"})
    List<Puesto> findAll();
}
