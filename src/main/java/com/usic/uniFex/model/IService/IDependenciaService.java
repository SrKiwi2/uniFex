package com.usic.uniFex.model.IService;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Service;

import com.usic.uniFex.model.entity.Dependencia;

@Service
public interface IDependenciaService extends IServiceGenerico<Dependencia, Long> {
    List<Dependencia> listarDependencias();
    Optional<Dependencia> findByNombre(String nombre);
}