package com.usic.uniFex.model.IServiceImp;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.usic.uniFex.model.IService.IRolService;
import com.usic.uniFex.model.dao.IRolDao;
import com.usic.uniFex.model.entity.Rol;

@Service
public class RolServiceImpl implements IRolService{

    @Autowired private IRolDao dao;

    @Override
    public List<Rol> findAll() {
        return dao.findAll();
    }

    @Override
    public Rol findById(Long idEntidad) {
        return dao.findById(idEntidad).orElse(null);
    }

    @Override
    public Rol save(Rol entidad) {
        return dao.save(entidad);
    }

    @Override
    public void deleteById(Long idEntidad) {
        dao.deleteById(idEntidad);
    }

    @Override
    public Optional<Rol> findByNombre(String nombre) {
        return dao.findByNombre(nombre);
    }

}
