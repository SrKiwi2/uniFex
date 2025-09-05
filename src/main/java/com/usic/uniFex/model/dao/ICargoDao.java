package com.usic.uniFex.model.dao;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.usic.uniFex.model.entity.Cargo;

public interface ICargoDao extends JpaRepository<Cargo, Long> {
    Optional<Cargo> findByNombre(String nombre);
}
