package com.usic.uniFex.model.IServiceImp;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.usic.uniFex.model.IService.IAccesoResponsableService;
import com.usic.uniFex.model.IService.IResponsableService;
import com.usic.uniFex.model.dao.IAccesoResponsableDao;
import com.usic.uniFex.model.entity.AccesoResponsable;

import jakarta.transaction.Transactional;

@Service
public class AccesoResponsableServiceImpl implements IAccesoResponsableService {

    @Autowired private IAccesoResponsableDao dao;
    @Autowired private IResponsableService responsableService;

    @Override
    public List<AccesoResponsable> findAll() {
        return dao.findAll();
    }

    @Override
    public AccesoResponsable findById(Long idEntidad) {
        return dao.findById(idEntidad).orElse(null);
    }

    @Override
    public AccesoResponsable save(AccesoResponsable entidad) {
        return dao.save(entidad);
    }

    @Override
    public void deleteById(Long idEntidad) {
        dao.deleteById(idEntidad);
    }

    @Override
    public Map<String, Object> estadoPorCi(String ci) {
        var dto = responsableService.findDetallePorCi(ci);
        if (dto == null) return Map.of("ok", false, "mensaje", "Responsable no encontrado");

        boolean dentro = dao.existsByIdPersonaAndFechaSalidaIsNull(dto.idPersona());
        List<Map<String,Object>> logs = dao
        .findTop20ByIdPersonaOrderByFechaEntradaDesc(dto.idPersona())
        .stream()
        .map((AccesoResponsable a) -> {
            Map<String,Object> m = new LinkedHashMap<>();
            m.put("id", a.getId());
            m.put("entrada", a.getFechaEntrada());
            m.put("salida", a.getFechaSalida()); // <-- F mayúscula
            m.put("observacion", a.getObservacion());
            return m;
        })
        .collect(Collectors.toList());

        return Map.of("ok", true, "dentro", dentro, "logs", logs);
    }

    @Override
    @Transactional
    public Map<String, Object> entrarPorCi(String ci) {
        var dto = responsableService.findDetallePorCi(ci);
        if (dto == null) return Map.of("ok", false, "mensaje", "Responsable no encontrado");
        if (dao.existsByIdPersonaAndFechaSalidaIsNull(dto.idPersona()))
            return Map.of("ok", false, "mensaje", "Ya está dentro");

        var acc = new AccesoResponsable();
        acc.setIdPersona(dto.idPersona());
        acc.setCi(dto.ci());
        acc.setFechaEntrada(LocalDateTime.now());
        dao.save(acc);

        return Map.of("ok", true, "mensaje", "Entrada registrada", "id", acc.getId());
    }

    @Override
    @Transactional
    public Map<String, Object> salirPorCi(String ci) {
        var dto = responsableService.findDetallePorCi(ci);
        if (dto == null) return Map.of("ok", false, "mensaje", "Responsable no encontrado");

        var abierto = dao.findFirstByIdPersonaAndFechaSalidaIsNullOrderByFechaEntradaDesc(dto.idPersona())
                .orElse(null);
        if (abierto == null) return Map.of("ok", false, "mensaje", "No tiene una entrada abierta");

        abierto.setFechaSalida(LocalDateTime.now());
        dao.save(abierto);

        return Map.of("ok", true, "mensaje", "Salida registrada", "id", abierto.getId());
    }
    
}
