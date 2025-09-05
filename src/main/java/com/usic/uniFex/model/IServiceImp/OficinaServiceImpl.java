package com.usic.uniFex.model.IServiceImp;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.usic.uniFex.model.IService.IOficinaService;
import com.usic.uniFex.model.dao.IOficinaDao;
import com.usic.uniFex.model.entity.Oficina;

@Service
public class OficinaServiceImpl implements IOficinaService{

    @Autowired private IOficinaDao dao;

    @Override
    public List<Oficina> findAll() {
        return dao.findAll();
    }

    @Override
    public Oficina findById(Long idEntidad) {
        return dao.findById(idEntidad).orElse(null);
    }

    @Override
    public Oficina save(Oficina entidad) {
        return dao.save(entidad);
    }

    @Override
    public void deleteById(Long idEntidad) {
        dao.deleteById(idEntidad);
    }

    @Override
    public Optional<Oficina> findByNombre(String nombre) {
        return dao.findByNombre(nombre);
    }
}
