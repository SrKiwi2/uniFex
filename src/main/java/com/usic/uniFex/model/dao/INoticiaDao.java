package com.usic.uniFex.model.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.usic.uniFex.model.entity.Noticia;

public interface INoticiaDao extends JpaRepository<Noticia, Long> {

    /** Noticias vivas de una edicion, de la ultima registrada a la primera. */
    List<Noticia> findByEdicionIdAndEstadoNotOrderByIdDesc(Long idEdicion, String estado);

    /** Las 10 ultimas registradas: el carrusel de la vista publica. */
    List<Noticia> findTop10ByEdicionIdAndEstadoNotOrderByIdDesc(Long idEdicion, String estado);
}
