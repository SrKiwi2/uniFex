package com.usic.uniFex.controller.promotores;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import com.usic.uniFex.anotacion.ValidarUsuarioAutenticado;
import com.usic.uniFex.model.IService.IAdministrativoService;
import com.usic.uniFex.model.IService.IResponsableService;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
@RequestMapping("/administracion/promotores")
public class PromotoresController {

    private final IResponsableService responsableService;
    
    @ValidarUsuarioAutenticado
    @GetMapping("/vista")
    public String vista_promotores() {
        return "administrativos/vista";
    }

    @RequestMapping(value = "/tabla-registros", method = {RequestMethod.GET, RequestMethod.POST})
    public String tabla_promotores(Model model) {
        model.addAttribute("promotores", responsableService.listarParaTabla());
        return "administrativos/tabla_registro";
    }
}
