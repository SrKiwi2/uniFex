package com.usic.uniFex.controller.persona;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.usic.uniFex.model.dto.AreaDTO;
import com.usic.uniFex.model.dto.CarreraDTO;
import com.usic.uniFex.model.service.CatalogoAcademicoService;

import lombok.RequiredArgsConstructor;

/**
 * El catalogo de areas y carreras de la UAP (V35). Solo lectura.
 *
 * Sin {@code @PreAuthorize}: la cadena JWT ya exige estar autenticado, y esto es un catalogo,
 * no un dato de nadie. Restringirlo a administracion obligaria a duplicarlo el dia que una
 * pantalla de vendedor tenga que enseñar la carrera de un compañero.
 */
@RestController
@RequestMapping("/api/app")
@RequiredArgsConstructor
public class CatalogoAcademicoApiController {

    private final CatalogoAcademicoService catalogo;

    /** Las areas con sus carreras dentro: lo que necesita un desplegable agrupado o un filtro. */
    @GetMapping("/areas")
    public List<AreaDTO> areas() {
        return catalogo.areasConCarreras();
    }

    /** Las carreras planas, cada una con la sigla de su area. Para un selector sin grupos. */
    @GetMapping("/carreras")
    public List<CarreraDTO> carreras() {
        return catalogo.carreras();
    }
}
