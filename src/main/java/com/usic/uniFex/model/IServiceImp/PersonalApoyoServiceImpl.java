package com.usic.uniFex.model.IServiceImp;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.usic.uniFex.model.IService.IPersonalApoyoService;
import com.usic.uniFex.model.dao.IPersonalApoyoDao;
import com.usic.uniFex.model.entity.PersonalApoyo;

@Service
public class PersonalApoyoServiceImpl implements IPersonalApoyoService {
    @Autowired
    private IPersonalApoyoDao personalApoyoDao;

    @Override
    public List<PersonalApoyo> findAll() {
        return personalApoyoDao.findAll();
    }

    @Override
    public PersonalApoyo findById(Long idEntidad) {
        return personalApoyoDao.findById(idEntidad).orElse(null);
    }

    @Override
    public PersonalApoyo save(PersonalApoyo entidad) {
        return personalApoyoDao.save(entidad);
    }

    @Override
    public void deleteById(Long idEntidad) {
        personalApoyoDao.deleteById(idEntidad);
    }

    @Override
    public List<PersonalApoyo> listarPersonalApoyo() {
        return personalApoyoDao.listarPersonalApoyo();
    }

    @Override
    public List<PersonalApoyo> buscarPorDependencia(Long idDependencia) {
        return personalApoyoDao.buscarPorDependencia(idDependencia);
    }

    @Override
    public Optional<PersonalApoyo> findByCi(String ci) {
        return personalApoyoDao.findByCi(ci);
    }

    @Override
    public List<PersonalApoyo> buscarPorNombreCompleto(String nombre, String paterno, String materno) {
        return personalApoyoDao.buscarPorNombreCompleto(nombre, paterno, materno);
    }
}