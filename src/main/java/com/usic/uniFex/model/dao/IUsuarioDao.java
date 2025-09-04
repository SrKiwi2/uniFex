package com.usic.uniFex.model.dao;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.usic.uniFex.model.entity.Usuario;

public interface IUsuarioDao extends JpaRepository<Usuario, Long> {
      Optional<Usuario> findByUsername(String username);
}
