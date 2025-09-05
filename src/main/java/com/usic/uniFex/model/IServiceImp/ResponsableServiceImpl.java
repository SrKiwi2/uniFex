package com.usic.uniFex.model.IServiceImp;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.usic.uniFex.model.IService.IResponsableService;
import com.usic.uniFex.model.dao.IResponsableDao;
import com.usic.uniFex.model.entity.Responsable;

@Service
public class ResponsableServiceImpl implements IResponsableService{
    
    @Autowired
    private IResponsableDao responsableDao;

    @Override
    public List<Responsable> findAll() {
        // TODO Auto-generated method stub
        return responsableDao.findAll();
    }

    @Override
    public Responsable findById(Long idEntidad) {
        // TODO Auto-generated method stub
        return responsableDao.findById(idEntidad).orElse(null);
    }

    @Override
    public Responsable save(Responsable entidad) {
        // TODO Auto-generated method stub
        return responsableDao.save(entidad);
    }

    @Override
    public void deleteById(Long idEntidad) {
        // TODO Auto-generated method stub
        responsableDao.deleteById(idEntidad);
    }

    
}
