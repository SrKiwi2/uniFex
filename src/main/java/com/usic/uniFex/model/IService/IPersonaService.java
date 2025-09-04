package com.usic.uniFex.model.IService;

import java.util.*;

import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Service;

import com.usic.uniFex.model.entity.Persona;

@Service
public interface IPersonaService  extends IServiceGenerico<Persona, Long>{
    List<Persona> listarPersonas();
    Persona buscarPersonaPorCI(String ci);
    List<Persona> buscarPersonaPorNombrePaternoMaterno(String nombre, String paterno, String materno);
    Persona buscarPersonaPorNombreCompletoUno(String nombre, String paterno, String materno);
    Persona buscarPersonaPorNombrePaterno(String nombre, String paterno);
    Persona buscarPersonaNombre(String nombre);
    Optional<Persona> findFirstByCi(String ci);
}
