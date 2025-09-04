package com.usic.uniFex.model.IServiceImp;

import java.util.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.usic.uniFex.model.IService.IPersonaService;
import com.usic.uniFex.model.dao.IPersonasDao;
import com.usic.uniFex.model.entity.Persona;

@Service
public class PersonaServiceImpl implements IPersonaService {
    @Autowired
    private IPersonasDao personaDao;

    @Override
    public List<Persona> findAll() {
        return personaDao.findAll();
    }

    @Override
    public Persona findById(Long idEntidad) {
        return personaDao.findById(idEntidad).orElse(null);
    }

    @Override
    public Persona save(Persona entidad) {
        return personaDao.save(entidad);
    }

    @Override
    public void deleteById(Long idEntidad) {
        personaDao.deleteById(idEntidad);
    }

    @Override
    public List<Persona> listarPersonas() {
        return personaDao.listarPersonas();
    }

    @Override
    public Persona buscarPersonaPorCI(String ci) {
        return personaDao.buscarPersonaPorCI(ci);
    }

    @Override
    public List<Persona> buscarPersonaPorNombrePaternoMaterno(String nombre, String paterno, String materno) {
        return personaDao.buscarPersonaPorNombrePaternoMaterno(nombre, paterno, materno);
    }

    @Override
    public Persona buscarPersonaPorNombreCompletoUno(String nombre, String paterno, String materno) {
        return personaDao.buscarPersonaPorNombreCompletoUno(nombre, paterno, materno);
    }

    @Override
    public Persona buscarPersonaPorNombrePaterno(String nombre, String paterno) {
       return personaDao.buscarPersonaPorNombrePaterno(nombre, paterno);
    }

    @Override
    public Persona buscarPersonaNombre(String nombre) {
        return personaDao.buscarPersonaNombre(nombre);
    }

    @Override
    public Optional<Persona> findFirstByCi(String ci) {
        return personaDao.findFirstByCi(ci);
    }
}
