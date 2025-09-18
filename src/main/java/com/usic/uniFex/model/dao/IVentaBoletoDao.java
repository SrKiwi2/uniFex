package com.usic.uniFex.model.dao;

import org.springframework.data.jpa.repository.JpaRepository;

import com.usic.uniFex.model.entity.VentaBoleto;

public interface IVentaBoletoDao extends JpaRepository<VentaBoleto, Long> {
    
}
