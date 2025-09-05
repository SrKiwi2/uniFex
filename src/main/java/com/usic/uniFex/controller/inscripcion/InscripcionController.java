package com.usic.uniFex.controller.inscripcion;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import com.usic.uniFex.anotacion.ValidarUsuarioAutenticado;
import com.usic.uniFex.model.IService.IInscripcionService;
import com.usic.uniFex.model.dto.InscripcionListadoDTO;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
@RequestMapping("/administracion/inscripcion")
public class InscripcionController {
    
    private final IInscripcionService inscripcionService;

    @ValidarUsuarioAutenticado
    @GetMapping("/vista")
    public String vista_i() {
        return "inscripciones/vista";
    }

    @RequestMapping(value = "/tabla-registros", method = {RequestMethod.GET, RequestMethod.POST})
    public String tabla(Model model) {
        List<InscripcionListadoDTO> inscripciones = inscripcionService.listarParaTabla();
        model.addAttribute("inscripciones", inscripciones);
        return "inscripciones/tabla_registro";
    }
}
