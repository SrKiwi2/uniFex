package com.usic.uniFex.model.dao;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.usic.uniFex.model.entity.Inscripcion;

public interface IInscripcionDao extends JpaRepository <Inscripcion, Long> {
    // Evita N+1: trae puesto->categoria y entidad->tipoEntidad
  @EntityGraph(attributePaths = {
      "puesto", "puesto.categoria",
      "entidad", "entidad.tipoEntidad"
  })
  List<Inscripcion> findAll();

  
}
