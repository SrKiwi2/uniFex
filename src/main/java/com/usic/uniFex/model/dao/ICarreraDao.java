package com.usic.uniFex.model.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.usic.uniFex.model.entity.Carrera;

/** Carreras de la UAP, cada una dentro de un area. Ver V35. */
public interface ICarreraDao extends JpaRepository<Carrera, Long> {

    /**
     * Las carreras vivas con su area ya cargada.
     *
     * El {@code join fetch} no es optimizacion prematura: sin el, pintar el desplegable de 15
     * carreras con la sigla de su area son 15 SELECT extra (el area es LAZY), y encima solo
     * funciona mientras open-in-view siga activo.
     */
    @Query("select c from Carrera c join fetch c.area a "
            + "where (c.estado is null or c.estado <> 'X') and (a.estado is null or a.estado <> 'X') "
            + "order by a.orden, a.sigla, c.nombre")
    List<Carrera> listarVivasConArea();
}
