package com.usic.uniFex.model.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.usic.uniFex.model.entity.Dependencia;

public interface IDependenciaDao extends JpaRepository<Dependencia, Long> {
    @Query("SELECT d FROM Dependencia d WHERE d.estado = 'A'")
    List<Dependencia> listarDependencias();

    Optional<Dependencia> findByNombre(String nombre);
}