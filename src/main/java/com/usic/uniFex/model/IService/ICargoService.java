package com.usic.uniFex.model.IService;

import java.util.Optional;

import org.springframework.stereotype.Service;

import com.usic.uniFex.model.entity.Cargo;
import com.usic.uniFex.model.service.IServiceGenerico;

@Service
public interface ICargoService extends IServiceGenerico<Cargo, Long> {
    Optional<Cargo> findByNombre(String nombre);
}
