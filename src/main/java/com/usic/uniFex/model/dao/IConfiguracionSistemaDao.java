package com.usic.uniFex.model.dao;

import org.springframework.data.jpa.repository.JpaRepository;

import com.usic.uniFex.model.entity.ConfiguracionSistema;

public interface IConfiguracionSistemaDao extends JpaRepository<ConfiguracionSistema, String> {
}
