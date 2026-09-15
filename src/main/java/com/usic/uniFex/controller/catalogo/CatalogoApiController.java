package com.usic.uniFex.controller.catalogo;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.usic.uniFex.model.entity.Categoria;
import com.usic.uniFex.model.service.CategoriaMapaService;

import lombok.RequiredArgsConstructor;

/** Catalogo de categorias y precios para consulta desde la SPA. */
@RestController
@RequestMapping("/api/app/catalogo")
@RequiredArgsConstructor
public class CatalogoApiController {

    private final CategoriaMapaService categorias;

    @GetMapping
    public List<Map<String, Object>> listar() {
        return categorias.listar().stream()
                .map(this::de)
                .toList();
    }

    private Map<String, Object> de(Categoria c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("nombre", c.getNombre());
        m.put("descripcion", c.getDescripcion());
        m.put("color", c.getColor());
        m.put("forma", c.getForma());
        m.put("tamano", c.getTamanoMapa());
        m.put("precioBase", c.getPrecioBase());
        return m;
    }
}
