package com.usic.uniFex.controller.catalogo;

import java.io.ByteArrayOutputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.usic.uniFex.model.entity.Categoria;
import com.usic.uniFex.model.entity.CategoriaOpcion;
import com.usic.uniFex.model.service.CatalogoPdfService;
import com.usic.uniFex.model.service.CategoriaMapaService;
import com.usic.uniFex.model.service.CategoriaOpcionService;

import lombok.RequiredArgsConstructor;

/** Catalogo de categorias y precios para consulta desde la SPA. */
@RestController
@RequestMapping("/api/app/catalogo")
@RequiredArgsConstructor
public class CatalogoApiController {

    private final CategoriaMapaService categorias;
    private final CategoriaOpcionService opciones;
    private final CatalogoPdfService pdfService;

    @GetMapping
    public List<Map<String, Object>> listar() {
        Map<Long, List<CategoriaOpcion>> porCategoria = opciones.porCategoria();
        return categorias.listar().stream()
                .map(c -> de(c, porCategoria.getOrDefault(c.getId(), List.of())))
                .toList();
    }

    @GetMapping("/pdf")
    public ResponseEntity<byte[]> pdf() {
        try (ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            pdfService.generar(categorias.listar(), opciones.porCategoria(), salida);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=catalogo-precios.pdf")
                    .body(salida.toByteArray());
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(("No se pudo generar el catálogo de precios: " + e.getMessage()).getBytes());
        }
    }

    private Map<String, Object> de(Categoria c, List<CategoriaOpcion> opciones) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("nombre", c.getNombre());
        m.put("descripcion", c.getDescripcion());
        m.put("color", c.getColor());
        m.put("forma", c.getForma());
        m.put("tamano", c.getTamanoMapa());
        m.put("precioBase", c.getPrecioBase());
        m.put("opciones", opciones.stream().map(this::opcion).toList());
        return m;
    }

    private Map<String, Object> opcion(CategoriaOpcion o) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", o.getId());
        m.put("nombre", o.getNombre());
        m.put("precio", o.getPrecio());
        m.put("predeterminada", Boolean.TRUE.equals(o.getPredeterminada()));
        m.put("orden", o.getOrden());
        return m;
    }
}
