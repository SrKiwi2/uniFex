package com.usic.uniFex.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.usic.uniFex.model.IService.ICategoriaService;
import com.usic.uniFex.model.IService.IPuestoService;
import com.usic.uniFex.model.IService.ITipoEntidadService;
import com.usic.uniFex.model.repository.FuncionesInscripcion;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;


@Controller
@RequiredArgsConstructor
public class HomeController {

    private final ITipoEntidadService tipoEntidadService;
    private final ICategoriaService categoriaService;
    private final IPuestoService puestoService;
    private final FuncionesInscripcion funcionesInscripcion;

    @GetMapping("/")
    public String home(HttpServletRequest request, Model model) {
        model.addAttribute("tiposEntidads", tipoEntidadService.findAll());
        model.addAttribute("categorias", categoriaService.findAll());
        model.addAttribute("puestos", puestoService.findAll());

        List<Map<String, Object>> puestosLibres = funcionesInscripcion.fn_lista_puestos();
        // Mantén el orden del SQL (c.nombre, p.codigo)
        Map<String, List<Map<String, Object>>> puestosPorCategoria =
            puestosLibres.stream().collect(Collectors.groupingBy(
                m -> (String) m.get("categoria"),
                LinkedHashMap::new,
                Collectors.toList()
            ));

        model.addAttribute("puestosPorCategoria", puestosPorCategoria);

        System.out.println("vista inicial");
        model.addAttribute("mapaPdf", "mapa.pdf");
        return "publico/index"; // templates/publico/index.html
    }

}
