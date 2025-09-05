package com.usic.uniFex.model.dao;

import org.springframework.data.jpa.repository.JpaRepository;

import com.usic.uniFex.model.entity.InscripcionPuesto;

public interface IInscripcionPuestoDao extends JpaRepository<InscripcionPuesto, Long>{
    
}
