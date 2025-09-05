package com.usic.uniFex.model.IServiceImp;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.usic.uniFex.model.IService.IInscripcionService;
import com.usic.uniFex.model.dao.IAdministrativoDao;
import com.usic.uniFex.model.dao.IInscripcionDao;
import com.usic.uniFex.model.dto.InscripcionListadoDTO;
import com.usic.uniFex.model.dto.ResumenCategoriaView;
import com.usic.uniFex.model.dto.ResumenEntidadView;
import com.usic.uniFex.model.entity.Inscripcion;
import com.usic.uniFex.model.entity.InscripcionPuesto;

@Service
public class InscripcionServiceImpl implements IInscripcionService{

    private final IAdministrativoDao IAdministrativoDao;
    
    @Autowired
    private IInscripcionDao inscripcionDao;

    InscripcionServiceImpl(IAdministrativoDao IAdministrativoDao) {
        this.IAdministrativoDao = IAdministrativoDao;
    }

    @Override
    public List<Inscripcion> findAll() {
        // TODO Auto-generated method stub
        return inscripcionDao.findAll();
    }

    @Override
    public Inscripcion findById(Long idEntidad) {
        // TODO Auto-generated method stub
        return inscripcionDao.findById(idEntidad).orElse(null);
    }

    @Override
    public Inscripcion save(Inscripcion entidad) {
        // TODO Auto-generated method stub
        return inscripcionDao.save(entidad);
    }

    @Override
    public void deleteById(Long idEntidad) {
        // TODO Auto-generated method stub
        inscripcionDao.deleteById(idEntidad);
    }

    @Override
    public List<ResumenCategoriaView> resumenPorCategoria() {
        return inscripcionDao.resumenPorCategoria();
    }

    @Override
    public List<ResumenEntidadView> resumenPorEntidad() {
        return inscripcionDao.resumenPorEntidad();
    }

    @Override
    public List<InscripcionListadoDTO> listarParaTabla() {
        List<Inscripcion> ins = inscripcionDao.findAll();

        return ins.stream().map(i -> {
            var entidad = i.getEntidad();
            var tipo = (entidad != null && entidad.getTipoEntidad() != null)
                    ? entidad.getTipoEntidad().getNombre() : null;

            var items = i.getInscripcionPuestos();

            int cantidad = items != null ? items.size() : 0;

            // Total = suma de costo (BigDecimal)
            BigDecimal total = (items == null ? BigDecimal.ZERO :
                    items.stream()
                         .map(InscripcionPuesto::getCosto)
                         .filter(v -> v != null)
                         .reduce(BigDecimal.ZERO, BigDecimal::add));

            // Categorías únicas (ordenadas) de los puestos
            List<String> categorias = (items == null ? List.<String>of() :
                    items.stream()
                         .map(ip -> ip.getPuesto() != null && ip.getPuesto().getCategoria() != null
                                   ? ip.getPuesto().getCategoria().getNombre() : null)
                         .filter(n -> n != null && !n.isBlank())
                         .distinct()
                         .sorted(Comparator.naturalOrder())
                         .collect(Collectors.toList()));

            return new InscripcionListadoDTO(
                i.getId(),
                entidad != null ? entidad.getNombre() : null,
                tipo,
                entidad != null ? entidad.getNit() : null,
                cantidad,
                categorias,
                total,
                i.getFechaCompra(),
                i.getInscripcionEstado()
            );
        }).collect(Collectors.toList());
    }
}
