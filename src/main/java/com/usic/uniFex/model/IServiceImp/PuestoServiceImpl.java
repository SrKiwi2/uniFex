package com.usic.uniFex.model.IServiceImp;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.usic.uniFex.model.IService.IPuestoService;
import com.usic.uniFex.model.dao.IPuestoDao;
import com.usic.uniFex.model.entity.Puesto;

import jakarta.transaction.Transactional;

@Service
public class PuestoServiceImpl implements IPuestoService {
    
    @Autowired
    private IPuestoDao puestoDao;

    @Override
    public List<Puesto> findAll() {
        // TODO Auto-generated method stub
        return puestoDao.findAll();
    }

    @Override
    public Puesto findById(Long idEntidad) {
        // TODO Auto-generated method stub
        return puestoDao.findById(idEntidad).orElse(null);
    }

    @Override
    public Puesto save(Puesto entidad) {
        // TODO Auto-generated method stub
        return puestoDao.save(entidad);
    }

    @Override
    public void deleteById(Long idEntidad) {
        // TODO Auto-generated method stub
        puestoDao.deleteById(idEntidad);
    }

    @Override
    public List<Puesto> listarConCategoria() {
        return puestoDao.findAll();
    }
    
    @Override
    public List<Puesto> listarPuestos() {
       return puestoDao.listarPuestos();
    }

    @Override
    public List<Puesto> listarLibresPorCategoria(Long categoriaId) {
        return puestoDao.findByEstadoPuestoAndCategoriaIdOrderByCodigoAsc("L", categoriaId);
    }

}
