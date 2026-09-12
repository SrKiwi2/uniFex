package com.usic.uniFex.model.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.usic.uniFex.model.entity.PersonalApoyo;

public interface IPersonalApoyoDao extends JpaRepository<PersonalApoyo, Long> {
    @Query("SELECT p FROM PersonalApoyo p WHERE p.estado = 'A'")
    List<PersonalApoyo> listarPersonalApoyo();

    @Query("SELECT p FROM PersonalApoyo p WHERE p.dependencia.id = ?1 AND p.estado = 'A'")
    List<PersonalApoyo> buscarPorDependencia(Long idDependencia);

    Optional<PersonalApoyo> findByCi(String ci);

    @Query("SELECT p FROM PersonalApoyo p WHERE p.nombre = ?1 AND p.paterno = ?2 AND p.materno = ?3 AND p.estado = 'A'")
    List<PersonalApoyo> buscarPorNombreCompleto(String nombre, String paterno, String materno);
}