package com.usic.uniFex.model.dao;

import org.springframework.data.jpa.repository.JpaRepository;

import com.usic.uniFex.model.entity.Inscripcion;

public interface IInscripcionDao extends JpaRepository <Inscripcion, Long> {
    
}
