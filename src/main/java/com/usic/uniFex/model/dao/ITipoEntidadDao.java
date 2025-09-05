package com.usic.uniFex.model.dao;

import org.springframework.data.jpa.repository.JpaRepository;

import com.usic.uniFex.model.entity.TipoEntidad;

public interface ITipoEntidadDao extends JpaRepository <TipoEntidad, Long> {
    
    
}
