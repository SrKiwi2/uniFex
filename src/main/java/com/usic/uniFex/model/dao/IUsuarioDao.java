package com.usic.uniFex.model.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.usic.uniFex.model.entity.Usuario;

public interface IUsuarioDao extends JpaRepository<Usuario, Long> {
      Optional<Usuario> findByUsername(String username);

      /** Los ids de administracion (roles SUPER USUARIO y ADMINISTRADOR), para notificarles. */
      @Query("select u.id from Usuario u where u.rol.nombre in ('SUPER USUARIO','ADMINISTRADOR') "
              + "and (u.estado is null or u.estado <> 'ELIMINADO')")
      List<Long> idsDeAdministracion();
}
