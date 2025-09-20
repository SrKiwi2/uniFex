package com.usic.uniFex.model.IService;

import java.util.List;

import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Service;

import com.usic.uniFex.model.dto.PromotoresListadoDTO;
import com.usic.uniFex.model.dto.ResponsableDetalleDTO;
import com.usic.uniFex.model.dto.ResponsableListadoExplodeView;
import com.usic.uniFex.model.dto.ResponsableListadoView;
import com.usic.uniFex.model.entity.Responsable;
import com.usic.uniFex.model.service.ResponsableDetalleRow;

@Service
public interface IResponsableService extends IServiceGenerico<Responsable, Long> {
    List<Responsable> findByEntidadId(Long entidadId);

    List<PromotoresListadoDTO> listarParaTabla();

    List<Responsable> listarConPersonaYEntidad();

    List<ResponsableListadoView> listarVista();

    List<Responsable> findByEntidadIdWithPersona(@Param("entidadId") Long entidadId);

    List<ResponsableListadoExplodeView> listarVistaExplode();

    ResponsableDetalleDTO findDetallePorCi(String ci);
}
