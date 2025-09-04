package com.usic.uniFex.model.IService;

import java.util.Optional;

import org.springframework.stereotype.Service;

import com.usic.uniFex.model.entity.Rol;

@Service
public interface IRolService extends IServiceGenerico<Rol, Long> {
    Optional<Rol> findByNombre(String nombre);
}
