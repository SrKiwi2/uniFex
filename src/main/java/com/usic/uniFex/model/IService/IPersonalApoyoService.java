package com.usic.uniFex.model.IService;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Service;

import com.usic.uniFex.model.entity.PersonalApoyo;

@Service
public interface IPersonalApoyoService extends IServiceGenerico<PersonalApoyo, Long> {
    List<PersonalApoyo> listarPersonalApoyo();
    List<PersonalApoyo> buscarPorDependencia(Long idDependencia);
    Optional<PersonalApoyo> findByCi(String ci);
    List<PersonalApoyo> buscarActivosPorCi(@Param("ci") String ci);
    List<PersonalApoyo> buscarPorNombreCompleto(String nombre, String paterno, String materno);
}