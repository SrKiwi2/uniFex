package com.usic.uniFex.controller;

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
        model.addAttribute("puestos_libres", funcionesInscripcion.fn_lista_puestos());
        System.out.println("vista inicial");
        model.addAttribute("mapaPdf", "mapa.pdf");
        return "publico/index"; // templates/publico/index.html
    }
}
