package com.usic.uniFex.model.IService;

import java.util.Optional;

import org.springframework.stereotype.Service;

import com.usic.uniFex.model.entity.Usuario;

@Service
public interface IUsuarioService extends IServiceGenerico<Usuario, Long>{
    Optional<Usuario> findByUsername(String username);
}
