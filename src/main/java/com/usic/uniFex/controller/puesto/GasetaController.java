package com.usic.uniFex.controller.puesto;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import com.usic.uniFex.anotacion.ValidarUsuarioAutenticado;
import com.usic.uniFex.model.IService.IPuestoService;
import com.usic.uniFex.model.entity.Puesto;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
@RequestMapping("/administracion/gaseta")
public class GasetaController {
    
    private final IPuestoService puestoService;

    @ValidarUsuarioAutenticado
    @GetMapping("/vista")
    public String vista_puesto() {
        return "gasetas/vista";
    }

    @RequestMapping(value = "/tabla-registros", method = {RequestMethod.GET, RequestMethod.POST})
    public String tablaRegistros(Model model) {
        List<Puesto> puestos = puestoService.listarConCategoria();
        model.addAttribute("puestos", puestos);
       return "gasetas/tabla_registro";
    }

    @GetMapping
    public String vistaPrincipal() {
        return "administracion/gaseta/index";
    }
}
