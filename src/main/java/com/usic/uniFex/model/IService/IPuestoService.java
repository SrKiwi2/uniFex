package com.usic.uniFex.model.IService;

import java.util.List;

import org.springframework.stereotype.Service;

import com.usic.uniFex.model.entity.Puesto;

@Service
public interface IPuestoService extends IServiceGenerico<Puesto, Long> {
    List<Puesto> listarConCategoria();
}
