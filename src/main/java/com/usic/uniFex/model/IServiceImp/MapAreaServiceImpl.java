package com.usic.uniFex.model.IServiceImp;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.usic.uniFex.model.IService.IMapAreaService;
import com.usic.uniFex.model.dao.MapAreaDao;
import com.usic.uniFex.model.entity.MapArea;

@Service
public class MapAreaServiceImpl implements IMapAreaService{

    @Autowired private MapAreaDao dao;

    @Override
    public List<MapArea> findAll() {
        return dao.findAll();
    }

    @Override
    public MapArea findById(Long idEntidad) {
        return dao.findById(idEntidad).orElse(null);
    }

    @Override
    public MapArea save(MapArea entidad) {
        return dao.save(entidad);
    }

    @Override
    public void deleteById(Long idEntidad) {
        dao.deleteById(idEntidad);
    }
    
}
