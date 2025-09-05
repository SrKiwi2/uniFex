package com.usic.uniFex.model.IService;

import java.util.Optional;

import org.springframework.stereotype.Service;

import com.usic.uniFex.model.entity.Oficina;
import com.usic.uniFex.model.service.IServiceGenerico;

@Service
public interface IOficinaService extends IServiceGenerico<Oficina, Long> {
    Optional<Oficina> findByNombre(String nombre);
}
