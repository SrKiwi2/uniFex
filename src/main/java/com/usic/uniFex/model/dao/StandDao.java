package com.usic.uniFex.model.dao;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.usic.uniFex.model.entity.Stand;

public interface StandDao extends JpaRepository<Stand, Long> {
    Optional<Stand> findBySlug(String slug);
}
