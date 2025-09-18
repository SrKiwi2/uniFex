package com.usic.uniFex.model.IService;

import org.springframework.stereotype.Service;

import com.usic.uniFex.model.entity.VentaBoleto;
import com.usic.uniFex.model.service.IServiceGenerico;

@Service
public interface IVentaBoletoService extends IServiceGenerico<VentaBoleto, Long> {
    
}
