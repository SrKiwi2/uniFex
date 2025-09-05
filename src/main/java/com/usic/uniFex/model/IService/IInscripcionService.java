package com.usic.uniFex.model.IService;

import java.util.List;

import org.springframework.stereotype.Service;

import com.usic.uniFex.model.entity.Inscripcion;

@Service
public interface IInscripcionService extends IServiceGenerico<Inscripcion, Long> {
    List<Inscripcion> listarConRelaciones();
}
