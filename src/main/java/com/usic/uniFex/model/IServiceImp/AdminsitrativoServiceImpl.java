package com.usic.uniFex.model.IServiceImp;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.usic.uniFex.model.IService.IAdministrativoService;
import com.usic.uniFex.model.dao.IAdministrativoDao;
import com.usic.uniFex.model.entity.Administrativo;

@Service
public class AdminsitrativoServiceImpl implements IAdministrativoService {

    @Autowired private IAdministrativoDao dao;

    @Override
    public List<Administrativo> findAll() {
        return dao.findAll();
    }

    @Override
    public Administrativo findById(Long idEntidad) {
        return dao.findById(idEntidad).orElse(null);
    }

    @Override
    public Administrativo save(Administrativo entidad) {
        return dao.save(entidad);
    }

    @Override
    public void deleteById(Long idEntidad) {
        dao.deleteById(idEntidad);
    }

    @Override
    public Optional<Administrativo> findByCodigoFuncionario(String codigoFuncionario) {
        return dao.findByCodigoFuncionario(codigoFuncionario);
    }
}
