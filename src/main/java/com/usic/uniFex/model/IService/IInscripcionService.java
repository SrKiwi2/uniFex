package com.usic.uniFex.model.IService;

import org.springframework.stereotype.Service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.usic.uniFex.model.dto.InscripcionDetalleDTO;
import com.usic.uniFex.model.dto.InscripcionListadoDTO;
import com.usic.uniFex.model.dto.ResumenCategoriaView;
import com.usic.uniFex.model.dto.ResumenEntidadView;
import com.usic.uniFex.model.dto.ResumenGeneralView;
import com.usic.uniFex.model.entity.Inscripcion;

@Service
public interface IInscripcionService extends IServiceGenerico<Inscripcion, Long> {
    List<InscripcionListadoDTO> listarParaTabla();
    List<InscripcionListadoDTO> listarParaTabla(boolean canceladas);
    InscripcionDetalleDTO detalleParaTabla(Long id);
    List<ResumenCategoriaView> resumenPorCategoria();
    List<ResumenEntidadView> resumenPorEntidad();
    ResumenGeneralView resumenGeneral();
}
