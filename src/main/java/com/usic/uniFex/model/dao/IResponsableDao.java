package com.usic.uniFex.model.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.usic.uniFex.model.entity.Responsable;

public interface IResponsableDao extends JpaRepository <Responsable, Long>{
    List<Responsable> findByEntidadId(Long entidadId);
}
