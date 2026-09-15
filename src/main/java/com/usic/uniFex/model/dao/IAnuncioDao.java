package com.usic.uniFex.model.dao;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.usic.uniFex.model.entity.Anuncio;

public interface IAnuncioDao extends JpaRepository<Anuncio, Long> {

    /**
     * Los anuncios que hay que enseñar ahora mismo: publicados y sin vencer.
     *
     * El vencimiento se filtra EN LA CONSULTA y no en el cliente: si dependiera del reloj del
     * telefono, uno mal puesto enseñaria avisos caducados o esconderia los vigentes.
     */
    @Query("SELECT a FROM Anuncio a WHERE a.estado = 'A' "
         + "AND (a.vigenteHasta IS NULL OR a.vigenteHasta > :ahora) "
         + "ORDER BY a.registro DESC")
    List<Anuncio> vigentes(@Param("ahora") LocalDateTime ahora);

    /** Todos, para la pantalla de administracion: tambien los retirados y los vencidos. */
    @Query("SELECT a FROM Anuncio a ORDER BY a.registro DESC")
    List<Anuncio> historial();
}
