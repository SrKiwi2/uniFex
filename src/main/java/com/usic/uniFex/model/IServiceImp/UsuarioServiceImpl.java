package com.usic.uniFex.model.IServiceImp;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.usic.uniFex.model.IService.IUsuarioService;
import com.usic.uniFex.model.dao.IUsuarioDao;
import com.usic.uniFex.model.entity.Usuario;

@Service
public class UsuarioServiceImpl implements IUsuarioService {

    @Autowired private IUsuarioDao dao;

    @Override
    public List<Usuario> findAll() {
        return dao.findAll();
    }

    @Override
    public Usuario findById(Long idEntidad) {
        return dao.findById(idEntidad).orElse(null);
    }

    @Override
    public Usuario save(Usuario entidad) {
        return dao.save(entidad);
    }

    @Override
    public void deleteById(Long idEntidad) {
        dao.deleteById(idEntidad);
    }

    @Override
    public Optional<Usuario> findByUsername(String username) {
       return dao.findByUsername(username);
    }
    
}
