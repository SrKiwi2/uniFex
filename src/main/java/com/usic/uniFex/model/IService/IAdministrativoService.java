package com.usic.uniFex.model.IService;

import java.util.Optional;

import org.springframework.stereotype.Service;

import com.usic.uniFex.model.entity.Administrativo;

@Service
public interface IAdministrativoService extends IServiceGenerico<Administrativo, Long>{
    Optional<Administrativo> findByCodigoFuncionario(String codigoFuncionario);
}
