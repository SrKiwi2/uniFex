package com.usic.uniFex.model.dao;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.usic.uniFex.model.entity.Administrativo;

public interface IAdministrativoDao extends JpaRepository<Administrativo, Long>{
    Optional<Administrativo> findByCodigoFuncionario(String codigoFuncionario);
}
