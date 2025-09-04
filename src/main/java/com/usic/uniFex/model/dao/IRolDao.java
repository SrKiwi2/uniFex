package com.usic.uniFex.model.dao;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.usic.uniFex.model.entity.Rol;

public interface IRolDao extends JpaRepository<Rol, Long> {
    Optional<Rol> findByNombre(String nombre);
}
