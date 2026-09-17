package com.usic.uniFex.model.dao;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.usic.uniFex.model.entity.InstanciaWhatsApp;

public interface IInstanciaWhatsAppDao extends JpaRepository<InstanciaWhatsApp, Long> {
    List<InstanciaWhatsApp> findAllByOrderByActivaDescIdAsc();
    Optional<InstanciaWhatsApp> findByActivaTrue();
}
