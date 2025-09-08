package com.usic.uniFex.model.IService;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

@Service
public interface FuncionesInscripcionService {
    List<Map<String,Object>> obtenerPuestosPorInscripcion(Long idInscripcion);
}
