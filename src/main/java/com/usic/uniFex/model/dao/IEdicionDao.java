package com.usic.uniFex.model.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.usic.uniFex.model.entity.Edicion;

/**
 * Acceso a las ediciones de la feria. Solo lectura por ahora: crear/activar ediciones
 * queda fuera de la SPA (se maneja como datos de administracion en la base).
 */
public interface IEdicionDao extends JpaRepository<Edicion, Long> {

    List<Edicion> findAllByOrderByAnioDesc();

    /**
     * La edicion en curso. Toda venta nueva se etiqueta con ella, para que los listados y
     * reportes por edicion la vean. Se usa {@code findFirst} y no {@code findBy} porque
     * nada en el esquema impide que haya dos marcadas como activas: si eso pasara, es
     * preferible quedarse con una a que reviente el registro de una venta.
     */
    Optional<Edicion> findFirstByActivaTrueOrderByAnioDesc();
}
