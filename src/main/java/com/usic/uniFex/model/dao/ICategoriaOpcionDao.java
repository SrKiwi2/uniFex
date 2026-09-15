package com.usic.uniFex.model.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.usic.uniFex.model.entity.CategoriaOpcion;

public interface ICategoriaOpcionDao extends JpaRepository<CategoriaOpcion, Long> {

    /** Las opciones vivas de una categoria, en el orden en que se muestran. */
    @Query("SELECT o FROM CategoriaOpcion o WHERE o.categoria.id = :categoriaId "
         + "AND o.estado <> 'X' ORDER BY o.predeterminada DESC, o.orden, o.id")
    List<CategoriaOpcion> vivasDe(@Param("categoriaId") Long categoriaId);

    /** Todas las opciones vivas, para armar el catalogo de una sola consulta. */
    @Query("SELECT o FROM CategoriaOpcion o WHERE o.estado <> 'X' "
         + "ORDER BY o.categoria.id, o.predeterminada DESC, o.orden, o.id")
    List<CategoriaOpcion> todasVivas();

    @Query("SELECT o FROM CategoriaOpcion o WHERE o.categoria.id = :categoriaId "
         + "AND o.predeterminada = true AND o.estado <> 'X'")
    Optional<CategoriaOpcion> predeterminadaDe(@Param("categoriaId") Long categoriaId);

    /** Cuantas ventas se registraron con esta opcion. Una opcion usada no se borra. */
    @Query(value = "SELECT COUNT(*) FROM inscripcion_puesto WHERE id_categoria_opcion = :id",
           nativeQuery = true)
    long ventasCon(@Param("id") Long id);
}
