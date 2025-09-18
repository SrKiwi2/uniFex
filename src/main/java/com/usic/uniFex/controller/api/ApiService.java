package com.usic.uniFex.controller.api;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.usic.uniFex.model.repository.FuncionesApi;

@Service
public class ApiService {

    @Autowired
    private FuncionesApi funcionesApi;

    public Integer verificarInscripcionPuesto(String ci, Integer idEntidad) {
        return funcionesApi.verificar_inscripcion_puesto(ci, idEntidad);
    }
}