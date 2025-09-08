package com.usic.uniFex.model.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.usic.uniFex.model.entity.Administrativo;
import com.usic.uniFex.model.entity.Inscripcion;

public interface IAdministrativoDao extends JpaRepository<Administrativo, Long>{
    Optional<Administrativo> findByCodigoFuncionario(String codigoFuncionario);

    @EntityGraph(attributePaths = {"persona"})
    @Query("select a from Administrativo a")
    List<Administrativo> findAllConTodo();
}
