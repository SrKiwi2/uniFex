package com.usic.uniFex.model.dao;

import org.springframework.data.jpa.repository.JpaRepository;

import com.usic.uniFex.model.entity.Entidad;

public interface IEntidadDao extends JpaRepository <Entidad, Long>{
    
}
