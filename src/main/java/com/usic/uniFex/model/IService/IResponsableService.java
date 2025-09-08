package com.usic.uniFex.model.IService;

import java.util.List;

import org.springframework.stereotype.Service;

import com.usic.uniFex.model.entity.Responsable;

@Service
public interface IResponsableService extends IServiceGenerico<Responsable, Long> {
    List<Responsable> findByEntidadId(Long entidadId);
}
