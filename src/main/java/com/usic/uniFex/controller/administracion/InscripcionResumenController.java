package com.usic.uniFex.controller.administracion;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import com.usic.uniFex.anotacion.ValidarUsuarioAutenticado;
import com.usic.uniFex.model.IService.IInscripcionService;
import com.usic.uniFex.model.dto.ResumenCategoriaView;
import com.usic.uniFex.model.dto.ResumenEntidadView;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
@RequestMapping("/administracion/inscripcion/resumen")
public class InscripcionResumenController {
    private final IInscripcionService resumenService;

    @ValidarUsuarioAutenticado
    @GetMapping("/vista")
    public String vista() {
        return "resumen/vista";
    }

    @RequestMapping(value = "/tabla-categorias", method = {RequestMethod.GET, RequestMethod.POST})
    public String tablaCategorias(Model model) {
        List<ResumenCategoriaView> data = resumenService.resumenPorCategoria();
        model.addAttribute("data", data);
        return "resumen/tabla_registro";
    }

    @RequestMapping(value = "/tabla-entidades", method = {RequestMethod.GET, RequestMethod.POST})
    public String tablaEntidades(Model model) {
        List<ResumenEntidadView> data = resumenService.resumenPorEntidad();
        model.addAttribute("data", data);
        return "resumen/tabla_registro1";
    }
}
