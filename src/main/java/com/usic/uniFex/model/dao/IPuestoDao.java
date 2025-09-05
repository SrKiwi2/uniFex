package com.usic.uniFex.model.dao;

import org.springframework.data.jpa.repository.JpaRepository;

import com.usic.uniFex.model.entity.Puesto;

public interface IPuestoDao extends JpaRepository <Puesto, Long>{
    
}
