package com.usic.uniFex.model.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.usic.uniFex.model.entity.NocheFexpo;

public interface INocheFexpoDao extends JpaRepository<NocheFexpo, Long> {

    /** Noches vivas de una edicion, en orden de calendario (fecha, luego orden manual). */
    List<NocheFexpo> findByEdicionIdAndEstadoNotOrderByFechaAscOrdenAsc(Long idEdicion, String estado);
}
