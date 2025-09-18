package com.usic.uniFex.model.dao;

import org.springframework.data.jpa.repository.JpaRepository;

import com.usic.uniFex.model.entity.CategoriaVenta;

public interface ICategoriaVentaDao extends JpaRepository<CategoriaVenta, Long>{
    
}
