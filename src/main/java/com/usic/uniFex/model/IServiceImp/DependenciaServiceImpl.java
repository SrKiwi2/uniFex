package com.usic.uniFex.model.IServiceImp;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.usic.uniFex.model.IService.IDependenciaService;
import com.usic.uniFex.model.dao.IDependenciaDao;
import com.usic.uniFex.model.entity.Dependencia;

@Service
public class DependenciaServiceImpl implements IDependenciaService {
    @Autowired
    private IDependenciaDao dependenciaDao;

    @Override
    public List<Dependencia> findAll() {
        return dependenciaDao.findAll();
    }

    @Override
    public Dependencia findById(Long idEntidad) {
        return dependenciaDao.findById(idEntidad).orElse(null);
    }

    @Override
    public Dependencia save(Dependencia entidad) {
        return dependenciaDao.save(entidad);
    }

    @Override
    public void deleteById(Long idEntidad) {
        dependenciaDao.deleteById(idEntidad);
    }

    @Override
    public List<Dependencia> listarDependencias() {
        return dependenciaDao.listarDependencias();
    }

    @Override
    public Optional<Dependencia> findByNombre(String nombre) {
        return dependenciaDao.findByNombre(nombre);
    }
}