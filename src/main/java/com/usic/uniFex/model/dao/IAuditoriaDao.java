package com.usic.uniFex.model.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.usic.uniFex.model.entity.Auditoria;

public interface IAuditoriaDao extends JpaRepository<Auditoria, Long> {

    /** La traza de un registro, de la mas antigua a la mas reciente. */
    List<Auditoria> findByTablaAndIdRegistroOrderByFechaAsc(String tabla, Long idRegistro);
}
