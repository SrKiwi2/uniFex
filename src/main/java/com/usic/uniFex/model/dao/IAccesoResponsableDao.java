package com.usic.uniFex.model.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.usic.uniFex.model.entity.AccesoResponsable;

public interface IAccesoResponsableDao extends JpaRepository<AccesoResponsable, Long>{
    Optional<AccesoResponsable> findFirstByIdPersonaAndFechaSalidaIsNullOrderByFechaEntradaDesc(Long idPersona);
    List<AccesoResponsable> findTop20ByIdPersonaOrderByFechaEntradaDesc(Long idPersona);
    boolean existsByIdPersonaAndFechaSalidaIsNull(Long idPersona);
}
