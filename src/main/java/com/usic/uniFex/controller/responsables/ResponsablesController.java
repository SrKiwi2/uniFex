package com.usic.uniFex.controller.responsables;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import com.usic.uniFex.anotacion.ValidarUsuarioAutenticado;
import com.usic.uniFex.model.IService.IResponsableService;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
@RequestMapping("/administracion/responsables")
public class ResponsablesController {

    private final IResponsableService responsableService;
    
    @ValidarUsuarioAutenticado
    @GetMapping("/vista")
    public String vista_responsables() {
        return "responsables/vista";
    }

    @RequestMapping(value = "/tabla-registros", method = {RequestMethod.GET, RequestMethod.POST})
    public String tabla_responsbales(Model model) {
        model.addAttribute("responsables", responsableService.listarParaTabla());
        return "responsables/tabla_registro";
    }

    /* RESPONSABLES RUEDA */
    @ValidarUsuarioAutenticado
    @GetMapping("/vistaRA")
    public String vista_responsablesR() {
        return "responsable_rueda/vista";
    }

    @RequestMapping(value = "/tabla-registrosR", method = {RequestMethod.GET, RequestMethod.POST})
    public String tabla_responsbalesR(Model model) {
        model.addAttribute("responsables", responsableService.listarVista());
        return "responsable_rueda/tabla_registro";
    }
}
