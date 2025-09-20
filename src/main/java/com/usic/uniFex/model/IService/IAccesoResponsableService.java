package com.usic.uniFex.model.IService;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.usic.uniFex.model.entity.AccesoResponsable;

@Service
public interface IAccesoResponsableService extends IServiceGenerico<AccesoResponsable, Long> {
     Map<String,Object> estadoPorCi(String ci);      // { dentro: boolean, logs: [...] }
    Map<String,Object> entrarPorCi(String ci);      // abre registro
    Map<String,Object> salirPorCi(String ci);       // cierra último abierto
}
