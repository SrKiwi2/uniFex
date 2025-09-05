package com.usic.uniFex.model.IServiceImp;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.usic.uniFex.model.IService.ITipoEntidadService;
import com.usic.uniFex.model.dao.ITipoEntidadDao;
import com.usic.uniFex.model.entity.TipoEntidad;

@Service
public class TipoEntidadServiceImpl implements ITipoEntidadService{
    @Autowired
    private ITipoEntidadDao tipoEntidadDao;

    @Override
    public List<TipoEntidad> findAll() {
        // TODO Auto-generated method stub
        return tipoEntidadDao.findAll();
    }

    @Override
    public TipoEntidad findById(Long idEntidad) {
        // TODO Auto-generated method stub
        return tipoEntidadDao.findById(idEntidad).orElse(null); 
    }

    @Override
    public TipoEntidad save(TipoEntidad entidad) {
        // TODO Auto-generated method stub
        return tipoEntidadDao.save(entidad);
    }

    @Override
    public void deleteById(Long idEntidad) {
        // TODO Auto-generated method stub
        tipoEntidadDao.deleteById(idEntidad);
    }
}
