package com.usic.uniFex.model.IServiceImp;

import java.util.List;

import org.springframework.stereotype.Service;

import com.usic.uniFex.model.IService.IVentaBoletoService;
import com.usic.uniFex.model.dao.IVentaBoletoDao;
import com.usic.uniFex.model.entity.VentaBoleto;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class VentaBoletoServiceImpl implements IVentaBoletoService{

    private final IVentaBoletoDao dao;

    @Override
    public List<VentaBoleto> findAll() {
        return dao.findAll();
    }

    @Override
    public VentaBoleto findById(Long idEntidad) {
        return dao.findById(idEntidad).orElse(null);
    }

    @Override
    public VentaBoleto save(VentaBoleto entidad) {
        return dao.save(entidad);
    }

    @Override
    public void deleteById(Long idEntidad) {
        dao.deleteById(idEntidad);
    }
}