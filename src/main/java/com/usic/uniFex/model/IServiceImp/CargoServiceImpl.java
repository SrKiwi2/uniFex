package com.usic.uniFex.model.IServiceImp;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.usic.uniFex.model.IService.ICargoService;
import com.usic.uniFex.model.dao.ICargoDao;
import com.usic.uniFex.model.entity.Cargo;

@Service
public class CargoServiceImpl implements ICargoService {

    @Autowired private ICargoDao dao;

    @Override
    public List<Cargo> findAll() {
        return dao.findAll();
    }

    @Override
    public Cargo findById(Long idEntidad) {
       return dao.findById(idEntidad).orElse(null);
    }

    @Override
    public Cargo save(Cargo entidad) {
        return dao.save(entidad);
    }

    @Override
    public void deleteById(Long idEntidad) {
        dao.deleteById(idEntidad);
    }

    @Override
    public Optional<Cargo> findByNombre(String nombre) {
        return dao.findByNombre(nombre);
    }

}
