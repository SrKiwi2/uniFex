package com.usic.uniFex.model.dao;

import org.springframework.data.jpa.repository.JpaRepository;

import com.usic.uniFex.model.entity.Categoria;

public interface ICategoriaDao extends JpaRepository <Categoria, Long>{
    
}
