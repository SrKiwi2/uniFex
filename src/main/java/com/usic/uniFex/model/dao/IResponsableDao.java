package com.usic.uniFex.model.dao;

import org.springframework.data.jpa.repository.JpaRepository;

import com.usic.uniFex.model.entity.Responsable;

public interface IResponsableDao extends JpaRepository <Responsable, Long>{
    
}
