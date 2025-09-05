package com.usic.uniFex.model.IServiceImp;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.usic.uniFex.model.IService.IInscripcionService;
import com.usic.uniFex.model.dao.IInscripcionDao;
import com.usic.uniFex.model.entity.Inscripcion;

@Service
public class InscripcionServiceImpl implements IInscripcionService{
    
    @Autowired
    private IInscripcionDao inscripcionDao;

    @Override
    public List<Inscripcion> findAll() {
        // TODO Auto-generated method stub
        return inscripcionDao.findAll();
    }

    @Override
    public Inscripcion findById(Long idEntidad) {
        // TODO Auto-generated method stub
        return inscripcionDao.findById(idEntidad).orElse(null);
    }

    @Override
    public Inscripcion save(Inscripcion entidad) {
        // TODO Auto-generated method stub
        return inscripcionDao.save(entidad);
    }

    @Override
    public void deleteById(Long idEntidad) {
        // TODO Auto-generated method stub
        inscripcionDao.deleteById(idEntidad);
    }
}
