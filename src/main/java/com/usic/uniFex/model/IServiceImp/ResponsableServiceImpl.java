package com.usic.uniFex.model.IServiceImp;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.usic.uniFex.model.IService.IResponsableService;
import com.usic.uniFex.model.dao.IResponsableDao;
import com.usic.uniFex.model.dto.PromotoresListadoDTO;
import com.usic.uniFex.model.dto.ResponsableListadoView;
import com.usic.uniFex.model.entity.Responsable;

@Service
public class ResponsableServiceImpl implements IResponsableService{
    
    @Autowired
    private IResponsableDao responsableDao;

    @Override
    public List<Responsable> findAll() {
        // TODO Auto-generated method stub
        return responsableDao.findAll();
    }

    @Override
    public Responsable findById(Long idEntidad) {
        // TODO Auto-generated method stub
        return responsableDao.findById(idEntidad).orElse(null);
    }

    @Override
    public Responsable save(Responsable entidad) {
        // TODO Auto-generated method stub
        return responsableDao.save(entidad);
    }

    @Override
    public void deleteById(Long idEntidad) {
        // TODO Auto-generated method stub
        responsableDao.deleteById(idEntidad);
    }

    @Override
    public List<Responsable> findByEntidadId(Long entidadId) {
        return responsableDao.findByEntidadId(entidadId);
    }

    @Override
    public List<PromotoresListadoDTO> listarParaTabla() {
        return responsableDao.findAllConPersonaYEntidad().stream().map(r -> {
            var p = r.getPersona();
            var e = r.getEntidad();

            String nombreCompleto = (p != null)
                    ? String.format("%s %s %s",
                        p.getNombre()  != null ? p.getNombre()  : "",
                        p.getPaterno() != null ? p.getPaterno() : "",
                        p.getMaterno() != null ? p.getMaterno() : ""
                    ).trim().replaceAll("\\s{2,}", " ")
                    : "";

            String ci   = (p != null && p.getCi()   != null) ? p.getCi()   : "";
            String foto = (p != null && p.getFoto() != null) ? p.getFoto() : "";

            Long   entidadId      = (e != null) ? e.getId() : null;
            String entidadNombre  = (e != null && e.getNombre() != null) ? e.getNombre() : "";

            return new PromotoresListadoDTO(
                r.getId(), entidadId, entidadNombre, nombreCompleto, ci, foto
            );
        }).toList();
    }

    @Override
    public List<Responsable> listarConPersonaYEntidad() {
        return responsableDao.listarConPersonaYEntidad();
    }

    @Override
    public List<ResponsableListadoView> listarVista() {
        return responsableDao.listarVista();
    }

    @Override
    public List<Responsable> findByEntidadIdWithPersona(Long entidadId) {
        return responsableDao.findByEntidadIdWithPersona(entidadId);
    }


    
}
