package com.usic.uniFex.model.IService;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.usic.uniFex.model.dto.PromotoresListadoDTO;
import com.usic.uniFex.model.entity.Administrativo;

@Service
public interface IAdministrativoService extends IServiceGenerico<Administrativo, Long>{
    Optional<Administrativo> findByCodigoFuncionario(String codigoFuncionario);
}
