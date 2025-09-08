package com.usic.uniFex.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.usic.uniFex.model.IService.ICategoriaService;
import com.usic.uniFex.model.IService.IPuestoService;
import com.usic.uniFex.model.IService.ITipoEntidadService;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class HomeController {

    private final ITipoEntidadService tipoEntidadService;
    private final ICategoriaService categoriaService;
    private final IPuestoService puestoService;

    @GetMapping("/")
    public String home(HttpServletRequest request, Model model) {
        model.addAttribute("tiposEntidads", tipoEntidadService.findAll());
        model.addAttribute("categorias", categoriaService.findAll());
        model.addAttribute("puestos", puestoService.findAll());
        System.out.println("vista inicial");
        return "publico/index"; // templates/publico/index.html
    }
}
