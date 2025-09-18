package com.usic.uniFex.model.IServiceImp;

import java.util.List;

import org.springframework.stereotype.Service;

import com.usic.uniFex.model.IService.ICategoriaVentaService;
import com.usic.uniFex.model.dao.ICategoriaVentaDao;
import com.usic.uniFex.model.entity.CategoriaVenta;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CategoriaVentaServiceImpl implements ICategoriaVentaService{

    private final ICategoriaVentaDao dao;

    @Override
    public List<CategoriaVenta> findAll() {
        return dao.findAll();
    }

    @Override
    public CategoriaVenta findById(Long idEntidad) {
        return dao.findById(idEntidad).orElse(null);
    }

    @Override
    public CategoriaVenta save(CategoriaVenta entidad) {
        return dao.save(entidad);
    }

    @Override
    public void deleteById(Long idEntidad) {
        dao.deleteById(idEntidad);
    }
    
}
