package com.usic.uniFex.model.dao;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.usic.uniFex.model.entity.Oficina;

public interface IOficinaDao extends JpaRepository<Oficina, Long> {
    Optional<Oficina> findByNombre(String nombre);
}
