package com.usic.uniFex.controller.edicion;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.usic.uniFex.model.dao.IEdicionDao;
import com.usic.uniFex.model.dto.EdicionDTO;

import lombok.RequiredArgsConstructor;

/**
 * Ediciones de la feria para la SPA. Cualquier usuario autenticado las necesita: "Mis
 * ventas" ofrece un selector por edicion (por defecto la activa) y los reportes futuros
 * tambien filtraran por edicion.
 */
@RestController
@RequestMapping("/api/app/ediciones")
@RequiredArgsConstructor
public class EdicionApiController {

    private final IEdicionDao edicionDao;

    @GetMapping
    public List<EdicionDTO> listar() {
        return edicionDao.findAllByOrderByAnioDesc().stream()
                .map(EdicionDTO::de)
                .toList();
    }
}
